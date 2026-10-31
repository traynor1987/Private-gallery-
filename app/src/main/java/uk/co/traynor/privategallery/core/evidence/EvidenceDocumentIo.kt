package uk.co.traynor.privategallery.core.evidence

import android.content.Context
import android.net.Uri
import android.os.CancellationSignal
import android.os.Looper
import android.provider.DocumentsContract
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** Picker-granted IO only. Cancellation admission is immediate; provider close never blocks Main. */
internal class EvidenceDocumentIo(private val context:Context,private val access:EvidenceAccess,private val operationCurrent:()->Boolean={true}):AutoCloseable {
 private val ended=AtomicBoolean()
 private val admission=Any()
 private val signal=CancellationSignal()
 private val resources=LinkedHashSet<AutoCloseable>()
 private var retirement:AutoCloseable?=null
 init{retirement=access.onRetire{close()};if(ended.get())retirement?.close()}
 fun current()=!ended.get()&&operationCurrent()&&access.current()
 private fun checkCurrent(){check(current()){"Document operation cancelled"}}
 private fun own(resource:AutoCloseable){
  val accepted=synchronized(admission){if(ended.get())false else{resources.add(resource);true}}
  if(!accepted){cleanup.execute{runCatching{resource.close()}};error("Document operation cancelled")}
 }
 private fun release(resource:AutoCloseable){synchronized(admission){resources.remove(resource)};resource.close()}
 fun <T> read(uri:Uri,consume:(InputStream)->T):T {
  check(Looper.myLooper()!==Looper.getMainLooper());require(uri.scheme=="content");checkCurrent()
  val descriptor=checkNotNull(context.contentResolver.openAssetFileDescriptor(uri,"r",signal));own(descriptor)
  try {
   val input=descriptor.createInputStream();own(input)
   try {
    checkCurrent()
    val guarded=object:java.io.FilterInputStream(input){
     override fun read():Int{checkCurrent();return super.read().also{checkCurrent()}}
     override fun read(buffer:ByteArray,offset:Int,length:Int):Int{checkCurrent();return super.read(buffer,offset,length).also{checkCurrent()}}
     override fun close(){}
    }
    return consume(guarded).also{checkCurrent()}
   }finally{release(input)}
  }finally{release(descriptor)}
 }
 fun <T> write(uri:Uri,protectedSource:Uri?=null,consume:(OutputStream)->T):T {
  check(Looper.myLooper()!==Looper.getMainLooper());require(uri.scheme=="content");checkCurrent()
  // Establish destination ownership before opening with truncation. A provider may return
  // the incoming archive itself, including a tree/document or canonical URI alias.
  require(protectedSource==null||distinctDestination(context,uri,protectedSource)){"Choose a provably different destination; the original archive is protected"}
  checkCurrent()
  try {
   val descriptor=checkNotNull(context.contentResolver.openAssetFileDescriptor(uri,"wt",signal));own(descriptor)
   try {
    val output=descriptor.createOutputStream();own(output)
    try {
     checkCurrent()
     val guarded=object:java.io.FilterOutputStream(output){
      override fun write(value:Int){checkCurrent();out.write(value);checkCurrent()}
      override fun write(buffer:ByteArray,offset:Int,length:Int){checkCurrent();out.write(buffer,offset,length);checkCurrent()}
      override fun flush(){checkCurrent();out.flush();checkCurrent()}
      override fun close(){}
     }
     val result=consume(guarded);guarded.flush();checkCurrent();return result
    }finally{release(output)}
   }finally{release(descriptor)}
  }catch(failure:Throwable){requestPartialCleanup(context,uri,protectedSource);throw failure}
 }
 override fun close(){
  if(!ended.compareAndSet(false,true))return
  retirement?.close()
  val owned=synchronized(admission){resources.toList().also{resources.clear()}}
  cleanup.execute{runCatching{signal.cancel()};owned.reversed().forEach{runCatching{it.close()}}}
 }
 companion object {
  private val cleanup=Executors.newCachedThreadPool{r->Thread(r,"evidence-document-cleanup").apply{isDaemon=true}}
  internal fun distinctDestination(context:Context,first:Uri,second:Uri):Boolean {
   fun matches(a:Uri,b:Uri):Boolean {
    if(a.normalizeScheme()==b.normalizeScheme())return true
    if(a.authority!=b.authority)return false
    val left=runCatching{DocumentsContract.getDocumentId(a)}.getOrNull()
    val right=runCatching{DocumentsContract.getDocumentId(b)}.getOrNull()
    return left!=null&&left==right
   }
   if(matches(first,second))return false
   val canonicalFirst=runCatching{context.contentResolver.canonicalize(first)}.getOrNull()
   val canonicalSecond=runCatching{context.contentResolver.canonicalize(second)}.getOrNull()
   if(canonicalFirst!=null&&matches(canonicalFirst,second)||canonicalSecond!=null&&matches(first,canonicalSecond))return false
   if(canonicalFirst!=null&&canonicalSecond!=null&&matches(canonicalFirst,canonicalSecond))return false
   if(!first.authority.isNullOrBlank()&&!second.authority.isNullOrBlank()&&first.authority!=second.authority)return true
   val left=runCatching{DocumentsContract.getDocumentId(first)}.getOrNull()
   val right=runCatching{DocumentsContract.getDocumentId(second)}.getOrNull()
   if(first.authority==second.authority&&left!=null&&right!=null)return left!=right
   // Non-document provider aliases require both canonical identity claims. Unknown
   // identity denies both truncation and cleanup rather than risking the incoming file.
   return canonicalFirst!=null&&canonicalSecond!=null&&!matches(canonicalFirst,canonicalSecond)
  }
  fun requestPartialCleanup(context:Context,uri:Uri,protectedSource:Uri?=null){cleanup.execute{
   runCatching{if(protectedSource==null||distinctDestination(context,uri,protectedSource))DocumentsContract.deleteDocument(context.contentResolver,uri)}
  }}
 }
}
