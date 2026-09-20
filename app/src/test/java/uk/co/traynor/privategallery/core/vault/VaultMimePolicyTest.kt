package uk.co.traynor.privategallery.core.vault

import org.junit.Assert.assertEquals
import org.junit.Test

class VaultMimePolicyTest {
    @Test fun `generic browser image is classified from authenticated prefix`() {
        assertEquals("image/png", VaultMimePolicy.effectiveType("application/octet-stream", "download", byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)))
    }

    @Test fun `declared media type remains authoritative when safe`() {
        assertEquals("image/jpeg", VaultMimePolicy.effectiveType("image/jpeg", "opaque", byteArrayOf()))
    }

    @Test fun `unknown bytes do not masquerade as an image`() {
        assertEquals("application/octet-stream", VaultMimePolicy.effectiveType("application/octet-stream", "opaque", byteArrayOf(1, 2, 3)))
    }
}
