package uk.co.traynor.novaos.preview

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExperienceAcceptanceTest {
 @get:Rule val rule=createAndroidComposeRule<MainActivity>()
 private fun launch(name:String,id:String){rule.onNodeWithTag("open-apps").performClick();rule.onNodeWithTag("drawer-search").performTextInput(name);rule.onNodeWithTag("app-$id").performClick()}
 @Before fun reset(){rule.runOnUiThread{val d=rule.activity.previewData;if(d.state.current!="primary")d.switchProfile();d.change{Profile()};d.shell.configure(gestures=false,edge=false);rule.activity.browserPool.closeAll()}}
 @Test fun journalMoodAndBrowserListsRoundTripWithoutCrossingProfiles(){
  rule.runOnUiThread{
   val m=MindData(rule.activity);val id="synthetic-${System.nanoTime()}"
   assertFalse(m.put("primary","","",true))
   assertTrue(m.put("primary","synthetic note","Okay",true,id))
   val restored=MindData(rule.activity);assertEquals("synthetic note",restored.entries("primary").first{it.id==id}.text);assertFalse(restored.entries("hidden").any{it.id==id})
   assertTrue(restored.put("primary","edited synthetic","Calm",true,id));assertEquals(1,restored.entries("primary").count{it.id==id});assertTrue(restored.delete("primary",id));assertFalse(MindData(rule.activity).entries("primary").any{it.id==id})
   val lib=rule.activity.browserPool.library;lib.add("bookmarks","primary","Fixture","https://example.com/");assertTrue(BrowserLibrary(rule.activity).links("bookmarks","primary").any{it.url=="https://example.com/"})
  }
 }
 @Test fun breathingGroundingJournalAndSanctuaryAreInteractive(){
  launch("Nova Mind","mind");rule.onNodeWithTag("nova-mind").assertIsDisplayed()
  rule.onNodeWithTag("calm-now").performClick();rule.onNodeWithTag("breathing-circle").performScrollTo().assertIsDisplayed();rule.onNodeWithTag("breathing-pause").performScrollTo().performClick();rule.onNodeWithText("Resume").assertIsDisplayed();rule.onNodeWithTag("breathing-stop").performClick()
  rule.onNodeWithContentDescription("Back to Home").performClick();rule.onNodeWithTag("mind-grounding").performScrollTo().performClick()
  repeat(5){rule.onNodeWithTag("grounding-skip").performScrollTo().performClick()};rule.onNodeWithText("You’re here. Take your time.").assertExists()
  rule.onNodeWithContentDescription("Back to Home").performClick();rule.onNodeWithTag("mind-journal").performScrollTo().performClick();rule.onNodeWithTag("mind-entry-new").performClick();rule.onNodeWithTag("mind-entry-text").performTextInput("Synthetic UI note");rule.onNodeWithTag("mind-entry-save").performScrollTo().performClick();rule.onNodeWithText("Synthetic UI note").assertExists()
  rule.onNodeWithTag("nova-home").performClick();launch("Nova Sanctuary","sanctuary");rule.onNodeWithTag("nova-sanctuary").assertIsDisplayed();rule.onNodeWithTag("exit-sanctuary").performScrollTo().performClick();rule.onNodeWithTag("preview-home").assertIsDisplayed()
 }
 @Test fun catalogueGameAndStandaloneBrowserUseSameSafeEngine(){
  rule.runOnUiThread{val d=rule.activity.previewData;assertEquals(40,d.catalogue.count{it.category=="Games"});d.install(d.catalogue.first{it.id=="game-2048"})}
  val e=rule.activity.previewData.catalogue.first{it.id=="game-2048"};launch("2048",rule.activity.previewData.entryKey(e));rule.runOnUiThread{assertTrue(rule.activity.browserPool.active!!.appMode);assertEquals("Games",rule.activity.browserPool.active!!.app.category)}
  val game=rule.activity.browserPool.active!!.view
  rule.onNodeWithTag("nova-home").performClick();launch("Browser V1","browser-v1");rule.runOnUiThread{val p=rule.activity.browserPool;assertEquals("browser-v1",p.active!!.app.id);assertFalse(p.active!!.appMode);assertTrue(p.tabs.any{it.view===game});assertFalse(p.active!!.view.settings.allowFileAccess)}
 }
 @Test fun offlineSoundTimerAndSensitiveWindowAreReal(){
  rule.runOnUiThread{val a=rule.activity.mindAudio;a.play("rain");assertTrue(a.playing);a.volume(.2f);assertEquals(.2f,a.volume);a.timer(5);assertTrue(a.deadline>android.os.SystemClock.elapsedRealtime());a.pause();assertFalse(a.playing);a.resume();assertTrue(a.playing);a.timer(0);a.pause()}
  launch("Nova Mind","mind");rule.waitForIdle();rule.runOnUiThread{assertTrue(rule.activity.window.attributes.flags and android.view.WindowManager.LayoutParams.FLAG_SECURE != 0)}
 }
 @Test fun calmNowOverridesRetainedJournalDestination(){
  launch("Nova Mind","mind");rule.onNodeWithTag("mind-journal").performScrollTo().performClick();rule.onNodeWithTag("mind-entry-new").assertExists()
  rule.onNodeWithTag("nova-home").performClick();rule.onNodeWithContentDescription("Calm Now").performClick();rule.onNodeWithTag("breathing-circle").assertExists();rule.onNodeWithTag("mind-entry-new").assertDoesNotExist()
 }
 @Test fun audioSurvivesRecreationAndBreathingPausesWhenStopped(){
  rule.runOnUiThread{rule.activity.mindAudio.play("rain")};rule.activityRule.scenario.recreate();rule.waitForIdle();rule.runOnUiThread{assertTrue(rule.activity.mindAudio.playing);rule.activity.mindAudio.pause()}
  launch("Calm Now","calm");rule.onNodeWithTag("breathing-circle").assertExists()
  rule.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED);rule.activityRule.scenario.moveToState(androidx.lifecycle.Lifecycle.State.RESUMED);rule.waitForIdle();rule.onNodeWithText("Resume").assertExists()
 }
}
