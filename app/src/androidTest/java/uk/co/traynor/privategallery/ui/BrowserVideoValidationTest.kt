package uk.co.traynor.privategallery.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.traynor.privategallery.core.browser.v2.BrowserVideoUnavailableException
import uk.co.traynor.privategallery.core.browser.v2.MediaSaveReason
import uk.co.traynor.privategallery.core.browser.v2.VideoFileValidator
import java.io.File

@RunWith(AndroidJUnit4::class)
class BrowserVideoValidationTest {
    private fun fixture(name: String): File {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val file = File(instrumentation.targetContext.cacheDir, "fixture-$name")
        instrumentation.context.assets.open("browser-media/$name").use { input -> file.outputStream().use(input::copyTo) }
        return file
    }

    @Test fun validVideoWithAudioParsesButTruncationIsRejected() {
        val valid = fixture("valid.mp4")
        val truncated = fixture("truncated.mp4")
        try {
            val facts = VideoFileValidator.inspect(valid, "video/mp4", false)
            assertTrue(facts.durationMs > 0)
            assertTrue(facts.videoTracks > 0)
            assertTrue(facts.audioTracks > 0)
            val reason = runCatching { VideoFileValidator.inspect(truncated, "video/mp4", false) }
                .exceptionOrNull() as BrowserVideoUnavailableException
            assertEquals(MediaSaveReason.MEDIA_VALIDATION_FAILED, reason.reason)
        } finally { valid.delete(); truncated.delete() }
    }
}
