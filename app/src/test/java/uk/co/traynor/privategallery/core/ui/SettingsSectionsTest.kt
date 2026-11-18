package uk.co.traynor.privategallery.core.ui

import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsSectionsTest {
    @Test fun `ordinary settings excludes debug`() = assertTrue(SettingsSections.DEBUG !in SettingsSections.production)
    @Test fun `active settings content includes updates`() = assertTrue(SettingsSections.UPDATES in SettingsSections.production)
    @Test fun `active settings content includes browser controls`() = assertTrue(SettingsSections.BROWSER in SettingsSections.production)
    @Test fun `Nova Brain is an ordinary dedicated destination distinct from AI editing`() {
        assertTrue(SettingsSections.NOVA_BRAIN in SettingsSections.production)
        assertTrue(SettingsCategory.NOVA_BRAIN in SettingsCategory.ordinary)
        assertTrue(SettingsCategory.NOVA_BRAIN.title == "Nova Brain")
        assertTrue(SettingsCategory.NOVA_BRAIN != SettingsCategory.AI)
    }
}
