package uk.co.traynor.novaos.preview

import android.webkit.WebView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class MultitaskingAcceptanceTest {
 @get:Rule val rule=createAndroidComposeRule<MainActivity>()
 private fun data()=rule.activity.previewData
 @Before fun reset(){rule.runOnUiThread{val d=data();rule.activity.browserPool.closeAll();d.change("primary"){Profile()};d.change("hidden"){Profile()};if(d.state.current!="primary")d.switchProfile();d.shell.clear("primary");d.shell.clear("hidden");d.shell.configure(reverse=false,gestures=false,edge=false)}}
 private fun open(name:String):WebView{
  rule.runOnUiThread{val d=data();d.install(d.catalogue.first{it.name==name})}
  rule.onNodeWithTag("open-apps").performClick();rule.onNodeWithTag("drawer-search").performTextInput(name)
  val app=data().catalogue.first{it.name==name};rule.onNodeWithTag("app-"+data().entryKey(app)).performClick();rule.waitForIdle()
  return rule.activity.browserPool.active!!.view
 }
 private fun js(v:WebView,s:String):String{val l=CountDownLatch(1);var result="";rule.runOnUiThread{v.evaluateJavascript(s){result=it;l.countDown()}};assertTrue(l.await(10,TimeUnit.SECONDS));return result}
 @Test fun recentsResumeCloseAllAndBackPreserveOwners(){
  val a=open("YouTube Music")
  rule.runOnUiThread{a.loadDataWithBaseURL("https://example.com/","<input id='memo' value='retained'>","text/html","UTF-8",null)}
  rule.waitUntil(10000){js(a,"document.getElementById('memo')?.value")=="\"retained\""}
  rule.onNodeWithTag("nova-home").performClick();rule.onNodeWithTag("preview-home").assertIsDisplayed()
  val b=open("Photopea");rule.runOnUiThread{assertNotNull(a.parent);assertNotNull(b.parent);val p=rule.activity.browserPool;p.create(p.active!!.app,"hidden",false)}
  rule.onNodeWithTag("nova-recents").performClick();rule.onNodeWithTag("nova-recents-screen").assertIsDisplayed()
  assertEquals(2,rule.activity.browserPool.recents("primary").size)
  val musicId=rule.activity.browserPool.tabs.first{it.view===a}.app.id
  rule.onNodeWithTag("recents-pager").performTouchInput{swipeLeft()}
  rule.onNodeWithTag("recent-$musicId").performClick();assertSame(a,rule.activity.browserPool.active!!.view)
  assertEquals("\"retained\"",js(a,"document.getElementById('memo').value"))
  rule.onNodeWithTag("nova-recents").performClick()
  rule.onNodeWithTag("recent-$musicId").performTouchInput{swipeUp()}
  rule.waitUntil(5000){rule.activity.browserPool.recents("primary").size==1}
  rule.onNodeWithTag("recents-close-all").performClick();rule.onNodeWithTag("confirm-close-all").performClick()
  assertTrue(rule.activity.browserPool.recents("primary").isEmpty());assertTrue(rule.activity.browserPool.tabs.any{it.profile=="hidden"})
  rule.onNodeWithTag("nova-back").performClick();rule.onNodeWithTag("preview-home").assertIsDisplayed()
 }
 @Test fun settingsEventsAndNavigationInsetsPersistWithoutProfileLeaks(){
  rule.runOnUiThread{
   val d=data();val e=d.catalogue.first{it.id=="photopea"};d.install(e);d.install(e);d.change{it.copy(wallpaper=Wallpaper("Dusk"))}
   assertEquals(2,d.shell.events("primary").size);assertTrue(d.shell.events("hidden").isEmpty())
   d.shell.configure(reverse=true,edge=true,left=true,position=.3f)
   val restored=PreviewData(rule.activity);assertTrue(restored.shell.reverse);assertTrue(restored.shell.edge);assertEquals(2,restored.shell.events("primary").size);d.shell.favourite("primary",d.entryKey(e),true);d.uninstall(d.entryKey(e));assertFalse(d.entryKey(e) in d.shell.favourites("primary"))
  }
  val back=rule.onNodeWithTag("nova-back").getUnclippedBoundsInRoot();val recents=rule.onNodeWithTag("nova-recents").getUnclippedBoundsInRoot();assertTrue(back.left<recents.left)
  val nav=rule.onNodeWithTag("nova-navigation").getUnclippedBoundsInRoot()
  rule.runOnUiThread{val decor=rule.activity.window.decorView;val density=rule.activity.resources.displayMetrics.density;val inset=decor.rootWindowInsets.getInsets(android.view.WindowInsets.Type.navigationBars()).bottom;assertTrue(nav.bottom.value<=(decor.height-inset)/density+2)}
  rule.runOnUiThread{data().switchProfile()};assertTrue(data().shell.events("hidden").isEmpty());assertTrue(data().profile.installed.isEmpty())
 }
 @Test fun edgePanelNotificationCentreAndGestureAlternativesWork(){
  rule.runOnUiThread{data().shell.configure(edge=true);data().shell.favourite("primary","store",true);data().shell.record("primary","Wallpaper changed")}
  rule.onNodeWithTag("nova-edge-handle").performClick();rule.onNodeWithTag("nova-edge-panel").assertIsDisplayed();rule.onNodeWithTag("edge-home").performClick()
  rule.onNodeWithContentDescription("Open Quick Panel").performClick();rule.onNodeWithText("Nova events (1)").performClick();rule.onNodeWithTag("nova-notifications").assertIsDisplayed();rule.onNodeWithTag("clear-events").performClick();rule.onNodeWithText("No preview events yet.").assertIsDisplayed();rule.onNodeWithContentDescription("Close Nova events").performClick()
  rule.onNodeWithContentDescription("Open Quick Panel").performClick();rule.onNodeWithText("Navigation & Edge Panel").performClick();rule.onNodeWithTag("gesture-mode").performClick();rule.onNodeWithTag("shell-settings-done").performScrollTo().performClick();rule.onNodeWithTag("nova-gesture-strip").assertIsDisplayed()
  rule.onNodeWithContentDescription("Navigation alternatives").performClick();rule.onNodeWithText("Recents",useUnmergedTree=false).performClick();rule.onNodeWithTag("nova-recents-screen").assertIsDisplayed()
 }
 @Test fun miniPlayerUsesOnlyProvidedMetadataAndPrioritisesPlaying(){
  val v=open("YouTube Music")
  rule.runOnUiThread{v.loadDataWithBaseURL("https://example.com/","<title>Fixture</title><script>window.fixtureReady=true;if(navigator.mediaSession){navigator.mediaSession.metadata=new MediaMetadata({title:'Synthetic session'});navigator.mediaSession.playbackState='playing';}</script>","text/html","UTF-8",null)}
  rule.waitUntil(10000){js(v,"window.fixtureReady===true")=="true"}
  val supported=js(v,"!!navigator.mediaSession")=="true"
  rule.onNodeWithTag("nova-home").performClick()
  if(supported){rule.waitUntil(10000){rule.activity.browserPool.active?.mediaTitle=="Synthetic session"};rule.onNodeWithTag("nova-mini-player").assertIsDisplayed()}
  else {rule.onNodeWithTag("nova-mini-player").assertDoesNotExist();assertNull(rule.activity.browserPool.player("primary"))}
  rule.runOnUiThread{val p=rule.activity.browserPool;val a=p.active!!;a.mediaState="playing";a.touched=1;val b=p.create(a.app.copy(id="other-fixture"),"primary",false);b.mediaState="paused";b.touched=2;assertSame(a,p.player("primary"));p.select(b);assertTrue(a.resumed);assertTrue(b.resumed);p.foreground=false;p.syncLifecycle();assertFalse(a.resumed);assertFalse(b.resumed);p.foreground=true;p.select(a);p.close(b);a.mediaState="none";a.mediaTitle=""}
 }
}
