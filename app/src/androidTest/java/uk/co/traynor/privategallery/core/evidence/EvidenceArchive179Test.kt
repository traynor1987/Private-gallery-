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
 internal class Fixture(val owner:EvidenceCatalog.Owner=EvidenceCatalog.Owner.PRIMARY,val restricted:Boolean=false,initialBytes:ByteArray="exact original\nwith UTF8 café".toByteArray()) {
  var bytes=initialBytes;val id=UUID.randomUUID().toString();val source=UUID.randomUUID().toString();var active=true;var retireAfterRead=false
  val hash=MessageDigest.getInstance("SHA-256").digest(bytes)
  var meta=EvidenceCatalog.Metadata("source café.txt",0,2,3,0,0,0,linkedMapOf("z-last" to "last metadata claim","sourceNameProvenance" to "Provider claim","a-first" to "first metadata claim"),restricted)
  var item=VaultItem(source,"text/plain","source",1,bytes.size.toLong(),hash,ByteArray(12),VaultItemState.COMPLETE,vaultOnly=restricted)
  val ocr=EvidenceCatalog.Ocr(UUID.randomUUID().toString(),4,"UTF8_DOCUMENT",0,0,0,"raw OCR",-1f,emptyList(),emptyList(),1,false)
  var record=EvidenceCatalog.Evidence(id,source,"title","text/plain",hash.joinToString(""){"%02x".format(it.toInt() and 255)},bytes.size.toLong(),1,meta,"description","notes",listOf("tag"),emptyList(),listOf(ocr),listOf(EvidenceCatalog.Correction(UUID.randomUUID().toString(),ocr.id(),5,"corrected transcript")))
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
   val receipt=EvidenceArchive.write(f.plan(),f.reader,pass,output,context);assertTrue(receipt.encrypted);assertArrayEquals(saved,pass)
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
  val f=Fixture();val out=ByteArrayOutputStream();EvidenceArchive.write(f.plan(),f.reader,password,out,context);val bytes=out.toByteArray()
  rejected{EvidenceArchive.open(context,f.owner,ByteArrayInputStream(bytes),"wrong password long enough".toCharArray()){true}}
  for(index in listOf(0,12,bytes.lastIndex)){val bad=bytes.copyOf();bad[index]=(bad[index].toInt() xor 1).toByte();rejected{EvidenceArchive.open(context,f.owner,ByteArrayInputStream(bad),password){true}}}
  rejected{EvidenceArchive.open(context,f.owner,ByteArrayInputStream(bytes.copyOf(bytes.size-1)),password){true}}
  rejected{EvidenceArchive.open(context,EvidenceCatalog.Owner.HIDDEN,ByteArrayInputStream(bytes),password){true}}
 }
 @Test fun deniedAdmissionProducesNoOutputAndRetainedRestrictionBlocksBothModes(){
  val f=Fixture();f.active=false;val out=ByteArrayOutputStream();rejected{EvidenceArchive.write(f.plan(),f.reader,password,out,context)};assertEquals(0,out.size())
  for(plain in listOf(false,true)){val r=Fixture(restricted=true);rejected{EvidenceArchive.write(r.plan(plain),r.reader,password,ByteArrayOutputStream(),context)}
   val incident=EvidenceCatalog.Incident(UUID.randomUUID().toString(),"retained",0,0,"derived notes","",emptyList(),listOf(r.id),emptyList(),false,false,listOf(EvidenceEgress.fingerprint(r.record)))
   r.catalog=r.catalog.upsertIncident(incident,2,"LINK").removeEvidence(r.id,3)
   val plan=EvidenceExportPlan(r.catalog,listOf(incident.id()),emptyList(),EvidenceEgress.digest(r.catalog),plain)
   rejected{EvidenceArchive.write(plan,r.reader,password,ByteArrayOutputStream(),context)}
  }
 }
 @Test fun retirementAfterOriginalReadFailsAndCloseIsIdempotent(){
  val f=Fixture();f.retireAfterRead=true;rejected{EvidenceArchive.write(f.plan(),f.reader,password,ByteArrayOutputStream(),context)}
  val good=Fixture();val out=ByteArrayOutputStream();EvidenceArchive.write(good.plan(),good.reader,password,out,context)
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
  EvidenceArchive.write(f.plan(),f.reader,password,out,context);assertFalse(closed)
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
  val out=ByteArrayOutputStream();EvidenceArchive.write(f.plan(true),f.reader,password,out,context);val entries=linkedMapOf<String,ByteArray>()
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
   {it["evidence.json"]=org.json.JSONObject(String(it.getValue("evidence.json"))).put("version",1).toString().toByteArray()},
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
  val f=Fixture();val raw="""{"summary":"Advisory finding","suggestions":[{"kind":"GROUP","evidence_ids":["${f.id}"],"title":"Review","reasons":["Owner review"]}],"events":[{"evidence_ids":["${f.id}"],"title":"Visible event","date_time_quote":null,"provenance":"AI_INFERRED","uncertainty":"unknown date"}],"_receipt":{"status":"completed","refusal":false,"cachedInputTokens":0,"advisory":true,"label":"ADVISORY AI — COMPLETED. Owner review required; no changes applied. Cached input tokens: 0."}}"""
  val analysis=EvidenceCatalog.Analysis(UUID.randomUUID().toString(),5,"test model","SUMMARY","0".repeat(64),listOf(f.id),raw,10,2,false,listOf(EvidenceEgress.fingerprint(f.record)))
  val incident=EvidenceCatalog.Incident(UUID.randomUUID().toString(),"selected incident",0,0,"","",emptyList(),listOf(f.id),emptyList(),false,false,listOf(EvidenceEgress.fingerprint(f.record)))
  f.catalog=f.catalog.saveAnalysis(analysis,5).upsertIncident(incident,6,"LINK").associateAnalysis(analysis.id(),incident.id(),6).removeEvidence(f.id,7)
  val plan=EvidenceExportPlan(f.catalog,listOf(incident.id()),emptyList(),EvidenceEgress.digest(f.catalog),false)
  val selected=EvidenceArchive.select(plan,f.reader);val text=EvidencePdf.text(selected)
  assertTrue(text.contains("ADVISORY AI"));assertTrue(text.contains("Advisory finding"));assertTrue(text.contains("not attached"));assertTrue(selected.originals.isEmpty())
  val encrypted=ByteArrayOutputStream();EvidenceArchive.write(plan,f.reader,password,encrypted,context)
  EvidenceArchive.open(context,f.owner,ByteArrayInputStream(encrypted.toByteArray()),password){true}.use{assertEquals(0,it.originalCount)}
 }

 private fun entries(bytes:ByteArray):LinkedHashMap<String,ByteArray> = linkedMapOf<String,ByteArray>().also{result->
  ZipInputStream(ByteArrayInputStream(bytes)).use{z->while(true){val e=z.nextEntry?:break;result[e.name]=z.readBytes()}}
 }
 private fun rawAnalysis(ids:List<String>,summary:String="Retained A+B advisory"):String {
  val refs=ids.joinToString(","){"\"$it\""}
  return """{"summary":"$summary","suggestions":[{"kind":"GROUP","evidence_ids":[$refs],"title":"Review related sources","reasons":["Owner review required"]}],"events":[{"evidence_ids":[$refs],"title":"Historical source event","date_time_quote":"unknown","provenance":"AI_INFERRED","uncertainty":"Unverified chronology"}],"_receipt":{"status":"completed","refusal":false,"cachedInputTokens":0,"advisory":true,"label":"ADVISORY AI — COMPLETED. Owner review required; no changes applied. Cached input tokens: 0."}}"""
 }
 private class Multiple(val owner:EvidenceCatalog.Owner,count:Int=2,content:ByteArray?=null){
  val fixtures=List(count){if(content==null)Fixture(owner)else Fixture(owner,initialBytes=content)};var active=true;var streaming=false;var failSource:String?=null;var cancelSource:String?=null;var streamFailure:String?=null
  val reader=object:EvidenceSourceReader {
   override val owner=this@Multiple.owner
   override fun current()=active
   override fun inspect(sourceId:String):EvidenceOriginal {if(failSource==sourceId)throw IOException("Unavailable source");val f=fixtures.single{it.source==sourceId};return EvidenceOriginal(f.item,f.meta)}
   override fun <T> readVerified(sourceId:String,consume:(EvidenceOriginal,InputStream)->T):T {
    val f=fixtures.single{it.source==sourceId}
    val input=object:ByteArrayInputStream(f.bytes){override fun read(b:ByteArray,off:Int,len:Int):Int {
     if(streamFailure==sourceId)throw IOException("Original reader failed")
     val n=super.read(b,off,minOf(7,len));if(cancelSource==sourceId)active=false;return n
    }}
    streaming=true;try{return input.use{consume(inspect(sourceId),it)}}finally{streaming=false}
   }
  }
  var catalog=fixtures.fold(EvidenceCatalog.empty(owner)){c,f->c.upsertEvidence(f.record,1,"IMPORT")}
  fun plan(plain:Boolean=false)=EvidenceExportPlan(catalog,emptyList(),fixtures.map{it.id},EvidenceEgress.digest(catalog),plain)
 }
 @Test fun appliedHistoricalReferencesSurviveUnlinkAndRemovalWithoutOriginalOrUnrelatedNotes(){
  for(owner in EvidenceCatalog.Owner.values())for(remove in listOf(false,true)){
   val m=Multiple(owner);val a=m.fixtures[0];val b=m.fixtures[1]
   val incident=EvidenceCatalog.Incident(UUID.randomUUID().toString(),"Selected incident",0,0,"","",emptyList(),listOf(a.id,b.id),emptyList(),false,false,listOf(EvidenceEgress.fingerprint(a.record),EvidenceEgress.fingerprint(b.record)))
   val ai=EvidenceCatalog.Analysis(UUID.randomUUID().toString(),5,"test model","SUMMARY","0".repeat(64),listOf(a.id,b.id),rawAnalysis(listOf(a.id,b.id)),10,2,false,emptyList())
   val unrelated=EvidenceCatalog.Analysis(UUID.randomUUID().toString(),5,"test model","SUMMARY","0".repeat(64),listOf(a.id,b.id),rawAnalysis(listOf(a.id,b.id),"UNRELATED ADVISORY SECRET"),10,2,false,emptyList())
   val changedB=EvidenceCatalog.Evidence(b.record.id(),b.record.sourceId(),"UNRELATED B NAME",b.record.mime(),b.record.sha256(),b.record.size(),b.record.importedAt(),b.record.metadata(),"UNRELATED B DESCRIPTION","UNRELATED B NOTES",emptyList(),emptyList(),b.record.ocr(),b.record.corrections())
   m.catalog=m.catalog.upsertEvidence(changedB,2,"NOTE").saveAnalysis(ai,5).saveAnalysis(unrelated,5).upsertIncident(incident,6,"CREATE").associateAnalysis(ai.id(),incident.id(),7).unlink(listOf(b.id),incident.id(),8)
   if(remove)m.catalog=m.catalog.removeEvidence(b.id,9)
   for(plain in listOf(false,true)){
    val plan=EvidenceExportPlan(m.catalog,listOf(incident.id()),emptyList(),EvidenceEgress.digest(m.catalog),plain)
    val selected=EvidenceArchive.select(plan,m.reader);assertEquals(listOf(ai.id()),selected.catalog.analyses().map{it.id()});assertEquals(listOf(a.source),selected.originals.map{it.sourceId()})
    val text=EvidencePdf.text(selected);assertTrue(text.contains("Retained A+B advisory"));assertTrue(text.contains("Historical source event"));assertTrue(text.contains("${b.id} => source ${b.source}; not attached"));assertTrue(text.contains("input/output tokens 10/2"))
    val out=ByteArrayOutputStream();EvidenceArchive.write(plan,m.reader,password,out,context)
    val zip=if(plain)out.toByteArray()else EvidenceArchive.open(context,owner,ByteArrayInputStream(out.toByteArray()),password){true}.use{opened->assertEquals(1,opened.originalCount);ByteArrayOutputStream().also{opened.writePlaintext(it,true){true}}.toByteArray()}
    val files=entries(zip);assertArrayEquals(a.bytes,files[EvidenceArchive.path(a.source)]);assertFalse(files.containsKey(EvidenceArchive.path(b.source)))
    val json=String(files.getValue("evidence.json"));val transcript=String(files.getValue("transcript.txt"));for(secret in listOf("UNRELATED B NAME","UNRELATED B NOTES","UNRELATED B DESCRIPTION","UNRELATED ADVISORY SECRET")){assertFalse(json.contains(secret));assertFalse(transcript.contains(secret))}
    val (decoded,sources)=EvidenceArchive.decodeMetadata(files.getValue("evidence.json"),owner);assertEquals(ai.text(),decoded.analyses().single().text());assertEquals(setOf(a.source,b.source),sources.map{it.sourceId()}.toSet());assertTrue(decoded.incidents().single().analysisIds().contains(ai.id()))
    for(failure in 0..2){val original=b.item;when(failure){0->b.item=original.copy(vaultOnly=true);1->b.item=original.copy(plaintextSha256=ByteArray(32));2->m.failSource=b.source}
     val denied=ByteArrayOutputStream();try{rejected{EvidenceArchive.write(plan,m.reader,password,denied,context)};assertEquals(0,denied.size())}finally{b.item=original;m.failSource=null}}
   }
  }
 }
 @Test fun genuinelyForeignRawAiReferencesNeverAcquireHistoricalAuthority(){
  val f=Fixture();val foreign=UUID.randomUUID().toString()
  val ai=EvidenceCatalog.Analysis(UUID.randomUUID().toString(),5,"test","SUMMARY","0".repeat(64),listOf(f.id),rawAnalysis(listOf(foreign)),10,2,false,emptyList())
  f.catalog=f.catalog.saveAnalysis(ai,5)
  val analysis=f.catalog.analyses().single();assertEquals(listOf(f.id),analysis.sourceReferences().map{it.evidenceId()})
  try{EvidenceAi.details(analysis);fail("foreign structured reference trusted")}catch(_:EvidenceAiFailure){}
  for(plain in listOf(false,true)){val output=ByteArrayOutputStream();try{EvidenceArchive.write(f.plan(plain),f.reader,password,output,context);fail("foreign reference exported")}catch(_:EvidenceAiFailure){};assertEquals(0,output.size())}
 }
 @Test fun reverseMetadataOrderIsCanonicalAcrossCodecWriteOpenAndReexportBothOwners(){
  for(owner in EvidenceCatalog.Owner.values()){
   val f=Fixture(owner);val selected=EvidenceArchive.select(f.plan(),f.reader)
   assertEquals("z-last",f.record.metadata().embedded().keys.first())
   val decoded=EvidenceCatalog.decode(f.catalog.encode(),owner);val dp=EvidenceExportPlan(decoded,emptyList(),listOf(f.id),EvidenceEgress.digest(decoded),false);val ds=EvidenceArchive.select(dp,f.reader)
   assertArrayEquals(EvidenceArchive.metadataBytes(selected.catalog,selected.sources),EvidenceArchive.metadataBytes(ds.catalog,ds.sources));assertEquals(EvidencePdf.text(selected),EvidencePdf.text(ds))
   val output=ByteArrayOutputStream();EvidenceArchive.write(f.plan(),f.reader,password,output,context)
   EvidenceArchive.open(context,owner,ByteArrayInputStream(output.toByteArray()),password){true}.use{opened->val plain=ByteArrayOutputStream();opened.writePlaintext(plain,true){true};val files=entries(plain.toByteArray());assertArrayEquals(f.bytes,files[EvidenceArchive.path(f.source)]);val c=EvidenceArchive.decodeMetadata(files.getValue("evidence.json"),owner).first;assertEquals(f.meta,c.evidence().single().metadata())}
  }
 }
 @Test fun originalCount128Admitted129RejectedBeforeOutput(){
  val m=Multiple(EvidenceCatalog.Owner.PRIMARY,128);assertEquals(128,EvidenceArchive.select(m.plan(true),m.reader).originals.size)
  val output=ByteArrayOutputStream();EvidenceArchive.write(m.plan(true),m.reader,password,output,context);assertEquals(128,entries(output.toByteArray()).keys.count{it.startsWith("originals/")})
  val over=Multiple(EvidenceCatalog.Owner.HIDDEN,129);for(plain in listOf(false,true)){val denied=ByteArrayOutputStream();rejected{EvidenceArchive.write(over.plan(plain),over.reader,password,denied,context)};assertEquals(0,denied.size())}
 }
 @Test fun combinedMetadataAndManifestQuotaRejectsAndCleansStage(){
  val f=Fixture();val files=archiveEntries(f);files["evidence.json"]=ByteArray(EvidenceArchiveFormat.META/2+1);files["hashes.json"]=ByteArray(EvidenceArchiveFormat.META/2+1)
  val directory=File(context.noBackupFilesDir,"evidence-open-primary");val before=directory.listFiles()?.map{it.name}?.toSet().orEmpty()
  try{EvidenceArchive.open(context,f.owner,ByteArrayInputStream(encryptedPayload(packed(files))),password){true};fail("combined quota accepted")}catch(expected:IllegalArgumentException){assertEquals("Combined metadata quota exceeded",expected.message)};assertEquals(before,directory.listFiles()?.map{it.name}?.toSet().orEmpty())
 }
 @Test fun writerOriginalActualSizeHashReaderFailuresAndStreamingRetirementNeverSucceed(){
  for(plain in listOf(false,true))for(failure in 0..4){
   val m=Multiple(EvidenceCatalog.Owner.PRIMARY,1);val f=m.fixtures.single()
   when(failure){0->f.bytes=f.bytes.copyOf(f.bytes.size-1);1->f.bytes=f.bytes.copyOf(f.bytes.size+1);2->f.bytes=f.bytes.copyOf().also{it[0]=(it[0].toInt() xor 1).toByte()};3->m.streamFailure=f.source;4->m.cancelSource=f.source}
   rejected{EvidenceArchive.write(m.plan(plain),m.reader,password,ByteArrayOutputStream(),context)}
  }
 }
 @Test fun wrongWriterOwnerAndDigestFailBeforeAdmission(){
  val f=Fixture();val foreign=Fixture(EvidenceCatalog.Owner.HIDDEN)
  for(plain in listOf(false,true)){val output=ByteArrayOutputStream();rejected{EvidenceArchive.write(f.plan(plain),foreign.reader,password,output,context)};assertEquals(0,output.size());rejected{EvidenceArchive.write(f.plan(plain).copy(digest="0".repeat(64)),f.reader,password,output,context)};assertEquals(0,output.size())}
 }
 @Test fun outputFailureDoesNotCloseCallerOrProduceReceipt(){
  for(plain in listOf(false,true)){val m=Multiple(EvidenceCatalog.Owner.PRIMARY,1);var closed=false
   val output=object:OutputStream(){override fun write(b:Int){throw IOException("destination failed")};override fun write(b:ByteArray,off:Int,len:Int){throw IOException("destination failed")};override fun close(){closed=true}}
   rejected{EvidenceArchive.write(m.plan(plain),m.reader,password,output,context)};assertFalse(closed)
  }
 }
 @Test fun ciphertextLimitAndProviderFailureDeleteOnlyNewStage(){
  val f=Fixture();val out=ByteArrayOutputStream();EvidenceArchive.write(f.plan(),f.reader,password,out,context);val bytes=out.toByteArray()
  val directory=File(context.noBackupFilesDir,"evidence-open-primary");val before=directory.listFiles()?.map{it.name}?.toSet().orEmpty()
  rejected{EvidenceOpenedArchive.openForTest(context,f.owner,ByteArrayInputStream(bytes),password,{true},bytes.size.toLong()-1)}
  assertEquals(before,directory.listFiles()?.map{it.name}?.toSet().orEmpty())
  val input=object:ByteArrayInputStream(bytes){var reads=0;override fun read(b:ByteArray,off:Int,len:Int):Int{if(++reads>1)throw IOException("provider failed");return super.read(b,off,len)}}
  rejected{EvidenceArchive.open(context,f.owner,input,password){true}};assertEquals(before,directory.listFiles()?.map{it.name}?.toSet().orEmpty())
 }
 @Test fun bothAuthenticatedPassesCancelAndSecondPassTagFaultNeverPublishOrLeavePlaintext(){
  val f=Fixture();val out=ByteArrayOutputStream();EvidenceArchive.write(f.plan(),f.reader,password,out,context);val bytes=out.toByteArray()
  val directory=File(context.noBackupFilesDir,"evidence-open-primary");val before=directory.listFiles()?.map{it.name}?.toSet().orEmpty()
  for(stop in listOf("authenticate","parse","validated")){var active=true;var reached=false
   rejected{EvidenceOpenedArchive.openForTest(context,f.owner,ByteArrayInputStream(bytes),password,{active},phase={phase->
    if(phase=="staged"){val held=directory.listFiles()!!.single{it.name !in before};assertArrayEquals(bytes,held.readBytes())}
    if(phase==stop){reached=true;active=false}
   })};assertTrue(reached);assertEquals(before,directory.listFiles()?.map{it.name}?.toSet().orEmpty())
  }
  var firstAuthenticated=false;var parsed=false
  try{EvidenceOpenedArchive.openForTest(context,f.owner,ByteArrayInputStream(bytes),password,{true},phase={phase->
   if(phase=="authenticated"){firstAuthenticated=true;val held=directory.listFiles()!!.single{it.name !in before};android.system.Os.chmod(held.absolutePath,384);RandomAccessFile(held,"rw").use{it.seek(it.length()-1);val last=it.readByte();it.seek(it.length()-1);it.writeByte(last.toInt() xor 1)};android.system.Os.chmod(held.absolutePath,256)}
   if(phase.startsWith("entry:"))parsed=true
  });fail("second-pass tag fault published")}catch(expected:IOException){assertEquals("Evidence authentication failed",expected.message)}
  assertTrue(firstAuthenticated);assertTrue(parsed);assertEquals(before,directory.listFiles()?.map{it.name}?.toSet().orEmpty())
 }
 @Test fun closeWaitsForBorrowThenWipesReportAndDeletesStageAndRetiredReexportRefuses(){
  val f=Fixture();val out=ByteArrayOutputStream();EvidenceArchive.write(f.plan(),f.reader,password,out,context)
  val directory=File(context.noBackupFilesDir,"evidence-open-primary");val before=directory.listFiles()?.map{it.name}?.toSet().orEmpty();var active=true
  val opened=EvidenceArchive.open(context,f.owner,ByteArrayInputStream(out.toByteArray()),password){active};val stage=directory.listFiles()!!.single{it.name !in before}
  var borrowed:ByteArray?=null;val entered=java.util.concurrent.CountDownLatch(1);val release=java.util.concurrent.CountDownLatch(1)
  val borrower=Thread{try{opened.withReport{borrowed=it;entered.countDown();check(release.await(10,java.util.concurrent.TimeUnit.SECONDS))}}catch(_:IllegalStateException){}}
  borrower.start();assertTrue(entered.await(10,java.util.concurrent.TimeUnit.SECONDS));val closer=Thread{opened.close()};closer.start()
  // Observe close blocked on the held borrow monitor before checking its buffer lifetime.
  val deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(5)
  while(closer.isAlive&&closer.state!=Thread.State.BLOCKED&&System.nanoTime()<deadline)Thread.sleep(1)
  assertEquals(Thread.State.BLOCKED,closer.state)
  assertTrue(stage.exists());assertTrue(borrowed!!.any{it.toInt()!=0});release.countDown();borrower.join(10000);closer.join(10000);assertFalse(borrower.isAlive);assertFalse(closer.isAlive);assertTrue(borrowed!!.all{it.toInt()==0});assertFalse(stage.exists())
  val again=EvidenceArchive.open(context,f.owner,ByteArrayInputStream(out.toByteArray()),password){active};active=false;val denied=ByteArrayOutputStream();try{rejected{again.writePlaintext(denied,true){true}};assertEquals(0,denied.size())}finally{again.close()}
  assertEquals(before,directory.listFiles()?.map{it.name}?.toSet().orEmpty())
 }
 @Test fun plaintextReexportOutputFailureReleasesStageOnClose(){
  val f=Fixture();val out=ByteArrayOutputStream();EvidenceArchive.write(f.plan(),f.reader,password,out,context)
  val directory=File(context.noBackupFilesDir,"evidence-open-primary");val before=directory.listFiles()?.map{it.name}?.toSet().orEmpty()
  EvidenceArchive.open(context,f.owner,ByteArrayInputStream(out.toByteArray()),password){true}.use{opened->val output=object:OutputStream(){override fun write(b:Int){throw IOException("output failed")}};rejected{opened.writePlaintext(output,true){true}}}
  assertEquals(before,directory.listFiles()?.map{it.name}?.toSet().orEmpty())
 }


 @Test fun outputFailureAndRevocationDuringOriginalStreamingNeverReturnReceipt(){
  val bytes=ByteArray(192*1024).also{java.util.Random(179).nextBytes(it)}
  for(plain in listOf(false,true))for(retire in listOf(false,true)){
   val m=Multiple(EvidenceCatalog.Owner.PRIMARY,1,bytes);var hitOriginal=false;var publicBytes=0L
   val output=object:OutputStream(){
    override fun write(b:Int){write(byteArrayOf(b.toByte()),0,1)}
    override fun write(b:ByteArray,off:Int,len:Int){
     publicBytes+=len
     // Plaintext now streams authenticated snapshot bytes after every source returned.
     // Cross the small report/index prefix so the failure still interrupts original payload.
     if((if(plain)publicBytes>64*1024 else m.streaming)&&len>0){
      if(plain)assertFalse(m.streaming)
      hitOriginal=true;if(retire)m.active=false else throw IOException("destination failed during original")
     }
    }
   }
   rejected{EvidenceArchive.write(m.plan(plain),m.reader,password,output,context)};assertTrue("failure must interrupt live encrypted staging or authenticated plaintext snapshot originals",hitOriginal)
  }
 }

 private class PublicProbe(private val admitted:()->Boolean={false}):ByteArrayOutputStream(){
  var writes=0;var flushes=0;var closes=0
  override fun write(b:Int){writes++;check(admitted()){ "public byte before authenticated snapshot" };super.write(b)}
  override fun write(b:ByteArray,off:Int,len:Int){writes++;check(admitted()){ "public bytes before authenticated snapshot" };super.write(b,off,len)}
  override fun flush(){flushes++;check(admitted()){ "public flush before authenticated snapshot" };super.flush()}
  override fun close(){closes++;super.close()}
  fun untouched(){assertEquals(0,size());assertEquals(0,writes);assertEquals(0,flushes);assertEquals(0,closes)}
 }
 @Test fun plaintextLateTagHashAndPostReaderFailuresNeverTouchPublicOutput(){
  val bytes=ByteArray(192*1024).also{java.util.Random(179).nextBytes(it)}
  for(failure in listOf("late-tag","hash","post-reader","policy","retirement")){
   val f=Fixture(initialBytes=bytes);var returned=false;var readBytes=0L
   val reader=object:EvidenceSourceReader by f.reader {
    override fun <T> readVerified(sourceId:String,consume:(EvidenceOriginal,InputStream)->T):T {
     val content=bytes.copyOf();if(failure=="hash")content[0]=(content[0].toInt() xor 1).toByte()
     try {
      val input=object:ByteArrayInputStream(content){override fun read(b:ByteArray,off:Int,len:Int):Int{
       val n=super.read(b,off,len);if(n>0)readBytes+=n
       if(n<0&&failure=="late-tag")throw IOException("synthetic late GCM tag failure")
       return n
      }}
      val result=consume(inspect(sourceId),input)
      if(failure=="post-reader")throw IOException("post-consumer authentication failure")
      if(failure=="policy")f.item=f.item.copy(vaultOnly=true)
      if(failure=="retirement")f.active=false
      returned=true;return result
     }finally{content.fill(0)}
    }
   }
   val directory=File(context.noBackupFilesDir,"evidence-open-primary");val before=directory.listFiles()?.map{it.name}?.toSet().orEmpty()
   val output=PublicProbe();rejected{EvidenceArchive.write(f.plan(true),reader,password,output,context)}
   assertEquals(before,directory.listFiles()?.map{it.name}?.toSet().orEmpty())
   assertEquals(bytes.size.toLong(),readBytes);assertEquals(failure=="policy"||failure=="retirement",returned);output.untouched()
  }
 }
 @Test fun plaintextAllOriginalsAuthenticateBeforePublicOutputAndSnapshotNeverRereadsSources(){
  val m=Multiple(EvidenceCatalog.Owner.PRIMARY,2,ByteArray(192*1024).also{java.util.Random(180).nextBytes(it)})
  val authenticated=mutableSetOf<String>()
  val reader=object:EvidenceSourceReader by m.reader {
   override fun <T> readVerified(sourceId:String,consume:(EvidenceOriginal,InputStream)->T):T {
    check(sourceId !in authenticated){"mutable source reread"}
    return m.reader.readVerified(sourceId,consume).also{authenticated.add(sourceId)}
   }
  }
  val output=PublicProbe{authenticated.size==2&&!m.streaming}
  val receipt=EvidenceArchive.write(m.plan(true),reader,password,output,context)
  assertFalse(receipt.encrypted);assertTrue(output.writes>0);assertEquals(0,output.closes)
  val files=entries(output.toByteArray());for(f in m.fixtures)assertArrayEquals(f.bytes,files[EvidenceArchive.path(f.source)])
 }
 @Test fun plaintextSnapshotIsIndependentOfMutableOriginalAndRechecksLivePolicy(){
  val f=Fixture();val expected=f.bytes.copyOf();var reads=0
  val reader=object:EvidenceSourceReader by f.reader {
   override fun <T> readVerified(sourceId:String,consume:(EvidenceOriginal,InputStream)->T):T {reads++;return f.reader.readVerified(sourceId,consume)}
  }
  val directory=File(context.noBackupFilesDir,"evidence-open-primary");val before=directory.listFiles()?.map{it.name}?.toSet().orEmpty()
  rejected{EvidenceArchive.preparePlaintext(context,f.plan(),reader)}
  val missingContext=PublicProbe();rejected{EvidenceArchive.write(f.plan(true),reader,charArrayOf(),missingContext)};missingContext.untouched()
  EvidenceArchive.preparePlaintext(context,f.plan(true),reader).use{snapshot->
   assertEquals(1,reads)
   val stages=directory.listFiles()!!.filter{it.name !in before};assertEquals(1,stages.size)
   assertEquals(256,android.system.Os.stat(stages.single().path).st_mode and 511)
   assertEquals("PGEVID17",stages.single().inputStream().use{String(it.readNBytes(8),Charsets.US_ASCII)})
   f.bytes=ByteArray(expected.size){7} // No second, unchecked original read can supply exported bytes.
   val output=PublicProbe{true};snapshot.write(output)
   assertArrayEquals(expected,entries(output.toByteArray())[EvidenceArchive.path(f.source)]);assertEquals(1,reads)
   for(failure in listOf("policy","fingerprint","retirement")){
    val original=f.item;when(failure){"policy"->f.item=original.copy(vaultOnly=true);"fingerprint"->f.item=original.copy(plaintextSha256=ByteArray(32));else->f.active=false}
    val denied=PublicProbe();try{rejected{snapshot.write(denied)};denied.untouched()}finally{f.item=original;f.active=true}
   }
  }
  assertEquals(before,directory.listFiles()?.map{it.name}?.toSet().orEmpty());expected.fill(0)
 }
 @Test fun primaryLegacyLateGcmTagFailureNeverPublishesPlaintext(){
  val root=File(context.cacheDir,"evidence-legacy-tag-${UUID.randomUUID()}")
  val isolated=object:android.content.ContextWrapper(context){
   override fun getFilesDir()=File(root,"files").apply{mkdirs()}
   override fun getNoBackupFilesDir()=File(root,"no-backup").apply{mkdirs()}
   override fun getApplicationContext():android.content.Context=this
  }
  val key=ByteArray(32){31};val bytes=ByteArray(256*1024).also{java.util.Random(181).nextBytes(it)}
  var access:EvidenceAccess?=null
  try {
   val repo=AndroidVaultRepository(isolated,key)
   val item=(repo.importVerified(VaultImportSource("legacy179.txt","text/plain",{bytes.inputStream()}))as ImportResult.Imported).item
   val payload=File(isolated.filesDir,"vault/payloads/${item.id}.vault")
   assertFalse(ChunkedVaultVideoStore.isChunked(payload))
   val record=EvidenceCatalog.Evidence(UUID.randomUUID().toString(),item.id,item.displayName,item.mimeType,EvidenceArchive.hex(item.plaintextSha256),item.plaintextSize,1,EvidenceMetadata.fromItem(item),"","",emptyList(),emptyList(),emptyList(),emptyList())
   val catalog=EvidenceCatalog.empty(EvidenceCatalog.Owner.PRIMARY).upsertEvidence(record,1,"IMPORT")
   access=EvidenceAccess.primary(isolated,key,{key},{true},{key.copyOf()},Any())
   RandomAccessFile(payload,"rw").use{it.seek(it.length()-1);val last=it.readByte();it.seek(it.length()-1);it.writeByte(last.toInt() xor 1)}
   var preliminary=0L
   // Demonstrate that the actual reused Primary reader exposes a prefix before its late tag fails.
   rejected{repo.withHiddenCopySource(item.id,{false}){_,input->val buffer=ByteArray(32768);try{while(true){val n=input.read(buffer);if(n<0)break;preliminary+=n}}finally{buffer.fill(0)}}}
   assertTrue(preliminary>64*1024)
   val output=PublicProbe();val plan=EvidenceExportPlan(catalog,emptyList(),listOf(record.id()),EvidenceEgress.digest(catalog),true)
   rejected{EvidenceArchive.write(plan,access.reader(),charArrayOf(),output,isolated)};output.untouched()
   assertEquals(0,File(isolated.noBackupFilesDir,"evidence-open-primary").listFiles().orEmpty().size)
  }finally{access?.close();key.fill(0);bytes.fill(0);root.deleteRecursively()}
 }

 @Test fun portableSchema2RejectsLegacyBinaryCatalog(){
  val encoded=ByteArrayOutputStream();DataOutputStream(encoded).use{out->
   out.writeInt(0x50474543);out.writeInt(1);val owner="PRIMARY".toByteArray();out.writeInt(owner.size);out.write(owner);out.writeLong(0);repeat(5){out.writeInt(0)}
  }
  val legacy=EvidenceCatalog.decode(encoded.toByteArray(),EvidenceCatalog.Owner.PRIMARY)
  val metadata=EvidenceArchive.metadataBytes(legacy,emptyList())
  try{EvidenceArchive.decodeMetadata(metadata,EvidenceCatalog.Owner.PRIMARY);fail("portable schema2 accepted a legacy binary snapshot")}
  catch(expected:IllegalArgumentException){assertEquals("Unsupported evidence catalog schema in export",expected.message)}finally{metadata.fill(0)}
 }
}
