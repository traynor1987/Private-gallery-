package uk.co.traynor.privategallery.core.browser.v2

import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Test
import uk.co.traynor.privategallery.core.security.PrimaryScope
import uk.co.traynor.privategallery.core.security.ScopedIoGuard
import uk.co.traynor.privategallery.core.security.testPrimaryAuthority

class BrowserVideoDownloadOwnershipTest {
    @Test fun acceptedDirectVideoRetiresItsOriginalConnectionWhenTheInputCloses() {
        val authority = testPrimaryAuthority(); authority.open(ByteArray(32))
        val operation = checkNotNull(authority.operationOrNull(PrimaryScope.entries.toSet()))
        val disconnects = AtomicInteger()
        val connection = FakeConnection(disconnects)
        try {
            val source = videoVaultSource(
                MediaSaveCandidate("https://example.test/video.mp4", "video/mp4", MediaSaveKind.DIRECT),
                "Public", "https://example.test/page", { false }, {}, connectionFactory = { connection },
            )
            source.openScopedStream!!.invoke(ScopedIoGuard(operation, PrimaryScope.BROWSER_UPLOAD_EGRESS)).use { input ->
                while (input.read(ByteArray(32)) >= 0) Unit
            }
            assertEquals(1, disconnects.get())
        } finally { operation.close(); authority.revoke() }
    }

    private class FakeConnection(private val disconnects: AtomicInteger) : HttpURLConnection(URL("https://example.test/video.mp4")) {
        private val bytes = byteArrayOf(0, 0, 0, 16, 'f'.code.toByte(), 't'.code.toByte(), 'y'.code.toByte(), 'p'.code.toByte(),
            'i'.code.toByte(), 's'.code.toByte(), 'o'.code.toByte(), 'm'.code.toByte(), 0, 0, 0, 0)
        override fun connect() = Unit
        override fun usingProxy() = false
        override fun disconnect() { disconnects.incrementAndGet() }
        override fun getResponseCode() = HTTP_OK
        override fun getInputStream() = ByteArrayInputStream(bytes)
        override fun getContentLengthLong() = bytes.size.toLong()
    }
}
