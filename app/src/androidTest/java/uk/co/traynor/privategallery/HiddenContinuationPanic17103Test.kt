package uk.co.traynor.privategallery

import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import org.junit.Assert.*
import org.junit.Test
import uk.co.traynor.privategallery.core.browser.hidden.HiddenBrowserRuntime
import uk.co.traynor.privategallery.core.browser.hidden.HiddenConnectionPolicy

/** Real Main ownership checks; no browser transport, provider or live account. */
class HiddenContinuationPanic17103Test {
    private fun route(main:MainActivity,value:Route) {
        MainActivity::class.java.getDeclaredMethod("setRoute",Route::class.java).apply {isAccessible=true}.invoke(main,value)
    }
    @Test fun primaryExpiryDoesNotDisarmCurrentHiddenPanicAndStaleRuntimeIsDenied() {
        ActivityScenario.launch(MainActivity::class.java).use {scenario ->
            scenario.onActivity {main ->
                val state=ViewModelProvider(main)[ProtectedSessionState::class.java]
                fun authenticate():HiddenBrowserRuntime {
                    state.session.unlock()
                    state.hidden.session.onForeground()
                    assertTrue(state.hidden.session.complete(state.hidden.session.beginAuthentication(),ByteArray(32){79}))
                    state.hidden.select(Route.BROWSER);state.hidden.authenticated=true;route(main,Route.BROWSER)
                    return HiddenBrowserRuntime(main,state.hidden.session,HiddenConnectionPolicy.Mode.VPN_ONLY).also {main.hiddenBrowserOwner=it}
                }
                var runtime=authenticate()
                try {
                    val owner=main.captureHiddenVpnPresentationAuthority()
                    state.session.lock()
                    assertNotEquals(owner.primaryEpoch,state.session.captureAuthenticationEpoch())
                    assertTrue(state.hidden.session.isAuthenticationCurrent(owner.hidden))
                    main.hiddenBrowserPanic(owner,runtime)
                    assertFalse("Primary expiry cannot disarm current Hidden Panic",state.hidden.session.isUnlocked)
                    assertFalse(state.session.isUnlocked)
                    runtime=authenticate()
                    val stale=main.captureHiddenVpnPresentationAuthority()
                    val replacement=HiddenBrowserRuntime(main,state.hidden.session,HiddenConnectionPolicy.Mode.VPN_ONLY)
                    main.hiddenBrowserOwner=replacement
                    main.hiddenBrowserPanic(stale,runtime)
                    assertTrue("Retired runtime cannot Panic a replacement presentation",state.hidden.session.isUnlocked)
                    assertTrue(state.session.isUnlocked)
                    replacement.close()
                }finally {runtime.close();state.hidden.leave();state.session.lock();route(main,Route.LOCK)}
            }
        }
    }
}
