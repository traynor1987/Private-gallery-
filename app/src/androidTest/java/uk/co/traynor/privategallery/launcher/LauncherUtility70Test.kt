package uk.co.traynor.privategallery.launcher

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test
import uk.co.traynor.privategallery.core.ui.AppTheme
import uk.co.traynor.privategallery.ui.PrivateGalleryTheme

class LauncherUtility70Test {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun calculatorUsesOrdinaryLauncherWithoutDiscreetAction() {
        open(LauncherUtility.CALCULATOR)
        for (key in listOf("2", "+", "2", "=")) {
            compose.onNodeWithTag("calculator-key-$key").performScrollTo().performClick()
        }
        compose.onNodeWithTag("calculator-result").performScrollTo().assertTextEquals("4")
        compose.onNodeWithTag("calculator-key-=").assert(
            SemanticsMatcher.keyNotDefined(SemanticsActions.OnLongClick),
        )
    }

    @Test fun arcadeCanStartAGameWithoutPrivateHomeShortcuts() {
        open(LauncherUtility.ARCADE)
        compose.onNodeWithTag("shortcut-2048").assertDoesNotExist()
        compose.onNodeWithTag("play-2048").performScrollTo().performClick()
        compose.onNodeWithTag("tiles-board").assertIsDisplayed()
    }

    @Test fun storeUsesBundledCatalogueWithoutPrivateAppsOrInstallControls() {
        open(LauncherUtility.STORE)
        compose.onNodeWithTag("nova-store").assertIsDisplayed()
        compose.onNodeWithTag("store-my-apps").assertDoesNotExist()
        compose.onNodeWithContentDescription("Install custom website").assertDoesNotExist()
        compose.onNodeWithText("Discover Photopea", substring = true).performScrollTo().performClick()
        compose.onNodeWithText("Open NovaOS to install").assertIsDisplayed()
        compose.onNodeWithTag("install-app").assertDoesNotExist()
    }

    private fun open(utility: LauncherUtility) {
        compose.setContent {
            PrivateGalleryTheme(AppTheme.SYSTEM) {
                LauncherUtilityScreen(utility, onBack = {}, onOpenWorkspace = {})
            }
        }
    }
}
