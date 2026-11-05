package uk.co.traynor.novaos.preview
import android.webkit.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.geometry.Offset
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.json.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.io.File

@RunWith(AndroidJUnit4::class)
class CorrectionAcceptanceTest {
 @get:Rule val rule=createAndroidComposeRule<MainActivity>()
 private val d get()=rule.activity.previewData
 private val w get()=rule.activity.widgetData
 private val pool get()=rule.activity.browserPool
 private fun js(v:WebView,code:String):String{val done=CountDownLatch(1);var result="";rule.runOnUiThread{v.evaluateJavascript(code){result=it;done.countDown()}};assertTrue(done.await(10,TimeUnit.SECONDS));return result}
 private fun browser(){rule.onNodeWithTag("open-apps").performClick();rule.onNodeWithTag("drawer-search").performTextInput("Browser V1");rule.onNodeWithTag("app-browser-v1").performClick();rule.waitForIdle()}
 @Before fun reset(){rule.runOnUiThread{if(d.state.current!="primary")d.switchProfile();d.change{Profile()};w.save("primary",emptyList());w.demoLock(false);w.clock("primary",true);w.editingId=null;w.clearError();d.shell.dim(0f);d.shell.configure(gestures=false,edge=false);pool.closeAll();pool.library.setFlag("friends-required:primary:browser-v1",false)}}
 @Test fun desktopPreferenceIsPerSiteRetainsWebViewAndRestoresMobile(){
  lateinit var a:PreviewTab;lateinit var other:PreviewTab
  rule.runOnUiThread{a=pool.create(standaloneBrowser.copy(id="desktop-fixture",url="https://wa.example/"),"primary",false);pool.select(a);other=pool.create(standaloneBrowser.copy(id="unrelated-fixture",url="https://music.youtube.com/"),"primary",false);pool.select(a);pool.setDesktop(a,true)}
  val original=WebSettings.getDefaultUserAgent(rule.activity)
  rule.runOnUiThread{assertTrue(a.desktopApplied);assertFalse(a.view.settings.userAgentString.contains("Mobile"));assertEquals(original,other.view.settings.userAgentString);assertTrue(BrowserLibrary(rule.activity).flag("desktop:primary:wa.example"));val delegate=a.view.webViewClient;a.view.webViewClient=object:WebViewClient(){
    override fun shouldInterceptRequest(v:WebView,r:WebResourceRequest):WebResourceResponse?=if(r.url.host=="wa.example")WebResourceResponse("text/html","UTF-8",java.io.ByteArrayInputStream("<input id='memo'>".toByteArray()))else null
    override fun onPageStarted(v:WebView,u:String,b:android.graphics.Bitmap?){delegate.onPageStarted(v,u,b)}
    override fun onPageFinished(v:WebView,u:String){delegate.onPageFinished(v,u)}
    override fun shouldOverrideUrlLoading(v:WebView,r:WebResourceRequest)=delegate.shouldOverrideUrlLoading(v,r)
   };pool.navigate(a,"https://wa.example/")}
  rule.waitUntil(10000){js(a.view,"!!document.getElementById('memo')")=="true"}
  js(a.view,"sessionStorage.setItem('nova-desktop','retained')");assertEquals("\"retained\"",js(a.view,"sessionStorage.getItem('nova-desktop')"))
  val view=a.view
  rule.runOnUiThread{pool.setDesktop(a,false);assertSame(view,a.view);assertEquals(original,a.view.settings.userAgentString);assertFalse(BrowserLibrary(rule.activity).flag("desktop:primary:wa.example"));pool.applyDesktop(a,"https://accounts.google.com/ServiceLogin");assertEquals(original,a.view.settings.userAgentString);assertEquals(original,other.view.settings.userAgentString)}
  rule.waitUntil(10000){js(view,"sessionStorage.getItem('nova-desktop')")=="\"retained\""}
 }
 @Test fun desktopControlsExistInBrowserAndAppMode(){
  browser();val tab=pool.active!!
  rule.runOnUiThread{tab.view.loadDataWithBaseURL("https://wa.example/","<p>Desktop settings fixture</p>","text/html","UTF-8",null)}
  rule.waitForIdle();rule.onNodeWithContentDescription("Browser menu").performClick();rule.onNodeWithTag("desktop-menu").assertExists();rule.onNodeWithText("Settings",substring=false).performClick();rule.onNodeWithTag("desktop-site-switch").assertExists();rule.onNodeWithTag("desktop-reload").assertExists();rule.onNodeWithText("Done",substring=false).performClick()
  rule.runOnUiThread{pool.setBrowserMode(tab,false)};rule.onNodeWithTag("web-app-menu").performClick();rule.onNodeWithTag("desktop-menu").assertExists();rule.onNodeWithText("App settings & compatibility").performClick();rule.onNodeWithTag("desktop-site-switch").assertExists()
 }
 @Test fun protectedClockSurvivesWidgetAddRemoveUndoAndRestart(){
  val pages=d.profile.pages;rule.onNodeWithTag("home-original-clock").assertIsDisplayed()
  rule.runOnUiThread{w.add("primary","mind",2,1,0)};val id=w.slots("primary").single().id
  rule.onNodeWithTag("home-original-clock").assertIsDisplayed()
  rule.runOnUiThread{assertTrue(w.moveWidget("primary",id,2,0,2,1,0));w.undoMove();assertEquals(0,w.slots("primary").single().x);w.remove("primary",id);w.undoRemove();assertEquals(pages,d.profile.pages)}
  rule.activityRule.scenario.recreate();rule.waitForIdle();rule.onNodeWithTag("home-original-clock").assertIsDisplayed()
  rule.runOnUiThread{assertTrue(WidgetData(rule.activity).clockVisible("primary"));assertEquals(id,w.slots("primary").single().id);w.clock("primary",false)}
  rule.onNodeWithTag("home-original-clock").assertDoesNotExist();rule.runOnUiThread{w.clock("primary",true)}
 }
 @Test fun dragPreviewIsClippedAndCancellationDoesNotSave(){
  rule.runOnUiThread{w.add("primary","media",2,2,0)};val before=w.slots("primary").single();val id=before.id
  rule.onNodeWithContentDescription("Hold and drag widget",useUnmergedTree=true).performScrollTo().performTouchInput{down(center);advanceEventTime(700);moveBy(Offset(300f,500f),300)}
  val area=rule.onNodeWithTag("home-widgets",useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
  val preview=rule.onNodeWithTag("widget-placement-preview",useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
  assertTrue("Drag preview cannot escape widget area",preview.bottom<=area.bottom+2&&preview.top>=area.top-2)
  rule.onNodeWithContentDescription("Hold and drag widget",useUnmergedTree=true).performTouchInput{cancel()}
  rule.runOnUiThread{assertEquals(before,w.slots("primary").single())};rule.onNodeWithTag("widget-placement-preview",useUnmergedTree=true).assertDoesNotExist()
  rule.onNodeWithTag("widget-edit-done").performScrollTo().performClick();rule.onNodeWithTag("widget-resize-$id",useUnmergedTree=true).assertDoesNotExist();rule.onNodeWithTag("home-original-clock").assertIsDisplayed()
 }
 @Test fun rejectedResizeAndInvalidLayoutRecoveryPreserveNotesAndValidSlots(){
  rule.runOnUiThread{
   w.add("primary","note",2,2,0);w.add("primary","mind",2,2,0);w.add("primary","clock",2,1,1)
   val note=w.slots("primary").first();w.note("primary",note.id,note.items.single().id,"SYNTHETIC preserved note")
   val before=w.slots("primary");assertFalse(w.moveWidget("primary",note.id,0,0,4,2,0));assertEquals(before,w.slots("primary"));w.clearError()
   val a=WidgetData.encode(before);a.getJSONObject(1).put("x",0);w.prefs.edit().putString("layout:primary",a.toString()).commit();w.repairLayout("primary",d.profile.pages.size)
   val repaired=w.slots("primary");assertEquals(before[0],repaired[0]);assertEquals(before[2],repaired[2]);assertEquals("SYNTHETIC preserved note",repaired[0].items.single().config);assertFalse(WidgetRules.overlap(repaired[0].rect(),repaired[1].rect()));assertNotNull(w.recoveryNotice);assertTrue(w.prefs.all.keys.any{it.startsWith("recovery-original:primary:")})
  }
  rule.onNodeWithTag("home-original-clock").assertIsDisplayed()
 }
 @Test fun panelTilesHaveIconsGapsAndCapabilityDisclosure(){
  rule.onNodeWithContentDescription("Open Quick Panel").performClick();rule.onNodeWithTag("quick-expand").performClick()
  val a=rule.onNodeWithTag("quick-theme").fetchSemanticsNode().boundsInRoot;val b=rule.onNodeWithTag("quick-sanctuary").fetchSemanticsNode().boundsInRoot
  assertTrue("Control rows must have a visible gap",b.top>a.bottom+4)
  rule.onNodeWithTag("media-diagnostics-toggle").performScrollTo().performClick();rule.onNodeWithTag("media-capability-report").assertExists()
  rule.onNodeWithContentDescription("Close Quick Panel").performScrollTo().performClick();rule.onNodeWithTag("nova-quick-panel").assertDoesNotExist()
 }
 @Test fun absentFriendsAdapterConcealsSyntheticChatsAndPreservesContent(){
  browser();val tab=pool.active!!
  rule.runOnUiThread{tab.view.loadDataWithBaseURL("https://web.whatsapp.com/","<div id='approved'>SYNTHETIC approved</div><div id='unapproved'>SYNTHETIC unapproved</div>","text/html","UTF-8",null);pool.requireFriends(tab,true);tab.mediaTitle="SYNTHETIC late callback";tab.mediaState="playing";assertNull(pool.player("primary"))}
  rule.onNodeWithTag("friends-compatibility-error").assertIsDisplayed();rule.runOnUiThread{assertEquals(android.view.View.INVISIBLE,tab.view.visibility);assertEquals(android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS,tab.view.importantForAccessibility)}
  rule.waitUntil(10000){js(tab.view,"document.getElementById('unapproved')?.textContent")=="\"SYNTHETIC unapproved\""};assertEquals("\"SYNTHETIC unapproved\"",js(tab.view,"document.getElementById('unapproved').textContent"))
  rule.activityRule.scenario.recreate();rule.waitForIdle();rule.onNodeWithTag("friends-compatibility-error").assertIsDisplayed()
  rule.runOnUiThread{pool.requireFriends(pool.active!!,false)}
 }
 @Test fun whatsappPublicDesktopProbeRecordsOnlyUnauthenticatedCapabilities(){
  lateinit var tab:PreviewTab
  rule.runOnUiThread{val app=d.catalogue.first{it.id=="whatsapp"};tab=pool.create(app,"primary",false);pool.select(tab);tab.url="https://web.whatsapp.com/";pool.setDesktop(tab,true);pool.navigate(tab,"https://web.whatsapp.com/")}
  val end=android.os.SystemClock.uptimeMillis()+45000
  while(android.os.SystemClock.uptimeMillis()<end&&(tab.navigationRevision==0||tab.progress<100)&&tab.error==null)android.os.SystemClock.sleep(500)
  android.os.SystemClock.sleep(1500)
  var mobile=false;rule.runOnUiThread{mobile=tab.view.settings.userAgentString.contains("Mobile")}
  val result=JSONObject().put("api",android.os.Build.VERSION.SDK_INT).put("desktopRequested",pool.desktopSite(tab,"https://web.whatsapp.com/")).put("mobileToken",mobile).put("authenticatedChatTested",false).put("friendsAllowlistImplemented",false)
  val probe=js(tab.view,"JSON.stringify({url:location.origin+location.pathname,desktopLoginCues:/Log in|Scan.*QR|Link with phone|Use WhatsApp on your computer/i.test(document.body?.innerText||''),mobileFallback:/Use WhatsApp Web by going|Download WhatsApp.*phone/i.test(document.body?.innerText||''),qrLikeElement:!!document.querySelector('[data-ref] canvas,canvas[aria-label*=Scan],svg[aria-label*=Scan]')})")
  result.put("publicLoginProbe",runCatching{JSONObject(JSONTokener(probe).nextValue() as String)}.getOrElse{JSONObject().put("unavailable",true)});result.put("pageError",tab.error!=null)
  File(rule.activity.filesDir,"whatsapp-public-probe.json").writeText(result.toString(2))
  assertTrue(pool.desktopSite(tab,"https://web.whatsapp.com/"));rule.runOnUiThread{pool.close(tab)}
 }
}
