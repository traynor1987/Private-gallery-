package uk.co.traynor.novaos.preview
import android.webkit.WebView
import android.view.View
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import org.json.JSONObject

@RunWith(AndroidJUnit4::class)
class ChatInspectorAcceptanceTest{
 @get:Rule val rule=createAndroidComposeRule<MainActivity>()
 private val pool get()=rule.activity.browserPool
 private fun model()=ChatInspector(rule.activity)
 private fun js(v:WebView,s:String):String{var result="";val latch=CountDownLatch(1);rule.runOnUiThread{v.evaluateJavascript(s){result=it;latch.countDown()}};assertTrue(latch.await(10,TimeUnit.SECONDS));return result}
 private fun fixture(url:String="https://web.whatsapp.com/",known:Boolean=true):PreviewTab{lateinit var tab:PreviewTab;rule.runOnUiThread{tab=pool.create(standaloneBrowser.copy(id="inspector-fixture",url=url),"primary",false);pool.select(tab);tab.view.loadDataWithBaseURL(url,"""<script>window.secretReads=0;window.sent=0;window.network=0;Object.defineProperty(document,'cookie',{get(){secretReads++;return 'SESSION_TOKEN_SECRET'}});window.fetch=()=>{network++;throw Error('Not allowed')};</script><div id='${if(known)"pane-side"else "unknown-pane"}' style='height:100px;overflow:auto'><div style='height:3000px'><div role='row' data-id='447700900123' title='SYNTHETIC PRIVATE CONTACT' style='height:20px'><span role='gridcell'>SYNTHETIC PRIVATE MESSAGE friend@example.com</span><span data-icon='unread-count'>99</span></div><div role='row' style='height:20px'><span role='gridcell'>EXCLUDED PRIVATE MESSAGE</span><span data-icon='unread-count'>2</span></div></div></div><button onclick='sent++'>Send</button>""","text/html","UTF-8",null)};rule.waitUntil(10000){js(tab.view,"typeof window.secretReads")=="\"number\""};return tab}
 private fun open(){rule.onNodeWithTag("open-apps").performClick();rule.onNodeWithTag("drawer-search").performTextInput("Nova Chat Lite");rule.onNodeWithTag("app-chat").performClick();rule.onNodeWithTag("open-chat-inspector").performScrollTo().performClick();rule.onNodeWithTag("chat-inspector").assertIsDisplayed()}
 private fun complete(){rule.onNodeWithTag("start-chat-inspection").performScrollTo().performClick();rule.waitUntil(15000){model().report("primary")!=null};rule.onNodeWithTag("view-chat-report").performScrollTo().performClick()}
 @Before fun reset(){rule.runOnUiThread{val d=rule.activity.previewData;if(d.state.current!="primary")d.switchProfile();d.change{Profile()};pool.closeAll();rule.activity.getSharedPreferences("nova_chat_inspector_v01",0).edit().clear().commit();rule.activity.getSharedPreferences("nova_chat_lite_v1",0).edit().clear().commit();pool.library.setFlag("friends-required:primary:inspector-fixture",false)}}
 @Test fun explicitActionAndExactOriginAreRequiredAndStopCancelsSamples(){
  fixture("https://web.whatsapp.com.evil.example/");val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main);val m=model()
  try{rule.runOnUiThread{m.start(scope,pool,"primary",false);assertEquals("IDLE",m.status);m.start(scope,pool,"primary",true);assertEquals("BROKEN",m.status);assertEquals(0,m.samplesTaken);pool.closeAll()};fixture();rule.runOnUiThread{m.start(scope,pool,"primary",false);assertEquals(0,m.samplesTaken);m.start(scope,pool,"primary",true);m.stop();assertEquals("STOPPED",m.status)};android.os.SystemClock.sleep(2500);rule.runOnUiThread{assertEquals(0,m.samplesTaken);assertNull(m.report("primary"))}}finally{scope.cancel()}
 }
 @Test fun structuralReportHasClosedSchemaWithoutNamesMessagesTokensOrNetwork(){
  val tab=fixture();val v=tab.view;js(v,"sessionStorage.setItem('inspection-session','kept')");open();rule.onNodeWithTag("copy-chat-report").assertIsNotEnabled();assertNull(model().report("primary"));complete()
  val report=model().report("primary")!!;assertEquals(report,ChatInspectorRules.validateReport(report));val o=JSONObject(report);assertEquals("YES",o.getString("conversationContainer"));assertEquals("YES",o.getString("rowStructureRecognised"));assertEquals("INDICATED_BY_GEOMETRY",o.getString("virtualisedList"));assertEquals("YES",o.getString("unreadIndicatorStructure"));assertEquals("UNKNOWN",o.getString("stableNonPersonalRowIdentity"));assertEquals("INVESTIGATION_REQUIRED",o.getString("compatibilityState"))
  for(secret in listOf("447700","friend@example.com","SYNTHETIC","PRIVATE MESSAGE","SESSION_TOKEN_SECRET","<div","data:image"))assertFalse(report.contains(secret))
  assertEquals("0",js(v,"secretReads+sent+network"));assertEquals("2",js(v,"document.querySelectorAll('[role=row]').length"));assertEquals("\"kept\"",js(v,"sessionStorage.getItem('inspection-session')"));rule.runOnUiThread{assertSame(v,pool.tabs.single().view);assertEquals(View.INVISIBLE,v.visibility);assertTrue(rule.activity.window.attributes.flags and android.view.WindowManager.LayoutParams.FLAG_SECURE!=0)}
 }
 @Test fun unknownDomProducesObservedIssueWithoutClaimingFilteringWorks(){
  fixture(known=false);open();complete();val o=JSONObject(model().report("primary")!!);assertEquals("NO",o.getString("conversationContainer"));assertEquals("UNSUPPORTED_STRUCTURE",o.getString("compatibilityState"));assertEquals("UNVERIFIED",o.getString("filteringAttachmentFeasible"));rule.onNodeWithText("A conversation container was not detected by this inspector version.").performScrollTo().assertIsDisplayed()
 }
 @Test fun hostilePageStringsCannotBecomeAStoredOrCopiedReport(){
  val tab=fixture();js(tab.view,"JSON.stringify=()=>'{\"origin\":true,\"container\":\"SYNTHETIC PRIVATE MESSAGE\",\"rows\":2,\"consistent\":true,\"virtual\":false,\"unread\":true,\"shadow\":false}'");open();rule.onNodeWithTag("start-chat-inspection").performScrollTo().performClick();rule.waitUntil(10000){rule.onAllNodesWithText("BROKEN").fetchSemanticsNodes().isNotEmpty()};assertNull(model().report("primary"));rule.onNodeWithTag("copy-chat-report").assertIsNotEnabled()
 }
 @Test fun reportReviewCopyDeleteAndRestartAreExplicit(){
  fixture();open();complete();val report=model().report("primary")!!;rule.onNodeWithTag("copy-chat-report").performScrollTo().performClick();rule.runOnUiThread{val clipboard=rule.activity.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager;assertEquals(report,clipboard.primaryClip!!.getItemAt(0).text.toString())};rule.activityRule.scenario.recreate();rule.waitForIdle();rule.onNodeWithTag("chat-inspector").assertIsDisplayed();assertEquals(report,model().report("primary"));rule.onNodeWithTag("delete-chat-report").performScrollTo().performClick();assertNull(model().report("primary"));rule.onNodeWithTag("view-chat-report").assertIsNotEnabled()
 }
 @Test fun inspectorStaysLocalAcrossFoldAndHomeWithoutAutomaticRestart(){
  val tab=fixture();open()
  fun shell(s:String){val fd=InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(s);android.os.ParcelFileDescriptor.AutoCloseInputStream(fd).use{it.readBytes()}}
  try{shell("wm density 420");for(size in listOf("904x2316","1840x2208","2316x904")){shell("wm size $size");android.os.SystemClock.sleep(1000);rule.waitForIdle();rule.onNodeWithTag("chat-inspector").assertIsDisplayed();rule.onNodeWithText("IDLE").assertExists();assertNull(model().report("primary"));rule.runOnUiThread{assertEquals(View.INVISIBLE,tab.view.visibility)}}}finally{shell("wm size reset");shell("wm density reset")}
  rule.onNodeWithTag("nova-home").performClick();assertNull(model().report("primary"));rule.runOnUiThread{assertTrue(pool.friendsBlocked(tab))}
 }
}
