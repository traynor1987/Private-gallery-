package uk.co.traynor.privategallery.core.security

import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import org.junit.Assert.*
import org.junit.Test

class ScopedConnectionTest {
    @Test fun normalInputDisposalDispatchesTransportWithoutExternalRevocation() {
        val authority = testPrimaryAuthority(); authority.open(ByteArray(32))
        val op = authority.operationOrNull(setOf(PrimaryScope.READ))!!; val guard = ScopedIoGuard(op, PrimaryScope.READ)
        val disconnected = CountDownLatch(1)
        val native = object : FakeConnection() { override fun disconnect() { super.disconnect(); disconnected.countDown() } }
        val transport = guard.connection { native }
        val input = transport.input { object : java.io.ByteArrayInputStream(byteArrayOf(1)) {
            override fun close() { assertTrue(disconnected.await(5, TimeUnit.SECONDS)) }
        } }
        input.close(); transport.close(); assertEquals(1, native.disconnected.get())
        op.close(); authority.revoke(); assertTrue(authority.cleanupComplete)
    }

    @Test fun exhaustedTransportAdmissionNeverCreatesNativeConnection() {
        val authority = testPrimaryAuthority(); authority.open(ByteArray(32))
        val op = authority.operationOrNull(setOf(PrimaryScope.READ))!!; val guard = ScopedIoGuard(op, PrimaryScope.READ)
        val owners = (1..16).map { guard.connection { FakeConnection() } }
        var calls = 0
        assertThrows(IllegalStateException::class.java) { guard.connection { calls++; FakeConnection() } }
        assertEquals(0, calls); owners.forEach { it.close() }; op.close(); authority.revoke(); assertTrue(authority.cleanupComplete)
    }

    @Test fun normalCloseAndRevocationUseOneOriginalDisconnect() {
        val authority = testPrimaryAuthority(); authority.open(ByteArray(32))
        val op = authority.operationOrNull(setOf(PrimaryScope.READ))!!; val guard = ScopedIoGuard(op, PrimaryScope.READ)
        val actual = FakeConnection(); val owned = guard.connection { actual }
        assertSame(actual, owned.value); owned.close(); owned.close(); authority.revoke()
        assertEquals(1, actual.disconnected.get()); assertTrue(authority.cleanupComplete)
        assertThrows(IllegalStateException::class.java) { owned.value }
    }

    @Test fun independentTransportWorkerCanUnblockNativeStreamClose() {
        val authority = testPrimaryAuthority(); authority.open(ByteArray(32))
        val op = authority.operationOrNull(setOf(PrimaryScope.READ))!!; val guard = ScopedIoGuard(op, PrimaryScope.READ)
        val disconnected = CountDownLatch(1); val inputClosing = CountDownLatch(1); val closed = CountDownLatch(1)
        val actual = object : FakeConnection() { override fun disconnect() { super.disconnect(); disconnected.countDown() } }
        val transport = guard.connection { actual }
        val input = guard.input { object : java.io.ByteArrayInputStream(byteArrayOf(1)) {
            override fun close() { inputClosing.countDown(); assertTrue(disconnected.await(5, TimeUnit.SECONDS)); closed.countDown() }
        } }
        val done = CountDownLatch(1)
        val closer = thread(isDaemon = true) { try { input.close() } finally { done.countDown() } }
        try {
            assertTrue(inputClosing.await(5, TimeUnit.SECONDS)); authority.revoke()
            assertTrue(done.await(5, TimeUnit.SECONDS)); assertEquals(0L, closed.count)
            transport.close(); assertEquals(1, actual.disconnected.get()); assertTrue(authority.cleanupComplete)
        } finally { disconnected.countDown(); closer.interrupt(); assertTrue(done.await(5, TimeUnit.SECONDS)) }
    }

    private open class FakeConnection : HttpURLConnection(URL("https://invalid.example/")) {
        val disconnected = AtomicInteger()
        override fun disconnect() { disconnected.incrementAndGet() }
        override fun connect() = Unit
        override fun usingProxy() = false
    }
}
