package uk.co.traynor.privategallery.core.browser.v2

import java.net.HttpURLConnection
import java.net.URL
import java.io.ByteArrayInputStream
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.*
import org.junit.Test
import uk.co.traynor.privategallery.core.security.*

/** Actual source callbacks with isolated synthetic transports; no external network/owner data. */
class Phase3DownloadOwnershipTest {
    @Test fun normalImageConsumptionRetiresOriginalNativeTransportExactlyOnce() = isolated { authority, guard ->
        val native = FakeConnection()
        source(native).openOwnedStream!!(guard).adopt(guard).use { assertEquals(1, it.read()) }
        assertEquals(1, native.closes.get()); authority.revoke(); assertEquals(1, native.closes.get())
        assertTrue(authority.cleanupComplete)
    }
    @Test fun exhaustedDownloadAdmissionDoesNotInvokeNativeFactory() = isolated { _, guard ->
        val owners = (1..16).map { guard.connection { FakeConnection() } }; var calls = 0
        try {
            val source = browserV2DownloadSource("https://invalid.example/file", "synthetic", "download", "image/png", { calls++; FakeConnection() })
            assertThrows(IllegalStateException::class.java) { source.openOwnedStream!!(guard).adopt(guard) }; assertEquals(0, calls)
        } finally { owners.forEach { it.close() } }
    }
    @Test fun rejectedHeadersDisposeOriginalTransportBeforeReturningFailure() = isolated { _, guard ->
        val native = object : FakeConnection() { override fun getResponseCode() = 500 }
        assertThrows(IllegalArgumentException::class.java) { source(native).openOwnedStream!!(guard).adopt(guard) }
        assertEquals(1, native.closes.get())
    }
    @Test fun inputCreationFailureDisposesOriginalTransportBeforeReturningFailure() = isolated { _, guard ->
        val native = object : FakeConnection() { override fun getInputStream(): java.io.InputStream = throw IOException("synthetic unavailable") }
        assertThrows(IOException::class.java) { source(native).openOwnedStream!!(guard).adopt(guard) }; assertEquals(1, native.closes.get())
    }
    @Test fun actualStreamCloseGetsIndependentTransportUnblocker() = isolated { _, guard ->
        val disconnected = CountDownLatch(1)
        val native = object : FakeConnection() {
            override fun disconnect() { super.disconnect(); disconnected.countDown() }
            override fun getInputStream() = object : ByteArrayInputStream(byteArrayOf(1)) {
                override fun close() { assertTrue("native input close needs disconnect", disconnected.await(5, TimeUnit.SECONDS)) }
            }
        }
        try { source(native).openOwnedStream!!(guard).adopt(guard).close(); assertEquals(1, native.closes.get()) }
        finally { disconnected.countDown() }
    }
    @Test fun failedNativeDisconnectCannotBecomeSuccessfulConsumptionOrNewAdmission() = isolated { authority, guard ->
        val native = object : FakeConnection() { override fun disconnect(): Unit = throw IOException("synthetic disconnect failure") }
        val input = source(native).openOwnedStream!!(guard).adopt(guard)
        assertThrows(IOException::class.java) { input.close() }
        authority.revoke(); assertFalse(authority.cleanupComplete)
        val key = ByteArray(32) { 9 }; assertThrows(IllegalStateException::class.java) { authority.open(key) }
        assertArrayEquals(ByteArray(32), key)
    }
    @Test fun readonlyOperationCannotCreateBrowserEgressTransport() = isolated { authority, _ ->
        val read = authority.operationOrNull(setOf(PrimaryScope.READ))!!; var calls = 0
        try {
            val source = browserV2DownloadSource("https://invalid.example/file", "synthetic", "download", "image/png", { calls++; FakeConnection() })
            assertThrows(IllegalStateException::class.java) { source.openOwnedStream!!(ScopedIoGuard(read, PrimaryScope.READ)) }
            assertEquals(0, calls)
        } finally { read.close() }
    }
    @Test fun revocationDuringHeadersDeniesNativeInputCreation() = isolated { authority, guard ->
        var inputCalls = 0
        val native = object : FakeConnection() {
            override fun getResponseCode(): Int { authority.revoke(); return 200 }
            override fun getInputStream(): java.io.InputStream { inputCalls++; return super.getInputStream() }
        }
        assertThrows(IllegalStateException::class.java) { source(native).openOwnedStream!!(guard).adopt(guard) }
        assertEquals(0, inputCalls); assertEquals(1, native.closes.get()); assertTrue(authority.cleanupComplete)
    }
    @Test fun completePairAdmissionFailureCreatesNeitherNativeChild() = isolated { _, guard ->
        val owners = (1..15).map { guard.connection { FakeConnection() } }; var inputCalls = 0; var factories = 0
        val native = object : FakeConnection() {
            override fun getInputStream(): java.io.InputStream { inputCalls++; return super.getInputStream() }
        }
        try {
            val source = browserV2ImageSource("https://invalid.example/image.png", "synthetic", null, { factories++; native })
            assertThrows(IllegalStateException::class.java) { source.openOwnedStream!!(guard) }
            assertEquals(0, factories); assertEquals(0, inputCalls); assertEquals(0, native.closes.get())
        } finally { owners.forEach { it.close() } }
    }
    @Test fun normalDownloadConsumptionRetiresOriginalNativeTransport() = isolated { _, guard ->
        val native = FakeConnection()
        val source = browserV2DownloadSource("https://invalid.example/file", "synthetic", "download", "image/png", { native })
        source.openOwnedStream!!(guard).adopt(guard).close(); assertEquals(1, native.closes.get())
    }
    @Test fun productionNetworkImportAdmissionSupportsImageAndDownloadSourceCallbacks() = isolated { authority, _ ->
        val operation = browserImportOperation(authority, true)!!
        try {
            val guard = ScopedIoGuard(operation, PrimaryScope.WRITE)
            val image = FakeConnection(); source(image).openOwnedStream!!(guard).adopt(guard).close(); assertEquals(1, image.closes.get())
            val download = FakeConnection()
            browserV2DownloadSource("https://invalid.example/file", "synthetic", "download", "image/png", { download })
                .openOwnedStream!!(guard).adopt(guard).close(); assertEquals(1, download.closes.get())
        } finally { operation.close() }
    }
    @Test fun productionLocalCaptureAdmissionCannotOpenNetworkSource() = isolated { authority, _ ->
        val operation = browserImportOperation(authority, false)!!; var calls = 0
        try {
            val guard = ScopedIoGuard(operation, PrimaryScope.WRITE)
            guard.requireScope(PrimaryScope.READ); guard.requireScope(PrimaryScope.WRITE)
            val source = browserV2DownloadSource("https://invalid.example/file", "synthetic", "download", "image/png", { calls++; FakeConnection() })
            assertThrows(IllegalStateException::class.java) { source.openOwnedStream!!(guard).adopt(guard) }; assertEquals(0, calls)
        } finally { operation.close() }
    }
    private fun source(native: HttpURLConnection) = browserV2ImageSource("https://invalid.example/image.png", "synthetic", null, { native })
    private open class FakeConnection : HttpURLConnection(URL("https://invalid.example/image.png")) {
        val closes = AtomicInteger()
        override fun disconnect() { closes.incrementAndGet() }
        override fun getResponseCode() = 200
        override fun getInputStream(): java.io.InputStream = ByteArrayInputStream(byteArrayOf(1, 2))
        override fun connect() = Unit
        override fun usingProxy() = false
    }
    private fun isolated(test: (PrimarySessionAuthority, ScopedIoGuard) -> Unit) {
        val authority = PrimarySessionAuthority { 0 }
        for (name in listOf("ioReleasePool", "presentationReleasePool")) {
            PrimarySessionAuthority::class.java.getDeclaredField(name).apply { isAccessible = true }.set(authority, ReleasePool(16))
        }
        authority.open(ByteArray(32) { 11 }); val op = authority.operationOrNull(PrimaryScope.entries.toSet())!!
        try { test(authority, ScopedIoGuard(op, PrimaryScope.BROWSER_UPLOAD_EGRESS)) }
        finally { op.close(); authority.revoke() }
    }
}
