package uk.co.traynor.privategallery.core.evidence

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.io.*
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipInputStream
import uk.co.traynor.privategallery.core.vault.*

class EvidenceArchive179Test {
 internal class Fixture(val owner:EvidenceCatalog.Owner=EvidenceCatalog.Owner.PRIMARY,val restricted:Boolean=false) {
  val bytes="exact original\nwith UTF8 café".toByteArray();val id=UUID.randomUUID().toString();val source=UUID.randomUUID().toString();var active=true;var retireAfterRead=false
  val hash=MessageDigest.getInstance("SHA-256").digest(bytes)
  val meta=EvidenceCatalog.Metadata("source café.txt",0,2,3,0,0,0,mapOf("sourceNameProvenance" to "Provider claim"),restricted)
  val item=VaultItem(source,"text/plain","source",1,bytes.size.toLong(),hash,ByteArray(12),VaultItemState.COMPLETE,vaultOnly=restricted)
  val ocr=EvidenceCatalog.Ocr(UUID.randomUUID().toString(),4,"UTF8_DOCUMENT",0,0,0,"raw OCR",-1f,emptyList(),emptyList(),1,false)
  val record=EvidenceCatalog.Evidence(id,source,"title","text/plain",hash.joinToString(""){"%02x".format(it.toInt() and 255)},bytes.size.toLong(),1,meta,"description","notes",listOf("tag"),emptyList(),listOf(ocr),listOf(EvidenceCatalog.Correction(UUID.randomUUID().toString(),ocr.id(),5,"corrected transcript")))
  var catalog=EvidenceCatalog.empty(owner).upsertEvidence(record,1,"IMPORT")
  val reader=object:EvidenceSourceReader {
   override val owner=this@Fixture.owner
   override fun current()=active
   override fun inspect(sourceId:String)=EvidenceOriginal(item,meta)
   override fun <T> readVerified(sourceId:String,consume:(EvidenceOriginal,InputStream)->T):T=consume(inspect(sourceId),ByteArrayInputStream(bytes)).also{if(retireAfterRead)active=false}
  }
  fun plan(plain:Boolean=false)=EvidenceExportPlan(catalog,emptyList(),listOf(id),EvidenceEgress.digest(catalog),plain)
 }
 private fun rejected(action:()->Unit){try{action();fail("invalid archive accepted")}catch(_:IOException){}catch(_:IllegalArgumentException){}catch(_:IllegalStateException){}catch(_:SecurityException){}}
 private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
 private val password get()="  verbatim café passphrase  ".toCharArray()
 @Test fun encryptedRoundTripBothOwnersAndExplicitPlaintextOriginalsExact() {
  for(owner in EvidenceCatalog.Owner.values()){
   val f=Fixture(owner);val output=ByteArrayOutputStream();val pass=password;val saved=pass.copyOf()
   val receipt=EvidenceArchive.write(f.plan(),f.reader,pass,output);assertTrue(receipt.encrypted);assertArrayEquals(saved,pass)
   EvidenceArchive.open(context,owner,ByteArrayInputStream(output.toByteArray()),pass){true}.use{opened->
    assertEquals(owner,opened.owner);assertEquals(1,opened.originalCount);assertEquals(receipt.sha256,opened.ciphertextSha256)
    opened.withReport{assertTrue(String(it.copyOfRange(0,5)).startsWith("%PDF-"))}
    rejected{opened.writePlaintext(ByteArrayOutputStream(),false){true}}
    val plain=ByteArrayOutputStream();assertFalse(opened.writePlaintext(plain,true){true}.encrypted)
    var original:ByteArray?=null;var metadata="";ZipInputStream(ByteArrayInputStream(plain.toByteArray())).use{z->while(true){val e=z.nextEntry?:break;val bytes=z.readBytes();if(e.name.startsWith("originals/"))original=bytes;if(e.name=="evidence.json")metadata=String(bytes)}}
    assertArrayEquals(f.bytes,original);assertTrue(metadata.contains("raw OCR"));assertTrue(metadata.contains("corrected transcript"));assertTrue(metadata.contains("Provider claim"))
   }
  }
 }
 @Test fun wrongPasswordLateTagTruncateHeaderAndForeignOwnerNeverReturnReport(){
  val f=Fixture();val out=ByteArrayOutputStream();EvidenceArchive.write(f.plan(),f.reader,password,out);val bytes=out.toByteArray()
  rejected{EvidenceArchive.open(context,f.owner,ByteArrayInputStream(bytes),"wrong password long enough".toCharArray()){true}}
  for(index in listOf(0,12,bytes.lastIndex)){val bad=bytes.copyOf();bad[index]=(bad[index].toInt() xor 1).toByte();rejected{EvidenceArchive.open(context,f.owner,ByteArrayInputStream(bad),password){true}}}
  rejected{EvidenceArchive.open(context,f.owner,ByteArrayInputStream(bytes.copyOf(bytes.size-1)),password){true}}
  rejected{EvidenceArchive.open(context,EvidenceCatalog.Owner.HIDDEN,ByteArrayInputStream(bytes),password){true}}
 }
 @Test fun deniedAdmissionProducesNoOutputAndRetainedRestrictionBlocksBothModes(){
  val f=Fixture();f.active=false;val out=ByteArrayOutputStream();rejected{EvidenceArchive.write(f.plan(),f.reader,password,out)};assertEquals(0,out.size())
  for(plain in listOf(false,true)){val r=Fixture(restricted=true);rejected{EvidenceArchive.write(r.plan(plain),r.reader,password,ByteArrayOutputStream())}
   val incident=EvidenceCatalog.Incident(UUID.randomUUID().toString(),"retained",0,0,"derived notes","",emptyList(),listOf(r.id),emptyList(),false,false,listOf(EvidenceEgress.fingerprint(r.record)))
   r.catalog=r.catalog.upsertIncident(incident,2,"LINK").removeEvidence(r.id,3)
   val plan=EvidenceExportPlan(r.catalog,listOf(incident.id()),emptyList(),EvidenceEgress.digest(r.catalog),plain)
   rejected{EvidenceArchive.write(plan,r.reader,password,ByteArrayOutputStream())}
  }
 }
 @Test fun retirementAfterOriginalReadFailsAndCloseIsIdempotent(){
  val f=Fixture();f.retireAfterRead=true;rejected{EvidenceArchive.write(f.plan(),f.reader,password,ByteArrayOutputStream())}
  val good=Fixture();val out=ByteArrayOutputStream();EvidenceArchive.write(good.plan(),good.reader,password,out)
  val opened=EvidenceArchive.open(context,good.owner,ByteArrayInputStream(out.toByteArray()),password){true};opened.close();opened.close();rejected{opened.withReport{}}
 }
 @Test fun fixedScryptGcmVectorPreservesWhitespaceUnicodeAndHeaderAad(){
  fun bytes(hex:String)=hex.chunked(2).map{it.toInt(16).toByte()}.toByteArray()
  val header=bytes("504745564944313700000001000080000000000800000001000102030405060708090a0b0c0d0e0f101112131415161718191a1b")
  val key=EvidenceArchiveCrypto.key("  café export passphrase  ".toCharArray(),header)
  try{
   assertEquals("249094999462050835ee330c7d7efbffeb222a0c4e209bdb149e5326ce81d0db",EvidenceArchive.hex(key))
   val out=ByteArrayOutputStream();val encrypted=EvidenceArchiveCrypto.Encrypt(out,key,header){true}
   encrypted.write("PrivateGallery179 known-answer plaintext\n".toByteArray());encrypted.finish()
   assertEquals("adb397bd6dc6c7466d187a759abc4d12abcee3e3141620590ffd477654d81631026881bb67afd60f004aa65b723524883d399cbf3f1df5f301",EvidenceArchive.hex(out.toByteArray()))
   EvidenceArchiveCrypto.Decrypt(ByteArrayInputStream(out.toByteArray()),key,header){true}.use{assertEquals("PrivateGallery179 known-answer plaintext\n",String(it.readBytes()))}
  }finally{key.fill(0)}
 }
 @Test fun cancelledOpenAndMalformedHeaderDeleteCiphertextStagesAndNeverCloseCallerStreams(){
  val directory=File(context.noBackupFilesDir,"evidence-open-primary");val before=directory.listFiles()?.map{it.name}?.toSet().orEmpty()
  rejected{EvidenceArchive.open(context,EvidenceCatalog.Owner.PRIMARY,ByteArrayInputStream(ByteArray(52)),password){true}}
  assertEquals(before,directory.listFiles()?.map{it.name}?.toSet().orEmpty())
  val f=Fixture();var closed=false;val out=object:ByteArrayOutputStream(){override fun close(){closed=true;super.close()}}
  EvidenceArchive.write(f.plan(),f.reader,password,out);assertFalse(closed)
  var active=true
  val input=object:ByteArrayInputStream(out.toByteArray()){override fun read(b:ByteArray,off:Int,len:Int):Int=super.read(b,off,len).also{active=false}}
  rejected{EvidenceArchive.open(context,f.owner,input,password){active}}
  assertEquals(before,directory.listFiles()?.map{it.name}?.toSet().orEmpty())
 }

