@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
package uk.co.traynor.privategallery

import androidx.activity.compose.setContent
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import org.junit.Assert.*
import org.junit.Test

class PrivacyShield175Test {
 private fun owner(activity: MainActivity) = ViewModelProvider(activity)[ProtectedSessionState::class.java]
 @Test fun panicRevokesBothSessionsWipesKeyAndNeverReopensHidden() {
  ActivityScenario.launch(MainActivity::class.java).use { scenario ->
   scenario.onActivity { activity ->
    val state=owner(activity)
    val key=ByteArray(32){7};state.key=key;state.session.unlock()
    assertTrue(state.hidden.session.complete(state.hidden.session.beginAuthentication(),ByteArray(32){9}))
    state.hidden.select()
    activity.panicExit()
    assertNull(state.key);assertFalse(state.session.isUnlocked)
    assertFalse(state.hidden.selected);assertFalse(state.hidden.session.isUnlocked)
    assertTrue(key.all {it==0.toByte()})
    assertTrue(state.automaticBiometricPromptAttempted)
    assertEquals(Route.VAULT,state.afterAuthentication)
   }
   scenario.recreate()
   scenario.onActivity { activity ->
    val state=owner(activity)
    assertNull(state.key);assertFalse(state.session.isUnlocked)
    assertFalse(state.hidden.selected);assertFalse(state.hidden.session.isUnlocked)
    assertTrue(state.automaticBiometricPromptAttempted)
   }
  }
 }
 @Test fun videoCompletingUnderOwnedDialogCannotAutoplayOrCover() {
  var complete: ((Result<ByteArray>)->Unit)? = null
  ActivityScenario.launch(MainActivity::class.java).use { scenario ->
   var focusDialog:android.app.Dialog?=null
   try {
    scenario.onActivity {activity -> activity.setContent {
     uk.co.traynor.privategallery.ui.FullscreenMediaViewer(
      listOf(uk.co.traynor.privategallery.ui.ViewerMediaEntry("late","video/mp4")),
      uk.co.traynor.privategallery.core.ui.MediaViewerSource.VAULT,0,{},
      onLoadProtectedBytes={_,done->complete=done})
    }}
    val instrumentation=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
    var deadline=android.os.SystemClock.uptimeMillis()+15000
    var loaded=false
    while(!loaded && android.os.SystemClock.uptimeMillis()<deadline) {
     instrumentation.waitForIdleSync();scenario.onActivity {loaded=complete!=null}
     if(!loaded)Thread.sleep(50)
    }
    assertTrue("Protected loader must be reached",loaded)
    scenario.onActivity {activity ->
     focusDialog=android.app.Dialog(activity).apply {
      setContentView(android.widget.TextView(activity).apply {text="Focus fixture"})
      show()
     }
    }
    deadline=android.os.SystemClock.uptimeMillis()+15000
    var covered=false
    while(!covered && android.os.SystemClock.uptimeMillis()<deadline) {
     instrumentation.waitForIdleSync()
     scenario.onActivity {covered=!it.window.decorView.hasWindowFocus()}
     if(!covered)Thread.sleep(50)
    }
    assertTrue("A real owned dialog must hold focus",covered)
    scenario.onActivity {activity ->
     assertEquals(androidx.lifecycle.Lifecycle.State.RESUMED,activity.lifecycle.currentState)
     complete!!.invoke(Result.success(uk.co.traynor.privategallery.core.media.SyntheticVideo.bytes()))
    }
    deadline=android.os.SystemClock.uptimeMillis()+15000
    var player:androidx.media3.common.Player?=null
    while(player==null && android.os.SystemClock.uptimeMillis()<deadline) {
     instrumentation.waitForIdleSync();scenario.onActivity {player=findPlayer(it.window.decorView)?.player}
     if(player==null)Thread.sleep(50)
    }
    scenario.onActivity {activity ->
     assertNotNull("Late completion must construct a player",player)
     assertFalse("Owned dialog must not cover authorised content",activity.privacyCoverVisible())
     assertFalse(activity.window.decorView.hasWindowFocus())
     assertFalse("Covered player must never request autoplay",player!!.playWhenReady)
    }
   } finally {scenario.onActivity {focusDialog?.dismiss()}}
  }
 }
 @Test fun panicDuringRealPlayerPlaybackStopsImmediatelyAndRevokesSession() {
  ActivityScenario.launch(MainActivity::class.java).use {scenario ->
   scenario.onActivity {activity ->
    val state=owner(activity);state.key=ByteArray(32){7};state.session.unlock()
    activity.setContent {uk.co.traynor.privategallery.ui.FullscreenMediaViewer(
     listOf(uk.co.traynor.privategallery.ui.ViewerMediaEntry("panic-video","video/mp4")),uk.co.traynor.privategallery.core.ui.MediaViewerSource.VAULT,0,{},
     onLoadProtectedBytes={_,done->done(Result.success(uk.co.traynor.privategallery.core.media.SyntheticVideo.bytes()))})}
   }
   val instrumentation=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
   val deadline=android.os.SystemClock.uptimeMillis()+15000
   var ready=false
   while(!ready && android.os.SystemClock.uptimeMillis()<deadline) {
    instrumentation.waitForIdleSync();scenario.onActivity {ready=findPlayer(it.window.decorView)?.player?.playbackState==androidx.media3.common.Player.STATE_READY}
    if(!ready)Thread.sleep(50)
   }
   assertTrue("Real player must prepare before Panic",ready)
   scenario.onActivity {activity ->
    val player=checkNotNull(findPlayer(activity.window.decorView)?.player)
    player.play();assertTrue(player.playWhenReady)
    activity.panicExit()
    assertFalse("Panic must synchronously stop playback",player.playWhenReady)
    assertNull(owner(activity).key);assertFalse(owner(activity).session.isUnlocked)
    assertTrue(owner(activity).automaticBiometricPromptAttempted)
   }
  }
 }
 private fun findPlayer(view:android.view.View):androidx.media3.ui.PlayerView? {
  if(view is androidx.media3.ui.PlayerView)return view
  if(view is android.view.ViewGroup)for(i in 0 until view.childCount)findPlayer(view.getChildAt(i))?.let{return it}
  return null
 }
 @Test fun repeatedPanicOnAlreadyNeutralEntryEventuallyUncovers() {
  ActivityScenario.launch(MainActivity::class.java).use {scenario ->
   val instrumentation=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
   repeat(2) {
    scenario.onActivity {activity ->
     assertFalse(owner(activity).session.isUnlocked)
     assertFalse(owner(activity).hidden.session.isUnlocked)
     activity.panicExit()
     assertTrue(activity.privacyCoverVisible())
    }
    val deadline=android.os.SystemClock.uptimeMillis()+10000
    var covered=true
    while(covered && android.os.SystemClock.uptimeMillis()<deadline) {
     instrumentation.waitForIdleSync()
     scenario.onActivity {covered=it.privacyCoverVisible()}
     if(covered)Thread.sleep(50)
    }
    assertFalse("Neutral Panic must commit and uncover even when route was unchanged",covered)
   }
  }
 }
 @Test fun actualBackgroundStopCoversContentAndEnforcesSecureWindow() {
  ActivityScenario.launch(MainActivity::class.java).use { scenario ->
   scenario.onActivity {it.window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)}
   scenario.moveToState(androidx.lifecycle.Lifecycle.State.CREATED)
   scenario.onActivity { activity ->
    assertTrue(activity.privacyCoverVisible())
    assertTrue(activity.window.attributes.flags and android.view.WindowManager.LayoutParams.FLAG_SECURE != 0)
   }
  }
 }
}
