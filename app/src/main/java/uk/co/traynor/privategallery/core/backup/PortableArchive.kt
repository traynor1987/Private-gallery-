package uk.co.traynor.privategallery.core.backup

import java.io.*
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import uk.co.traynor.privategallery.core.crypto.RecoveryEnvelope
import uk.co.traynor.privategallery.core.crypto.RecoveryKey
import uk.co.traynor.privategallery.core.crypto.RecoveryWrappedKey

/** Bounded authenticated records. Callers must validate the complete archive before installing data.
 * No CipherInputStream: every frame's tag is checked before any plaintext is delivered. */
internal object PortableArchive {
 const val MAX_TOTAL=100L*1024*1024*1024
 const val MAX_ITEM=8L*1024*1024*1024
 const val MAX_ENTRIES=12000
 private const val CHUNK=64*1024
 private val MAGIC="NOVABK01".toByteArray(Charsets.US_ASCII)
 private const val HEADER_SIZE=8+16+12+48
 private const val META=1
 private const val DATA=2
 private const val END=3
 private const val COMPLETE=4
 data class Entry(val metadata:ByteArray,val write:(OutputStream)->Unit)
 private fun checkCurrent(current:()->Boolean){check(current()){ "Backup interrupted; unlock this Space again" }}
 fun write(output:OutputStream, secret:CharArray, entries:List<Entry>, current:()->Boolean={true}) {
  val key=ByteArray(32).also(SecureRandom()::nextBytes)
  try {
   checkCurrent(current);require(entries.size<=MAX_ENTRIES)
   val normalized=RecoveryKey.normalise(secret)
   val envelope=try{require(normalized.size in 16..512){"Use the generated recovery key"};RecoveryEnvelope.create(normalized,key)}finally{normalized.fill('\u0000')}
   val header=MAGIC+envelope.salt+envelope.nonce+envelope.ciphertext
   output.write(header)
   val frames=Frames(key,header,current)
   var total=0L
   entries.forEach {entry->
    require(entry.metadata.size in 1..CHUNK)
    frames.write(output,META,entry.metadata)
    var count=0L
    val digest=MessageDigest.getInstance("SHA-256")
    val buffer=ByteArray(CHUNK);var used=0
    fun flushBlock(){if(used>0){frames.write(output,DATA,buffer.copyOf(used));used=0}}
    val stream=object:OutputStream(){
     override fun write(value:Int){write(byteArrayOf(value.toByte()),0,1)}
     override fun write(bytes:ByteArray,offset:Int,length:Int){
      checkCurrent(current);require(offset>=0&&length>=0&&offset<=bytes.size-length)
      count=Math.addExact(count,length.toLong());total=Math.addExact(total,length.toLong());require(count<=MAX_ITEM&&total<=MAX_TOTAL)
      digest.update(bytes,offset,length)
      var start=offset;var remaining=length
      while(remaining>0){val take=minOf(CHUNK-used,remaining);bytes.copyInto(buffer,used,start,start+take);used+=take;start+=take;remaining-=take;if(used==CHUNK)flushBlock()}
     }
     override fun close()=Unit
    }
    try {entry.write(stream);flushBlock();frames.write(output,END,ByteBuffer.allocate(40).putLong(count).put(digest.digest()).array())}
    finally {buffer.fill(0)}
   }
   frames.write(output,COMPLETE,ByteBuffer.allocate(12).putInt(entries.size).putLong(total).array());output.flush();checkCurrent(current)
  }finally {key.fill(0);secret.fill('\u0000')}
 }
 fun read(input:InputStream, secret:CharArray, current:()->Boolean={true}, consume:(ByteArray,InputStream)->Unit) {
  var key:ByteArray?=null
  try {
   checkCurrent(current)
   val source=DataInputStream(input);val header=ByteArray(HEADER_SIZE);source.readFully(header)
   require(header.copyOfRange(0,8).contentEquals(MAGIC)){"Unsupported backup format"}
   require(secret.size<=1024)
   key=RecoveryEnvelope.unwrap(secret,RecoveryWrappedKey(header.copyOfRange(8,24),header.copyOfRange(24,36),header.copyOfRange(36,84)))
   val frames=Frames(key,header,current)
   var entries=0;var total=0L
   while(true){
    val (type,metadata)=frames.read(source)
    if(type==COMPLETE){
     try {require(metadata.size==12);val complete=ByteBuffer.wrap(metadata);require(complete.int==entries&&complete.long==total);require(source.read()==-1){"Trailing archive data"};checkCurrent(current);return}
     finally{metadata.fill(0)}
    }
    if(type!=META||metadata.isEmpty()||++entries>MAX_ENTRIES){metadata.fill(0);error("Invalid archive entry")}
    var count=0L;var ended=false;var bytes=ByteArray(0);var position=0
    val digest=MessageDigest.getInstance("SHA-256")
    val stream=object:InputStream(){
     override fun read():Int {val single=ByteArray(1);return try {if(read(single,0,1)<0)-1 else single[0].toInt()and 255}finally{single.fill(0)}}
     override fun read(target:ByteArray,offset:Int,length:Int):Int{
      checkCurrent(current);require(offset>=0&&length>=0&&offset<=target.size-length);if(length==0)return 0
      if(position==bytes.size){
       bytes.fill(0);bytes=ByteArray(0);position=0
       if(ended)return -1
       val frame=frames.read(source)
       if(frame.first==END){
        try {require(frame.second.size==40);val end=ByteBuffer.wrap(frame.second);require(end.long==count);val hash=ByteArray(32);end.get(hash);require(MessageDigest.isEqual(hash,digest.digest())){"Item integrity failed"};ended=true;return -1}
        finally {frame.second.fill(0)}
       }
       require(frame.first==DATA&&frame.second.isNotEmpty()){"Incomplete archive item"}
       bytes=frame.second;count=Math.addExact(count,bytes.size.toLong());total=Math.addExact(total,bytes.size.toLong());require(count<=MAX_ITEM&&total<=MAX_TOTAL);digest.update(bytes)
      }
      val take=minOf(length,bytes.size-position);bytes.copyInto(target,offset,position,position+take);position+=take;checkCurrent(current);return take
     }
     override fun close()=Unit
    }
    try {consume(metadata,stream);val drain=ByteArray(CHUNK);try{while(stream.read(drain)>=0){} }finally{drain.fill(0)};check(ended)}
    finally {metadata.fill(0);bytes.fill(0)}
   }
  }finally {key?.fill(0);secret.fill('\u0000')}
 }
 private class Frames(val key:ByteArray,header:ByteArray,val current:()->Boolean){
  private val headerHash=MessageDigest.getInstance("SHA-256").digest(header)
  private var sequence=0L
  private fun cipher(mode:Int,type:Int,size:Int):Cipher {
   // Each archive has an independent random key; a monotonic 96-bit nonce is unique within it.
   val nonce=ByteBuffer.allocate(12).putInt(0).putLong(sequence).array()
   return Cipher.getInstance("AES/GCM/NoPadding").apply{
    init(mode,SecretKeySpec(key,"AES"),GCMParameterSpec(128,nonce))
    updateAAD(headerHash);updateAAD(ByteBuffer.allocate(16).putLong(sequence).putInt(type).putInt(size).array())
   }
  }
  fun write(output:OutputStream,type:Int,plain:ByteArray){
   checkCurrent(current);require(plain.size<=CHUNK)
   val encrypted=cipher(Cipher.ENCRYPT_MODE,type,plain.size).doFinal(plain)
   try {DataOutputStream(output).apply{writeByte(type);writeInt(encrypted.size);write(encrypted)};sequence=Math.addExact(sequence,1);checkCurrent(current)}finally{encrypted.fill(0);if(type==DATA)plain.fill(0)}
  }
  fun read(input:DataInputStream):Pair<Int,ByteArray>{
   checkCurrent(current);val type=input.readUnsignedByte();require(type in META..COMPLETE)
   val size=input.readInt();require(size in 16..CHUNK+16)
   val encrypted=ByteArray(size)
   try {input.readFully(encrypted);val plain=cipher(Cipher.DECRYPT_MODE,type,size-16).doFinal(encrypted);sequence=Math.addExact(sequence,1);try{checkCurrent(current);return type to plain}catch(failure:Throwable){plain.fill(0);throw failure}}
   finally{encrypted.fill(0)}
  }
 }
}
