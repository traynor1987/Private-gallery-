package uk.co.traynor.privategallery.ui

import android.content.Context
import android.content.ContextWrapper
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import uk.co.traynor.privategallery.core.editor.*
import uk.co.traynor.privategallery.core.evidence.*
import uk.co.traynor.privategallery.core.ui.AppTheme
import uk.co.traynor.privategallery.core.vault.*

/** Real encrypted repository/controller/UI; only the remote HTTP boundary is synthetic. */
class EvidenceReportWorkflow60Test {
 @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
 @Test fun permissionRequestKeepsCurrentTaskAfterDismissAndTaskChipChange() {
  val root=File(compose.activity.cacheDir,"report-permission60-${UUID.randomUUID()}")
  val context=object:ContextWrapper(compose.activity){override fun getApplicationContext():Context=this
   override fun getFilesDir()=File(root,"files").apply{mkdirs()};override fun getNoBackupFilesDir()=File(root,"backup").apply{mkdirs()}}
  val key=ByteArray(32){62};val access=EvidenceAccess.primary(context,key,{key},{true},{key.copyOf()},Any())
  val controller=compose.runOnIdle{EvidenceController(context,access)}
  var showing by mutableStateOf(true);var received by mutableStateOf("");var parentTask:EvidenceAi.Task?=EvidenceAi.Task.EVIDENCE_REPORT
  try {
   compose.setContent{CompositionLocalProvider(LocalContext provides context){PrivateGalleryTheme(AppTheme.DARK){
    if(showing)EvidenceAiDialog(controller,emptyList(),false,createCoordinator={error("Permission preparation cannot connect or send evidence")},
     onRequestPermission={fail("Task-aware permission route must be used")},onClose={parentTask=null;showing=false},
     initialMode="VPN",initialTask=EvidenceAi.Task.EVIDENCE_REPORT,onRequestPermissionWithTask={mode,task->
      assertNull("Dismissal already clears parent selection",parentTask);assertEquals("VPN",mode);received=task.name
     })
    else Text("Permission task: $received")
   }}}
   compose.evidenceNode(hasText("Set up Android VPN / isolation permission")).performClick()
   compose.onNodeWithText("Permission task: EVIDENCE_REPORT").assertExists()
   compose.runOnIdle{parentTask=EvidenceAi.Task.EVIDENCE_REPORT;received="";showing=true}
   compose.evidenceNode(hasText("Structured event extraction")).performClick()
   compose.evidenceNode(hasText("Set up Android VPN / isolation permission")).performClick()
   compose.onNodeWithText("Permission task: STRUCTURE").assertExists()
  }finally{compose.runOnIdle{controller.close()};access.close();key.fill(0);root.deleteRecursively()}
 }
 @Test fun explicitConsentEncryptedHistoryAndLinkedOriginalPreserveEvidence() {
  val root=File(compose.activity.cacheDir,"report60-${UUID.randomUUID()}")
  val context=object:ContextWrapper(compose.activity){override fun getApplicationContext():Context=this
   override fun getFilesDir()=File(root,"files").apply{mkdirs()};override fun getNoBackupFilesDir()=File(root,"backup").apply{mkdirs()}}
  val key=ByteArray(32){61};val quote="If you leave I will hurt you."
  val bytes=quote.toByteArray();val calls=AtomicInteger();val reads=AtomicInteger()
  val repo=AndroidVaultRepository(context,key)
  val original=(repo.importVerified(VaultImportSource("selected60.txt","text/plain",{bytes.inputStream()}))as ImportResult.Imported).item
  val excluded="UNSELECTED_PRIVATE_RECORD_60".toByteArray()
  val other=(repo.importVerified(VaultImportSource("excluded60.txt","text/plain",{excluded.inputStream()}))as ImportResult.Imported).item
  val access=EvidenceAccess.primary(context,key,{key},{true},{key.copyOf()},Any())
  val controller=compose.runOnIdle{EvidenceController(context,access).also{it.register(original.id);it.register(other.id)}}
  val credentials=EvidenceCredentials(EvidenceCatalog.Owner.PRIMARY,access::current,{action->access.leases.acquire().use{it.commit{action()}}},object:EvidenceCredentials.Backend {
   override fun configured()=true
   override fun read():ByteArray{reads.incrementAndGet();return "SYNTHETIC_REPORT60_KEY".toByteArray()}
   override fun stage(value:ByteArray):AiCredentialStore.PreparedChange=error("unused")
   override fun removal():AiCredentialStore.PreparedChange=error("unused")
  },{})
  var screen by mutableStateOf("ai");var export by mutableStateOf("")
  try {
   runBlocking{withTimeout(15000){controller.state.first{!it.busy&&it.catalog?.evidence()?.size==2}}}
   val id=access.store.read().evidence().single{it.sourceId()==original.id}.id()
   compose.setContent{CompositionLocalProvider(LocalContext provides context){PrivateGalleryTheme(AppTheme.DARK){
    if(screen=="ai")EvidenceAiDialog(controller,listOf(id),true,createCoordinator={object:EvidenceRouteConnection {
     var connection:EvidenceRoute?=null
     override suspend fun connect(mode:String)=object:EvidenceRoute {
      var ended=false;override val owner=EvidenceCatalog.Owner.PRIMARY;override val label="Synthetic report route";override val identity=UUID.randomUUID().toString()
      override fun current()=!ended&&access.current()
      override suspend fun execute(request:AiHttpRequest):AiHttpResponse {
       calls.incrementAndGet();val body=ByteArrayOutputStream().use{request.body!!.writeTo(it);it.toString("UTF-8")}
       assertTrue(body.contains(quote));assertFalse(body.contains("UNSELECTED_PRIVATE_RECORD_60"));assertFalse(body.contains("excluded60.txt"))
       val reference=JSONObject().put("evidence_id",id).put("field","transcript").put("passage",quote).put("timestamp_quote",JSONObject.NULL)
       val report=JSONObject().put("version",1).put("outcome","INSUFFICIENT_EVIDENCE").put("assessment","A selected excerpt may contain a threat; wider context is missing").put("confidence","LOW")
        .put("alternatives",JSONArray().put("Could quote another person")).put("limitations",JSONArray().put("Only selected material"))
        .put("chronology",JSONArray()).put("instances",JSONArray().put(JSONObject().put("categories",JSONArray().put("THREATS")).put("assessment","Potential threat").put("confidence","LOW").put("intent","UNKNOWN")
         .put("alternatives",JSONArray().put("Missing context")).put("references",JSONArray().put(reference))))
       val response=JSONObject().put("status","completed").put("usage",JSONObject().put("input_tokens",30).put("output_tokens",40))
        .put("output",JSONArray().put(JSONObject().put("type","message").put("content",JSONArray().put(JSONObject().put("type","output_text").put("text",report.toString())))))
       return AiHttpResponse(200,"application/json",response.toString().toByteArray())
      }
      override fun close(){ended=true}
     }.also{connection=it}
     override fun close(){connection?.close()}
    }},onRequestPermission={fail("No real permission/network required")},onClose={screen="locker"},providedCredentials=credentials,
     initialTask=EvidenceAi.Task.EVIDENCE_REPORT,onExportReport={export=it})
    if(screen=="locker")EvidenceLocker(controller,{screen="closed"},{},{},{},{})
    if(export.isNotEmpty())Text("Requested report export $export")
   }}}
   compose.evidenceNode(hasText("Prepare exact upload preview")).performClick()
   compose.waitUntil(15000){compose.onAllNodesWithText("Review before sending").fetchSemanticsNodes().isNotEmpty()}
   assertEquals(0,calls.get());assertEquals(0,reads.get())
   compose.evidenceNode(hasText("Cancel preview")).performClick();assertEquals(0,calls.get())
   compose.evidenceNode(hasText("Prepare exact upload preview")).performClick()
   compose.waitUntil(15000){compose.onAllNodesWithText("Confirm and send selected evidence").fetchSemanticsNodes().isNotEmpty()}
   compose.evidenceNode(hasText("Confirm and send selected evidence")).performClick()
   compose.waitUntil(15000){compose.onAllNodesWithText("Insufficient Evidence*").fetchSemanticsNodes().isNotEmpty()}
   compose.evidenceNode(hasText("Threats*: 1*")).assertIsDisplayed();assertEquals(1,calls.get());assertEquals(1,reads.get())
   compose.evidenceNode(hasText("Save encrypted report")).performClick()
   compose.waitUntil(15000){controller.state.value.catalog?.analyses()?.size==1&&!controller.state.value.busy}
   val saved=access.store.read().analyses().single();assertEquals(1,EvidenceAi.details(saved).report!!.counts[EvidenceAiReport.Category.THREATS])
   assertTrue(access.store.read().incidents().isEmpty());assertArrayEquals(original.plaintextSha256,repo.items().single{it.id==original.id}.plaintextSha256)
   compose.evidenceNode(hasText("Export report only")).performClick();assertEquals(saved.id(),export)
   compose.evidenceNode(hasText("Close") and hasClickAction()).performClick()
   compose.onNodeWithText("AI reports").performClick();compose.evidenceNode(hasText("Open report ${saved.id().take(8)}")).performClick()
   compose.evidenceNode(hasText("Open supporting evidence ${id.take(8)}")).performClick()
   val verifiedOriginal="Verified original bytes · display rendering only"
   val automation=InstrumentationRegistry.getInstrumentation().uiAutomation
   fun hasVerifiedOriginal(node:AccessibilityNodeInfo?):Boolean {
    if(node==null)return false
    if(node.isVisibleToUser&&node.text?.toString()==verifiedOriginal)return true
    return (0 until node.childCount).any{hasVerifiedOriginal(node.getChild(it))}
   }
   // Let Compose advance frames without entering Espresso idling on every poll while the preview lays out.
   compose.waitUntil(15000){hasVerifiedOriginal(automation.rootInActiveWindow)}
   compose.onNodeWithText(verifiedOriginal).assertIsDisplayed()
   compose.onNodeWithText(quote).assertExists();assertEquals(2,repo.items().size)
   compose.runOnIdle{controller.close()}
   compose.waitUntil(10000){compose.onAllNodesWithText("AI evidence report*").fetchSemanticsNodes().isEmpty()}
  }finally{compose.runOnIdle{controller.close()};access.close();key.fill(0);bytes.fill(0);excluded.fill(0);root.deleteRecursively()}
 }
}
