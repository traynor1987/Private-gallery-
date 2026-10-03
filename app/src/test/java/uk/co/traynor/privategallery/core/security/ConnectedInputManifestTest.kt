package uk.co.traynor.privategallery.core.security

import java.net.HttpURLConnection
import java.net.URL
import java.io.InputStream
import java.io.ByteArrayInputStream
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.*
import org.junit.Test

class ConnectedInputManifestTest {
    @Test fun completePairAdmissionDeniesBeforeAnyNativeChildWhenOnlyOneSlotRemains() = isolated { _, guard ->
        val holds = (1..15).map { guard.connection { FakeConnection() } }; var calls = 0
        try {
            assertThrows(IllegalStateException::class.java) { guard.connectedInput({ calls++; FakeConnection() }) }
            assertEquals(0, calls)
        } finally { holds.forEach { it.close() } }
    }
    @Test fun nativeInputCloseAndDisconnectHaveIndependentlyFundedWorkers() = isolated { authority, guard ->
        val disconnected = CountDownLatch(1); val native = object : FakeConnection() {
            override fun disconnect() { super.disconnect(); disconnected.countDown() }
            override fun getInputStream(): InputStream = object : ByteArrayInputStream(byteArrayOf(7)) {
                override fun close() { assertTrue(disconnected.await(5, TimeUnit.SECONDS)) }
            }
        }
        val input = guard.connectedInput({ native }); input.close()
        assertEquals(1, native.disposals.get()); authority.revoke(); assertTrue(authority.cleanupComplete)
    }
    @Test fun revocationDuringPrepareDeniesNativeStreamFactory() = isolated { authority, guard ->
        val native = FakeConnection(); var opens = 0
        assertThrows(IllegalStateException::class.java) {
            guard.connectedInput({ native }, prepare = { authority.revoke() }, open = { opens++; it.inputStream })
        }
        assertEquals(0, opens); assertTrue(native.closed.await(5, TimeUnit.SECONDS)); assertEquals(1, native.disposals.get())
    }
    @Test fun actualNativeInputFailureRetainsFailedPairAndDeniesNewEpoch() = isolated { authority, guard ->
        val input = guard.connectedInput({ FakeConnection() }, open = {
            object : ByteArrayInputStream(byteArrayOf(7)) { override fun close(): Unit = throw IOException("synthetic failure") }
        })
        assertThrows(IOException::class.java) { input.close() }; authority.revoke(); assertFalse(authority.cleanupComplete)
        val key = ByteArray(32) { 1 }; assertThrows(IllegalStateException::class.java) { authority.open(key) }
        assertArrayEquals(ByteArray(32), key)
    }
    @Test fun revokedCachedAccessorsAndReadsStillRequireOriginalOperation() = isolated { authority, guard ->
        val input = guard.connectedInput({ FakeConnection() }); authority.revoke()
        assertThrows(IllegalStateException::class.java) { input.available() }
        assertThrows(IllegalStateException::class.java) { input.markSupported() }
        val out = ByteArray(4) { 9 }; assertThrows(IllegalStateException::class.java) { input.read(out, 1, 2) }
        assertArrayEquals(byteArrayOf(9,0,0,9), out)
    }
    @Test fun lateNativeInputAttachesAndDisposesAfterEarlierTransportRetires() = isolated { authority, guard ->
        val native = FakeConnection(); val entered = CountDownLatch(1); val allow = CountDownLatch(1)
        val closed = CountDownLatch(1); val done = CountDownLatch(1); val failure = java.util.concurrent.atomic.AtomicReference<Throwable>()
        val worker = kotlin.concurrent.thread(isDaemon = true) {
            try {
                guard.connectedInput({ native }, open = {
                    entered.countDown(); assertTrue(allow.await(5, TimeUnit.SECONDS))
                    object : ByteArrayInputStream(byteArrayOf(7)) { override fun close() { closed.countDown() } }
                })
                failure.set(AssertionError("revoked factory must deny facade"))
            } catch (problem: Throwable) { if (problem !is IllegalStateException) failure.set(problem) }
            finally { done.countDown() }
        }
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS)); authority.revoke()
            assertTrue(native.closed.await(5, TimeUnit.SECONDS)); assertFalse(authority.cleanupComplete)
            val key = ByteArray(32) { 9 }; assertThrows(IllegalStateException::class.java) { authority.open(key) }
            assertArrayEquals(ByteArray(32), key)
            allow.countDown(); assertTrue(done.await(5, TimeUnit.SECONDS)); assertTrue(closed.await(5, TimeUnit.SECONDS))
            failure.get()?.let { throw AssertionError("late native input failed", it) }
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
            while (!authority.cleanupComplete && System.nanoTime() < deadline) Thread.yield()
            assertTrue(authority.cleanupComplete); assertEquals(1, native.disposals.get())
        } finally { allow.countDown(); worker.interrupt(); assertTrue(done.await(5, TimeUnit.SECONDS)) }
    }
    @Test fun prepareFailureRetiresKnownTransportAndUnusedInputObligation() = isolated { authority, guard ->
        val native = FakeConnection(); var inputs = 0
        assertThrows(IOException::class.java) {
            guard.connectedInput({ native }, prepare = { throw IOException("synthetic preparation") }, open = { inputs++; it.inputStream })
        }
        assertEquals(0, inputs); assertTrue(native.closed.await(5, TimeUnit.SECONDS)); assertEquals(1, native.disposals.get())
        authority.revoke()
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (!authority.cleanupComplete && System.nanoTime() < deadline) Thread.yield()
        assertTrue(authority.cleanupComplete)
    }
    @Test fun revokedConnectionFactoryReturnNeverInvokesPreparationOrNativeInput() = isolated { authority, guard ->
        val native = FakeConnection(); var preparations = 0; var inputs = 0
        assertThrows(IllegalStateException::class.java) {
            guard.connectedInput({ authority.revoke(); native },
                prepare = { preparations++ }, open = { inputs++; it.inputStream })
        }
        assertEquals(0, preparations); assertEquals(0, inputs)
        assertTrue(native.closed.await(5, TimeUnit.SECONDS)); assertEquals(1, native.disposals.get())
    }
    private open class FakeConnection : HttpURLConnection(URL("https://invalid.example/")) {
        val disposals = AtomicInteger(); val closed = CountDownLatch(1)
        override fun disconnect() { disposals.incrementAndGet(); closed.countDown() }
        override fun getInputStream(): InputStream = ByteArrayInputStream(byteArrayOf(1))
        override fun usingProxy() = false
        override fun connect() = Unit
    }
    private fun isolated(test: (PrimarySessionAuthority, ScopedIoGuard) -> Unit) {
        val authority = testPrimaryAuthority(); authority.open(ByteArray(32))
        val op = authority.operationOrNull(setOf(PrimaryScope.READ))!!
        try { test(authority, ScopedIoGuard(op, PrimaryScope.READ)) } finally { op.close(); authority.revoke() }
    }
}
