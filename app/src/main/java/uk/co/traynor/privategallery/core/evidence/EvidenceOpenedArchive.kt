package uk.co.traynor.privategallery.core.evidence

import android.content.Context
import android.system.Os
import java.io.*
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean

/** Owns one sealed ciphertext descriptor, key and bounded authenticated report. No plaintext files. */
internal class EvidenceOpenedArchive private constructor(
 val owner:EvidenceCatalog.Owner,private val stage:File,private val sealed:FileInputStream,
 private val header:ByteArray,private val key:ByteArray,private val report:ByteArray,
 val originalCount:Int,val ciphertextSha256:String,private val originatingCurrent:()->Boolean,
):AutoCloseable {
 private val closed=AtomicBoolean()
 val reportBytes:Int get()=report.size
 val integritySummary:String get()="Authenticated AES-256-GCM container; $originalCount byte-verified original(s); SHA-256 index verified. Hashes do not certify authenticity."
 private fun active()= !closed.get()&&originatingCurrent()
 @Synchronized fun withReport(consume:(ByteArray)->Unit){check(active());consume(report);check(active())}
 private fun decrypt(current:()->Boolean):EvidenceArchiveCrypto.Decrypt {
  check(current());sealed.channel.position(EvidenceArchiveFormat.HEADER.toLong())
  return EvidenceArchiveCrypto.Decrypt(object:FilterInputStream(sealed){override fun close(){}},key,header){current()}
 }
 private fun drain(current:()->Boolean){decrypt(current).use{input->val buffer=ByteArray(32768);try{while(true){check(current());val n=input.read(buffer);if(n<0)break}}finally{buffer.fill(0)}}}
 @Synchronized fun writePlaintext(output:OutputStream,confirmed:Boolean,current:()->Boolean):EvidenceExportReceipt {
  EvidenceArchive.offMain();require(confirmed){"Explicit plaintext confirmation required"};val allowed={active()&&current()}
  check(allowed());drain(allowed) // Complete fresh authentication before the destination receives any plaintext.
  val receipt=EvidenceArchive.ReceiptOutput(output,allowed)
  decrypt(allowed).use{input->val buffer=ByteArray(32768);try{while(true){check(allowed());val n=input.read(buffer);if(n<0)break;receipt.write(buffer,0,n)}}finally{buffer.fill(0)}}
  check(allowed());receipt.flush();return receipt.receipt(false)
 }
 override fun close(){if(closed.compareAndSet(false,true))synchronized(this){try{sealed.close()}finally{key.fill(0);header.fill(0);report.fill(0);stage.delete()}}}
 companion object {
  fun open(context:Context,owner:EvidenceCatalog.Owner,input:InputStream,passphrase:CharArray,current:()->Boolean):EvidenceOpenedArchive {
   EvidenceArchive.offMain();check(current())
   val directory=File(context.noBackupFilesDir,"evidence-open-${owner.name.lowercase()}")
   require(directory.isDirectory||directory.mkdirs());Os.chmod(directory.absolutePath,448) // 0700
   val stage=File.createTempFile("encrypted-",".stage",directory);var sealed:FileInputStream?=null;var key:ByteArray?=null;var report:ByteArray?=null;var header:ByteArray?=null;var transferred=false
   try {
    Os.chmod(stage.absolutePath,384) // 0600, exclusive creation; disk contains only ciphertext.
    val hash=MessageDigest.getInstance("SHA-256");var total=0L
    FileOutputStream(stage).use{output->
     val fixed=ByteArray(EvidenceArchiveFormat.HEADER);var at=0
     while(at<fixed.size){check(current());val n=input.read(fixed,at,fixed.size-at);require(n>0){"Truncated evidence header"};at+=n}
     EvidenceArchiveFormat.validateHeader(fixed);header=fixed;output.write(fixed);hash.update(fixed);total=fixed.size.toLong()
     val buffer=ByteArray(32768)
     try{while(true){check(current());val n=input.read(buffer);if(n<0)break;if(n==0)continue;require(total<=EvidenceArchiveFormat.MAX_CIPHER-n){"Evidence container too large"};output.write(buffer,0,n);hash.update(buffer,0,n);total+=n};check(current());output.fd.sync()}finally{buffer.fill(0)}
    }
    require(total>=EvidenceArchiveFormat.HEADER+16);Os.chmod(stage.absolutePath,256) // 0400; never reopen a provider between passes.
    sealed=FileInputStream(stage);key=EvidenceArchiveCrypto.key(passphrase,header!!);check(current())
    fun decrypt():EvidenceArchiveCrypto.Decrypt {
     check(current());sealed!!.channel.position(EvidenceArchiveFormat.HEADER.toLong())
     return EvidenceArchiveCrypto.Decrypt(object:FilterInputStream(sealed!!){override fun close(){}},key!!,header!!){current()}
    }
    // Pass 1: no ZIP/parser/report allocation until complete authentication, including the final tag.
    decrypt().use{authenticated->val discard=ByteArray(32768);try{while(true){check(current());if(authenticated.read(discard)<0)break}}finally{discard.fill(0)}}
    val held=linkedMapOf<String,ByteArray>();val observed=linkedMapOf<String,Pair<Long,String>>();var originalEntries=0
    try {
     // Pass 2: strict ZIP grammar drains every entry and completes GCM before anything is returned.
     decrypt().use{authenticated->EvidenceArchiveFormat.readZip(authenticated,{current()}){name,entry->
      val limit=when(name){"report.pdf"->EvidenceArchiveFormat.REPORT;"evidence.json","hashes.json"->EvidenceArchiveFormat.META;"transcript.txt"->EvidenceArchiveFormat.TRANSCRIPT;else->{require(name.matches(Regex("originals/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.bin"))){"Unexpected archive entry"};0}}
      if(limit==0){originalEntries++;require(originalEntries<=128)}
      val digest=MessageDigest.getInstance("SHA-256");var count=0L;val memory=if(limit>0)WipingMemory(limit)else null;val buffer=ByteArray(32768)
      try{while(true){check(current());val n=entry.read(buffer);if(n<0)break;if(n==0)continue;count+=n;require(count<=if(limit>0)limit.toLong()else EvidenceArchiveFormat.MAX_EXPANDED);digest.update(buffer,0,n);memory?.write(buffer,0,n)}
       memory?.let{held[name]=it.toByteArray()};observed[name]=count to EvidenceArchive.hex(digest.digest())
      }finally{memory?.wipe();buffer.fill(0)}
     }}
     check(current());require(held.keys==setOf("evidence.json","report.pdf","transcript.txt","hashes.json"))
     require(held.getValue("evidence.json").size<=EvidenceArchiveFormat.META-held.getValue("hashes.json").size)
     val (catalog,sources)=EvidenceArchive.decodeMetadata(held.getValue("evidence.json"),owner)
     val originals=catalog.evidence().distinctBy{it.sourceId()};val paths=originals.map{EvidenceArchive.path(it.sourceId())}.toSet()
     require(observed.keys==paths+held.keys)
     for(e in originals)require(observed[EvidenceArchive.path(e.sourceId())]==(e.size() to e.sha256())){"Original hash mismatch"}
     val docs=linkedMapOf<String,Pair<Long,String>>()
     for(name in listOf("evidence.json","report.pdf","transcript.txt"))docs[name]=observed.getValue(name)
     val manifest=held.getValue("hashes.json");EvidenceStrictJson.validate(EvidenceArchive.utf8(manifest),16,EvidenceArchiveFormat.META)
     val expected=EvidenceArchive.hashes(catalog,sources,docs)
     try{require(MessageDigest.isEqual(manifest,expected)){"Invalid hash manifest"}}finally{expected.fill(0)}
     val pdf=held.getValue("report.pdf");require(pdf.size>=5&&String(pdf,0,5,Charsets.US_ASCII)=="%PDF-")
     val fakePlan=EvidenceExportPlan(catalog,catalog.incidents().map{it.id()},catalog.evidence().map{it.id()},"",false)
     val transcript=EvidencePdf.text(EvidenceArchive.Selected(fakePlan,catalog,sources)).toByteArray(Charsets.UTF_8)
     try{require(MessageDigest.isEqual(transcript,held.getValue("transcript.txt"))){"Transcript differs from selected annotations"}}finally{transcript.fill(0)}
     check(current());report=held.remove("report.pdf")!!
     val result=EvidenceOpenedArchive(owner,stage,sealed!!,header!!,key!!,report!!,originals.size,EvidenceArchive.hex(hash.digest()),current)
     transferred=true;sealed=null;header=null;key=null;report=null;return result
    }finally{held.values.forEach{it.fill(0)}}
   }finally{key?.fill(0);header?.fill(0);report?.fill(0);try{sealed?.close()}finally{if(!transferred)stage.delete()}}
  }
  private class WipingMemory(private val maximum:Int):ByteArrayOutputStream(){
   override fun write(b:ByteArray,off:Int,len:Int){require(count<=maximum-len);super.write(b,off,len)}
   fun wipe(){buf.fill(0);reset()}
  }
 }
}
