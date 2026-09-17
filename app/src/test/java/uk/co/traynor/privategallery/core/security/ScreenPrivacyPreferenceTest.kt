package uk.co.traynor.privategallery.core.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenPrivacyPreferenceTest {
    @Test fun `screenshots are blocked by default`() = assertTrue(ScreenPrivacyPreference.secureWindow(null))
    @Test fun `allowing screenshots clears only capture flag policy`() = assertFalse(ScreenPrivacyPreference.secureWindow("true"))
    @Test fun `invalid value remains private`() = assertTrue(ScreenPrivacyPreference.secureWindow("not-a-boolean"))
}
