package uk.co.traynor.novaos.preview
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class WidgetExperienceTest {
 @get:Rule val rule=createAndroidComposeRule<MainActivity>()
 private fun data()=rule.activity.previewData
 private fun widgets()=rule.activity.widgetData
 @Before fun reset(){rule.runOnUiThread{val d=data();if(d.state.current!="primary")d.switchProfile();d.change{Profile()};widgets().save("primary",emptyList());widgets().save("hidden",emptyList());widgets().demoLock(false);d.shell.configure(gestures=false,edge=false);rule.activity.video.close();rule.activity.browserPool.closeAll()}}
 private fun launch(name:String,id:String){rule.onNodeWithTag("open-apps").performClick();rule.onNodeWithTag("drawer-search").performTextInput(name);rule.onNodeWithTag("app-$id").performClick()}
 @Test fun widgetCreationResizeCollisionStackAndPersistence(){
  launch("Widget Centre","widgets");rule.onNodeWithTag("widget-centre").assertIsDisplayed();rule.onNodeWithTag("add-widget-clock").performScrollTo().performClick();rule.onNodeWithTag("home-widgets").assertExists()
  rule.runOnUiThread{val w=widgets();val first=w.slots("primary").single();assertTrue(w.place("primary",first.id,0,0,2,2,0));assertTrue(w.add("primary","mind",2,2,0));val other=w.slots("primary").last();assertFalse(w.place("primary",other.id,0,0,2,2,0));assertTrue(w.stack("primary",first.id,other.id));w.active("primary",first.id,1);val restored=WidgetData(rule.activity);assertEquals(2,restored.slots("primary").single().items.size);assertEquals(1,restored.slots("primary").single().active);assertTrue(restored.slots("hidden").isEmpty());w.remove("primary",first.id);w.undoRemove();assertEquals(1,w.slots("primary").size)}
 }
 @Test fun localSearchRoutesToBreathingWithoutChangingBrowser(){
  rule.onNodeWithContentDescription("Open Nova Search").performClick();rule.onNodeWithTag("nova-search-input").performTextInput("Breathing");rule.onNodeWithText("Breathing",substring=false).performClick();rule.onNodeWithTag("breathing-circle").assertExists();assertTrue(rule.activity.browserPool.tabs.isEmpty())
 }
 @Test fun backupRoundTripRejectsMalformedAndExcludesNotes(){
  rule.runOnUiThread{val d=data();val w=widgets();w.add("primary","note",2,2,0);val slot=w.slots("primary").single();w.note("primary",slot.id,slot.items.single().id,"SYNTHETIC-PRIVATE-NOTE");rule.activity.mindData.put("primary","SYNTHETIC-JOURNAL","Okay",true,"widget-fixture");d.change{it.copy(wallpaper=Wallpaper("Dusk"),columns=5)}
   val b=rule.activity.homeBackup;val raw=b.export(d,w);assertFalse(raw.contains("SYNTHETIC"));assertFalse(raw.contains("https://"));HomeBackupCodec.validate(raw);d.change{Profile()};w.save("primary",emptyList());b.prepare(raw,"primary");assertNotNull(b.pending);assertTrue(b.restore(d,w));assertEquals("Dusk",d.profile.wallpaper.builtin);assertEquals(5,d.profile.columns);assertEquals("",w.slots("primary").single().items.single().config);assertTrue(rule.activity.mindData.entries("primary").any{it.id=="widget-fixture"});b.prepare(raw.replace("nova-home-layout","wrong-format"),"primary");assertNull(b.pending)
  }
 }
 @Test fun demoLockConcealsAppContentAndUnlocksSameHome(){
  launch("Preview lock","lock-settings");rule.onNodeWithTag("enable-demo-lock").performClick();rule.onNodeWithText("Show preview lock").performClick();rule.onNodeWithTag("nova-demo-lock").assertIsDisplayed();rule.onNodeWithTag("preview-home").assertDoesNotExist();rule.onNodeWithTag("preview-web-app").assertDoesNotExist();rule.onNodeWithTag("nova-mini-player").assertDoesNotExist();rule.onNodeWithTag("unlock-demo").performClick();rule.onNodeWithText("Nova preview lock").assertExists();rule.onNodeWithTag("nova-home").performClick();rule.onNodeWithTag("preview-home").assertIsDisplayed()
 }
 @Test fun nativeNowPlayingAndLocalVideoMiniLifecycle(){
  rule.runOnUiThread{rule.activity.mindAudio.play("rain");assertTrue(rule.activity.mindAudio.playing);widgets().add("primary","media",4,2,0)}
  rule.onNodeWithTag("now-playing").assertExists();rule.onNodeWithContentDescription("Calm sound play or pause").performClick();rule.runOnUiThread{assertFalse(rule.activity.mindAudio.playing)}
  val file=File(rule.activity.cacheDir,"synthetic-video.mp4");InstrumentationRegistry.getInstrumentation().context.assets.open("nova-video-fixture.mp4").use{input->file.outputStream().use{input.copyTo(it)}}
  rule.runOnUiThread{rule.activity.video.open(android.net.Uri.fromFile(file),"Synthetic fixture","primary");rule.activity.video.mini=true};rule.waitUntil(15000){rule.activity.video.prepared};rule.runOnUiThread{val v=rule.activity.video;assertTrue(WidgetRules.pipEligible(true,v.prepared,false));assertFalse(WidgetRules.pipEligible(true,v.prepared,true));v.mini=true}
  rule.onNodeWithTag("video-mini-player").assertExists();rule.onNodeWithContentDescription("Mini video play or pause").performClick();rule.runOnUiThread{assertFalse(rule.activity.video.playing);rule.activity.video.close()};rule.onNodeWithTag("video-mini-player").assertDoesNotExist()
 }
 @Test fun widgetOnlyPageCannotBeDeletedAndReorderingKeepsWidgets(){
  rule.runOnUiThread{val d=data();val w=widgets();d.addPage();assertTrue(w.add("primary","clock",2,1,1));d.removePage(1);assertEquals(3,d.profile.pages.size);d.movePage(1,1);assertEquals(2,w.slots("primary").single().page);d.removePage(1);assertEquals(2,d.profile.pages.size);assertEquals(1,w.slots("primary").single().page);HomeBackupCodec.validate(rule.activity.homeBackup.export(d,w))}
 }
 @Test fun dragResizeAndStackSwipeOperateOnCurrentGeometry(){
  rule.runOnUiThread{widgets().add("primary","clock",2,2,0)}
  val id=widgets().slots("primary").single().id
  rule.onNodeWithTag("widget-$id").performTouchInput{down(bottomRight-androidx.compose.ui.geometry.Offset(7f,7f));moveBy(androidx.compose.ui.geometry.Offset(width.toFloat(),0f),200);up()}
  rule.waitUntil(3000){widgets().slots("primary").single().w==4}
  val rowPx=156f*rule.activity.resources.displayMetrics.density*rule.activity.resources.configuration.fontScale.coerceAtLeast(1f)
  rule.onNodeWithContentDescription("Hold and drag widget").performTouchInput{down(center);advanceEventTime(700);moveBy(androidx.compose.ui.geometry.Offset(0f,rowPx),200);up()}
  rule.runOnUiThread{val slot=widgets().slots("primary").single();assertEquals(4,slot.w);assertEquals(1,slot.y);widgets().place("primary",id,0,0,2,2,0);widgets().add("primary","mind",2,2,0);val other=widgets().slots("primary").last();assertTrue(widgets().stack("primary",id,other.id))}
  rule.onNodeWithTag("widget-$id").performTouchInput{swipeLeft()};rule.waitUntil(5000){widgets().slots("primary").single().active==1};assertEquals(1,WidgetData(rule.activity).slots("primary").single().active)
 }
}
