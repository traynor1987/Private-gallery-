package uk.co.traynor.privategallery

import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import org.junit.Assert.*
import org.junit.Test
import uk.co.traynor.privategallery.core.browser.hidden.HiddenVpnBrowserBroker
import uk.co.traynor.privategallery.core.security.AutoLockTimeout
import uk.co.traynor.privategallery.ui.HiddenPreferences

/** Exercises Main's real callback/policies with synthetic memory-only credentials.
 * Foreground-owner facts are injected; full Main→real VPN child→Home remains a live UI gate. */
class HiddenVpnHandoff1762Test {
    private fun flag(activity:MainActivity,name:String,value:Boolean) {
        MainActivity::class.java.getDeclaredField(name).apply {isAccessible=true}.setBoolean(activity,value)
    }
    private fun authenticate(activity:MainActivity):ProtectedSessionState {
        val state=ViewModelProvider(activity)[ProtectedSessionState::class.java]
        state.session.setTimeout(AutoLockTimeout.IMMEDIATELY);state.session.unlock()
        assertTrue(state.hidden.session.complete(state.hidden.session.beginAuthentication(),ByteArray(32){17}))
        return state
    }
    @Test fun childHomeUsesIndependentHiddenGraceAndImmediatePrimaryPolicy() {
        ActivityScenario.launch(MainActivity::class.java).use {scenario ->scenario.onActivity {activity ->
            val preferences=HiddenPreferences(activity);val previous=preferences.timeout
            try {
                preferences.timeout=AutoLockTimeout.SECONDS_30
                val state=authenticate(activity)
                flag(activity,"mainPresentationStarted",false);flag(activity,"stoppedPrivacyDelivered",false)
                val stopped=android.os.SystemClock.elapsedRealtime()
                activity.hiddenVpnBrowserPresentationEnded(HiddenVpnBrowserBroker.BACKGROUND,stopped)
                assertFalse("Primary immediate policy still applies after child Home",state.session.isUnlocked)
                assertTrue("Hidden retains its independent grace",state.hidden.session.isUnlocked)
                try {state.hidden.session.beginOperation().close();fail("Background vault operations admitted")} catch(expected:IllegalStateException) {}
                state.hidden.session.onForeground();assertTrue(state.hidden.session.isUnlocked)
                state.hidden.session.lock()
            } finally {preferences.timeout=previous;flag(activity,"mainPresentationStarted",true)}
        }}
    }
    @Test fun childScreenOffLocksBothEvenWhenMainIsAlreadyStopped() {
        ActivityScenario.launch(MainActivity::class.java).use {scenario ->scenario.onActivity {activity ->
            val preferences=HiddenPreferences(activity);val previous=preferences.timeout
            try {
                preferences.timeout=AutoLockTimeout.MINUTES_5
                val state=authenticate(activity);state.session.setTimeout(AutoLockTimeout.MINUTES_5)
                flag(activity,"mainPresentationStarted",false);flag(activity,"stoppedPrivacyDelivered",false)
                activity.hiddenVpnBrowserPresentationEnded(HiddenVpnBrowserBroker.SCREEN_OFF,android.os.SystemClock.elapsedRealtime())
                assertFalse(state.session.isUnlocked);assertFalse(state.hidden.session.isUnlocked)
            } finally {preferences.timeout=previous;flag(activity,"mainPresentationStarted",true)}
        }}
    }
    @Test fun explicitReturnDoesNotStartImmediateLockWhenMainOwnsForegroundAgain() {
        ActivityScenario.launch(MainActivity::class.java).use {scenario ->
            var state:ProtectedSessionState?=null
            var old=AutoLockTimeout.MINUTE_1
            scenario.onActivity {activity ->
                val preferences=HiddenPreferences(activity);old=preferences.timeout;preferences.timeout=AutoLockTimeout.IMMEDIATELY
                state=authenticate(activity)
                flag(activity,"mainPresentationStarted",false);flag(activity,"stoppedPrivacyDelivered",false)
                activity.hiddenVpnBrowserPresentationEnded(HiddenVpnBrowserBroker.RETURN,android.os.SystemClock.elapsedRealtime())
                flag(activity,"mainPresentationStarted",true)
            }
            Thread.sleep(1_000)
            scenario.onActivity {activity ->
                try {assertTrue(state!!.session.isUnlocked);assertTrue(state!!.hidden.session.isUnlocked)}
                finally {HiddenPreferences(activity).timeout=old;state!!.session.lock();state!!.hidden.session.lock()}
            }
        }
    }
}
