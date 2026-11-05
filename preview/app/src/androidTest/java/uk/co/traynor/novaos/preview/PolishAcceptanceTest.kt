package uk.co.traynor.novaos.preview
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.geometry.Offset
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class PolishAcceptanceTest {
 @get:Rule val rule=createAndroidComposeRule<MainActivity>()
 private val d get()=rule.activity.previewData
 private val w get()=rule.activity.widgetData
 @Before fun reset(){rule.runOnUiThread{if(d.state.current!="primary")d.switchProfile();d.change{Profile(pages=listOf(emptyList(),emptyList()))};w.save("primary",emptyList());w.demoLock(false);w.editingId=null;d.shell.dim(0f);d.shell.configure(gestures=false,edge=false);rule.activity.browserPool.closeAll()}}
 private fun panel(){rule.onNodeWithContentDescription("Open Quick Panel").performClick();rule.onNodeWithTag("nova-quick-panel").assertIsDisplayed()}
 @Test fun emptyHomeSwipeOpensTopPanelAndHandleDismisses(){
  rule.onNodeWithTag("home-empty-gesture").performTouchInput{swipeDown()}
  val panel=rule.onNodeWithTag("nova-quick-panel").fetchSemanticsNode().boundsInRoot
  assertTrue("Panel must enter from top",panel.top<200)
  rule.onNodeWithTag("quick-expand").performClick();rule.onNodeWithTag("nova-dimming").performScrollTo().assertExists()
  rule.onNodeWithTag("quick-drag-handle").performScrollTo().performTouchInput{swipeUp()}
  rule.onNodeWithTag("nova-quick-panel").assertDoesNotExist();rule.onNodeWithTag("preview-home").assertIsDisplayed()
 }
 @Test fun fullScreenSettingsKeepNavigationAndBack(){
  panel();rule.onNodeWithTag("quick-expand").performClick();rule.onNodeWithTag("quick-home-settings").performScrollTo().performClick()
  rule.onNodeWithTag("nova-home-settings").assertIsDisplayed();rule.onNodeWithTag("nova-navigation").assertIsDisplayed()
  rule.onNodeWithText("Navigation & multitasking").performScrollTo().performClick();rule.onNodeWithTag("nova-shell-settings").assertIsDisplayed()
  rule.onNodeWithTag("nova-back").performClick();rule.onNodeWithTag("nova-home-settings").assertIsDisplayed()
  rule.onNodeWithTag("nova-home").performClick();rule.onNodeWithTag("preview-home").assertIsDisplayed()
 }
 @Test fun widgetEditDragResizeAndAtomicCollisionPersistence(){
  rule.runOnUiThread{assertTrue(w.add("primary","media",2,2,0));assertTrue(w.add("primary","mind",2,2,0))};val id=w.slots("primary").first().id
  rule.onNodeWithTag("widget-resize-$id",useUnmergedTree=true).assertDoesNotExist()
  rule.onAllNodesWithContentDescription("Hold and drag widget",useUnmergedTree=true)[0].performTouchInput{longClick()}
  rule.onNodeWithTag("widget-edit-done").assertExists()
  val width=rule.onNodeWithTag("widget-$id",useUnmergedTree=true).fetchSemanticsNode().boundsInRoot.width
  rule.onNodeWithTag("widget-resize-$id",useUnmergedTree=true).performScrollTo().performTouchInput{down(center);moveBy(Offset(width,0f),300);up()}
  rule.waitUntil(5000){w.slots("primary").first{it.id==id}.w==4}
  val row=156f*rule.activity.resources.displayMetrics.density*rule.activity.resources.configuration.fontScale.coerceAtLeast(1f)
  rule.onAllNodesWithContentDescription("Hold and drag widget",useUnmergedTree=true)[0].performScrollTo().performTouchInput{down(center);advanceEventTime(700);moveBy(Offset(0f,row*3),400);up()}
  rule.waitUntil(5000){w.slots("primary").first{it.id==id}.y>=2}
  rule.runOnUiThread{val saved=WidgetData(rule.activity).slots("primary");val a=saved.first{it.id==id};assertTrue(saved.filter{it.id!=id}.none{WidgetRules.overlap(a.rect(),it.rect())});assertTrue(w.moveWidget("primary",id,0,0,a.w,a.h,1));assertEquals(1,WidgetData(rule.activity).slots("primary").first{it.id==id}.page)}
 }
 @Test fun quickNotesAutosaveWithoutSaveButtonAndSurviveRecreation(){
  rule.runOnUiThread{w.add("primary","note",2,2,0)};val slot=w.slots("primary").single();val item=slot.items.single()
  rule.onNodeWithTag("quick-note-${item.id}",useUnmergedTree=true).performScrollTo().performTextInput("Synthetic autosaved note")
  rule.onNodeWithTag("quick-note-${item.id}",useUnmergedTree=true).performImeAction()
  rule.runOnUiThread{assertEquals("Synthetic autosaved note",WidgetData(rule.activity).slots("primary").single().items.single().config)}
  rule.activityRule.scenario.recreate();rule.waitForIdle();rule.onNodeWithTag("quick-note-${item.id}",useUnmergedTree=true).assertTextContains("Synthetic autosaved note")
  rule.onNodeWithTag("nova-quick-panel").assertDoesNotExist()
 }
 @Test fun ownPackageMediaSessionControlsAreRealAndAmbiguityFailsClosed(){
  var pauses=0;var next=0;lateinit var session:MediaSession;var sessionReady=false;lateinit var tab:PreviewTab
  val title="SYNTHETIC-OWN-MEDIA"+"X".repeat(130);val actions=PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_SKIP_TO_NEXT
  fun state(value:Int)=PlaybackState.Builder().setState(value,0,if(value==PlaybackState.STATE_PLAYING)1f else 0f).setActions(actions).build()
  try{
   rule.runOnUiThread{
    val pool=rule.activity.browserPool;tab=pool.create(standaloneBrowser.copy(id="media-fixture",name="Fixture player"),"primary",false);pool.select(tab)
    tab.view.loadDataWithBaseURL("https://example.com/","<script>navigator.mediaSession.metadata=new MediaMetadata({title:'$title'});navigator.mediaSession.playbackState='playing'</script>","text/html","UTF-8",null);tab.mediaTitle="";tab.mediaState="playing"
    session=MediaSession(rule.activity,"Nova test fixture");sessionReady=true;session.setMetadata(MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE,title).putString(MediaMetadata.METADATA_KEY_ARTIST,"Synthetic artist").build());session.setPlaybackState(state(PlaybackState.STATE_PLAYING));session.setCallback(object:MediaSession.Callback(){override fun onPause(){pauses++;session.setPlaybackState(state(PlaybackState.STATE_PAUSED))};override fun onSkipToNext(){next++}});session.isActive=true
    pool.mediaSessions.refresh(pool.tabs.toList(),"primary",true,rule.activity.mindAudio.sessionToken)
   }
   try{rule.waitUntil(10000){tab.mediaTitle==title}}catch(failure:Throwable){
    val latch=java.util.concurrent.CountDownLatch(1);var probe=""
    rule.runOnUiThread{tab.view.evaluateJavascript("JSON.stringify({ready:document.readyState,media:typeof navigator.mediaSession,title:navigator.mediaSession?.metadata?.title?.length})"){probe=it;latch.countDown()}}
    latch.await(5,java.util.concurrent.TimeUnit.SECONDS)
    throw AssertionError("Synthetic metadata readiness failed; stored=${tab.mediaTitle.length}, js=$probe",failure)
   }
   rule.runOnUiThread{
    val p=rule.activity.browserPool
    val controllers=rule.activity.getSystemService(android.media.session.MediaSessionManager::class.java).getActiveSessionsForPackage(rule.activity.packageName).map{android.media.session.MediaController(rule.activity,it)}
    val facts=controllers.map{listOf(it.packageName,it.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)?.length,it.playbackState?.state,it.playbackState?.actions)}
    p.mediaSessions.refresh(p.tabs.toList(),"primary",true,rule.activity.mindAudio.sessionToken)
    assertNotNull("Synthetic own-session source missing; tabTitleLength=${tab.mediaTitle.length}, foreground=${p.foreground}, profiles=${p.tabs.map{it.profile}}, native=$facts",p.mediaSessions.source)
   }
   panel()
   rule.onNodeWithText("Synthetic artist").assertExists();rule.onNodeWithTag("session-play-pause").performClick();rule.waitUntil(5000){pauses==1}
   rule.onNodeWithContentDescription("Next media track").performClick();rule.waitUntil(5000){next==1}
   rule.onNodeWithContentDescription("Previous media track").assertIsNotEnabled()
   rule.onNodeWithContentDescription("Close Quick Panel").performClick()
   rule.runOnUiThread{val p=rule.activity.browserPool;val hidden=p.create(standaloneBrowser.copy(id="other-profile"),"hidden",false);p.mediaSessions.refresh(p.tabs.toList(),"primary",true,rule.activity.mindAudio.sessionToken);assertNull(p.mediaSessions.source);p.close(hidden);val duplicate=p.create(standaloneBrowser.copy(id="ambiguous"),"primary",false);duplicate.mediaTitle=title;p.mediaSessions.refresh(p.tabs.toList(),"primary",true,rule.activity.mindAudio.sessionToken);assertNull(p.mediaSessions.source);p.close(duplicate);p.close(tab);assertNull(p.mediaSessions.source)}
  }finally{rule.runOnUiThread{if(sessionReady)session.release()}}
 }
 @Test fun quickPanelFitsFoldViewportsAndLeavesNativeAudioRunning(){
  fun shell(cmd:String){val fd=InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(cmd);android.os.ParcelFileDescriptor.AutoCloseInputStream(fd).use{it.readBytes()}}
  rule.runOnUiThread{rule.activity.mindAudio.play("rain")}
  try{
   shell("wm density 420")
   for((name,size) in listOf("cover" to "904x2316","inner" to "1840x2208","landscape" to "2316x904")){
    shell("wm size $size");android.os.SystemClock.sleep(1000);panel();rule.onNodeWithTag("quick-expand").performClick()
    val bounds=rule.onNodeWithTag("nova-quick-panel").fetchSemanticsNode().boundsInRoot;assertTrue(bounds.top>=0);assertTrue(bounds.height>0)
    rule.runOnUiThread{assertTrue(rule.activity.mindAudio.playing)}
    rule.runOnUiThread{
     val decor=android.view.inspector.WindowInspector.getGlobalWindowViews().last()
     val bmp=android.graphics.Bitmap.createBitmap(decor.width,decor.height,android.graphics.Bitmap.Config.ARGB_8888);decor.draw(android.graphics.Canvas(bmp))
     val dir=File(rule.activity.filesDir,"polish-screenshots");dir.mkdirs();File(dir,"quick-$name.png").outputStream().use{bmp.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};bmp.recycle()
    }
    rule.onNodeWithContentDescription("Close Quick Panel").performScrollTo().performClick()
   }
  }finally{shell("wm size reset");shell("wm density reset");rule.runOnUiThread{rule.activity.mindAudio.pause()}}
 }
}
