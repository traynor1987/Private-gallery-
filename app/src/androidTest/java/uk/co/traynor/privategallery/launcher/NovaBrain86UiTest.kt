package uk.co.traynor.privategallery.launcher

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Rule
import org.junit.Test

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
}
