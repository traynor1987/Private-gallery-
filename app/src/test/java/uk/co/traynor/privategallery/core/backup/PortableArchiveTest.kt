package uk.co.traynor.privategallery.core.backup

import org.junit.Assert.*
import org.junit.Test
import java.io.*

class PortableArchiveTest {
 private val secret="a replacement device recovery phrase 2026".toCharArray()
 private fun archive(payload:ByteArray=ByteArray(180000){(it%251).toByte()}):ByteArray=ByteArrayOutputStream().also {out->
  PortableArchive.write(out,secret.copyOf(),listOf(PortableArchive.Entry("same-name.jpg".toByteArray()){it.write(payload)},PortableArchive.Entry("same-name.jpg".toByteArray()){it.write(byteArrayOf(8,9))}))
 }.toByteArray()
 @Test fun roundtripIsPortableAndPreservesDuplicateNames(){
  val entries=mutableListOf<Pair<String,ByteArray>>()
  PortableArchive.read(ByteArrayInputStream(archive()),secret.copyOf()){metadata,input->entries+=metadata.decodeToString() to input.readBytes()}
  assertEquals(2,entries.size);assertEquals(entries[0].first,entries[1].first)
  assertArrayEquals(ByteArray(180000){(it%251).toByte()},entries[0].second);assertArrayEquals(byteArrayOf(8,9),entries[1].second)
 }
 @Test fun wrongCredentialFailsBeforeAnyMetadata(){
  var called=false
  assertThrows(Exception::class.java){PortableArchive.read(ByteArrayInputStream(archive()),"wrong credential".toCharArray()){_,_->called=true}}
  assertFalse(called)
 }
 @Test fun encryptedManifestDoesNotRevealNames(){assertFalse(archive().toString(Charsets.ISO_8859_1).contains("same-name.jpg"))}
 @Test fun truncationAndTrailingBytesNeverValidate(){
  val bytes=archive(byteArrayOf(1,2,3))
  for(size in listOf(0,12,92,bytes.size/2,bytes.size-1,bytes.size-20))assertThrows(Exception::class.java){PortableArchive.read(ByteArrayInputStream(bytes.copyOf(size)),secret.copyOf()){_,input->input.readBytes()}}
  assertThrows(Exception::class.java){PortableArchive.read(ByteArrayInputStream(bytes+byteArrayOf(0)),secret.copyOf()){_,input->input.readBytes()}}
 }
 @Test fun alteredFrameFails(){val bytes=archive();bytes[bytes.size/2]=(bytes[bytes.size/2].toInt() xor 1).toByte();assertThrows(Exception::class.java){PortableArchive.read(ByteArrayInputStream(bytes),secret.copyOf()){_,input->input.readBytes()}}}
 @Test fun retirementCancelsWriteAndRead(){
  var active=true
  assertThrows(Exception::class.java){PortableArchive.write(ByteArrayOutputStream(),secret.copyOf(),listOf(PortableArchive.Entry(byteArrayOf(1)){out->out.write(ByteArray(66000));active=false;out.write(ByteArray(66000))}),{active})}
  active=true
  assertThrows(Exception::class.java){PortableArchive.read(ByteArrayInputStream(archive()),secret.copyOf(),{active}){_,input->active=false;input.read()}}
 }
 @Test fun credentialsAreWipedOnFailure(){val chars=secret.copyOf();assertThrows(Exception::class.java){PortableArchive.read(ByteArrayInputStream(byteArrayOf(1)),chars){_,_->}};assertTrue(chars.all{it=='\u0000'})}
 private fun frames(bytes:ByteArray):List<ByteArray>{
  val frames=mutableListOf<ByteArray>();var at=84
  while(at<bytes.size){val size=java.nio.ByteBuffer.wrap(bytes,at+1,4).int;frames+=bytes.copyOfRange(at,at+5+size);at+=5+size}
  return frames
 }
 private fun refused(bytes:ByteArray){assertThrows(Exception::class.java){PortableArchive.read(ByteArrayInputStream(bytes),secret.copyOf()){_,input->input.readBytes()}}}
 @Test fun reorderedDuplicatedSplicedOrMissingCompletionFramesFail(){
  val bytes=archive();val header=bytes.copyOfRange(0,84);val pieces=frames(bytes)
  refused(header+pieces.flatMap{it.toList()}.toByteArray().let{it.copyOf(it.size-pieces.last().size)})
  val duplicate=pieces.toMutableList().apply{add(1,this[1])};refused(header+duplicate.flatMap{it.toList()}.toByteArray())
  val reordered=pieces.toMutableList().apply{java.util.Collections.swap(this,1,2)};refused(header+reordered.flatMap{it.toList()}.toByteArray())
  val other=frames(archive());val spliced=pieces.toMutableList().apply{this[1]=other[1]};refused(header+spliced.flatMap{it.toList()}.toByteArray())
 }
 @Test fun headerEnvelopeTypeAndOversizedLengthFail(){
  val bytes=archive(byteArrayOf(1))
  for(offset in listOf(0,9,25,37,84)){val bad=bytes.copyOf();bad[offset]=(bad[offset].toInt() xor 1).toByte();refused(bad)}
  val bad=bytes.copyOf();java.nio.ByteBuffer.wrap(bad,85,4).putInt(Int.MAX_VALUE);refused(bad)
 }
 @Test fun emptyArchiveHasAuthenticatedCompletion(){
  val bytes=ByteArrayOutputStream().also{PortableArchive.write(it,secret.copyOf(),emptyList())}.toByteArray()
  PortableArchive.read(ByteArrayInputStream(bytes),secret.copyOf()){_,_->fail("Empty archive emitted item")}
  refused(bytes.copyOf(84))
 }
 @Test fun largeStreamUsesBoundedCallbacks(){
  val file=java.nio.file.Files.createTempFile("portable-test-",".enc").toFile()
  try{
   val block=ByteArray(65536){(it%251).toByte()};val digest=java.security.MessageDigest.getInstance("SHA-256")
   FileOutputStream(file).use{PortableArchive.write(it,secret.copyOf(),listOf(PortableArchive.Entry(byteArrayOf(1)){out->repeat(192){out.write(block);digest.update(block)}}))}
   val expected=digest.digest();val actual=java.security.MessageDigest.getInstance("SHA-256");var size=0L
   FileInputStream(file).use{PortableArchive.read(it,secret.copyOf()){_,input->val buffer=ByteArray(8192);while(true){val count=input.read(buffer);if(count<0)break;size+=count;actual.update(buffer,0,count)}}}
   assertEquals(12L*1024*1024,size);assertArrayEquals(expected,actual.digest())
  }finally{file.delete()}
 }
 @Test fun destinationStorageFailureCannotCompleteAndWipesCredential(){
  val chars=secret.copyOf();var bytes=0
  val output=object:OutputStream(){override fun write(value:Int){if(++bytes>100)throw IOException("Storage full")}}
  assertThrows(IOException::class.java){PortableArchive.write(output,chars,listOf(PortableArchive.Entry(byteArrayOf(1)){it.write(ByteArray(1000))}))}
  assertTrue(chars.all{it=='\u0000'})
 }
}
