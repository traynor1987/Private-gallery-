package uk.co.traynor.privategallery.core.gallery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceGalleryPagePolicyTest {
    @Test fun `gallery begins with a bounded first page`() {
        assertEquals(0, DeviceGalleryPagePolicy.offsetForPage(0))
        assertEquals(120, DeviceGalleryPagePolicy.PAGE_SIZE)
    }

    @Test fun `next page is requested only when the current page was full`() {
        assertTrue(DeviceGalleryPagePolicy.hasNextPage(120))
        assertFalse(DeviceGalleryPagePolicy.hasNextPage(119))
        assertEquals(360, DeviceGalleryPagePolicy.offsetForPage(3))
    }
}
