package uk.co.traynor.privategallery.novaos2

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import uk.co.traynor.privategallery.ui.BrowserViewportRule

class NovaLockScreenStudioUiTest {
    private val compose = createAndroidComposeRule<ComponentActivity>()
    @get:Rule val rule = RuleChain.outerRule(BrowserViewportRule(fontScale = 1.15f) {
        if (it.startsWith("inner")) 820 to 920 else 375 to 850
    }).around(compose)

    @Test fun coverSelectsAndAppliesOnlyAnExplicitWallpaperChoice() {
        var applied: NovaLockWallpaper? = null
        compose.setContent {
            var style by remember { mutableStateOf(NovaLockStyle()) }
            var pending by remember { mutableStateOf<NovaLockWallpaper?>(null) }
            MaterialTheme {
                Surface(Modifier.fillMaxSize().safeDrawingPadding()) {
                    NovaLockStudioLayout(false, { NovaLockPreview(style, "17:45", 64) }, {
                        NovaLockControls(style, true, { style = it }, onSave = {}, onApply = { pending = style.wallpaper })
                    })
                }
                pending?.let { wallpaper ->
                    NovaLockWallpaperConfirmation(wallpaper, onDismiss = { pending = null }, onConfirm = {
                        applied = wallpaper
                        pending = null
                    })
                }
            }
        }
        compose.onNodeWithTag("nova-lock-preview").assertIsDisplayed()
        compose.onNodeWithText("Battery 64%").assertIsDisplayed()
        compose.onNodeWithText("Ember Dusk").performScrollTo().performClick()
        compose.runOnIdle { assertNull(applied) }
        compose.onNodeWithText("Apply wallpaper").performScrollTo().performClick()
        compose.onNodeWithText("Apply Ember Dusk?").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle { assertNull(applied) }
        compose.onNodeWithTag("nova-lock-preview").performScrollTo().assertIsDisplayed()
        captureNovaSystem("lock-cover")
        compose.onNodeWithText("Apply wallpaper").performScrollTo().performClick()
        compose.onNodeWithText("Apply lock wallpaper").performClick()
        compose.runOnIdle { assertEquals(NovaLockWallpaper.EMBER_DUSK, applied) }
    }

    @Test fun innerAmbientPreviewSuppressesEvenRedactedNotificationExample() {
        compose.setContent {
            val style = NovaLockStyle(ambientPreview = true, showNotificationExample = true)
            MaterialTheme {
                Surface(Modifier.fillMaxSize().safeDrawingPadding()) {
                    NovaLockStudioLayout(true, { NovaLockPreview(style, "17:45", null) }, {
                        NovaLockControls(style, true, onChange = {}, onSave = {}, onApply = {})
                    })
                }
            }
        }
        compose.onNodeWithText("Ambient preview").assertIsDisplayed()
        compose.onNodeWithText("17:45").assertIsDisplayed()
        compose.onNodeWithText("Battery unavailable").assertIsDisplayed()
        compose.onNodeWithText("Notification hidden").assertDoesNotExist()
        captureNovaSystem("lock-inner")
    }
}