 private fun encryptedPayload(payload:ByteArray):ByteArray {
  val header=EvidenceArchiveFormat.header(ByteArray(16){it.toByte()},ByteArray(12){(it+16).toByte()})
  val key=EvidenceArchiveCrypto.key(password,header);val out=ByteArrayOutputStream();out.write(header)
  try{val cipher=EvidenceArchiveCrypto.Encrypt(out,key,header){true};cipher.write(payload);cipher.finish();return out.toByteArray()}finally{key.fill(0)}
 }
 private fun archiveEntries(f:Fixture):LinkedHashMap<String,ByteArray>{
  val out=ByteArrayOutputStream();EvidenceArchive.write(f.plan(true),f.reader,password,out);val entries=linkedMapOf<String,ByteArray>()
  ZipInputStream(ByteArrayInputStream(out.toByteArray())).use{z->while(true){val entry=z.nextEntry?:break;entries[entry.name]=z.readBytes()}};return entries
 }
 private fun packed(entries:Map<String,ByteArray>):ByteArray {
  val out=ByteArrayOutputStream();java.util.zip.ZipOutputStream(out).use{z->for((name,bytes) in entries){z.putNextEntry(java.util.zip.ZipEntry(name));z.write(bytes);z.closeEntry()}};return out.toByteArray()
 }
 @Test fun fullTagAuthenticationPrecedesEvenInvalidZipParsing(){
  val encrypted=encryptedPayload("not a ZIP and never a report".toByteArray());encrypted[encrypted.lastIndex]=(encrypted.last().toInt() xor 1).toByte()
  try{EvidenceArchive.open(context,EvidenceCatalog.Owner.PRIMARY,ByteArrayInputStream(encrypted),password){true};fail("late tag accepted")}
  catch(expected:IOException){assertEquals("Evidence authentication failed",expected.message)}
 }
 @Test fun strictSchemaOwnerDepthUnknownKeysRestrictionHashAndExtraEntriesRejectAfterValidCrypto(){
  val f=Fixture()
  for(mutate in listOf<(LinkedHashMap<String,ByteArray>)->Unit>(
   {it["evidence.json"]=String(it.getValue("evidence.json")).replace("\"owner\":\"PRIMARY\"","\"owner\":\"HIDDEN\"").toByteArray()},
   {it["evidence.json"]=("[".repeat(17)+"0"+"]".repeat(17)).toByteArray()},
   {it["evidence.json"]=org.json.JSONObject(String(it.getValue("evidence.json"))).put("unknown",true).toString().toByteArray()},
   {it["hashes.json"]="{}".toByteArray()},
   {it["originals/${f.source}.bin"]="changed original".toByteArray()},
   {it["extra.txt"]="extra".toByteArray()},
   {it.remove("originals/${f.source}.bin")},
   {val restricted=Fixture(restricted=true);it["evidence.json"]=EvidenceArchive.metadataBytes(restricted.catalog,listOf(EvidenceEgress.fingerprint(restricted.record)))}
  )){val entries=archiveEntries(f);mutate(entries);val encrypted=encryptedPayload(packed(entries));rejected{EvidenceArchive.open(context,f.owner,ByteArrayInputStream(encrypted),password){true}}}
 }
 @Test fun perEntryMetadataTranscriptAndPdfBoundsRejectBeforePublishing(){
  val f=Fixture()
  for((name,limit) in listOf("evidence.json" to EvidenceArchiveFormat.META,"hashes.json" to EvidenceArchiveFormat.META,"transcript.txt" to EvidenceArchiveFormat.TRANSCRIPT,"report.pdf" to EvidenceArchiveFormat.REPORT)){
   val entries=archiveEntries(f);entries[name]=ByteArray(limit+1);val encrypted=encryptedPayload(packed(entries))
   rejected{EvidenceArchive.open(context,f.owner,ByteArrayInputStream(encrypted),password){true}}
  }
 }

