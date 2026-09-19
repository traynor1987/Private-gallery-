package uk.co.traynor.privategallery.core.ui

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class DominantContentCropPolicyTest {
    @Test fun `central textured media framed by quiet UI is accepted`() {
        assertNotNull(DominantContentCropPolicy.confidence(.68f, 2, .82f, 72f, 8f))
    }

    @Test fun `ambiguous rectangles without sufficient boundaries are rejected`() {
        assertNull(DominantContentCropPolicy.confidence(.68f, 1, .9f, 70f, 8f))
    }

    @Test fun `normal image-like texture on both sides is rejected`() {
        assertNull(DominantContentCropPolicy.confidence(.68f, 2, .50f, 44f, 41f))
    }
}
