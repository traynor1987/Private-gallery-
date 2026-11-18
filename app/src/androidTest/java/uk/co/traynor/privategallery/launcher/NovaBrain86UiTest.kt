package uk.co.traynor.privategallery.launcher

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*
import androidx.test.platform.app.InstrumentationRegistry
import uk.co.traynor.privategallery.core.brain.NovaBrainConnectionStore

class NovaBrain86UiTest {
    @get:Rule val compose=createComposeRule()
    @Test fun connectionSetupExplainsPrivacyAndRequiresHttpsAndBackendTokenBeforeSaving() {
        compose.setContent { MaterialTheme { NovaBrainConnectionSettings() } }
        compose.onNodeWithText("Nova Brain connection").assertIsDisplayed()
        compose.onNodeWithText("Connect").performClick()
        compose.onNodeWithText("Use your existing HTTPS backend and its owner token. Saving does not send any data or activate analysis.").assertIsDisplayed()
        compose.onNodeWithText("Save securely").assertIsNotEnabled()
        compose.onNodeWithText("HTTPS server address").performTextInput("http://synthetic.example")
        compose.onNodeWithText("Backend owner token").performTextInput("synthetic-owner-token-".repeat(3))
        compose.onNodeWithText("Save securely").assertIsNotEnabled()
        compose.onNodeWithText("HTTPS server address").performTextReplacement("https://synthetic.example")
        compose.onNodeWithText("Save securely").assertIsEnabled()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Check connection").assertDoesNotExist()
    }
    @Test fun savedAndRemovedConfigurationNotifiesOtherReviewSurfacesWithoutSendingAnything() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        assertTrue(context.packageName.endsWith(".hiddenprototype"))
        val store=NovaBrainConnectionStore(context)
        val before=store.read()
        var changes=0
        try {
            store.clear()
            compose.setContent { MaterialTheme { NovaBrainConnectionSettings(onConfigurationChanged={changes++}) } }
            compose.onNodeWithText("Connect").performClick()
            compose.onNodeWithText("HTTPS server address").performTextInput("https://synthetic.example")
            compose.onNodeWithText("Backend owner token").performTextInput("synthetic-owner-token-".repeat(3))
            compose.onNodeWithText("Save securely").performClick()
            compose.waitUntil(10_000) { changes==1 }
            assertEquals("https://synthetic.example",store.read()?.origin)
            compose.onNodeWithText("Configure").performClick()
            compose.onNodeWithText("HTTPS server address").performTextReplacement("https://discarded.example")
            compose.onNodeWithText("Cancel").performClick()
            compose.onNodeWithText("Server: https://synthetic.example").assertIsDisplayed()
            compose.onNodeWithText("Server: https://discarded.example").assertDoesNotExist()
            assertEquals("https://synthetic.example",store.read()?.origin)
            compose.onNodeWithText("Configure").performClick()
            compose.onNodeWithText("Disconnect").performClick()
            compose.waitUntil(10_000) { changes==2 }
            assertNull(store.read())
            compose.onNodeWithText("Check connection").assertDoesNotExist()
        } finally { if(before==null)store.clear() else store.save(before) }
    }

}
