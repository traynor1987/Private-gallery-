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
                activity.hiddenVpnBrowserPresentationEnded(HiddenVpnBrowserBroker.BACKGROUND,stopped,activity.captureHiddenVpnPresentationAuthority())
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
                activity.hiddenVpnBrowserPresentationEnded(HiddenVpnBrowserBroker.SCREEN_OFF,android.os.SystemClock.elapsedRealtime(),activity.captureHiddenVpnPresentationAuthority())
                assertFalse(state.session.isUnlocked);assertFalse(state.hidden.session.isUnlocked)
            } finally {preferences.timeout=previous;flag(activity,"mainPresentationStarted",true)}
        }}
    }
    private fun foregroundAt(activity:MainActivity,time:Long) {
        flag(activity,"mainPresentationStarted",true)
        MainActivity::class.java.getDeclaredField("mainPresentationStartedAt").apply {isAccessible=true}.setLong(activity,time)
    }
    @Test fun delayedLossAfterForegroundExpiresOnlyTheMatchingIndependentAuthentication() {
        ActivityScenario.launch(MainActivity::class.java).use {scenario ->scenario.onActivity {activity ->
            val preferences=HiddenPreferences(activity);val previous=preferences.timeout
            try {
                preferences.timeout=AutoLockTimeout.IMMEDIATELY
                val state=authenticate(activity);state.session.setTimeout(AutoLockTimeout.MINUTES_5)
                val owner=activity.captureHiddenVpnPresentationAuthority()
                val now=android.os.SystemClock.elapsedRealtime();foregroundAt(activity,now)
                activity.hiddenVpnBrowserPresentationEnded(HiddenVpnBrowserBroker.BACKGROUND,(now-1).coerceAtLeast(0),owner)
                assertFalse("Late Home cannot skip Hidden's expired immediate deadline",state.hidden.session.isUnlocked)
                assertTrue("Primary keeps its own five-minute grace",state.session.isUnlocked)
                // Duplicate old event and a separately queued old owner cannot lock a new auth.
                val other=owner
                state.hidden.session.onForeground();assertTrue(state.hidden.session.complete(state.hidden.session.beginAuthentication(),ByteArray(32){31}))
                state.session.unlock()
                activity.hiddenVpnBrowserPresentationEnded(HiddenVpnBrowserBroker.DEATH,0,other)
                assertTrue(state.hidden.session.isUnlocked);assertTrue(state.session.isUnlocked)
                val unseen=activity.captureHiddenVpnPresentationAuthority()
                state.hidden.session.lock();state.hidden.session.onForeground();assertTrue(state.hidden.session.complete(state.hidden.session.beginAuthentication(),ByteArray(32){32}))
                state.session.lock();state.session.unlock()
                activity.hiddenVpnBrowserPresentationEnded(HiddenVpnBrowserBroker.DEATH,0,unseen)
                assertTrue("Undelivered stale owner cannot revoke later Hidden auth",state.hidden.session.isUnlocked)
                assertTrue("Undelivered stale owner cannot revoke later Primary auth",state.session.isUnlocked)
                state.hidden.session.lock();state.session.lock()
            } finally {preferences.timeout=previous}
        }}
    }
    @Test fun delayedLossWithinGraceLeavesActiveMainInForeground() {
        ActivityScenario.launch(MainActivity::class.java).use {scenario ->scenario.onActivity {activity ->
            val preferences=HiddenPreferences(activity);val previous=preferences.timeout
            try {
                preferences.timeout=AutoLockTimeout.SECONDS_30
                val state=authenticate(activity);state.session.setTimeout(AutoLockTimeout.MINUTES_5)
                val owner=activity.captureHiddenVpnPresentationAuthority()
                val now=android.os.SystemClock.elapsedRealtime();foregroundAt(activity,now)
                activity.hiddenVpnBrowserPresentationEnded(HiddenVpnBrowserBroker.DEATH,(now-1).coerceAtLeast(0),owner)
                assertTrue(state.session.isUnlocked);assertTrue(state.hidden.session.isUnlocked)
                state.hidden.session.beginOperation().use {assertTrue("Late callback must not background active Main",state.hidden.session.isCurrent(it))}
                state.session.lock();state.hidden.session.lock()
            } finally {preferences.timeout=previous}
        }}
    }
    @Test fun lateMainStartConsumesReturnBeforeOverdueTimerCanBeCancelled() {
        ActivityScenario.launch(MainActivity::class.java).use {scenario ->scenario.onActivity {activity ->
            val preferences=HiddenPreferences(activity);val previous=preferences.timeout
            try {
                preferences.timeout=AutoLockTimeout.IMMEDIATELY
                val state=authenticate(activity);state.session.setTimeout(AutoLockTimeout.MINUTES_5)
                flag(activity,"mainPresentationStarted",false)
                val now=android.os.SystemClock.elapsedRealtime()
                activity.hiddenVpnBrowserPresentationEnded(HiddenVpnBrowserBroker.RETURN,(now-1_000).coerceAtLeast(0),activity.captureHiddenVpnPresentationAuthority())
                // All operations stay in one Main callback: the posted timer cannot dispatch.
                foregroundAt(activity,now)
                activity.applyPendingHiddenReturn()
                assertFalse("A late onStart must apply the original Hidden deadline",state.hidden.session.isUnlocked)
                assertTrue("Primary retains its independent five-minute policy",state.session.isUnlocked)
                state.hidden.session.onForeground()
                assertTrue(state.hidden.session.complete(state.hidden.session.beginAuthentication(),ByteArray(32){45}))
                activity.applyPendingHiddenReturn()
                assertTrue("Consumed return must not revoke a later authentication",state.hidden.session.isUnlocked)
                state.hidden.session.lock();state.session.lock()
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
                activity.hiddenVpnBrowserPresentationEnded(HiddenVpnBrowserBroker.RETURN,android.os.SystemClock.elapsedRealtime(),activity.captureHiddenVpnPresentationAuthority())
                flag(activity,"mainPresentationStarted",true)
            }
            Thread.sleep(1_000)
            scenario.onActivity {activity ->
                try {assertTrue(state!!.session.isUnlocked);assertTrue(state!!.hidden.session.isUnlocked)}
                finally {HiddenPreferences(activity).timeout=old;state!!.session.lock();state!!.hidden.session.lock()}
            }
        }
    }
    private fun invokeMain(activity:MainActivity,name:String) = MainActivity::class.java.getDeclaredMethod(name).apply {isAccessible=true}.invoke(activity)
    private fun enterHiddenBrowser(activity:MainActivity,state:ProtectedSessionState) {
        state.hidden.select(Route.BROWSER);state.hidden.authenticated=true
        invokeMain(activity,"openBrowser")
    }
    @Test fun downloadsReturnWaitsForForegroundAndNavigatesOnlyHiddenOnce() {
        ActivityScenario.launch(MainActivity::class.java).use {scenario ->scenario.onActivity {activity ->
            val state=authenticate(activity)
            try {
                enterHiddenBrowser(activity,state);val owner=activity.captureHiddenVpnPresentationAuthority()
                flag(activity,"activityResumed",false)
                activity.requestHiddenBrowserDownloads(owner)
                assertEquals(Route.BROWSER,state.hidden.destination)
                flag(activity,"activityResumed",true);invokeMain(activity,"applyPendingHiddenDownloads")
                assertTrue(state.hidden.selected);assertTrue(state.hidden.session.isUnlocked)
                assertEquals(Route.VAULT,state.hidden.destination)
                enterHiddenBrowser(activity,state);activity.requestHiddenBrowserDownloads(owner)
                assertEquals("A consumed child return cannot navigate a later browser",Route.BROWSER,state.hidden.destination)
            } finally {flag(activity,"activityResumed",true);state.hidden.leave();state.session.lock()}
        }}
    }
    @Test fun oldBrowserGenerationCannotNavigateCurrentHiddenBrowser() {
        ActivityScenario.launch(MainActivity::class.java).use {scenario ->scenario.onActivity {activity ->
            val state=authenticate(activity)
            try {
                enterHiddenBrowser(activity,state);val old=activity.captureHiddenVpnPresentationAuthority()
                activity.captureHiddenVpnPresentationAuthority()
                activity.requestHiddenBrowserDownloads(old)
                assertEquals(Route.BROWSER,state.hidden.destination)
            } finally {state.hidden.leave();state.session.lock()}
        }}
    }
    @Test fun pendingDownloadsReturnCannotCrossFreshHiddenAuthentication() {
        ActivityScenario.launch(MainActivity::class.java).use {scenario ->scenario.onActivity {activity ->
            val state=authenticate(activity)
            try {
                enterHiddenBrowser(activity,state);val old=activity.captureHiddenVpnPresentationAuthority()
                flag(activity,"activityResumed",false);activity.requestHiddenBrowserDownloads(old)
                state.hidden.session.lock();state.hidden.session.onForeground()
                assertTrue(state.hidden.session.complete(state.hidden.session.beginAuthentication(),ByteArray(32){61}))
                flag(activity,"activityResumed",true);invokeMain(activity,"applyPendingHiddenDownloads")
                assertEquals("Old authenticated child cannot navigate fresh Hidden auth",Route.BROWSER,state.hidden.destination)
                assertTrue(state.hidden.session.isUnlocked)
            } finally {flag(activity,"activityResumed",true);state.hidden.leave();state.session.lock()}
        }}
    }

}
