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

    @Test fun completeManifestCapacityDeniedBeforeNativeConnectionExists() = isolated { _, guard ->
        val holds = (1..14).map { guard.createOwned(OwnedResourceManifest.io("hold")) { attach("hold", AutoCloseable {}) } }
        var created = 0
        try {
            assertThrows(IllegalStateException::class.java) {
                BrowserMediaProbe.protectedManifest(URI("https://invalid.example/list.m3u8"), "synthetic", null, guard) {
                    created++
                    object : FakeConnection(200) { override fun getInputStream() = java.io.ByteArrayInputStream("#EXTM3U\n".toByteArray()) }
                }
            }
            assertEquals("complete transport/input/buffer reservation precedes native creation", 0, created)
        } finally { holds.forEach { guard.retire(it) } }
    }

    @Test fun actualManifestReadBufferWipedBeforeNormalResultReturns() = isolated { _, guard ->
        var actual: ByteArray? = null
        val native = object : FakeConnection(200) {
            override fun getInputStream() = object : java.io.ByteArrayInputStream("#EXTM3U\n#EXT-X-KEY:METHOD=AES-128,URI=\"synthetic\"\n".toByteArray()) {
                override fun read(b: ByteArray, off: Int, len: Int): Int {
                    actual = b
                    return super.read(b, off, len)
                }
            }
        }
        assertTrue(BrowserMediaProbe.protectedManifest(URI("https://invalid.example/list.m3u8"), "synthetic", null, guard, { native }))
        assertNotNull(actual)
        assertTrue("actual native read buffer is retired, not only a copied result", actual!!.all { it == 0.toByte() })
        assertEquals(1, native.disconnected.get())
    }

    @Test fun revokedBlockedManifestReadRetainsBufferUntilActualConsumerReturns() = isolated { authority, guard ->
        val entered = CountDownLatch(1); val allowReturn = CountDownLatch(1); val inputClosed = CountDownLatch(1)
        val finished = CountDownLatch(1); val actual = AtomicReference<ByteArray>(); val failure = AtomicReference<Throwable>()
        val native = object : FakeConnection(200) {
            override fun getInputStream() = object : java.io.InputStream() {
                override fun read(): Int = error("unexpected scalar read")
                override fun read(b: ByteArray, off: Int, len: Int): Int {
                    actual.set(b); entered.countDown(); check(allowReturn.await(5, TimeUnit.SECONDS))
                    b[off] = 99; return 1
                }
                override fun close() { inputClosed.countDown() }
            }
        }
        val worker = thread(isDaemon = true) {
            try { assertThrows(IllegalStateException::class.java) {
                BrowserMediaProbe.protectedManifest(URI("https://invalid.example/list.m3u8"), "synthetic", null, guard, { native })
            } } catch (problem: Throwable) { failure.set(problem) } finally { finished.countDown() }
        }
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS)); authority.revoke()
            assertTrue("input close dispatch cannot wait behind the buffer reader", inputClosed.await(5, TimeUnit.SECONDS))
            assertFalse(authority.cleanupComplete)
            val key = ByteArray(32) { 9 }; assertThrows(IllegalStateException::class.java) { authority.open(key) }; assertArrayEquals(ByteArray(32),key)
            allowReturn.countDown(); assertTrue(finished.await(5, TimeUnit.SECONDS)); failure.get()?.let { throw AssertionError("blocked manifest read", it) }
            assertTrue(actual.get().all { it == 0.toByte() }); assertTrue(authority.cleanupComplete); assertEquals(1,native.disconnected.get())
        } finally { allowReturn.countDown(); assertTrue(finished.await(5, TimeUnit.SECONDS)); worker.interrupt() }
    }

    @Test fun lateManifestInputFactoryRemainsOriginalObligationAfterRevocation() = isolated { authority, guard ->
        val entered = CountDownLatch(1); val allowCreate = CountDownLatch(1); val finished = CountDownLatch(1)
        val reads = AtomicInteger(); val closes = AtomicInteger(); val failure = AtomicReference<Throwable>()
        val native = object : FakeConnection(200) {
            override fun getInputStream(): java.io.InputStream {
                entered.countDown(); check(allowCreate.await(5, TimeUnit.SECONDS))
                return object : java.io.InputStream() {
                    override fun read(): Int { reads.incrementAndGet(); return -1 }
                    override fun close() { closes.incrementAndGet() }
                }
            }
        }
        val worker = thread(isDaemon = true) {
            try { assertThrows(IllegalStateException::class.java) {
                BrowserMediaProbe.protectedManifest(URI("https://invalid.example/list.m3u8"), "synthetic", null, guard, { native })
            } } catch (problem: Throwable) { failure.set(problem) } finally { finished.countDown() }
        }
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS)); authority.revoke(); assertFalse(authority.cleanupComplete)
            allowCreate.countDown(); assertTrue(finished.await(5, TimeUnit.SECONDS)); failure.get()?.let { throw AssertionError("late native input", it) }
            assertEquals(0,reads.get()); assertEquals(1,closes.get()); assertEquals(1,native.disconnected.get()); assertTrue(authority.cleanupComplete)
        } finally { allowCreate.countDown(); assertTrue(finished.await(5, TimeUnit.SECONDS)); worker.interrupt() }
    }

    @Test fun oversizedManifestCannotAuthorizeFromUnprotectedPrefix() = isolated { _, guard ->
        val native = object : FakeConnection(200) { override fun getInputStream() = java.io.ByteArrayInputStream(ByteArray(64 * 1024 + 1) { 32 }) }
        assertThrows(java.io.IOException::class.java) {
            BrowserMediaProbe.protectedManifest(URI("https://invalid.example/list.m3u8"), "synthetic", null, guard, { native })
        }
        assertEquals(1,native.disconnected.get())
    }

    @Test fun exactManifestBoundaryAndZeroLengthReadStillRequireActualEof() = isolated { _, guard ->
        var zero = true
        val native = object : FakeConnection(200) {
            override fun getInputStream() = object : java.io.ByteArrayInputStream(ByteArray(64 * 1024) { 32 }) {
                override fun read(b: ByteArray,off: Int,len: Int): Int = if (zero) { zero = false;0 } else super.read(b,off,len)
            }
        }
        assertFalse(BrowserMediaProbe.protectedManifest(URI("https://invalid.example/list.m3u8"), "synthetic", null, guard, { native }))
        assertFalse(zero); assertEquals(1,native.disconnected.get())
    }

    @Test fun partialNativeReadFailureWipesEntireActualBufferAndRetiresExactChildren() = isolated { authority, guard ->
        lateinit var actual: ByteArray; var closed = 0
        val native = object : FakeConnection(200) {
            override fun getInputStream() = object : java.io.InputStream() {
                override fun read(): Int = error("unexpected scalar read")
                override fun read(b: ByteArray,off: Int,len: Int): Int { actual = b; b.fill(91); throw java.io.IOException("synthetic read failure") }
                override fun close() { closed++ }
            }
        }
        assertThrows(java.io.IOException::class.java) {
            BrowserMediaProbe.protectedManifest(URI("https://invalid.example/list.m3u8"), "synthetic", null, guard, { native })
        }
        assertTrue(actual.all { it == 0.toByte() }); assertEquals(1,closed); assertEquals(1,native.disconnected.get())
        authority.revoke(); assertTrue(authority.cleanupComplete)
    }

    @Test fun failedManifestInputCloseWipesBufferButRetainsOriginalAuthenticationBarrier() = isolated { authority, guard ->
        lateinit var actual: ByteArray
        val native = object : FakeConnection(200) {
            override fun getInputStream() = object : java.io.ByteArrayInputStream("#EXTM3U\n".toByteArray()) {
                override fun read(b: ByteArray,off: Int,len: Int): Int { actual = b; return super.read(b,off,len) }
                override fun close(): Unit = throw java.io.IOException("synthetic input close failure")
            }
        }
        assertThrows(java.io.IOException::class.java) {
            BrowserMediaProbe.protectedManifest(URI("https://invalid.example/list.m3u8"), "synthetic", null, guard, { native })
        }
        authority.revoke();assertFalse(authority.cleanupComplete)
        val key = ByteArray(32) { 9 }; assertThrows(IllegalStateException::class.java) { authority.open(key) }; assertArrayEquals(ByteArray(32),key)
        val pool = PrimarySessionAuthority::class.java.getDeclaredField("ioReleasePool").apply { isAccessible = true }.get(authority) as ReleasePool
        val deadline = System.nanoTime()+TimeUnit.SECONDS.toNanos(5)
        while (pool.occupied > 1 && System.nanoTime()<deadline) Thread.yield()
        assertEquals("only the failed input's original slot remains pinned",1,pool.occupied)
        assertTrue(actual.all { it == 0.toByte() }); assertEquals(1,native.disconnected.get())
    }

    @Test fun redirectNextFactoryAwaitsPriorOriginalTerminalWithOnlyThreeFreeSlots() = isolated { _, guard ->
        val holds = (1..13).map { guard.createOwned(OwnedResourceManifest.io("hold")) { attach("hold",AutoCloseable {}) } }
        val entered = CountDownLatch(1);val allowClose = CountDownLatch(1);val finished = CountDownLatch(1)
        val calls = AtomicInteger();val failure = AtomicReference<Throwable>()
        val first = object : FakeConnection(302) {
            override fun getHeaderField(name: String?) = if (name == "Location") "/next.m3u8" else null
            override fun disconnect() { super.disconnect();entered.countDown();check(allowClose.await(5,TimeUnit.SECONDS)) }
        }
        val second = object : FakeConnection(200) { override fun getInputStream() = java.io.ByteArrayInputStream("#EXTM3U\n".toByteArray()) }
        val worker = thread(isDaemon = true) {
            try { assertFalse(BrowserMediaProbe.protectedManifest(URI("https://invalid.example/list.m3u8"),"synthetic",null,guard) {
                if (calls.incrementAndGet() == 1) first else second
            }) } catch (problem: Throwable) { failure.set(problem) } finally { finished.countDown() }
        }
        try {
            assertTrue(entered.await(5,TimeUnit.SECONDS));assertEquals(1,calls.get());assertFalse(finished.await(100,TimeUnit.MILLISECONDS))
            allowClose.countDown();assertTrue(finished.await(5,TimeUnit.SECONDS));failure.get()?.let { throw AssertionError("original redirect phase",it) }
            assertEquals(2,calls.get());assertEquals(1,first.disconnected.get());assertEquals(1,second.disconnected.get())
        } finally { allowClose.countDown();assertTrue(finished.await(5,TimeUnit.SECONDS));worker.interrupt();holds.forEach { guard.retire(it) } }
    }

    @Test fun readOnlyOriginalCannotInvokeManifestNetworkFactory() {
        val authority = PrimarySessionAuthority { 0 }
        for (field in listOf("ioReleasePool","presentationReleasePool"))
            PrimarySessionAuthority::class.java.getDeclaredField(field).apply { isAccessible = true }.set(authority,ReleasePool(16))
        authority.open(ByteArray(32) { 9 });val op = authority.operationOrNull(setOf(PrimaryScope.READ))!!
        var calls = 0
        try {
            assertThrows(IllegalStateException::class.java) {
                BrowserMediaProbe.protectedManifest(URI("https://invalid.example/list.m3u8"),"synthetic",null,ScopedIoGuard(op,PrimaryScope.READ)) {
                    calls++
                    object : FakeConnection(200) { override fun getInputStream() = java.io.ByteArrayInputStream("#EXTM3U\n".toByteArray()) }
                }
            }
            assertEquals(0,calls)
        } finally { op.close();authority.revoke() }
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
