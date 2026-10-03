package uk.co.traynor.privategallery.core.browser.v2

import java.net.HttpURLConnection
import java.net.URL
import java.net.URI
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import org.junit.Assert.*
import org.junit.Test
import uk.co.traynor.privategallery.core.security.*

/** Synthetic connection factories only; no external network or owner data. */
class Phase3ProbeOwnershipTest {
    @Test fun originalRevocationDuringDisconnectDeniesCompletedProbeResult() = isolated { authority, guard ->
        val native = object : FakeConnection(200) {
            override fun disconnect() { super.disconnect(); authority.revoke() }
        }
        val result = BrowserMediaProbe.inspect("https://invalid.example/media.mp4", "synthetic", "https://invalid.example/", guard,
            connectionFactory = { native }, cancelled = { false })
        assertEquals(MediaSaveKind.UNSUPPORTED, result.kind); assertEquals(1, native.disconnected.get())
        assertTrue(authority.cleanupComplete)
    }

    @Test fun normalManifestInputCloseDispatchesItsTransportUnblocker() = isolated { _, guard ->
        val disconnected = CountDownLatch(1); val finished = CountDownLatch(1); val failure = AtomicReference<Throwable>()
        val native = object : FakeConnection(200) {
            override fun disconnect() { super.disconnect(); disconnected.countDown() }
            override fun getInputStream() = object : java.io.ByteArrayInputStream("#EXTM3U\n#EXT-X-ENDLIST\n".toByteArray()) {
                override fun close() { assertTrue("native transport must be requested before input wait", disconnected.await(5, TimeUnit.SECONDS)) }
            }
        }
        val worker = thread(isDaemon = true) {
            try { assertFalse(BrowserMediaProbe.protectedManifest(URI("https://invalid.example/list.m3u8"), "synthetic", null, guard, { native })) }
            catch (problem: Throwable) { failure.set(problem) } finally { finished.countDown() }
        }
        try {
            assertTrue(finished.await(10, TimeUnit.SECONDS)); failure.get()?.let { throw AssertionError("synthetic manifest failed", it) }
            assertEquals(1, native.disconnected.get())
        } finally { disconnected.countDown(); worker.interrupt(); assertTrue(finished.await(5, TimeUnit.SECONDS)) }
    }

    @Test fun failedDisconnectReturnsNeutralUnavailableAndRetainsFailedObligation() = isolated { authority, guard ->
        val native = object : FakeConnection(200) { override fun disconnect(): Unit = throw java.io.IOException("synthetic disconnect failure") }
        val result = BrowserMediaProbe.inspect("https://invalid.example/media.mp4", "synthetic", "https://invalid.example/", guard,
            connectionFactory = { native }, cancelled = { false })
        assertEquals(MediaSaveKind.UNSUPPORTED, result.kind)
        authority.revoke(); assertFalse(authority.cleanupComplete)
        val key = ByteArray(32) { 9 }
        assertThrows(IllegalStateException::class.java) { authority.open(key) }
        assertArrayEquals(ByteArray(32), key)
    }

    @Test fun exhaustedProbeAdmissionDoesNotCreateTransport() = isolated { authority, guard ->
        val owners = (1..16).map { guard.connection { FakeConnection(200) } }
        var calls = 0
        val result = BrowserMediaProbe.inspect("https://invalid.example/media.mp4", "synthetic", "https://invalid.example/", guard,
            connectionFactory = { calls++; FakeConnection(200) }, cancelled = { false })
        assertEquals(0, calls); assertEquals(MediaSaveKind.UNSUPPORTED, result.kind)
        owners.forEach { it.close() }; authority.revoke(); assertTrue(authority.cleanupComplete)
    }

    @Test fun headFallbackRetiresEachOriginalTransportExactlyOnce() = isolated { _, guard ->
        val head = FakeConnection(405); val get = FakeConnection(200); var calls = 0
        val result = BrowserMediaProbe.inspect("https://invalid.example/media.mp4", "synthetic", "https://invalid.example/", guard,
            connectionFactory = { if (calls++ == 0) head else get }, cancelled = { false })
        assertEquals(MediaSaveKind.DIRECT, result.kind); assertEquals(2, calls)
        assertEquals(1, head.disconnected.get()); assertEquals(1, get.disconnected.get())
    }

    @Test fun originalRevocationDuringHeadersRetiresConnectionAndDeniesResult() = isolated { authority, guard ->
        val native = object : FakeConnection(200) {
            override fun getResponseCode(): Int { authority.revoke(); return super.getResponseCode() }
        }
        val result = BrowserMediaProbe.inspect("https://invalid.example/media.mp4", "synthetic", "https://invalid.example/", guard,
            connectionFactory = { native }, cancelled = { false })
        assertEquals(MediaSaveKind.UNSUPPORTED, result.kind); assertEquals(1, native.disconnected.get())
        assertTrue(authority.cleanupComplete)
    }

    private open class FakeConnection(private val code: Int) : HttpURLConnection(URL("https://invalid.example/media.mp4")) {
        val disconnected = AtomicInteger()
        override fun disconnect() { disconnected.incrementAndGet() }
        override fun getResponseCode() = code
        override fun getContentType() = "video/mp4"
        override fun connect() = Unit
        override fun usingProxy() = false
    }

    private fun isolated(test: (PrimarySessionAuthority, ScopedIoGuard) -> Unit) {
        val authority = PrimarySessionAuthority { 0 }
        for (field in listOf("ioReleasePool", "presentationReleasePool")) {
            PrimarySessionAuthority::class.java.getDeclaredField(field).apply { isAccessible = true }.set(authority, ReleasePool(16))
        }
        authority.open(ByteArray(32) { 11 })
        val op = authority.operationOrNull(PrimaryScope.entries.toSet())!!
        try { test(authority, ScopedIoGuard(op, PrimaryScope.BROWSER_UPLOAD_EGRESS)) }
        finally { op.close(); authority.revoke() }
    }
}
