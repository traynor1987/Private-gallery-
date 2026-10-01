package uk.co.traynor.privategallery.core.browser.v2

import java.net.HttpURLConnection
import java.net.URL
import org.junit.Assert.*
import org.junit.Test
import uk.co.traynor.privategallery.core.security.*

class Phase1ProbeOwnershipTest {
    @Test fun delayedHeadCannotIssueFallbackGetAfterRevoke() {
        val authority = PrimarySessionAuthority { 0 }
        authority.open(ByteArray(32))
        val operation = checkNotNull(authority.operationOrNull(setOf(PrimaryScope.BROWSER_UPLOAD_EGRESS)))
        val guard = ScopedIoGuard(operation, PrimaryScope.BROWSER_UPLOAD_EGRESS)
        var opened = 0
        var disconnected = false
        val candidate = BrowserMediaProbe.inspect("https://example.invalid/media", "synthetic", "https://example.invalid/", guard,
            connectionFactory = { uri ->
                opened++
                object : HttpURLConnection(URL(uri.toString())) {
                    override fun connect() = Unit
                    override fun usingProxy() = false
                    override fun disconnect() { disconnected = true }
                    override fun getResponseCode(): Int { authority.revoke(); return 405 }
                }
            }, cancelled = { false })
        assertEquals(1, opened)
        assertTrue(disconnected)
        assertEquals(MediaSaveKind.UNSUPPORTED, candidate.kind)
        authority.open(ByteArray(32))
        assertThrows(IllegalStateException::class.java) {
            BrowserMediaProbe.inspect("https://example.invalid/media", "synthetic", "", guard,
                connectionFactory = { opened++; error("stale connection") }, cancelled = { false })
        }
        assertEquals(1, opened)
        authority.revoke()
    }
}
