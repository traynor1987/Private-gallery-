package uk.co.traynor.privategallery.core.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultAutoCropPolicyTest {
    @Test fun `confident black border is accepted`() = assertTrue(VaultAutoCropPolicy.isConfidentEdgeBand(4, 0, 12, 140))
    @Test fun `confident white border is accepted`() = assertTrue(VaultAutoCropPolicy.isConfidentEdgeBand(249, 240, 255, 90))
    @Test fun `uniform white screenshot content is not mistaken for a border`() = assertFalse(VaultAutoCropPolicy.isConfidentEdgeBand(248, 240, 255, 245))
    @Test fun `dark photograph edge is not mistaken for a border`() = assertFalse(VaultAutoCropPolicy.isConfidentEdgeBand(15, 4, 24, 18))
    @Test fun `high variance edge is rejected`() = assertFalse(VaultAutoCropPolicy.isConfidentEdgeBand(5, 0, 80, 140))
}
