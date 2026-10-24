package uk.co.traynor.privategallery

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import uk.co.traynor.privategallery.core.security.AutoLockTimeout
import uk.co.traynor.privategallery.ui.HiddenPreferences

/** Disposable instrumentation identity only; no credential creation or media mutation. */
class SmartUnlockLifecycle173Test {
 private fun neutralRoute(activity:MainActivity) {
  val actual=MainActivity::class.java.getDeclaredMethod("getRoute").apply {isAccessible=true}.invoke(activity)
  val expected=if(uk.co.traynor.privategallery.core.security.PinVaultKeyStore(activity).isConfigured)Route.LOCK else Route.SETUP
  assertEquals(expected,actual)
 }
 private fun state(activity:MainActivity)=ViewModelProvider(activity)[ProtectedSessionState::class.java]
 private fun lock(activity:MainActivity)=MainActivity::class.java.getDeclaredMethod("lockPrimary",Boolean::class.javaPrimitiveType).apply {isAccessible=true}.invoke(activity,false)
 @Test fun cancelledLockedEntrySurvivesLockCleanupAndActivityRecreation() {
  ActivityScenario.launch(MainActivity::class.java).use {scenario ->
   scenario.onActivity {activity ->
    val owner=state(activity);owner.automaticBiometricPromptAttempted=true
    lock(activity);assertTrue(owner.automaticBiometricPromptAttempted)
   }
   scenario.recreate()
   scenario.onActivity {assertTrue(state(it).automaticBiometricPromptAttempted)}
  }
 }
 @Test fun authenticatedPrimaryLockClearsAuthorityAndAllowsFreshPrompt() {
  ActivityScenario.launch(MainActivity::class.java).use {scenario ->
   scenario.onActivity {activity ->
    val owner=state(activity);val synthetic=ByteArray(32){7}
    owner.key=synthetic;owner.session.unlock();owner.automaticBiometricPromptAttempted=true
    lock(activity)
    assertNull(owner.key);assertFalse(owner.session.isUnlocked)
    assertTrue(synthetic.all {it==0.toByte()});assertFalse(owner.automaticBiometricPromptAttempted)
   }
  }
 }
 @Test fun immediateHiddenTimeoutReturnsNeutralAndStartsFreshPrimaryEntry() {
  val preferences=HiddenPreferences(InstrumentationRegistry.getInstrumentation().targetContext)
  val previous=preferences.timeout
  assertFalse("Disposable identity must have no enrolled biometric envelope",uk.co.traynor.privategallery.core.security.BiometricVaultKeyStore(InstrumentationRegistry.getInstrumentation().targetContext).isEnabled)
  try {
   preferences.timeout=AutoLockTimeout.IMMEDIATELY
   ActivityScenario.launch(MainActivity::class.java).use {scenario ->
    scenario.onActivity {activity ->
     val owner=state(activity)
     assertTrue(owner.hidden.session.complete(owner.hidden.session.beginAuthentication(),ByteArray(32){9}))
     owner.hidden.select();owner.automaticBiometricPromptAttempted=true
    }
    scenario.moveToState(Lifecycle.State.CREATED);scenario.moveToState(Lifecycle.State.RESUMED)
    scenario.onActivity {activity ->
     val owner=state(activity)
     neutralRoute(activity)
     assertFalse(owner.hidden.selected);assertFalse(owner.hidden.session.isUnlocked)
     assertNull(owner.key);assertFalse(owner.session.isUnlocked)
     assertFalse(owner.automaticBiometricPromptAttempted)
    }
   }
  } finally {preferences.timeout=previous}
 }
}
