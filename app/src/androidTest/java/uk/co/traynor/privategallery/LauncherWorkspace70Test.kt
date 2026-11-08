package uk.co.traynor.privategallery

import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

class LauncherWorkspace70Test {
    @Test fun openWorkspaceIntentRevokesRetainedPrimaryAndHiddenAuthority() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val primaryKey = ByteArray(32) { 42 }
            scenario.onActivity { activity ->
                val state = ViewModelProvider(activity)[ProtectedSessionState::class.java]
                state.key = primaryKey
                state.session.unlock()
                val hidden = state.hidden.session
                assertTrue(hidden.complete(hidden.beginAuthentication(), ByteArray(32) { 66 }))
                state.hidden.select(Route.VAULT)
                state.hidden.authenticated = true
                assertTrue(state.session.isUnlocked && hidden.isUnlocked)
                activity.startActivity(privateWorkspaceEntryIntent(activity))
            }
            instrumentation.waitForIdleSync()
            scenario.onActivity { activity ->
                val state = ViewModelProvider(activity)[ProtectedSessionState::class.java]
                assertFalse("Explicit workspace entry must revoke the retained Primary authority", state.session.isUnlocked)
                assertFalse("Launcher handoff must never reuse the Hidden PIN session", state.hidden.session.isUnlocked)
                assertFalse(state.hidden.selected)
                assertNull(state.key)
                assertTrue("The detached Primary key must be wiped", primaryKey.all { it == 0.toByte() })
                assertNotEquals(ACTION_OPEN_PRIVATE_WORKSPACE, activity.intent.action)
            }
        }
    }
}
