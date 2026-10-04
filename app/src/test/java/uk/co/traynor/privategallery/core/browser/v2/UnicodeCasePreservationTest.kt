package uk.co.traynor.privategallery.core.browser.v2
import org.junit.Assert.*
import org.junit.Test
class UnicodeCasePreservationTest {
 @Test fun dotlessIProtectionMarkerRetainsLegacyDenial() = assertTrue(protected("<ContentProtectıon/>"))
 @Test fun kelvinKeyMarkerRetainsLegacyDenial() = assertTrue(protected("#EXT-X-KEY:METHOD=AES-128"))
 @Test fun longSSessionKeyRetainsLegacyDenial() = assertTrue(protected("#EXT-X-ſESSION-KEY:METHOD=NONE"))
 @Test fun multibyteKeyMarkerUsesActualEndpointForSoleNone() = assertFalse(protected("#EXT-X-KEY:METHOD=NONE"))
 private fun protected(text: String): Boolean { val bytes=text.toByteArray();return ManifestProtectionPolicy.isProtected(bytes,bytes.size) }
}