 @Test fun advisoryAiAndUnattachedRetainedLineageStayLabelledWithoutReattachingOriginals(){
  val f=Fixture();val raw="""{"summary":"Advisory finding","suggestions":[],"events":[],"_receipt":{"status":"completed","refusal":false,"cachedInputTokens":0,"advisory":true,"label":"ADVISORY AI — COMPLETED. Owner review required; no changes applied. Cached input tokens: 0."}}"""
  val analysis=EvidenceCatalog.Analysis(UUID.randomUUID().toString(),5,"test model","SUMMARY","0".repeat(64),listOf(f.id),raw,10,2,false,listOf(EvidenceEgress.fingerprint(f.record)))
  val incident=EvidenceCatalog.Incident(UUID.randomUUID().toString(),"selected incident",0,0,"","",emptyList(),listOf(f.id),emptyList(),false,false,listOf(EvidenceEgress.fingerprint(f.record)))
  f.catalog=f.catalog.saveAnalysis(analysis,5).upsertIncident(incident,6,"LINK").removeEvidence(f.id,7)
  val plan=EvidenceExportPlan(f.catalog,listOf(incident.id()),emptyList(),EvidenceEgress.digest(f.catalog),false)
  val selected=EvidenceArchive.select(plan,f.reader);val text=EvidencePdf.text(selected)
  assertTrue(text.contains("ADVISORY AI"));assertTrue(text.contains("Advisory finding"));assertTrue(text.contains("not attached"));assertTrue(selected.originals.isEmpty())
  val encrypted=ByteArrayOutputStream();EvidenceArchive.write(plan,f.reader,password,encrypted)
  EvidenceArchive.open(context,f.owner,ByteArrayInputStream(encrypted.toByteArray()),password){true}.use{assertEquals(0,it.originalCount)}
 }

}
