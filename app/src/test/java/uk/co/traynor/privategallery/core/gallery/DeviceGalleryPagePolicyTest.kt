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

    @Test fun `album selection is bound as a query argument`() {
        val maliciousId = "x' OR 1=1 --"
        val (selection, args) = DeviceAlbumQuery.selection(maliciousId)
        assertFalse(selection.contains(maliciousId))
        assertTrue(selection.contains("bucket_id=?"))
        assertEquals(maliciousId, args.last())
        assertEquals(3, args.size)
        assertEquals(2, DeviceAlbumQuery.selection(null).second.size)
    }
}
