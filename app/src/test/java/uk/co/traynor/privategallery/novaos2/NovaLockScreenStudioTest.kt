package uk.co.traynor.privategallery.novaos2

import java.time.LocalTime
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class NovaLockScreenStudioTest {
    @Test fun encryptedConfigurationPayloadRoundTripsEveryPreviewChoice() {
        val saved = NovaLockStyle(NovaLockWallpaper.EMBER_DUSK, NovaLockClock.EDITORIAL, NovaLockColour.MINT,
            showNotificationExample = true, showBattery = false, ambientPreview = true)
        assertEquals(saved, NovaLockStyle.decode(saved.encode()))
    }

    @Test fun invalidOrFutureStyleDoesNotSilentlyChangeWallpaper() {
        val encoded = NovaLockStyle().encode()
        assertThrows(IllegalArgumentException::class.java) {
            NovaLockStyle.decode(encoded.replace("INDIGO_HOUR", "UNKNOWN_WALLPAPER"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            NovaLockStyle.decode(encoded.replace("\"version\":1", "\"version\":2"))
        }
    }

    @Test fun timePreviewUsesAndroidTimeFormatChoice() {
        assertEquals("00:05", lockPreviewTime(LocalTime.of(0, 5), true, Locale.UK))
        assertEquals("12:05 AM", lockPreviewTime(LocalTime.of(0, 5), false, Locale.US))
        assertEquals("12:05 PM", lockPreviewTime(LocalTime.of(12, 5), false, Locale.US))
    }
}
