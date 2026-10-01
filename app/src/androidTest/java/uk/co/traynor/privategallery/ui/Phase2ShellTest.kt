package uk.co.traynor.privategallery.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.traynor.privategallery.core.domain.*

@RunWith(AndroidJUnit4::class)
class Phase2ShellTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
  private fun actions(exit: () -> Unit) = PrivateSpaceActions(exit, {}, {}, {}, {}, {}, {}, {}, { null }, {}, {}, { _, _ -> }, {}, {}, {})
  @Test fun closedRouteExposesNoShellAndExitClearsAuthenticatedShell() {
    val state = mutableStateOf(SecondaryUiState())
    compose.setContent { PrivateGalleryTheme { PrivateSpaceFlow(state.value, actions { state.value = SecondaryUiState() }) } }
    compose.onNodeWithText("Private space").assertDoesNotExist()
    compose.runOnIdle { state.value = SecondaryUiState(SecondaryRoute.READY) }
    compose.onNodeWithText("Private space").assertExists()
    compose.onNodeWithText("Move to Hidden").assertDoesNotExist()
    compose.onNodeWithText("Add from Primary").assertDoesNotExist()
    compose.onNodeWithText("Lock and return").performClick()
    compose.onNodeWithText("Private space").assertDoesNotExist()
  }
  @Test fun discoveryRouteIsAuthenticationOnlyAndDoesNotExposeReadyState() {
    compose.setContent { PrivateGalleryTheme { PrivateSpaceFlow(SecondaryUiState(SecondaryRoute.PIN), actions {}) } }
    compose.onNodeWithText("Authenticate").assertExists()
    compose.onNodeWithText("Private space").assertDoesNotExist()
    compose.onNodeWithText("Change PIN").assertDoesNotExist()
    compose.onNodeWithText("Recovery key confirmed").assertDoesNotExist()
  }
}
