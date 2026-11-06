package uk.co.traynor.privategallery.core.evidence

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.traynor.privategallery.core.vault.VaultItem
import uk.co.traynor.privategallery.core.vault.VaultItemState

/** Synthetic metadata and in-memory outputs only; no private originals, files, credentials or network. */
@RunWith(AndroidJUnit4::class)
class EvidenceReportExport60Test {
 private class Fixture(val longReport:Boolean=false) {
  private val original="ORIGINAL_BYTES_NEVER_EXPORT".toByteArray()
  private val hash=MessageDigest.getInstance("SHA-256").digest(original)
  fun record(text:String):EvidenceCatalog.Evidence {
   val extraction=EvidenceCatalog.Ocr(UUID.randomUUID().toString(),1,"SYNTHETIC",0,0,0,text,90f,emptyList(),emptyList(),1,false)
   return EvidenceCatalog.Evidence(UUID.randomUUID().toString(),UUID.randomUUID().toString(),"PRIVATE_FILENAME_NEVER_EXPORT","text/plain",hash.joinToString(""){"%02x".format(it.toInt() and 255)},original.size.toLong(),1,
    EvidenceCatalog.Metadata("PRIVATE_SOURCE_FILENAME",0,0,0,0,0,0,emptyMap(),false),"PRIVATE_DESCRIPTION_NEVER_EXPORT","PRIVATE_NOTES_NEVER_EXPORT",emptyList(),emptyList(),listOf(extraction),emptyList())
  }
  val first=record("If you leave I will hurt you.");val second=record("SELECTED_BUT_UNCITED_TRANSCRIPT_NEVER_EXPORT");val other=record("UNSELECTED_TRANSCRIPT_NEVER_EXPORT")
  val analysisId=UUID.randomUUID().toString()
  var catalog:EvidenceCatalog
  val originals=linkedMapOf<String,EvidenceOriginal>()
  var active=true;var inspections=0;var reads=0;var readerOwner=EvidenceCatalog.Owner.PRIMARY;var onInspect:()->Unit={}
  val reader=object:EvidenceSourceReader {
   override val owner get()=readerOwner
   override fun current()=active
   override fun inspect(sourceId:String):EvidenceOriginal{inspections++;onInspect();return originals[sourceId]?:error("Synthetic source unavailable")}
   override fun <T> readVerified(sourceId:String,consume:(EvidenceOriginal,InputStream)->T):T{reads++;error("Report export must never read original bytes")}
  }
  init {
   catalog=EvidenceCatalog.empty(EvidenceCatalog.Owner.PRIMARY)
   for(record in listOf(first,second,other)) {
    catalog=catalog.upsertEvidence(record,1,"SYNTHETIC")
    originals[record.sourceId()]=EvidenceOriginal(VaultItem(record.sourceId(),record.mime(),record.name(),1,record.size(),hash.copyOf(),ByteArray(12),VaultItemState.COMPLETE),record.metadata())
   }
   val reference=JSONObject().put("evidence_id",first.id()).put("field","transcript").put("passage",first.transcript()).put("timestamp_quote",JSONObject.NULL)
   val proposal=JSONObject().put("version",1).put("outcome","INSUFFICIENT_EVIDENCE").put("assessment","Context is incomplete").put("confidence","LOW")
    .put("alternatives",JSONArray(if(longReport)(0 until 12).map{"Alternative $it "+"x".repeat(2000)}else listOf("Missing context or a quotation"))).put("limitations",JSONArray().put("Selected material only")).put("chronology",JSONArray())
    .put("instances",JSONArray().put(JSONObject().put("categories",JSONArray().put("THREATS")).put("assessment","Possible threatening language").put("confidence","LOW").put("intent","UNKNOWN")
     .put("alternatives",JSONArray().put("Quotation or missing context")).put("references",JSONArray().put(reference))))
   val report=EvidenceAiReport.validate(proposal.toString(),EvidenceAiReport.corpus(listOf(first,second)))
   val text=JSONObject(EvidenceAiReport.encode(report)).put("_receipt",JSONObject().put("status","completed").put("refusal",false).put("cachedInputTokens",0).put("advisory",true)
    .put("label","ADVISORY AI — COMPLETED. Owner review required; no changes applied. Cached input tokens: 0.")).toString()
   catalog=catalog.saveAnalysis(EvidenceCatalog.Analysis(analysisId,1000,"synthetic-model",EvidenceAi.Task.EVIDENCE_REPORT.name,"d".repeat(64),listOf(first.id(),second.id()),text,0,0,false,listOf(EvidenceEgress.fingerprint(first),EvidenceEgress.fingerprint(second))),2)
  }
 }
 private fun reject(work:()->Unit){try{work();fail("Restricted report export was admitted")}catch(_:SecurityException){}catch(_:IllegalArgumentException){}catch(_:IllegalStateException){}catch(_:EvidenceAiFailure){}}
 @Test fun writesOnlyChosenReportAndNeverReadsOrCopiesOriginalsOrCatalogueAnnotations() {
  val f=Fixture();val unrelated=f.originals.getValue(f.other.sourceId());f.originals[f.other.sourceId()]=EvidenceOriginal(unrelated.item.copy(vaultOnly=true),unrelated.metadata)
  val output=ByteArrayOutputStream();EvidenceReportExport.write(f.catalog,f.analysisId,f.reader,output)
  val text=output.toString("UTF-8")
  assertTrue(text.contains(f.analysisId));assertTrue(text.contains("synthetic-model"));assertTrue(text.contains("1000"))
  assertTrue(text.contains(EvidenceAiReport.DISCLAIMER));assertTrue(text.contains("Threats*: 1*"));assertTrue(text.contains(f.first.transcript()))
  for(secret in listOf("ORIGINAL_BYTES_NEVER_EXPORT","PRIVATE_FILENAME_NEVER_EXPORT","PRIVATE_SOURCE_FILENAME","PRIVATE_DESCRIPTION_NEVER_EXPORT","PRIVATE_NOTES_NEVER_EXPORT","UNSELECTED_TRANSCRIPT_NEVER_EXPORT","SELECTED_BUT_UNCITED_TRANSCRIPT_NEVER_EXPORT"))assertFalse(text.contains(secret))
  assertEquals(0,f.reads)
 }
 @Test fun removedRecordStillRequiresEveryRetainedContributorAndMissingSourceBlocks() {
  val f=Fixture();f.catalog=f.catalog.removeEvidence(f.second.id(),3)
  assertEquals(f.analysisId,EvidenceReportExport.validate(f.catalog,f.analysisId,f.reader).id())
  f.originals.remove(f.second.sourceId());val output=ByteArrayOutputStream()
  reject{EvidenceReportExport.write(f.catalog,f.analysisId,f.reader,output)};assertEquals(0,output.size())
 }
 @Test fun vaultOnlyAndChangedHistoricalSourcesBlockEvenAfterTheirRecordsAreRemoved() {
  for(change in listOf("policy","hash","mime","size","state")) {
   val f=Fixture();f.catalog=f.catalog.removeEvidence(f.second.id(),3);val original=f.originals.getValue(f.second.sourceId())
   val changed=when(change){"policy"->original.item.copy(vaultOnly=true);"hash"->original.item.copy(plaintextSha256=ByteArray(32));"mime"->original.item.copy(mimeType="text/csv");"size"->original.item.copy(plaintextSize=999);else->original.item.copy(state=VaultItemState.IMPORTING)}
   f.originals[f.second.sourceId()]=EvidenceOriginal(changed,original.metadata)
   reject{EvidenceReportExport.validate(f.catalog,f.analysisId,f.reader)}
  }
 }
 @Test fun foreignOwnerUnknownAnalysisAndGenericAdvisoryFailBeforeOutput() {
  val f=Fixture();f.readerOwner=EvidenceCatalog.Owner.HIDDEN
  reject{EvidenceReportExport.validate(f.catalog,f.analysisId,f.reader)};assertEquals(0,f.inspections)
  f.readerOwner=EvidenceCatalog.Owner.PRIMARY;reject{EvidenceReportExport.validate(f.catalog,UUID.randomUUID().toString(),f.reader)}
  val old=EvidenceCatalog.Analysis(UUID.randomUUID().toString(),1000,"synthetic-model",EvidenceAi.Task.FACTUAL_SUMMARY.name,"e".repeat(64),listOf(f.first.id()),"Generic advisory",0,0,false,listOf(EvidenceEgress.fingerprint(f.first)))
  f.catalog=f.catalog.saveAnalysis(old,3);reject{EvidenceReportExport.validate(f.catalog,old.id(),f.reader)}
 }
 @Test fun revocationBeforeAndDuringOutputNeverReturnsSuccessAndWipesMutableBuffer() {
  val before=Fixture();before.active=false;val empty=ByteArrayOutputStream();reject{EvidenceReportExport.write(before.catalog,before.analysisId,before.reader,empty)};assertEquals(0,empty.size())
  val during=Fixture();var borrowed:ByteArray?=null;var written=0
  val output=object:OutputStream(){override fun write(value:Int)=error("Expected bounded array chunks");override fun write(bytes:ByteArray,offset:Int,length:Int){borrowed=bytes;written+=length;during.active=false}}
  reject{EvidenceReportExport.write(during.catalog,during.analysisId,during.reader,output)}
  assertTrue(written>0);assertTrue(written<=16384);assertTrue(borrowed!!.all{it==0.toByte()})
 }
 @Test fun policyChangeDuringOutputIsCaughtByPostWriteFingerprintCheck() {
  val f=Fixture();var bytesWritten=0
  val output=object:OutputStream(){override fun write(value:Int)=error("Expected chunks");override fun write(bytes:ByteArray,offset:Int,length:Int){bytesWritten+=length;val source=f.originals.getValue(f.second.sourceId());f.originals[f.second.sourceId()]=EvidenceOriginal(source.item.copy(vaultOnly=true),source.metadata)}}
  reject{EvidenceReportExport.write(f.catalog,f.analysisId,f.reader,output)};assertTrue(bytesWritten>0)
 }
 @Test fun everyOutputChunkIsBoundedAndRetainedBuffersAreWipedOnSuccess() {
  val f=Fixture(true);val lengths=mutableListOf<Int>();val borrowed=mutableListOf<ByteArray>()
  val output=object:OutputStream(){override fun write(value:Int)=error("Expected chunks");override fun write(bytes:ByteArray,offset:Int,length:Int){lengths+=length;borrowed+=bytes}}
  EvidenceReportExport.write(f.catalog,f.analysisId,f.reader,output)
  assertTrue(lengths.size>1);assertTrue(lengths.all{it in 1..16384});assertTrue(borrowed.all{array->array.all{it==0.toByte()}})
 }
 @Test fun malformedSavedReportNeverWritesAnyBytes() {
  val f=Fixture();val id=UUID.randomUUID().toString()
  f.catalog=f.catalog.saveAnalysis(EvidenceCatalog.Analysis(id,1000,"synthetic-model",EvidenceAi.Task.EVIDENCE_REPORT.name,"f".repeat(64),listOf(f.first.id()),"not JSON",0,0,false,listOf(EvidenceEgress.fingerprint(f.first))),3)
  val output=ByteArrayOutputStream();reject{EvidenceReportExport.write(f.catalog,id,f.reader,output)};assertEquals(0,output.size())
 }

}
