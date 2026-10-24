package uk.co.traynor.privategallery

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
