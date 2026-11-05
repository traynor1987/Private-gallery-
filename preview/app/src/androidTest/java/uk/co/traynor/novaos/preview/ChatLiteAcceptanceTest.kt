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
import java.io.File

@RunWith(AndroidJUnit4::class)
class ChatLiteAcceptanceTest{
 @get:Rule val rule=createAndroidComposeRule<MainActivity>()
 private val pool get()=rule.activity.browserPool
 private val d get()=rule.activity.previewData
 private fun chat()=ChatLiteData(rule.activity)
 private fun js(view:WebView,code:String):String{var value="";val done=CountDownLatch(1);rule.runOnUiThread{view.evaluateJavascript(code){value=it;done.countDown()}};assertTrue(done.await(10,TimeUnit.SECONDS));return value}
 private fun open(){rule.onNodeWithTag("open-apps").performClick();rule.onNodeWithTag("drawer-search").performTextInput("Nova Chat");rule.onNodeWithTag("app-chat").performClick();rule.onNodeWithTag("nova-chat-lite").assertIsDisplayed()}
 private fun fixture():PreviewTab{lateinit var tab:PreviewTab;rule.runOnUiThread{tab=pool.create(standaloneBrowser.copy(id="chat-fixture",url="https://web.whatsapp.com/"),"primary",false);pool.select(tab);tab.view.loadDataWithBaseURL("https://web.whatsapp.com/","<script>window.sent=0;window.deleted=0</script><div id='pane-side'><div role='grid'><div role='row'>SYNTHETIC approved</div><div role='row'>SYNTHETIC excluded <b>9</b></div><div role='row'>SYNTHETIC unknown</div></div></div><input aria-label='Search'><button onclick='sent++'>Send</button><button onclick='deleted++'>Delete</button>","text/html","UTF-8",null)};rule.waitUntil(10000){js(tab.view,"!!document.getElementById('pane-side')")=="true"};return tab}
 @Before fun reset(){rule.runOnUiThread{if(d.state.current!="primary")d.switchProfile();d.change{Profile()};pool.closeAll();rule.activity.getSharedPreferences("nova_chat_inspector_v01",0).edit().clear().commit();rule.activity.getSharedPreferences("nova_chat_lite_v1",0).edit().clear().commit();pool.library.setFlag("friends-required:primary:chat-fixture",false);pool.library.setFlag("friends-required:primary:browser-v1",false)}}
 @Test fun approvalsAreExplicitProfileLocalAndPersistWithoutAmbiguousNames(){
  rule.runOnUiThread{val c=chat();assertTrue(c.approve("primary","+44 7700 900123","SYNTHETIC friend"));assertFalse(c.approve("primary","+447700900123","Duplicate nickname"));assertEquals(1,c.friends("primary").size);assertTrue(c.friends("hidden").isEmpty());assertFalse(c.approve("primary","Display name only","Unknown"));assertEquals("SYNTHETIC friend",chat().friends("primary").single().alias);c.remove("primary","+447700900123");assertTrue(chat().friends("primary").isEmpty())}
 }
 @Test fun approvedLauncherNeverRevealsExcludedUnknownUnreadOrSearchSurfaces(){
  val tab=fixture();rule.runOnUiThread{assertTrue(chat().approve("primary","+447700900123","SYNTHETIC friend"))};open()
  rule.waitUntil(10000){rule.onAllNodesWithText("DEGRADED").fetchSemanticsNodes().isNotEmpty()};rule.onNodeWithText("SYNTHETIC friend").assertExists();rule.onNodeWithText("SYNTHETIC excluded",substring=true).assertDoesNotExist();rule.onNodeWithText("SYNTHETIC unknown",substring=true).assertDoesNotExist()
  rule.runOnUiThread{assertTrue(pool.friendsBlocked(tab));assertEquals(View.INVISIBLE,tab.view.visibility);assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS,tab.view.importantForAccessibility)}
  rule.onNodeWithTag("chat-approved-action").performScrollTo().performClick();rule.onNodeWithTag("chat-inspector").assertIsDisplayed();rule.onNodeWithText("IDLE").assertExists();assertNull(ChatInspector(rule.activity).report("primary"));assertEquals("3",js(tab.view,"document.querySelectorAll('[role=row]').length"));assertEquals("0",js(tab.view,"window.sent+window.deleted"))
 }
 @Test fun guardSurvivesReloadHomeAndRecreationWithoutResettingSession(){
  val tab=fixture();val original=tab.view;js(original,"sessionStorage.setItem('chat-probe','kept')");open();rule.onNodeWithTag("nova-home").performClick();rule.runOnUiThread{assertTrue(pool.friendsBlocked(tab));assertEquals(View.INVISIBLE,original.visibility);original.reload()};open();rule.activityRule.scenario.recreate();rule.waitForIdle();rule.onNodeWithTag("nova-chat-lite").assertIsDisplayed();rule.runOnUiThread{assertSame(original,pool.tabs.single().view);assertTrue(pool.friendsBlocked(pool.tabs.single()));assertEquals(View.INVISIBLE,original.visibility)};assertEquals("\"kept\"",js(original,"sessionStorage.getItem('chat-probe')"))
 }
 @Test fun changedDomKeepsFriendsGuardWithoutAutomaticInspection(){
  val tab=fixture();open();rule.runOnUiThread{tab.view.loadDataWithBaseURL("https://web.whatsapp.com/","<div id='changed'>SYNTHETIC excluded secret</div>","text/html","UTF-8",null)};rule.waitUntil(10000){js(tab.view,"!!document.getElementById('changed')")=="true"};rule.onNodeWithText("Open diagnostics").performScrollTo().performClick();val text=rule.onNodeWithTag("chat-diagnostics").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.Text].joinToString();assertFalse(text.contains("SYNTHETIC"));assertFalse(text.contains("447700"));assertNull(ChatInspector(rule.activity).report("primary"));rule.runOnUiThread{assertEquals(View.INVISIBLE,tab.view.visibility)}
 }
 @Test fun normalWhatsAppRequiresExplicitConfirmationAndReusesOriginalView(){
  val tab=fixture();open();rule.onNodeWithTag("chat-normal-whatsapp").performScrollTo().performClick();rule.onNodeWithText("Stay in Friends Mode").performClick();rule.runOnUiThread{assertTrue(pool.friendsBlocked(tab))};rule.onNodeWithTag("chat-normal-whatsapp").performScrollTo().performClick();rule.onNodeWithText("Open unfiltered WhatsApp").performClick();rule.onNodeWithTag("preview-web-app").assertExists();rule.runOnUiThread{assertSame(tab.view,pool.active!!.view);assertFalse(pool.friendsBlocked(tab));assertEquals(View.VISIBLE,tab.view.visibility)}
 }
 @Test fun browserWhatsAppGuardLeavesUnrelatedBrowserTabsAndDocumentsAlone(){
  rule.runOnUiThread{val wa=pool.create(standaloneBrowser.copy(url="https://web.whatsapp.com/"),"primary",false);val music=pool.create(standaloneBrowser.copy(url="https://music.youtube.com/"),"primary",false);val original=music.view;val ua=music.view.settings.userAgentString;pool.requireFriends(wa,true);assertTrue(pool.friendsBlocked(wa));assertEquals(View.INVISIBLE,wa.view.visibility);assertFalse(pool.friendsBlocked(music));assertEquals(View.VISIBLE,music.view.visibility);assertSame(original,music.view);assertEquals(ua,music.view.settings.userAgentString);val renewed=pool.create(standaloneBrowser.copy(url="https://web.whatsapp.com/"),"primary",false);assertEquals(View.INVISIBLE,renewed.view.visibility);pool.requireFriends(wa,false);assertFalse(pool.friendsBlocked(renewed));assertEquals(View.VISIBLE,original.visibility)}
 }
 @Test fun sanctuaryEntryAndFoldLayoutsRemainNativeAndConcealed(){
  fixture();rule.runOnUiThread{chat().sanctuary("primary",true)}
  rule.onNodeWithTag("open-apps").performClick();rule.onNodeWithTag("drawer-search").performTextInput("Nova Sanctuary");rule.onNodeWithTag("app-sanctuary").performClick();rule.onNodeWithTag("nova-chat-lite").assertIsDisplayed()
  fun shell(cmd:String){val fd=InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(cmd);android.os.ParcelFileDescriptor.AutoCloseInputStream(fd).use{it.readBytes()}}
  try{shell("wm density 420");for((name,size)in listOf("cover" to "904x2316","inner" to "1840x2208","landscape" to "2316x904")){shell("wm size $size");android.os.SystemClock.sleep(1000);rule.waitForIdle();rule.onNodeWithTag("nova-chat-lite").assertIsDisplayed();rule.onNodeWithTag("chat-compatibility-cover").assertExists();rule.runOnUiThread{assertTrue(pool.tabs.all{pool.friendsBlocked(it)&&it.view.visibility==View.INVISIBLE})}}}finally{shell("wm size reset");shell("wm density reset")}
 }
}
