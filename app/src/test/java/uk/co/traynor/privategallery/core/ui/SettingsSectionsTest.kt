package uk.co.traynor.privategallery.core.ui

import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsSectionsTest {
    @Test fun `active settings content includes debug`() = assertTrue(SettingsSections.DEBUG in SettingsSections.production)
    @Test fun `active settings content includes updates`() = assertTrue(SettingsSections.UPDATES in SettingsSections.production)
}
