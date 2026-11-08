package uk.co.traynor.privategallery

import android.os.SystemClock
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import org.junit.Assert.*
import org.junit.Test

class LauncherWorkspace70Test {
    @Test fun openWorkspaceIntentRevokesRetainedPrimaryAndHiddenAuthority() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val monitor = ActivityLifecycleMonitorRegistry.getInstance()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val primaryKey = ByteArray(32) { 42 }
            lateinit var oldActivity: MainActivity
            lateinit var oldState: ProtectedSessionState
            scenario.onActivity { activity ->
                oldActivity = activity
                oldState = ViewModelProvider(activity)[ProtectedSessionState::class.java]
                oldState.key = primaryKey
                oldState.session.unlock()
                val hidden = oldState.hidden.session
                assertTrue(hidden.complete(hidden.beginAuthentication(), ByteArray(32) { 66 }))
                oldState.hidden.select(Route.VAULT)
                oldState.hidden.authenticated = true
                assertTrue(oldState.session.isUnlocked && hidden.isUnlocked)
            }
            try {
                // Exercise the same explicit activity start used by the separate HOME task.
                instrumentation.targetContext.startActivity(privateWorkspaceEntryIntent(instrumentation.targetContext))
                var destination: MainActivity? = null
                val deadline = SystemClock.elapsedRealtime() + 10_000
                while (destination == null && SystemClock.elapsedRealtime() < deadline) {
                    instrumentation.runOnMainSync {
                        val foreground = monitor.getActivitiesInStage(Stage.RESUMED).filterIsInstance<MainActivity>()
                        destination = foreground.singleOrNull()?.takeIf {
                            it !== oldActivity || !oldState.session.isUnlocked
                        }
                    }
                    if (destination == null) SystemClock.sleep(50)
                }
                instrumentation.runOnMainSync {
                    assertFalse("Explicit workspace entry must revoke the retained Primary authority", oldState.session.isUnlocked)
                    assertFalse("Launcher handoff must never reuse the Hidden PIN session", oldState.hidden.session.isUnlocked)
                    assertFalse(oldState.hidden.selected)
                    assertNull(oldState.key)
                    assertTrue("The detached Primary key must be wiped", primaryKey.all { it == 0.toByte() })
                    val foreground = requireNotNull(destination) { "Private workspace never resumed after launcher entry" }
                    assertNotEquals(ACTION_OPEN_PRIVATE_WORKSPACE, foreground.intent.action)
                    val state = ViewModelProvider(foreground)[ProtectedSessionState::class.java]
                    assertFalse(state.session.isUnlocked)
                    assertFalse(state.hidden.session.isUnlocked)
                }
            } finally {
                // Android can keep both tasks. Finish any task this test opened before closing the scenario.
                instrumentation.runOnMainSync {
                    monitor.getActivitiesInStage(Stage.RESUMED).filterIsInstance<MainActivity>()
                        .filter { it !== oldActivity }.forEach { it.finish() }
                }
            }
        }
    }
}
