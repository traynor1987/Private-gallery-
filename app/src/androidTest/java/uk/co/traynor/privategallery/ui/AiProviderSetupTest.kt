package uk.co.traynor.privategallery.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.*
import android.view.WindowManager
import androidx.lifecycle.Lifecycle
import kotlinx.coroutines.*
import uk.co.traynor.privategallery.core.editor.*
import org.junit.Rule
import org.junit.Test

class AiProviderSetupTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun ownerCanOpenProviderSetupWithoutAConfiguredCredential() {
        compose.setContent { PrivateGalleryTheme { AiEditingSettings() } }
        compose.onNodeWithText("Set up provider").performClick()
        compose.onNodeWithText("Replicate · Seedream 4.5").assertIsDisplayed()
        compose.runOnIdle {
            assertTrue(android.view.inspector.WindowInspector.getGlobalWindowViews().any {
                val params = it.layoutParams as? WindowManager.LayoutParams
                params != null && (params.flags and WindowManager.LayoutParams.FLAG_SECURE) != 0
            })
        }
        compose.onNodeWithText("API token").assertIsDisplayed()
        compose.onNodeWithText("Test connection").assertIsNotEnabled()
    }
    private class MemoryCredentials : AiCredentials {
        var stored: ByteArray? = null
        override fun isConfigured() = stored != null
        override fun read() = stored?.copyOf()
        override fun save(credential: ByteArray) { stored = credential.copyOf() }
        override fun clear() { stored?.fill(0); stored = null }
    }
    private fun configuration(store: AiCredentials, check: suspend (ByteArray) -> Unit = {}): AiProviderConfiguration =
        AiProviderConfiguration(store,check) { read -> ReplicateSeedreamProvider(read,ReplicateSeedreamApi(AiHttpTransport { error("No live API in CI") }),{ it.copyOf() }) }

    @Test fun verifiedTokenIsNeverPrefilledAndRemovalDisablesProvider() {
        val store = MemoryCredentials(); val config = configuration(store)
        compose.setContent { PrivateGalleryTheme { AiEditingSettings(config) } }
        compose.onNodeWithText("Set up provider").performClick()
        compose.onNodeWithText("API token").performTextInput("synthetic-token")
        compose.onNodeWithText("Test connection").performScrollTo().performClick()
        compose.waitUntil(5000) { config.status.value == AiConnectionStatus.CONNECTED }
        compose.onNodeWithText("Connected", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("API token").assertTextEquals("API token", "")
        assertEquals("synthetic-token",store.stored!!.toString(Charsets.UTF_8))
        compose.onNodeWithText("Done").performScrollTo().performClick()
        compose.onNodeWithText("Manage provider").performClick()
        compose.onNodeWithText("API token").assertTextEquals("API token", "")
        compose.onNodeWithText("Remove configuration").performScrollTo().performClick()
        compose.onNodeWithText("Confirm removal").performScrollTo().performClick()
        compose.waitUntil(5000) { config.status.value == AiConnectionStatus.NOT_CONFIGURED }
        assertNull(store.stored); assertNull(config.provider)
    }
    @Test fun failedTokenIsNotSavedAndRetryRemainsAvailable() {
        val store = MemoryCredentials(); val config = configuration(store) { throw AiEditFailure("Token not accepted.") }
        compose.setContent { PrivateGalleryTheme { AiEditingSettings(config) } }
        compose.onNodeWithText("Set up provider").performClick()
        compose.onNodeWithText("API token").performTextInput("synthetic-token")
        compose.onNodeWithText("Test connection").performScrollTo().performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithText("Token not accepted.").fetchSemanticsNodes().isNotEmpty() }
        assertNull(store.stored); assertNull(config.provider)
        compose.onNodeWithText("API token").assertTextEquals("API token", "")
        compose.onNodeWithText("Test connection").assertIsNotEnabled()
    }
    @Test fun backgroundingCancelsVerificationAndDiscardsEntry() {
        val store = MemoryCredentials(); val started = CompletableDeferred<Unit>()
        val config = configuration(store) { started.complete(Unit); awaitCancellation() }
        compose.setContent { PrivateGalleryTheme { AiEditingSettings(config) } }
        compose.onNodeWithText("Set up provider").performClick()
        compose.onNodeWithText("API token").performTextInput("synthetic-token")
        compose.onNodeWithText("Test connection").performScrollTo().performClick()
        compose.waitUntil(5000) { started.isCompleted }
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.onNodeWithText("Set up provider").performClick()
        compose.onNodeWithText("API token").assertTextEquals("API token", "")
        assertNull(store.stored); assertNull(config.provider)
    }
}
