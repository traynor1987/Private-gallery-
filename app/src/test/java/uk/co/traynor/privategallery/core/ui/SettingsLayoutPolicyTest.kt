package uk.co.traynor.privategallery.core.ui

import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsLayoutPolicyTest {
    @Test fun `settings layout permits access beyond the first viewport`() {
        assertTrue(SettingsLayoutPolicy.isVerticallyScrollable)
    }
}
