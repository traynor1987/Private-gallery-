package uk.co.traynor.privategallery.ui

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import uk.co.traynor.privategallery.core.evidence.*
import uk.co.traynor.privategallery.core.hidden.HiddenRepository
import uk.co.traynor.privategallery.core.hidden.HiddenSession
import uk.co.traynor.privategallery.core.ui.AppTheme
import uk.co.traynor.privategallery.core.vault.*

/** Real gestures/components and encrypted storage; synthetic evidence only. No physical Fold claim. */
class EvidenceLocker179UiTest {
 private lateinit var controller:EvidenceController
 private val compose=createAndroidComposeRule<ComponentActivity>()
 private val viewport=BrowserViewportRule(fontScale=2f){name->if(name.contains("landscape",ignoreCase=true))840 to 392 else if(name.contains("inner",ignoreCase=true))840 to 900 else 320 to 840}
 @get:Rule val rules:TestRule=RuleChain.outerRule(viewport).around(compose)
 private class Fixture(val root:File,val context:Context,val key:ByteArray,val hidden:HiddenSession?,val access:EvidenceAccess,val ids:List<String>)
 private fun fixture(secret:Boolean=false):Fixture {
  val root=File(compose.activity.cacheDir,"evidence-ui179-${UUID.randomUUID()}")
  val context=object:ContextWrapper(compose.activity){override fun getFilesDir()=File(root,"files").apply{mkdirs()};override fun getNoBackupFilesDir()=File(root,"no-backup").apply{mkdirs()};override fun getApplicationContext():Context=this}
  val key=ByteArray(32){if(secret)71 else 42};val hidden=if(secret)HiddenSession{android.os.SystemClock.elapsedRealtime()}.also{check(it.complete(it.beginAuthentication(),key.copyOf()))}else null
  val texts=listOf("Alice 2026-10-31T10:00:00Z arrange delivery tomorrow morning at reception","Unrelated synthetic receipt banana 179")
  val ids=texts.mapIndexed{index,text->val bytes=text.toByteArray();try{if(hidden==null)(AndroidVaultRepository(context,key).importVerified(VaultImportSource(if(index==0)"alpha179.txt"else"beta179.txt","text/plain",{bytes.inputStream()}))as ImportResult.Imported).item.id else HiddenRepository(context,hidden).importMedia(if(index==0)"alpha179.txt"else"beta179.txt","text/plain",bytes.inputStream()).id}finally{bytes.fill(0)}}
  val access=if(hidden==null)EvidenceAccess.primary(context,key,{key},{true},{key.copyOf()},Any())else EvidenceAccess.hidden(context,hidden){true}
  return Fixture(root,context,key,hidden,access,ids)
 }
 private fun waitReady(controller:EvidenceController,count:Int?=null){
  try{compose.waitUntil(45000){val s=controller.state.value;!s.busy&&s.catalog!=null&&(count==null||s.catalog.evidence().size==count)}}
  catch(failure:ComposeTimeoutException){val s=controller.state.value;throw AssertionError("Controller readiness: expectedCount=$count actualCount=${s.catalog?.evidence()?.size} busy=${s.busy} revision=${s.catalog?.revision()} originals=${s.originals.size}",failure)}
 }
 private fun approveImportReview(){
  click("Run offline OCR")
  compose.waitUntil(45000){compose.onAllNodesWithText("Review groups").fetchSemanticsNodes().isNotEmpty()}
  click("Review groups");click("Save approved evidence")
 }
 private fun click(text:String){compose.evidenceNode(hasText(text)).performClick()}
 private fun field(label:String)=compose.onNode(hasSetTextAction() and hasText(label))
 private fun chooseTimestamp(hour:Int){
  click("Choose date and time")
  compose.onNodeWithTag("media-date-picker").assertExists()
  compose.onNodeWithText("Next").performClick()
  compose.onNodeWithTag("media-hour").performTextReplacement(hour.toString())
  compose.onNodeWithTag("media-minute").performTextReplacement("00")
  compose.onNodeWithText("Save date").performClick()
 }
 @Test fun primaryActualImportExtractCorrectIncidentSearchRemoveAndReopenPreservesOriginal() {
  val f=fixture()
  try {
   androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().runOnMainSync{controller=EvidenceController(f.context,f.access)}
   compose.setContent{CompositionLocalProvider(LocalContext provides f.context){PrivateGalleryTheme(AppTheme.LIGHT){EvidenceLocker(controller,{},{},{},{},{})}}}
   waitReady(controller,0)
   click("Add existing evidence");compose.evidenceNode(hasContentDescription("Register original alpha179.txt")).assertIsDisplayed().assertHeightIsAtLeast(48.dp).performClick().assertIsOn();approveImportReview();waitReady(controller,1)
   compose.onNodeWithText("Evidence").performClick();compose.evidenceNode(hasText("alpha179.txt")).performClick();waitReady(controller,1)
   click("Review / correct");field("Owner-corrected text").performTextReplacement("unique179 owner corrected Alice timestamp uncertain")
   compose.onNodeWithText("Save").performClick()
   compose.waitUntil(15000){controller.state.value.catalog?.evidence()?.singleOrNull()?.transcript()?.startsWith("unique179")==true&&!controller.state.value.busy}
   val corrected=f.access.store.read().evidence().single();assertTrue(corrected.ocr().single().text().contains("arrange delivery"));assertEquals(1,corrected.corrections().size)
   compose.runOnIdle{compose.activity.onBackPressedDispatcher.onBackPressed()}
   click("Add evidence");compose.evidenceNode(hasContentDescription("Register original beta179.txt")).assertIsDisplayed().assertHeightIsAtLeast(48.dp).performClick().assertIsOn();approveImportReview();waitReady(controller,2)
   compose.onNodeWithText("Timeline").performClick();click("New incident")
   field("Title").performTextReplacement("Synthetic incident179");compose.onNodeWithText("Save").performClick()
   compose.waitUntil(15000){controller.state.value.catalog?.incidents()?.size==1&&!controller.state.value.busy}
   compose.runOnIdle{compose.activity.onBackPressedDispatcher.onBackPressed()}
   compose.onNodeWithText("Evidence").performClick();compose.evidenceNode(hasContentDescription("Select evidence alpha179.txt")).performClick()
   click("Evidence tools");click("Add selected to incident");click("Synthetic incident179")
   compose.waitUntil(15000){controller.state.value.catalog?.incidents()?.singleOrNull()?.evidenceIds()?.contains(corrected.id())==true&&!controller.state.value.busy}
   compose.evidenceNode(hasTestTag("evidence-search") and hasSetTextAction()).performTextReplacement("unique179")
   // At 2x text the Library controls plus the IME can occupy the viewport, so a
   // matching result may be genuinely uncomposed. Materialize by matcher on the
   // list, then query afresh; do not retain a row while scrolling/filtering it.
   compose.onNodeWithTag("evidence-list").performScrollToNode(hasText("alpha179.txt") and hasClickAction())
   compose.onNode(hasText("alpha179.txt") and hasClickAction()).assertExists();compose.onNodeWithText("beta179.txt").assertDoesNotExist()
   assertEquals(listOf(corrected.id()),f.access.store.read().search("unique179",0,0,false).map{it.id()})
   compose.evidenceNode(hasTestTag("evidence-search") and hasSetTextAction()).performTextClearance()
   compose.evidenceNode(hasContentDescription("Select evidence alpha179.txt")).performClick()
   compose.evidenceNode(hasText("Remove references") and hasClickAction()).performClick();compose.evidenceNode(hasText("Confirm") and hasClickAction()).performClick();waitReady(controller,1)
   assertEquals(2,AndroidVaultRepository(f.context,f.key).items().size)
   val original=f.access.reader().readVerified(f.ids.first()){_,input->input.readBytes()};try{assertTrue(String(original).contains("arrange delivery"))}finally{original.fill(0)}
   compose.runOnIdle{controller.close()}
   val fresh=EvidenceAccess.primary(f.context,f.key,{f.key},{true},{f.key.copyOf()},Any())
   try{assertEquals("beta179.txt",fresh.store.read().evidence().single().name());assertEquals(1,fresh.store.read().incidents().size);assertEquals(2,fresh.sources.list().size)}finally{fresh.close()}
  }finally{if(::controller.isInitialized)compose.runOnIdle{controller.close()};f.hidden?.lock();f.key.fill(0);f.root.deleteRecursively()}
 }
 @Test fun ownerAcceptsAndRejectsActualGroupingHintsAndOrdersUncertainTimeline() {
  val f=fixture();val repo=AndroidVaultRepository(f.context,f.key)
  val duplicateBytes="Alice 2026-10-31T10:00:00Z arrange delivery tomorrow morning at reception".toByteArray()
  try {
   val duplicate=(repo.importVerified(VaultImportSource("copy179.txt","text/plain",{duplicateBytes.inputStream()},createDistinctCopy=true))as ImportResult.Imported).item
   androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().runOnMainSync{controller=EvidenceController(f.context,f.access)}
   compose.setContent{CompositionLocalProvider(LocalContext provides f.context){PrivateGalleryTheme(AppTheme.LIGHT){EvidenceLocker(controller,{},{},{},{},{})}}}
   compose.runOnIdle{controller.register(f.ids.first());controller.register(duplicate.id)};waitReady(controller,2)
   val before=f.access.store.read();assertTrue(before.incidents().isEmpty())
   val suggestion=before.suggestions().first{it.kind()=="GROUP"};val tag="evidence-suggestion-${suggestion.id()}"
   compose.evidenceNode(hasText("Review / accept") and hasAnyAncestor(hasTestTag(tag))).performClick()
   field("New incident title").performTextReplacement("Approved group179");compose.onNodeWithText("Save").performClick()
   compose.waitUntil(15000){controller.state.value.catalog?.incidents()?.size==1&&!controller.state.value.busy}
   val grouped=f.access.store.read();assertEquals(2,grouped.incidents().single().evidenceIds().size);assertEquals("ACCEPT_SUGGESTION",grouped.audit().last().action())
   compose.onNodeWithText("Timeline").performClick();compose.evidenceNode(hasText("Approved group179")).performClick()
   click("Add event");field("Title").performTextReplacement("Late event179");chooseTimestamp(12);compose.onNodeWithText("Save").performClick()
   compose.waitUntil(15000){controller.state.value.catalog?.incidents()?.singleOrNull()?.events()?.size==1&&!controller.state.value.busy}
   click("Add event");field("Title").performTextReplacement("Uncertain event179");compose.onNodeWithText("Save").performClick()
   compose.waitUntil(15000){controller.state.value.catalog?.incidents()?.singleOrNull()?.events()?.size==2&&!controller.state.value.busy}
   val uncertain=f.access.store.read().incidents().single().events().single{it.title()=="Uncertain event179"}
   assertEquals(0L,uncertain.timestamp());assertEquals("USER",uncertain.provenance())
   compose.evidenceNode(hasText("Move earlier") and hasAnyAncestor(hasTestTag("evidence-event-${uncertain.id()}"))).performClick()
   compose.waitUntil(15000){controller.state.value.catalog?.incidents()?.singleOrNull()?.manualOrder()==true&&!controller.state.value.busy}
   assertEquals(uncertain.id(),f.access.store.read().incidents().single().events().first().id())
   val third=(repo.importVerified(VaultImportSource("third-copy179.txt","text/plain",{duplicateBytes.inputStream()},createDistinctCopy=true))as ImportResult.Imported).item
   compose.runOnIdle{controller.register(third.id)};waitReady(controller,3)
   compose.onNodeWithText("Home").performClick()
   val assign=f.access.store.read().suggestions().first{it.kind()=="ASSIGN"}
   compose.evidenceNode(hasText("Reject") and hasAnyAncestor(hasTestTag("evidence-suggestion-${assign.id()}"))).performClick()
   compose.waitUntil(15000){controller.state.value.catalog?.rejectedSuggestions()?.contains(assign.id())==true&&!controller.state.value.busy}
   assertEquals(2,f.access.store.read().incidents().single().evidenceIds().size);assertEquals(4,repo.items().size)
  }finally{if(::controller.isInitialized)compose.runOnIdle{controller.close()};duplicateBytes.fill(0);f.key.fill(0);f.root.deleteRecursively()}
 }
 @Test fun firstManualMoveUsesVisibleChronologyAndDisablesBoundaries() {
  val f=fixture()
  val events=listOf(300L,0L,200L,100L).map{at->EvidenceCatalog.Event(UUID.randomUUID().toString(),emptyList(),at,"USER","event-$at","")}
  val incident=EvidenceCatalog.Incident(UUID.randomUUID().toString(),"Chronology179",0,0,"","",emptyList(),emptyList(),events,false,false,emptyList())
  f.access.store.update{it.upsertIncident(incident,1,"CREATE")}
  try {
   androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().runOnMainSync{controller=EvidenceController(f.context,f.access)}
   compose.setContent{CompositionLocalProvider(LocalContext provides f.context){PrivateGalleryTheme(AppTheme.LIGHT){EvidenceLocker(controller,{},{},{},{},{})}}}
   waitReady(controller);compose.onNodeWithText("Timeline").performClick();click("Chronology179")
   fun arrow(at:Long,label:String)=compose.evidenceNode(hasText(label) and hasAnyAncestor(hasTestTag("evidence-event-${events.single{it.timestamp()==at}.id()}")))
   arrow(100,"Move earlier").assertIsNotEnabled()
   arrow(0,"Move later").assertIsNotEnabled()
   val revision=f.access.store.read().revision()
   arrow(100,"Move later").performClick()
   compose.waitUntil(15000){!controller.state.value.busy&&controller.state.value.catalog?.revision()==revision+1}
   assertEquals(listOf(200L,100L,300L,0L),f.access.store.read().timeline(incident.id()).map{it.timestamp()})
   arrow(0,"Move earlier").performClick()
   compose.waitUntil(15000){!controller.state.value.busy&&controller.state.value.catalog?.revision()==revision+2}
   assertEquals(listOf(200L,100L,0L,300L),f.access.store.read().timeline(incident.id()).map{it.timestamp()})
  }finally{if(::controller.isInitialized)compose.runOnIdle{controller.close()};f.key.fill(0);f.root.deleteRecursively()}
 }
 @Test fun bulkOriginalRegistrationAndMixedExportSelectionAreOwnerReviewed() {
  val f=fixture();var exported:EvidenceExportSelection?=null
  val incidents=listOf("First selected179","Second selected179").map{title->EvidenceCatalog.Incident(UUID.randomUUID().toString(),title,0,0,"","",emptyList(),emptyList(),emptyList(),false,false,emptyList())}
  f.access.store.update{initial->incidents.fold(initial){c,i->c.upsertIncident(i,1,"CREATE")}}
  try {
   androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().runOnMainSync{controller=EvidenceController(f.context,f.access)}
   compose.setContent{CompositionLocalProvider(LocalContext provides f.context){PrivateGalleryTheme(AppTheme.LIGHT){EvidenceLocker(controller,{},{},{},{exported=it},{})}}}
   waitReady(controller,0);click("Add existing evidence");compose.evidenceNode(hasContentDescription("Register original alpha179.txt")).assertIsDisplayed().assertHeightIsAtLeast(48.dp).performClick().assertIsOn();compose.evidenceNode(hasContentDescription("Register original beta179.txt")).assertIsDisplayed().assertHeightIsAtLeast(48.dp).performClick().assertIsOn()
   assertTrue(f.access.store.read().evidence().isEmpty())
   approveImportReview();waitReady(controller,2)
   assertEquals(2,f.access.sources.list().size)
   compose.onNodeWithText("Evidence").performClick();compose.evidenceNode(hasContentDescription("Select evidence beta179.txt")).performClick()
   click("Evidence tools");click("Export selection")
   compose.evidenceNode(hasContentDescription("Export incident First selected179")).performClick()
   compose.evidenceNode(hasContentDescription("Export incident Second selected179")).performClick()
   assertNull(exported)
   compose.onNodeWithText("Review export").performClick()
   assertEquals(incidents.map{it.id()},exported!!.incidentIds)
   assertEquals(listOf(f.access.store.read().evidence().single{it.name()=="beta179.txt"}.id()),exported!!.evidenceIds)
   assertEquals(2,f.access.sources.list().size)
  }finally{if(::controller.isInitialized)compose.runOnIdle{controller.close()};f.key.fill(0);f.root.deleteRecursively()}
 }
 @Test fun editedExtractedTimestampRemainsUserEnteredWithRawQuotePreserved() {
  val f=fixture()
  try {
   androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().runOnMainSync{controller=EvidenceController(f.context,f.access);controller.register(f.ids.first(),extract=false)}
   waitReady(controller,1)
   val record=f.access.store.read().evidence().single()
   val extraction=EvidenceCatalog.Ocr(UUID.randomUUID().toString(),1,"fixture",0,1,1,"visible 10:00",-1f,emptyList(),listOf(EvidenceCatalog.ExtractedTime(0,"10:00",0,"Date and timezone unavailable",-1f)),1,false)
   val incident=EvidenceCatalog.Incident(UUID.randomUUID().toString(),"Reviewed time179",0,0,"","",emptyList(),listOf(record.id()),emptyList(),false,false,emptyList())
   f.access.store.update{it.saveOcr(record.id(),extraction,1).upsertIncident(incident,2,"CREATE")}
   compose.runOnIdle{controller.refresh()};waitReady(controller,1)
   compose.setContent{CompositionLocalProvider(LocalContext provides f.context){PrivateGalleryTheme(AppTheme.LIGHT){EvidenceLocker(controller,{},{},{},{},{})}}}
   compose.onNodeWithText("Timeline").performClick();click("Reviewed time179");click("Review time: 10:00")
   chooseTimestamp(10);compose.onNodeWithText("Save").performClick()
   compose.waitUntil(15000){!controller.state.value.busy&&controller.state.value.catalog?.incidents()?.single()?.events()?.size==1}
   val event=f.access.store.read().incidents().single().events().single()
   assertEquals("USER",event.provenance());assertTrue(event.notes().contains("OCR page 1: 10:00"));assertTrue(event.notes().contains("Date and timezone unavailable"))
   assertEquals(0L,f.access.store.read().evidence().single().ocr().single().times().single().epochMillis())
   assertEquals(2,f.access.sources.list().size)
  }finally{if(::controller.isInitialized)compose.runOnIdle{controller.close()};f.key.fill(0);f.root.deleteRecursively()}
 }
 @Test fun landscapeDarkLargeTextIncidentFormAndExitRemainReachable() {
  val f=fixture()
  assertTrue("Effective resource viewport must be landscape",compose.activity.resources.configuration.screenWidthDp>compose.activity.resources.configuration.screenHeightDp)
  assertEquals(840,compose.activity.resources.configuration.screenWidthDp)
  assertEquals(2f,compose.activity.resources.configuration.fontScale,0.001f)
  try {
   androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().runOnMainSync{controller=EvidenceController(f.context,f.access)}
   compose.setContent{CompositionLocalProvider(LocalContext provides f.context){PrivateGalleryTheme(AppTheme.DARK){EvidenceLocker(controller,{},{},{},{},{})}}}
   waitReady(controller,0);click("New incident")
   field("Title").performTextReplacement("Landscape179")
   compose.onNodeWithText("Save").assertIsDisplayed().assertHeightIsAtLeast(48.dp).performClick()
   compose.waitUntil(15000){controller.state.value.catalog?.incidents()?.singleOrNull()?.title()=="Landscape179"&&!controller.state.value.busy}
   compose.onNodeWithText("Close").assertIsDisplayed().performClick()
   compose.onNodeWithText("Evidence Locker").assertDoesNotExist()
  }finally{if(::controller.isInitialized)compose.runOnIdle{controller.close()};f.key.fill(0);f.root.deleteRecursively()}
 }
 @Test fun hiddenInnerLockClearsSearchSelectionsAndDialogsWithoutDeletingEncryptedEvidence() {
  val f=fixture(true);val closed=AtomicInteger()
  try {
   androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().runOnMainSync{controller=EvidenceController(f.context,f.access)}
   compose.setContent{CompositionLocalProvider(LocalContext provides f.context){PrivateGalleryTheme(AppTheme.DARK){EvidenceLocker(controller,{closed.incrementAndGet()},{},{},{},{})}}}
   compose.runOnIdle{controller.register(f.ids.first())};waitReady(controller,1)
   compose.onNodeWithText("Evidence").performClick();compose.onNodeWithTag("evidence-search").performTextReplacement("Alice")
   // Finish the real IME transition before scrolling this large-text Library viewport.
   var libraryWindow:android.view.View?=null
   androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.isRoot())
    .inRoot(androidx.test.espresso.matcher.RootMatchers.withDecorView(org.hamcrest.Matchers.equalTo(compose.activity.window.decorView)))
    .check{view,failure->if(failure!=null)throw failure;libraryWindow=checkNotNull(view)}
    .perform(androidx.test.espresso.action.ViewActions.closeSoftKeyboard())
   compose.waitUntil(10000){compose.runOnIdle{libraryWindow?.rootWindowInsets?.isVisible(android.view.WindowInsets.Type.ime())==false}}
   compose.onNodeWithTag("evidence-search").assertTextContains("Alice")
   val selectAlpha=hasContentDescription("Select evidence alpha179.txt")
   compose.waitForIdle()
   compose.onNodeWithTag("evidence-list").performScrollToNode(selectAlpha)
   compose.waitUntil(10000){compose.onAllNodes(selectAlpha).fetchSemanticsNodes().size==1&&compose.onNode(selectAlpha).isDisplayed()}
   compose.onNode(selectAlpha).assertIsDisplayed().performClick()
   // Use the known list after recomposition; the shared helper can lose its child between queries.
   compose.waitForIdle()
   compose.onNodeWithTag("evidence-list").performScrollToNode(selectAlpha)
   val checkedAlpha=selectAlpha and isOn()
   compose.waitUntil(10000){compose.onAllNodes(checkedAlpha).fetchSemanticsNodes().size==1&&compose.onNode(checkedAlpha).isDisplayed()}
   compose.onNode(checkedAlpha).assertIsDisplayed().assertIsOn()
   val openAlpha=hasText("alpha179.txt") and hasClickAction()
   compose.onNodeWithTag("evidence-list").performScrollToNode(openAlpha)
   try{compose.waitUntil(10000){compose.onAllNodes(openAlpha).fetchSemanticsNodes().size==1&&compose.onNode(openAlpha).isDisplayed()}}
   catch(failure:ComposeTimeoutException){val s=controller.state.value;throw AssertionError("Selected Hidden evidence open target: matches=${compose.onAllNodes(openAlpha).fetchSemanticsNodes().size} closed=${closed.get()} busy=${s.busy} records=${s.catalog?.evidence()?.size}",failure)}
   compose.onNode(openAlpha).assertIsDisplayed().performClick();waitReady(controller,1);click("Review / correct")
   field("Owner-corrected text").performTextReplacement("Unsaved private179")
   compose.runOnIdle{f.hidden!!.lock()}
   compose.waitUntil(10000){closed.get()>0&&controller.state.value.catalog==null}
   compose.onNodeWithText("Evidence Locker").assertDoesNotExist();compose.onNodeWithText("Unsaved private179").assertDoesNotExist()
   val next=HiddenSession{android.os.SystemClock.elapsedRealtime()};check(next.complete(next.beginAuthentication(),f.key.copyOf()))
   val fresh=EvidenceAccess.hidden(f.context,next){true}
   try{val c=fresh.store.read();assertEquals(1,c.evidence().size);assertTrue(c.evidence().single().corrections().isEmpty());assertEquals(2,fresh.sources.list().size)}finally{fresh.close();next.lock()}
  }finally{if(::controller.isInitialized)compose.runOnIdle{controller.close()};f.hidden?.lock();f.key.fill(0);f.root.deleteRecursively()}
 }
}
