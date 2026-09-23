package uk.co.traynor.privategallery.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.traynor.privategallery.BuildConfig

@RunWith(AndroidJUnit4::class)
class BrowserDiagnosticsSettingsTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun acceptanceControlsAreOptInAndIndependent() {
        val staticHost = mutableStateOf(false)
        val colours = mutableStateOf(false)
        compose.setContent {
            PrivateGalleryTheme {
                BrowserDiagnosticsControls(
                    staticContentHost = staticHost.value,
                    onStaticContentHostChanged = { staticHost.value = it },
                    layoutColours = colours.value,
                    onLayoutColoursChanged = { colours.value = it },
                )
            }
        }
        compose.onNodeWithText("REAL WebView").assertExists()
        compose.onNodeWithTag("browser-debug-static-host").assertIsOff().performClick().assertIsOn()
        compose.onNodeWithText("STATIC host").assertExists()
        compose.onNodeWithTag("browser-debug-layout-colours").assertIsOff().performClick().assertIsOn()
        compose.onNodeWithTag("browser-debug-static-host").performClick().assertIsOff()
        compose.onNodeWithTag("browser-debug-layout-colours").assertIsOn()
    }

    @Test fun productionSettingsOnlyExposeControlsInAcceptanceBuilds() {
        compose.setContent {
            PrivateGalleryTheme {
                BrowserDiagnosticsSettings(false, {}, false, {})
            }
        }
        if (BuildConfig.ACCEPTANCE_BROWSER_DIAGNOSTICS) {
            compose.onNodeWithTag("browser-debug-static-host").assertIsOff()
            compose.onNodeWithTag("browser-debug-layout-colours").assertIsOff()
        } else {
            compose.onNodeWithText("Browser diagnostics").assertDoesNotExist()
            compose.onNodeWithTag("browser-debug-static-host").assertDoesNotExist()
            compose.onNodeWithTag("browser-debug-layout-colours").assertDoesNotExist()
        }
    }

}
