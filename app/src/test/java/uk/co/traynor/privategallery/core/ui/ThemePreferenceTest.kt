package uk.co.traynor.privategallery.core.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ThemePreferenceTest {
    @Test
    fun `decodes stored choices and falls back to system`() {
        assertEquals(AppTheme.SYSTEM, ThemePreference.decode(null))
        assertEquals(AppTheme.SYSTEM, ThemePreference.decode("unexpected"))
        assertEquals(AppTheme.LIGHT, ThemePreference.decode("light"))
        assertEquals(AppTheme.DARK, ThemePreference.decode("dark"))
    }

    @Test
    fun `encodes stable preference values`() {
        assertEquals("system", ThemePreference.encode(AppTheme.SYSTEM))
        assertEquals("light", ThemePreference.encode(AppTheme.LIGHT))
        assertEquals("dark", ThemePreference.encode(AppTheme.DARK))
    }
}
