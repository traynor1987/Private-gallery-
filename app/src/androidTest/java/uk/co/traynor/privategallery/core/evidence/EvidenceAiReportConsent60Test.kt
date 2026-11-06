package uk.co.traynor.privategallery.core.evidence

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.traynor.privategallery.core.editor.AiCredentialStore
import uk.co.traynor.privategallery.core.editor.AiHttpRequest
import uk.co.traynor.privategallery.core.editor.AiHttpResponse
import uk.co.traynor.privategallery.core.vault.VaultItem
import uk.co.traynor.privategallery.core.vault.VaultItemState

/** Completely synthetic routes: no sockets, provider credentials or private content. */
@RunWith(AndroidJUnit4::class)
class EvidenceAiReportConsent60Test {
 private class Fixture {
  val bytes="Synthetic source text only".toByteArray();val id=UUID.randomUUID().toString();val sourceId=UUID.randomUUID().toString()
  val hash=MessageDigest.getInstance("SHA-256").digest(bytes)
  val item=VaultItem(sourceId,"text/plain","PRIVATE_FILENAME",1,bytes.size.toLong(),hash,ByteArray(12),VaultItemState.COMPLETE)
  val record=EvidenceCatalog.Evidence(id,sourceId,item.displayName,item.mimeType,hash.joinToString(""){"%02x".format(it.toInt() and 255)},bytes.size.toLong(),1,
   EvidenceCatalog.Metadata("PRIVATE_NAME",0,0,0,0,0,0,mapOf("GPS" to "PRIVATE_GPS"),false),"Owner description","",emptyList(),emptyList(),emptyList(),emptyList())
  val catalog=EvidenceCatalog.empty(EvidenceCatalog.Owner.PRIMARY).upsertEvidence(record,1,"SYNTHETIC")
  var active=true;var keyReads=0;var posts=0;var sent="";var onSend:()->Unit={}
  var status="completed";var refusal=false;var outputOverride:String?=null
  val reader=object:EvidenceSourceReader {
   override val owner=EvidenceCatalog.Owner.PRIMARY
   override fun current()=active
   override fun inspect(sourceId:String)=EvidenceOriginal(item,record.metadata())
   override fun <T> readVerified(sourceId:String,consume:(EvidenceOriginal,InputStream)->T)=ByteArrayInputStream(bytes).use{consume(inspect(sourceId),it)}
  }
  val route=object:EvidenceRoute {
   override val owner=EvidenceCatalog.Owner.PRIMARY;override val label="SYNTHETIC";override val identity=UUID.randomUUID().toString()
   var ended=false
   override fun current()=active&&!ended
   override suspend fun execute(request:AiHttpRequest):AiHttpResponse {
    posts++;sent=ByteArrayOutputStream().use{request.body!!.writeTo(it);it.toString("UTF-8")};onSend()
    val report=JSONObject().put("version",1).put("outcome","INSUFFICIENT_EVIDENCE").put("assessment","No anchored direct evidence was supplied").put("confidence","LOW")
     .put("alternatives",JSONArray().put("Ordinary disagreement or missing context")).put("limitations",JSONArray().put("No direct transcript")).put("chronology",JSONArray()).put("instances",JSONArray())
    val part=if(refusal)JSONObject().put("type","refusal").put("refusal","Synthetic refusal")else JSONObject().put("type","output_text").put("text",outputOverride?:report.toString())
    val envelope=JSONObject().put("status",status).put("output",JSONArray().put(JSONObject().put("type","message").put("content",JSONArray().put(part)))).put("usage",JSONObject().put("input_tokens",12).put("output_tokens",8))
    return AiHttpResponse(200,"application/json",envelope.toString().toByteArray())
   }
   override fun close(){ended=true}
  }
  val credentials=EvidenceCredentials(EvidenceCatalog.Owner.PRIMARY,{active},{it()},object:EvidenceCredentials.Backend {
   override fun configured()=true
   override fun read():ByteArray{keyReads++;return "SYNTHETIC_KEY".toByteArray()}
   override fun stage(value:ByteArray):AiCredentialStore.PreparedChange=error("Unused")
   override fun removal():AiCredentialStore.PreparedChange=error("Unused")
  },{})
  suspend fun preview()=EvidenceAi.prepare(catalog,listOf(id),emptyList(),EvidenceAi.Model.LUNA,EvidenceAi.Task.EVIDENCE_REPORT,reader,route)
 }
 private suspend fun rejected(work:suspend()->Unit){try{work();fail("Stale consent was admitted")}catch(_:EvidenceAiFailure){}catch(_:IllegalStateException){}catch(_:IllegalArgumentException){}}
 @Test fun reportPreviewPostsNothingAndEachSendRequiresFreshConsent()=runBlocking {
  val f=Fixture();val preview=f.preview()
  assertEquals(0,f.posts);assertEquals(0,f.keyReads)
  assertTrue(preview.estimatedUsd>=8192*EvidenceAi.Model.LUNA.outputUsdPerMillion/1000000.0)
  assertTrue(preview.exactText.contains("partial"));assertFalse(preview.exactText.contains("PRIVATE_FILENAME"));assertFalse(preview.exactText.contains("PRIVATE_GPS"))
  val result=EvidenceAi.analyze(preview,f.credentials){EvidenceEgress.digest(f.catalog)}
  val details=EvidenceAi.details(result);assertNotNull(details.report);assertTrue(details.summary.contains(EvidenceAiReport.DISCLAIMER))
  assertEquals("EVIDENCE_REPORT",result.task());assertEquals(8192,JSONObject(f.sent).getInt("max_output_tokens"))
  rejected{EvidenceAi.analyze(preview,f.credentials){EvidenceEgress.digest(f.catalog)}}
  assertEquals(1,f.posts);assertEquals(1,f.keyReads)
 }
 @Test fun cancelChangedCatalogueAndSpaceRetirementNeverPost()=runBlocking {
  val cancelled=Fixture();cancelled.preview().close();assertEquals(0,cancelled.posts);assertEquals(0,cancelled.keyReads)
  val changed=Fixture();val p=changed.preview();rejected{EvidenceAi.analyze(p,changed.credentials){"0".repeat(64)}};assertEquals(0,changed.posts)
  val retired=Fixture();val q=retired.preview();retired.active=false;rejected{EvidenceAi.analyze(q,retired.credentials){EvidenceEgress.digest(retired.catalog)}};assertEquals(0,retired.posts)
 }
 @Test fun incompleteAndRefusalReportsAlwaysRemainInsufficientAndUncounted()=runBlocking {
  for(refusal in listOf(false,true)) {
   val f=Fixture();f.status="incomplete";f.refusal=refusal
   val result=EvidenceAi.analyze(f.preview(),f.credentials){EvidenceEgress.digest(f.catalog)}
   val report=EvidenceAi.details(result).report!!
   assertEquals("INSUFFICIENT_EVIDENCE",report.outcome);assertTrue(report.instances.isEmpty());assertTrue(report.counts.values.all{it==0})
  }
 }
 @Test fun retiredResponseCannotPublishOrPersistAReport()=runBlocking {
  val f=Fixture();f.onSend={f.active=false};rejected{EvidenceAi.analyze(f.preview(),f.credentials){EvidenceEgress.digest(f.catalog)}}
  assertTrue(f.catalog.analyses().isEmpty())
 }
 @Test fun truncatedIncompleteJsonBecomesAppOwnedInsufficientReport()=runBlocking {
  val f=Fixture();f.status="incomplete";f.outputOverride="{\"version\":1,"
  val report=EvidenceAi.details(EvidenceAi.analyze(f.preview(),f.credentials){EvidenceEgress.digest(f.catalog)}).report!!
  assertEquals("INSUFFICIENT_EVIDENCE",report.outcome);assertEquals(0,report.instances.size);assertTrue(report.counts.values.all{it==0})
 }

}
