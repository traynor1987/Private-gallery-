package uk.co.traynor.privategallery.core.browser.v2

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserV2FatalCrashCaptureTest {
    @Test fun reportIncludesCrashFramesAndStructuralStateButRedactsAddressAndQuotedInput() {
        val failure = IllegalStateException(
            "Failed address=https://private.example/path?token=secret \"private search phrase\"",
        ).apply {
            stackTrace = arrayOf(
                StackTraceElement("uk.co.traynor.privategallery.MainActivity", "onValueChange", "MainActivity.kt", 317),
                StackTraceElement("androidx.compose.ui.text.input.TextFieldValue", "copy", "TextFieldValue.kt", 88),
            )
        }
        val state = BrowserV2CrashContext(
            route = "BROWSER",
            stateCategory = "OMNIBOX_TEXT_CHANGED",
            activeTabCount = 1,
            selectedTabExists = true,
            webViewAttached = true,
            webViewAttachedToWindow = true,
            webViewParentCategory = "AndroidViewHolder",
            attachCount = 1,
            androidViewUpdateCount = 4,
            lastStructuralEvent = "OMNIBOX_TEXT_CHANGED",
        )

        val report = BrowserV2FatalCrashCapture.formatReport("main", failure, state)

        assertTrue(report.contains("Exception[0]: java.lang.IllegalStateException"))
        assertTrue(report.contains("uk.co.traynor.privategallery.MainActivity.onValueChange(MainActivity.kt:317)"))
        assertTrue(report.contains("androidx.compose.ui.text.input.TextFieldValue.copy(TextFieldValue.kt:88)"))
        assertTrue(report.contains("Browser state category: OMNIBOX_TEXT_CHANGED"))
        assertTrue(report.contains("Active tab count: 1"))
        assertTrue(report.contains("Real WebView parented: true"))
        assertTrue(report.contains("WebView parent category: AndroidViewHolder"))
        assertTrue(report.contains("Last Browser structural event: OMNIBOX_TEXT_CHANGED"))
        assertFalse(report.contains("private.example"))
        assertFalse(report.contains("private search phrase"))
        assertFalse(report.contains("secret"))
    }
}
