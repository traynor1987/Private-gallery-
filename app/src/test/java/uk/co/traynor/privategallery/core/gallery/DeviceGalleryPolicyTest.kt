package uk.co.traynor.privategallery.core.gallery

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceGalleryPolicyTest {
    @Test fun `any granted media category permits browsing`() {
        assertTrue(DeviceGalleryPolicy.canBrowse(imagesGranted = true, videosGranted = false, selectedGranted = false))
        assertTrue(DeviceGalleryPolicy.canBrowse(imagesGranted = false, videosGranted = false, selectedGranted = true))
    }

    @Test fun `no media grant keeps browsing unavailable`() {
        assertFalse(DeviceGalleryPolicy.canBrowse(imagesGranted = false, videosGranted = false, selectedGranted = false))
    }
}
