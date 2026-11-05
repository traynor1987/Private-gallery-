package uk.co.traynor.novaos.preview
import android.webkit.WebView
import android.os.SystemClock
import android.view.MotionEvent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.nio.ByteBuffer
import java.nio.ByteOrder

@RunWith(AndroidJUnit4::class)
class MediaContinuityAcceptanceTest {
 @get:Rule val rule=createAndroidComposeRule<MainActivity>()
 private fun js(v:WebView,code:String):String{val latch=CountDownLatch(1);var result="";rule.runOnUiThread{v.evaluateJavascript(code){result=it;latch.countDown()}};assertTrue(latch.await(10,TimeUnit.SECONDS));return result}
 private fun waitJs(v:WebView,code:String){rule.waitUntil(15000){js(v,code)=="true"}}
 private fun tap(v:WebView){val xy=IntArray(2);rule.runOnUiThread{v.getLocationOnScreen(xy)};val now=SystemClock.uptimeMillis();val u=InstrumentationRegistry.getInstrumentation().uiAutomation;for(action in listOf(MotionEvent.ACTION_DOWN,MotionEvent.ACTION_UP)){val e=MotionEvent.obtain(now,SystemClock.uptimeMillis(),action,xy[0]+80f,xy[1]+80f,0);u.injectInputEvent(e,true);e.recycle()}}
 private fun launch(name:String,id:String){rule.onNodeWithTag("open-apps").performClick();rule.onNodeWithTag("drawer-search").performTextInput(name);rule.onNodeWithTag("app-$id").performClick()}
 private fun playing():WebView{
  rule.runOnUiThread{val d=rule.activity.previewData;d.change("primary"){Profile()};if(d.state.current!="primary")d.switchProfile();rule.activity.widgetData.demoLock(false);rule.activity.browserPool.closeAll();d.install(d.catalogue.first{it.id=="youtube-music"});d.shell.configure(gestures=false,edge=false)}
  val entry=rule.activity.previewData.catalogue.first{it.id=="youtube-music"};launch("YouTube Music",rule.activity.previewData.entryKey(entry))
  val v=rule.activity.browserPool.active!!.view
  val n=8000*120;val b=ByteBuffer.allocate(44+n*2).order(ByteOrder.LITTLE_ENDIAN);b.put("RIFF".toByteArray());b.putInt(36+n*2);b.put("WAVEfmt ".toByteArray());b.putInt(16);b.putShort(1);b.putShort(1);b.putInt(8000);b.putInt(16000);b.putShort(2);b.putShort(16);b.put("data".toByteArray());b.putInt(n*2);repeat(n){b.putShort(0)}
  val wav=android.util.Base64.encodeToString(b.array(),android.util.Base64.NO_WRAP)
  rule.runOnUiThread{v.loadDataWithBaseURL("https://example.com/","<meta name='viewport' content='width=device-width'><button style='height:180px;width:100%' onclick='a.play()'>Play synthetic audio</button><audio id='a' src='data:audio/wav;base64,$wav'></audio><input id='memo' value='retained'>","text/html","UTF-8",null)}
  waitJs(v,"document.getElementById('a')?.readyState>=2");tap(v);waitJs(v,"!a.paused&&a.currentTime>0")
  // Deliberately absent Media Session playbackState: host continuity cannot depend on it.
  assertEquals("\"none\"",js(v,"navigator.mediaSession?.playbackState||'none'"));return v
 }
 private fun advancing(v:WebView){val before=js(v,"a.currentTime").toDouble();SystemClock.sleep(650);assertEquals("false",js(v,"a.paused"));assertTrue(js(v,"a.currentTime").toDouble()>before);assertEquals("\"retained\"",js(v,"memo.value"));rule.runOnUiThread{assertNotNull(v.parent);assertTrue(rule.activity.browserPool.tabs.first{it.view===v}.resumed)}}
 @Test fun homeAnotherAppAndBrowserKeepTheOriginalUnreportedAudio(){
  val v=playing();rule.onNodeWithTag("nova-home").performClick();advancing(v)
  rule.runOnUiThread{val d=rule.activity.previewData;d.install(d.catalogue.first{it.id=="photopea"})};val entry=rule.activity.previewData.catalogue.first{it.id=="photopea"};launch("Photopea",rule.activity.previewData.entryKey(entry));advancing(v)
  rule.onNodeWithTag("nova-home").performClick();launch("Browser V1","browser-v1");advancing(v)
  rule.onNodeWithTag("nova-home").performClick();launch("YouTube Music",rule.activity.previewData.entryKey(rule.activity.previewData.catalogue.first{it.id=="youtube-music"}));assertSame(v,rule.activity.browserPool.active!!.view);advancing(v)
 }
 @Test fun rotationFoldAndActivityRecreationRetainAudioAndDocument(){
  val v=playing()
  try{
   rule.runOnUiThread{rule.activity.requestedOrientation=android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE};SystemClock.sleep(1000);advancing(v)
   fun shell(cmd:String){val fd=InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(cmd);android.os.ParcelFileDescriptor.AutoCloseInputStream(fd).use{it.readBytes()}}
   shell("wm size 1840x2208");SystemClock.sleep(1000);advancing(v);shell("wm size 904x2316");SystemClock.sleep(1000);advancing(v)
   rule.activityRule.scenario.recreate();rule.waitForIdle();assertSame(v,rule.activity.browserPool.active!!.view);advancing(v)
  }finally{val fd=InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("wm size reset");android.os.ParcelFileDescriptor.AutoCloseInputStream(fd).use{it.readBytes()};rule.runOnUiThread{rule.activity.requestedOrientation=android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED}}
 }
 @Test fun explicitCloseReleasesTheAudioSourceAndBackgroundScopeRemainsBounded(){
  val v=playing();val pool=rule.activity.browserPool;val tab=pool.active!!
  rule.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
  rule.runOnUiThread{assertFalse(pool.foreground);assertFalse(tab.resumed)}
  rule.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED);rule.waitForIdle()
  rule.runOnUiThread{assertTrue(pool.foreground);assertTrue(tab.resumed);assertSame(v,pool.active!!.view)}
  assertEquals("\"retained\"",js(v,"memo.value"))
  rule.runOnUiThread{rule.activity.previewData.switchProfile()};rule.waitForIdle()
  rule.runOnUiThread{assertEquals("hidden",pool.currentProfile);assertFalse(tab.resumed)}
  rule.runOnUiThread{rule.activity.previewData.switchProfile()};rule.waitForIdle()
  rule.runOnUiThread{assertEquals("primary",pool.currentProfile);assertTrue(tab.resumed);assertSame(v,pool.active!!.view)}
  assertEquals("\"retained\"",js(v,"memo.value"))
  rule.runOnUiThread{pool.closeApp(tab.profile,tab.app.id);assertFalse(pool.tabs.any{it.view===v});assertNull(v.parent);assertNull(pool.player("primary"));assertFalse(PreviewShellRules.keepRunning(false,true,true))}
 }
}
