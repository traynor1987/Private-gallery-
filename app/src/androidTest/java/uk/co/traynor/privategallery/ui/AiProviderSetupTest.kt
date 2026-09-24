package uk.co.traynor.privategallery.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test

class AiProviderSetupTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun ownerCanOpenProviderSetupWithoutAConfiguredCredential() {
        compose.setContent { PrivateGalleryTheme { AiEditingSettings() } }
        compose.onNodeWithText("Set up provider").performClick()
        compose.onNodeWithText("Replicate · Seedream 4.5").assertIsDisplayed()
        compose.onNodeWithText("API token").assertIsDisplayed()
        compose.onNodeWithText("Test connection").assertIsNotEnabled()
    }
}
