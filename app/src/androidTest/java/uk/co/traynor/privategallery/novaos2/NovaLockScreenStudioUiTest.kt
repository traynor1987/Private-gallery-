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
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
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
                    NovaLockStudioLayout(false, { NovaLockPreview(style, "17:45", 64, calendarDate = "Friday 27 November", musicActive = true) }, {
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
        compose.onNodeWithText("Calendar date").performScrollTo().performClick()
        compose.onNodeWithText("Friday 27 November").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Media status").performScrollTo().performClick()
        compose.onNodeWithText("Audio playing at 17:45").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Ember Dusk").performScrollTo().performClick()
        compose.runOnIdle { assertNull(applied) }
        compose.onNodeWithText("Apply wallpaper").performScrollTo().performClick()
        compose.onNodeWithText("Apply Ember Dusk?").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle { assertNull(applied) }
        compose.onNodeWithTag("nova-lock-preview").performScrollTo().assertIsDisplayed()
        captureStudio("lock-cover")
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
        captureStudio("lock-inner")
    }

    /** Require the clock glyphs and wallpaper in the same app-window image saved for review. */
    private fun captureStudio(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".hiddenprototype"))
        val directory = File(context.cacheDir, "novaos2-captures").apply { mkdirs() }
        val clock = compose.onNodeWithText("17:45").fetchSemanticsNode().boundsInRoot
        compose.waitUntil(timeoutMillis = 5_000) {
            val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
            try {
                var glyphPixels = 0
                var backgroundPixels = 0
                for (y in clock.top.toInt().coerceAtLeast(0) until clock.bottom.toInt().coerceAtMost(bitmap.height)) {
                    for (x in clock.left.toInt().coerceAtLeast(0) until clock.right.toInt().coerceAtMost(bitmap.width)) {
                        val red = android.graphics.Color.red(bitmap.getPixel(x, y))
                        if (red > 200) glyphPixels++
                        if (red < 140) backgroundPixels++
                    }
                }
                if (glyphPixels < 24 || backgroundPixels < 24) false else {
                    File(directory, "$name.png").outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
                    true
                }
            } finally { bitmap.recycle() }
        }
    }
}
