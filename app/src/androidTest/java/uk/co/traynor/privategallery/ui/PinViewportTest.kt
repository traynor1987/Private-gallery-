package uk.co.traynor.privategallery.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import uk.co.traynor.privategallery.PinPage

/** A keyboard-sized viewport must not make setup, retry or cancellation unreachable. */
class PinViewportTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun hiddenSetupAndCancelRemainReachableInShortViewport() {
        var submitted = false
        var cancelled = false
        compose.setContent {
            MaterialTheme {
                Box(Modifier.width(320.dp).height(240.dp)) {
                    PinPage(
                        title = "Hidden Vault · TEST ONLY",
                        detail = "Choose an independent Hidden PIN.",
                        pin = "728491", onPinChange = {},
                        confirmation = "728491", onConfirmationChange = {},
                        pinLabel = "Hidden PIN", confirmationLabel = "Confirm Hidden PIN",
                        action = "Create Hidden", message = "Retry with a different PIN.",
                        secondaryAction = { TextButton(onClick = { cancelled = true }) { Text("Close") } },
                        onAction = { submitted = true },
                    )
                }
            }
        }
        compose.onNodeWithText("Hidden PIN").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Confirm Hidden PIN").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Create Hidden").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithText("Close").performScrollTo().assertIsDisplayed().performClick()
        compose.runOnIdle { assertTrue(submitted); assertTrue(cancelled) }
    }
}
