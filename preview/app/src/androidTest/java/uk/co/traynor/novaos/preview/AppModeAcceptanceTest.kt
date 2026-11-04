package uk.co.traynor.novaos.preview

import android.os.SystemClock
import android.view.MotionEvent
import android.webkit.CookieManager
import android.webkit.WebView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.nio.ByteBuffer
import java.nio.ByteOrder
import android.util.Base64

@RunWith(AndroidJUnit4::class)
class AppModeAcceptanceTest {
 @get:Rule val rule=createAndroidComposeRule<MainActivity>()
 private fun js(v:WebView,s:String):String{val done=CountDownLatch(1);var result="";rule.runOnUiThread{v.evaluateJavascript(s){result=it;done.countDown()}};assertTrue(done.await(10,TimeUnit.SECONDS));return result}
 private fun open():WebView{
  rule.runOnUiThread{val d=rule.activity.previewData;d.change("primary"){Profile()};if(d.state.current!="primary")d.switchProfile();d.install(d.catalogue.first{it.id=="youtube-music"})}
  rule.onNodeWithTag("open-apps").performClick();rule.onNodeWithTag("drawer-search").performTextInput("YouTube Music")
  val id=rule.activity.previewData.entryKey(rule.activity.previewData.catalogue.first{it.id=="youtube-music"})
  rule.onNodeWithTag("app-$id").performClick();rule.waitForIdle()
  return rule.activity.browserPool.active!!.view
 }
 private fun waitJs(v:WebView,s:String){val end=SystemClock.uptimeMillis()+12000;while(SystemClock.uptimeMillis()<end){if(js(v,s)=="true")return;SystemClock.sleep(100)};fail("Synthetic page readiness/playback condition timed out")}
 private fun tap(v:WebView){val xy=IntArray(2);rule.runOnUiThread{v.getLocationOnScreen(xy)};val time=SystemClock.uptimeMillis();val automation=InstrumentationRegistry.getInstrumentation().uiAutomation
  for(action in listOf(MotionEvent.ACTION_DOWN,MotionEvent.ACTION_UP)){val e=MotionEvent.obtain(time,SystemClock.uptimeMillis(),action,(xy[0]+80).toFloat(),(xy[1]+80).toFloat(),0);automation.injectInputEvent(e,true);e.recycle()}
 }
 @Test fun appModeHomeRetainsAttachedUserStartedAudioAndActivityLifecycle(){
  val v=open();assertTrue(rule.activity.browserPool.active!!.appMode)
  // Synthetic 20s PCM WAV: no third-party media, autoplay or provider restrictions.
  val n=8000*20;val b=ByteBuffer.allocate(44+n*2).order(ByteOrder.LITTLE_ENDIAN)
  b.put("RIFF".toByteArray());b.putInt(36+n*2);b.put("WAVEfmt ".toByteArray());b.putInt(16);b.putShort(1);b.putShort(1);b.putInt(8000);b.putInt(16000);b.putShort(2);b.putShort(16);b.put("data".toByteArray());b.putInt(n*2);repeat(n){b.putShort(0)}
  val wav=Base64.encodeToString(b.array(),Base64.NO_WRAP)
  rule.runOnUiThread{v.loadDataWithBaseURL("https://example.com/","<meta name='viewport' content='width=device-width'><button style='width:100%;height:180px' onclick='a.play()'>Play synthetic audio</button><audio id='a' src='data:audio/wav;base64,$wav'></audio>","text/html","UTF-8",null)}
  waitJs(v,"document.getElementById('a')?.readyState>=2");tap(v);waitJs(v,"!a.paused && a.currentTime>0")
  val before=js(v,"a.currentTime").toDouble()
  rule.onNodeWithTag("web-app-menu").performClick();rule.onNodeWithTag("web-return-home").performClick();rule.onNodeWithTag("preview-home").assertIsDisplayed()
  rule.runOnUiThread{assertNotNull(v.parent);assertTrue(rule.activity.browserPool.foreground)}
  SystemClock.sleep(600);assertEquals("false",js(v,"a.paused"));assertTrue(js(v,"a.currentTime").toDouble()>before)
  rule.activityRule.scenario.moveToState(Lifecycle.State.CREATED);assertFalse(rule.activity.browserPool.foreground)
  rule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED);assertTrue(rule.activity.browserPool.foreground);assertSame(v,rule.activity.browserPool.active!!.view)
 }
 @Test fun googleLoginRequiresConfirmationAndPreservesInitiatingTab(){
  val v=open()
  rule.runOnUiThread{v.loadDataWithBaseURL("https://example.com/","<meta name='viewport' content='width=device-width'><a style='display:block;height:180px' href='https://accounts.google.com/o/oauth2/v2/auth?client_id=synthetic-test'>Google sign-in fixture</a>","text/html","UTF-8",null)}
  waitJs(v,"document.querySelector('a')!=null");tap(v)
  rule.onNodeWithText("Open in your browser?").assertIsDisplayed();rule.onNodeWithText("Cancel").performClick()
  assertSame(v,rule.activity.browserPool.active!!.view);assertFalse(js(v,"location.href").contains("accounts.google.com"))
  // App-initiated address loads do not pass through shouldOverrideUrlLoading.
  rule.runOnUiThread{rule.activity.browserPool.navigate(rule.activity.browserPool.active!!,"https://accounts.google.com/o/oauth2/v2/auth?client_id=synthetic-test")}
  rule.onNodeWithText("Open in your browser?").assertIsDisplayed();rule.onNodeWithText("Cancel").performClick()
  assertFalse(js(v,"location.href").contains("accounts.google.com"))
  // An initial custom-app URL must also stop before embedding Google.
  rule.runOnUiThread{val pool=rule.activity.browserPool;val tab=pool.create(pool.active!!.app.copy(id="google-fixture",url="https://accounts.google.com/"),"primary",true);assertEquals(tab.id,pool.externalRequest!!.tabId);assertNull(tab.view.url);pool.close(tab);pool.externalRequest=null}
  val done=CountDownLatch(1);rule.runOnUiThread{CookieManager.getInstance().setCookie("https://example.com/","nova_fixture=retained; Secure; SameSite=Lax"){done.countDown()}}
  assertTrue(done.await(5,TimeUnit.SECONDS));rule.runOnUiThread{CookieManager.getInstance().flush();assertTrue(CookieManager.getInstance().getCookie("https://example.com/").contains("nova_fixture=retained"))}
 }
}
