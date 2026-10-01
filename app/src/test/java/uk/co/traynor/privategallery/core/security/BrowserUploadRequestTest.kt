package uk.co.traynor.privategallery.core.security

import org.junit.Assert.*
import org.junit.Test

class BrowserUploadRequestTest {
    @Test fun `delayed chooser A cannot satisfy replacement chooser B or changed origin`() {
        val authority = PrimarySessionAuthority { 0 }
        authority.open(ByteArray(32))
        val operation = checkNotNull(authority.operationOrNull(uk.co.traynor.privategallery.core.security.PrimaryScope.entries.toSet()))
        val firstCallback = Any(); val secondCallback = Any()
        val first = BrowserUploadRequest(firstCallback, "https://original.example", listOf(operation.handle("original", "r")))
        assertTrue(first.matches(firstCallback, "https://original.example"))
        assertFalse(first.matches(secondCallback, "https://original.example"))
        assertFalse(first.matches(firstCallback, "https://other.example"))
        assertEquals("original", first.handles.single().itemId)
        assertThrows(IllegalStateException::class.java) {
            BrowserUploadRequest(firstCallback, "https://original.example", listOf(first.handles.single().copy(containerId = ContainerId.synthetic())))
        }
        authority.revoke()
    }
}
