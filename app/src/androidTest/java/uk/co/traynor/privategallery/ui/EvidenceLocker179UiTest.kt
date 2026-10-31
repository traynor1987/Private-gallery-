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
 private val viewport=BrowserViewportRule(fontScale=2f){name->if(name.contains("Landscape"))840 to 392 else if(name.contains("Inner"))840 to 900 else 320 to 840}
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
 private fun waitReady(controller:EvidenceController,count:Int?=null){compose.waitUntil(45000){val s=controller.state.value;!s.busy&&s.catalog!=null&&(count==null||s.catalog.evidence().size==count)}}
 private fun click(text:String){compose.onNodeWithText(text).performScrollTo().performClick()}
 private fun field(label:String)=compose.onNode(hasSetTextAction() and hasText(label))
 @Test fun primaryActualImportExtractCorrectIncidentSearchRemoveAndReopenPreservesOriginal() {
  val f=fixture()
  try {
   androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().runOnMainSync{controller=EvidenceController(f.context,f.access)}
   compose.setContent{CompositionLocalProvider(LocalContext provides f.context){PrivateGalleryTheme(AppTheme.LIGHT){EvidenceLocker(controller,{},{},{},{},{})}}}
   waitReady(controller,0)
   click("Add existing evidence");click("alpha179.txt");waitReady(controller,1)
   compose.onNodeWithText("Library").performClick();compose.onNodeWithText("alpha179.txt").performScrollTo().performClick();waitReady(controller,1)
   click("Review / correct");field("Owner-corrected text").performTextReplacement("unique179 owner corrected Alice timestamp uncertain")
   compose.onNodeWithText("Save").performClick()
   compose.waitUntil(15000){controller.state.value.catalog?.evidence()?.singleOrNull()?.transcript()?.startsWith("unique179")==true&&!controller.state.value.busy}
   val corrected=f.access.store.read().evidence().single();assertTrue(corrected.ocr().single().text().contains("arrange delivery"));assertEquals(1,corrected.corrections().size)
   compose.runOnIdle{compose.activity.onBackPressedDispatcher.onBackPressed()}
   click("Add evidence");click("beta179.txt");waitReady(controller,2)
   compose.onNodeWithText("Incidents").performClick();click("New incident")
   field("Title").performTextReplacement("Synthetic incident179");compose.onNodeWithText("Save").performClick()
   compose.waitUntil(15000){controller.state.value.catalog?.incidents()?.size==1&&!controller.state.value.busy}
   compose.runOnIdle{compose.activity.onBackPressedDispatcher.onBackPressed()}
   compose.onNodeWithText("Library").performClick();compose.onNodeWithContentDescription("Select evidence alpha179.txt").performScrollTo().performClick()
   click("Add selected to incident");click("Synthetic incident179")
   compose.waitUntil(15000){controller.state.value.catalog?.incidents()?.singleOrNull()?.evidenceIds()?.contains(corrected.id())==true&&!controller.state.value.busy}
   compose.onNodeWithTag("evidence-search").performTextReplacement("unique179")
   compose.onNodeWithText("alpha179.txt").assertExists();compose.onNodeWithText("beta179.txt").assertDoesNotExist()
   compose.onNodeWithTag("evidence-search").performTextClearance()
   compose.onNodeWithContentDescription("Select evidence alpha179.txt").performScrollTo().performClick()
   click("Remove references");compose.onNodeWithText("Confirm").performClick();waitReady(controller,1)
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
   compose.onNode(hasText("Review / accept") and hasAnyAncestor(hasTestTag(tag))).performScrollTo().performClick()
   field("New incident title").performTextReplacement("Approved group179");compose.onNodeWithText("Save").performClick()
   compose.waitUntil(15000){controller.state.value.catalog?.incidents()?.size==1&&!controller.state.value.busy}
   val grouped=f.access.store.read();assertEquals(2,grouped.incidents().single().evidenceIds().size);assertEquals("ACCEPT_SUGGESTION",grouped.audit().last().action())
   compose.onNodeWithText("Incidents").performClick();compose.onNodeWithText("Approved group179").performScrollTo().performClick()
   click("Add event");field("Title").performTextReplacement("Late event179");field("Timestamp (ISO with offset; blank is uncertain)").performTextReplacement("2026-10-31T12:00:00Z");compose.onNodeWithText("Save").performClick()
   compose.waitUntil(15000){controller.state.value.catalog?.incidents()?.singleOrNull()?.events()?.size==1&&!controller.state.value.busy}
   click("Add event");field("Title").performTextReplacement("Uncertain event179");compose.onNodeWithText("Save").performClick()
   compose.waitUntil(15000){controller.state.value.catalog?.incidents()?.singleOrNull()?.events()?.size==2&&!controller.state.value.busy}
   val uncertain=f.access.store.read().incidents().single().events().single{it.title()=="Uncertain event179"}
   assertEquals(0L,uncertain.timestamp());assertEquals("USER",uncertain.provenance())
   compose.onNode(hasText("Move earlier") and hasAnyAncestor(hasTestTag("evidence-event-${uncertain.id()}"))).performScrollTo().performClick()
   compose.waitUntil(15000){controller.state.value.catalog?.incidents()?.singleOrNull()?.manualOrder()==true&&!controller.state.value.busy}
   assertEquals(uncertain.id(),f.access.store.read().incidents().single().events().first().id())
   val third=(repo.importVerified(VaultImportSource("third-copy179.txt","text/plain",{duplicateBytes.inputStream()},createDistinctCopy=true))as ImportResult.Imported).item
   compose.runOnIdle{controller.register(third.id)};waitReady(controller,3)
   compose.onNodeWithText("Home").performClick()
   val assign=f.access.store.read().suggestions().first{it.kind()=="ASSIGN"}
   compose.onNode(hasText("Reject") and hasAnyAncestor(hasTestTag("evidence-suggestion-${assign.id()}"))).performScrollTo().performClick()
   compose.waitUntil(15000){controller.state.value.catalog?.rejectedSuggestions()?.contains(assign.id())==true&&!controller.state.value.busy}
   assertEquals(2,f.access.store.read().incidents().single().evidenceIds().size);assertEquals(4,repo.items().size)
  }finally{if(::controller.isInitialized)compose.runOnIdle{controller.close()};duplicateBytes.fill(0);f.key.fill(0);f.root.deleteRecursively()}
 }
 @Test fun landscapeDarkLargeTextIncidentFormAndExitRemainReachable() {
  val f=fixture()
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
   compose.onNodeWithText("Library").performClick();compose.onNodeWithTag("evidence-search").performTextReplacement("Alice")
   compose.onNodeWithContentDescription("Select evidence alpha179.txt").performScrollTo().performClick()
   compose.onNodeWithText("alpha179.txt").performClick();waitReady(controller,1);click("Review / correct")
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
