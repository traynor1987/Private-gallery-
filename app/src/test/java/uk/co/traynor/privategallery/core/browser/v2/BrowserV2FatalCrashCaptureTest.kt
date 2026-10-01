package uk.co.traynor.privategallery.core.browser.v2

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import java.io.File
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
        assertFalse(report.contains("Active tab count:"))
        assertTrue(report.contains("Real WebView parented: true"))
        assertTrue(report.contains("WebView parent category: AndroidViewHolder"))
        assertTrue(report.contains("Last Browser structural event: OMNIBOX_TEXT_CHANGED"))
        assertFalse(report.contains("private.example"))
        assertFalse(report.contains("private search phrase"))
        assertFalse(report.contains("secret"))
    }
    @Test fun arbitraryUnquotedInputsNeverEnterFatalReport() {
        val marker = "SENSITIVE_NAME_PROMPT_TOKEN_UNQUOTED"
        val failure = IllegalStateException(marker, RuntimeException(marker)).apply {
            stackTrace = arrayOf(
                StackTraceElement("uk.co.traynor.privategallery.$marker", marker, "$marker.kt", 9),
                StackTraceElement("uk.co.traynor.privategallery.MainActivity", marker, "$marker.kt", 9),
                StackTraceElement("androidx.compose.ui.text.input.TextFieldValue", "copy", "$marker.kt", 88),
            )
        }
        val report = BrowserV2FatalCrashCapture.formatReport(marker, failure, BrowserV2CrashContext(
            route = marker, stateCategory = marker, activeTabCount = 27,
            webViewParentCategory = marker, lastStructuralEvent = marker,
            routeBounds = marker, rootBounds = marker, chromeBounds = marker,
            contentHostBounds = marker, androidViewHostBounds = marker, webViewBounds = marker,
        ))
        assertFalse(report.contains(marker))
        assertFalse(report.contains("Thread:"))
        assertFalse(report.contains("Message["))
        assertFalse(report.contains("Active tab count:"))
        assertTrue(report.contains("Browser route: UNKNOWN"))
        assertTrue(report.contains("Exception[0]: java.lang.IllegalStateException"))
    }

    @Test fun customExceptionClassAndMalformedBoundsAreDiscarded() {
        class SENSITIVE_CUSTOM_ERROR : RuntimeException("unquoted-sensitive")
        val report = BrowserV2FatalCrashCapture.formatReport("main", SENSITIVE_CUSTOM_ERROR(), BrowserV2CrashContext(
            routeBounds = "x=3 y=4 width=500 height=600 prompt=unquoted-sensitive",
        ))
        assertFalse(report.contains("SENSITIVE_CUSTOM_ERROR"))
        assertFalse(report.contains("unquoted-sensitive"))
        assertTrue(report.contains("Exception[0]: OTHER"))
        assertTrue(report.contains("Browser route bounds: unmeasured"))
    }
    @Test fun obsoleteRawReportsAreDiscardedBeforeReadback() {
        val legacy = File.createTempFile("synthetic-legacy-report", ".txt")
        try {
            legacy.writeText("Private Gallery acceptance fatal report\nThread: SENSITIVE_UNQUOTED\nMessage[0]: SENSITIVE_UNQUOTED")
            assertNull(BrowserV2FatalCrashCapture.readReportFile(legacy))
            assertFalse(legacy.exists())
        } finally { legacy.delete() }
    }

    @Test fun fixedCategoryReportRemainsReadable() {
        val file = File.createTempFile("synthetic-fixed-report", ".txt")
        try {
            val report = BrowserV2FatalCrashCapture.formatReport("unquoted", IllegalStateException("unquoted"), BrowserV2CrashContext())
            file.writeText(report)
            assertTrue(BrowserV2FatalCrashCapture.readReportFile(file) == report)
        } finally { file.delete() }
    }
}
