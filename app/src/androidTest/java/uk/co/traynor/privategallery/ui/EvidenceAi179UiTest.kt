package uk.co.traynor.privategallery.ui

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import uk.co.traynor.privategallery.core.evidence.*
import uk.co.traynor.privategallery.core.editor.AiHttpRequest
import uk.co.traynor.privategallery.core.editor.AiHttpResponse
import uk.co.traynor.privategallery.core.ui.AppTheme
import uk.co.traynor.privategallery.core.vault.*

class EvidenceAi179UiTest {
 @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
 private lateinit var controller:EvidenceController
 @Test fun actualPreviewNeedsExplicitSendAndSeparateOwnerApplication() {
  val root=File(compose.activity.cacheDir,"evidence-ai-ui179-${UUID.randomUUID()}")
  val context=object:ContextWrapper(compose.activity){override fun getFilesDir()=File(root,"files").apply{mkdirs()};override fun getNoBackupFilesDir()=File(root,"no-backup").apply{mkdirs()};override fun getApplicationContext():Context=this}
  val key=ByteArray(32){19};val calls=AtomicInteger();val keyReads=AtomicInteger();val closed=AtomicInteger()
  val bytes="Selected synthetic Alice transcript179, visible date is uncertain".toByteArray()
  val repo=AndroidVaultRepository(context,key)
  val original=(repo.importVerified(VaultImportSource("selected179.txt","text/plain",{bytes.inputStream()}))as ImportResult.Imported).item
  var showingAi by mutableStateOf(true)
  val access=EvidenceAccess.primary(context,key,{key},{true},{key.copyOf()},Any())
  val credentials=EvidenceCredentials(EvidenceCatalog.Owner.PRIMARY,access::current,{action->access.leases.acquire().use{it.commit{action()}}},object:EvidenceCredentials.Backend{
   override fun configured()=true
   override fun stage(value:ByteArray):uk.co.traynor.privategallery.core.editor.AiCredentialStore.PreparedChange = error("This read-only consent fixture must not replace credentials")
   override fun read():ByteArray{keyReads.incrementAndGet();return "sk-synthetic179-only".toByteArray()}
   override fun removal():uk.co.traynor.privategallery.core.editor.AiCredentialStore.PreparedChange = error("This read-only consent fixture must not remove credentials")
  },{})
  try {
   androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().runOnMainSync{controller=EvidenceController(context,access);controller.register(original.id)}
   runBlocking{withTimeout(15000){controller.state.first{!it.busy&&it.catalog?.evidence()?.size==1}}}
   val selected=access.store.read().evidence().single().id()
   compose.setContent{CompositionLocalProvider(LocalContext provides context){PrivateGalleryTheme(AppTheme.DARK){if(!showingAi)EvidenceLocker(controller,{},{},{},{},{}) else EvidenceAiDialog(controller,listOf(selected),true,
    createCoordinator={object:EvidenceRouteConnection{
     var route:EvidenceRoute?=null
     override suspend fun connect(mode:String):EvidenceRoute=object:EvidenceRoute{
      override val owner=EvidenceCatalog.Owner.PRIMARY;override val label="Synthetic secure route";override val identity=UUID.randomUUID().toString();var ended=false
      override fun current()=!ended&&access.current()
      override suspend fun execute(request:AiHttpRequest):AiHttpResponse{check(current());calls.incrementAndGet();assertEquals(EvidenceAi.ENDPOINT,request.url);val advisory=org.json.JSONObject().put("summary","Synthetic advisory179. The visible timestamp is uncertain.").put("suggestions",org.json.JSONArray()).put("events",org.json.JSONArray().put(org.json.JSONObject().put("evidence_ids",org.json.JSONArray().put(selected)).put("title","Retained structured event179").put("date_time_quote","visible date uncertain").put("provenance","VISIBLE_SOURCE_UNVERIFIED").put("uncertainty","No verified date or timezone")))
       val response=org.json.JSONObject().put("status","completed").put("output",org.json.JSONArray().put(org.json.JSONObject().put("type","message").put("content",org.json.JSONArray().put(org.json.JSONObject().put("type","output_text").put("text",advisory.toString()))))).put("usage",org.json.JSONObject().put("input_tokens",50).put("output_tokens",12))
       return AiHttpResponse(200,"application/json",response.toString().toByteArray())}
      override fun close(){ended=true}
     }.also{route=it}
     override fun close(){route?.close()}
    }},onRequestPermission={fail("fixture must not request real VPN permission")},onClose={closed.incrementAndGet();showingAi=false},providedCredentials=credentials)}}}
   compose.waitUntil(10000){compose.onAllNodesWithText("Prepare exact upload preview").fetchSemanticsNodes().isNotEmpty()}
   compose.evidenceNode(hasText("Prepare exact upload preview")).performClick()
   compose.waitUntil(15000){compose.onAllNodesWithText("Review before sending").fetchSemanticsNodes().isNotEmpty()}
   compose.onNodeWithText("Synthetic secure route",substring=true).assertExists()
   compose.onNodeWithText("0 explicit images",substring=true).assertExists()
   compose.onNodeWithText("Selected synthetic Alice transcript179",substring=true).assertExists()
   assertEquals(0,calls.get());assertEquals(0,keyReads.get())
   compose.evidenceNode(hasText("Cancel preview")).performClick()
   assertEquals(0,calls.get());assertTrue(access.store.read().analyses().isEmpty())
   compose.evidenceNode(hasText("Prepare exact upload preview")).performClick()
   compose.waitUntil(15000){compose.onAllNodesWithText("Confirm and send selected evidence").fetchSemanticsNodes().isNotEmpty()}
   compose.evidenceNode(hasText("Confirm and send selected evidence")).performClick()
   compose.waitUntil(15000){compose.onAllNodesWithText("Synthetic advisory179.",substring=true).fetchSemanticsNodes().isNotEmpty()}
   assertEquals(1,calls.get());assertEquals(1,keyReads.get());assertTrue(access.store.read().incidents().isEmpty())
   compose.evidenceNode(hasText("Save advisory")).performClick()
   compose.waitUntil(15000){controller.state.value.catalog?.analyses()?.size==1&&!controller.state.value.busy}
   assertFalse(access.store.read().analyses().single().applied())
   compose.evidenceNode(hasText("Create incident")).performClick();compose.evidenceNode(hasText("Confirm") and hasClickAction()).performClick()
   compose.onNode(hasSetTextAction() and hasText("Title")).performTextReplacement("Owner-approved AI179")
   compose.onNodeWithText("Create").performClick()
   compose.waitUntil(15000){controller.state.value.catalog?.incidents()?.size==1&&!controller.state.value.busy}
   val catalog=access.store.read();assertEquals("Owner-approved AI179",catalog.incidents().single().title());assertTrue(catalog.analyses().single().applied());assertTrue(catalog.incidents().single().notes().contains("AI-generated advisory"))
   assertEquals(listOf(catalog.analyses().single().id()),catalog.incidents().single().analysisIds())
   assertArrayEquals(original.plaintextSha256,repo.items().single().plaintextSha256)
   compose.onNodeWithText("Close").performClick()
   compose.onNodeWithText("Timeline").performClick();compose.evidenceNode(hasText("Owner-approved AI179")).performClick()
   compose.evidenceNode(hasText("Synthetic advisory179. The visible timestamp is uncertain.")).assertIsDisplayed()
   compose.runOnIdle{compose.activity.onBackPressedDispatcher.onBackPressed()}
   compose.onNodeWithText("Evidence").performClick();compose.evidenceNode(hasContentDescription("Select evidence selected179.txt")).performClick()
   compose.evidenceNode(hasText("Evidence tools")).performClick()
   compose.evidenceNode(hasText("Remove references")).performClick();compose.evidenceNode(hasText("Confirm") and hasClickAction()).performClick()
   compose.waitUntil(15000){!controller.state.value.busy&&controller.state.value.catalog?.evidence()?.isEmpty()==true}
   compose.onNodeWithText("Timeline").performClick();compose.evidenceNode(hasText("Owner-approved AI179")).performClick()
   compose.evidenceNode(hasText("Synthetic advisory179. The visible timestamp is uncertain.")).assertIsDisplayed()
   compose.evidenceNode(hasText("Advisory event: Retained structured event179",substring=true)).assertIsDisplayed()
   compose.evidenceNode(hasText("Historical / unattached advisory source",substring=true),last=true).assertIsDisplayed()
   val retained=access.store.read();val incidentId=retained.incidents().single().id()
   assertTrue(retained.incidents().single().evidenceIds().isEmpty());assertEquals(1,repo.items().size)
   assertEquals(original.id,retained.analyses().single().sourceReferences().single().fingerprint().sourceId())
   EvidenceEgress.requireAllowed(retained,emptyList(),listOf(incidentId),access.reader())
   val index=EncryptedIndexStore(File(context.filesDir,"vault"));val snapshot=index.loadSnapshot(key)
   index.saveSnapshot(snapshot.copy(items=snapshot.items.map{it.copy(vaultOnly=true)}),key)
   try{EvidenceEgress.requireAllowed(retained,emptyList(),listOf(incidentId),access.reader());fail("retained advisory declassified") }catch(expected:SecurityException){}
   assertEquals(1,repo.items().size)
   compose.runOnIdle{controller.close();assertFalse(controller.current());assertEquals(EvidenceController.State(),controller.state.value)}
   compose.waitUntil(10000){closed.get()>0}
   compose.onNodeWithText("Optional AI analysis").assertDoesNotExist();compose.onNodeWithText("Evidence Locker").assertDoesNotExist()
  }finally{if(::controller.isInitialized)compose.runOnIdle{controller.close()};access.close();key.fill(0);bytes.fill(0);root.deleteRecursively()}
 }
}
