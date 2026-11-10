package uk.co.traynor.privategallery

import android.content.Intent
import android.os.SystemClock
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import uk.co.traynor.privategallery.core.security.PinVaultKeyStore
import uk.co.traynor.privategallery.core.security.RecoveryVaultKeyStore

class LauncherWorkspace70Test {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun openWorkspaceIntentRevokesRetainedPrimaryAndHiddenAuthority() = verifyFreshHandoff(false)

    @Test fun primaryGalleryIntentRevokesRetainedPrimaryAndHiddenAuthority() = verifyFreshHandoff(true)

    @Test fun primaryGalleryHasNoExportedWorkspaceActivity() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val entry = privateWorkspaceEntryIntent(context, primaryGallery = true)
        val info = context.packageManager.getActivityInfo(requireNotNull(entry.component), 0)
        assertFalse(info.exported)
        assertNull(entry.data)
        assertNull(entry.extras)
    }

    @Test fun directPrimaryVaultHidesOnlyInternalNavigationUntilReturningHome() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".hiddenprototype"))
        val keys = PinVaultKeyStore(context)
        val recovery = RecoveryVaultKeyStore(context)
        check(!keys.isConfigured && !recovery.isConfigured) { "Fixture requires an unused isolated vault" }
        val pin = "481625"
        try {
            val key = keys.create(pin.toCharArray())
            try {
                recovery.create(key).fill('\u0000')
            } finally {
                key.fill(0)
            }
            ActivityScenario.launch<MainActivity>(privateWorkspaceEntryIntent(context, primaryGallery = true)).use { scenario ->
                compose.onNodeWithTag("passcode-indicator").assertIsDisplayed()
                compose.onNodeWithTag("nova-app-header").assertDoesNotExist()
                pin.forEach { compose.onNodeWithText(it.toString()).performClick() }
                compose.onNodeWithText("Unlock").performClick()
                compose.waitUntil(15_000) {
                    compose.onAllNodesWithTag("nova-app-header").fetchSemanticsNodes().isNotEmpty()
                }
                compose.onNodeWithTag("nova-navigation").assertDoesNotExist()
                compose.onNodeWithContentDescription("Nova Home").assertIsDisplayed()
                compose.onNodeWithContentDescription("Lock Space").assertIsDisplayed()
                compose.onNodeWithContentDescription("Panic Exit").assertIsDisplayed()

                scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
                compose.onNodeWithTag("preview-home").assertIsDisplayed()
                compose.onNodeWithTag("app-vault").performClick()
                compose.onNodeWithTag("nova-navigation").assertIsDisplayed()
                compose.onNodeWithContentDescription("Lock Space").performClick()
                scenario.onActivity { activity ->
                    val state = ViewModelProvider(activity)[ProtectedSessionState::class.java]
                    assertFalse(state.session.isUnlocked)
                    assertFalse(state.hidden.session.isUnlocked)
                }
                compose.onNodeWithTag("nova-navigation").assertDoesNotExist()
            }
        } finally {
            recovery.clearFailedRestore()
            keys.clearFailedRestore()
        }
    }

    private fun verifyFreshHandoff(primaryGallery: Boolean) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val monitor = ActivityLifecycleMonitorRegistry.getInstance()
        val oldActivity = instrumentation.startActivitySync(
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ) as MainActivity
        val primaryKey = ByteArray(32) { 42 }
        lateinit var oldState: ProtectedSessionState
        var destination: MainActivity? = null
        try {
            instrumentation.runOnMainSync {
                oldState = ViewModelProvider(oldActivity)[ProtectedSessionState::class.java]
                oldState.key = primaryKey
                oldState.session.unlock()
                val hidden = oldState.hidden.session
                assertTrue(hidden.complete(hidden.beginAuthentication(), ByteArray(32) { 66 }))
                oldState.hidden.select(Route.VAULT)
                oldState.hidden.authenticated = true
                assertTrue(oldState.session.isUnlocked && hidden.isUnlocked)
            }

            // Use the same explicit Android task handoff as the launcher.
            context.startActivity(privateWorkspaceEntryIntent(context, primaryGallery))
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
                assertNull("A launcher navigation request must be consumed", foreground.intent.action)
                val state = ViewModelProvider(foreground)[ProtectedSessionState::class.java]
                assertFalse(state.session.isUnlocked)
                assertFalse(state.hidden.session.isUnlocked)
            }
        } finally {
            // Finish the exact instances. A single-activity scenario cannot track two tasks of the same class.
            instrumentation.runOnMainSync {
                destination?.takeIf { it !== oldActivity }?.finish()
                oldActivity.finish()
            }
        }
    }
}
