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
 @Test fun videoCompletingAfterFocusLossCannotAutoplayBehindCover() {
  var complete: ((Result<ByteArray>)->Unit)? = null
  ActivityScenario.launch(MainActivity::class.java).use { scenario ->
   scenario.onActivity {activity -> activity.setContent {
    uk.co.traynor.privategallery.ui.FullscreenMediaViewer(
     listOf(uk.co.traynor.privategallery.ui.ViewerMediaEntry("late","video/mp4")),
     uk.co.traynor.privategallery.core.ui.MediaViewerSource.VAULT,0,{},
     onLoadProtectedBytes={_,done->complete=done})
   }}
   val instrumentation=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
   val deadline=android.os.SystemClock.uptimeMillis()+15000
   var loaded=false
   while(!loaded && android.os.SystemClock.uptimeMillis()<deadline) {
    instrumentation.waitForIdleSync()
    scenario.onActivity { loaded=complete!=null }
    if(!loaded)Thread.sleep(50)
   }
   assertTrue("Protected loader must be reached",loaded)
   scenario.onActivity {activity ->
    activity.onWindowFocusChanged(false)
    complete!!.invoke(Result.success(uk.co.traynor.privategallery.core.media.SyntheticVideo.bytes()))
   }
   var player:androidx.media3.common.Player?=null
   while(player==null && android.os.SystemClock.uptimeMillis()<deadline) {
    instrumentation.waitForIdleSync()
    scenario.onActivity {activity -> player=findPlayer(activity.window.decorView)?.player }
    if(player==null)Thread.sleep(50)
   }
   scenario.onActivity {activity ->
    assertNotNull("Late completion must construct a player",player)
    assertTrue(activity.privacyCoverVisible())
    assertFalse("Covered player must never request autoplay",player!!.playWhenReady)
   }
  }
 }
 private fun findPlayer(view:android.view.View):androidx.media3.ui.PlayerView? {
  if(view is androidx.media3.ui.PlayerView)return view
  if(view is android.view.ViewGroup)for(i in 0 until view.childCount)findPlayer(view.getChildAt(i))?.let{return it}
  return null
 }
 @Test fun focusLossCoversContentAndEnforcesSecureWindow() {
  ActivityScenario.launch(MainActivity::class.java).use { scenario ->
   scenario.onActivity { activity ->
    activity.window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
    activity.onWindowFocusChanged(false)
    assertTrue(activity.privacyCoverVisible())
    assertTrue(activity.window.attributes.flags and android.view.WindowManager.LayoutParams.FLAG_SECURE != 0)
   }
  }
 }
}
