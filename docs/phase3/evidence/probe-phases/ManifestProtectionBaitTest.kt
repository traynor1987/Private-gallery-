package uk.co.traynor.privategallery.core.browser.v2

import org.junit.Assert.*
import org.junit.Test

/** Actual current classifier regression; literal synthetic manifests only. */
class ManifestProtectionBaitTest {
    @Test fun noneBaitInsideQuotedKeyUriDoesNotHideActualEncryptionMethod() {
        assertTrue(ManifestProtectionPolicy.isProtected("#EXTM3U\n#EXT-X-KEY:METHOD=AES-128,URI=\"https://invalid.example/key?x=METHOD=NONE,foo\"\n"))
    }
    @Test fun conflictingNoneMethodDoesNotHideActualEncryptionMethod() {
        assertTrue(ManifestProtectionPolicy.isProtected("#EXTM3U\n#EXT-X-KEY:METHOD=AES-128,METHOD=NONE\n"))
    }
    @Test fun unrelatedAttributeSuffixDoesNotStandInForRequiredMethod() {
        assertTrue(ManifestProtectionPolicy.isProtected("#EXTM3U\n#EXT-X-KEY:X-METHOD=NONE\n"))
    }
    @Test fun standaloneNoneRemainsEligibleAndSessionKeyRemainsProtected() {
        assertFalse(ManifestProtectionPolicy.isProtected("#EXTM3U\n#EXT-X-KEY:METHOD=NONE\n"))
        assertTrue(ManifestProtectionPolicy.isProtected("#EXTM3U\n#EXT-X-SESSION-KEY:METHOD=NONE\n"))
    }
}
