package uk.co.traynor.privategallery.home2

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class BackupSizeDisplayTest {
    @Test fun decimalUnitsCoverEmptySmallAndLargeArchives() {
        assertEquals("0 KB", backupSizeDisplay(0, Locale.UK))
        assertEquals("<1 KB", backupSizeDisplay(999, Locale.UK))
        assertEquals("1 KB", backupSizeDisplay(1_000, Locale.UK))
        assertEquals("1.5 KB", backupSizeDisplay(1_500, Locale.UK))
        assertEquals("1 MB", backupSizeDisplay(999_999, Locale.UK))
        assertEquals("12.3 MB", backupSizeDisplay(12_345_678, Locale.UK))
        assertEquals("1 GB", backupSizeDisplay(1_000_000_000, Locale.UK))
        assertEquals("3.8 GB", backupSizeDisplay(3_750_000_000, Locale.UK))
    }

    @Test fun decimalSeparatorFollowsDisplayLocale() {
        assertEquals("1,5 GB", backupSizeDisplay(1_500_000_000, Locale.GERMANY))
    }
}
