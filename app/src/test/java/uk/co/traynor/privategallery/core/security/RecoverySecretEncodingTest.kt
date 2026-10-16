package uk.co.traynor.privategallery.core.security

import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CharsetEncoder
import java.nio.charset.CoderResult
import java.nio.charset.StandardCharsets
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.*
import org.junit.Test
import uk.co.traynor.privategallery.core.crypto.RecoveryKey

class RecoverySecretEncodingTest {
    private fun source(encoder: () -> CharsetEncoder): RecoverySecretEncoding {
        val constructor = RecoverySecretEncoding::class.java.getDeclaredConstructor(Function0::class.java)
        constructor.isAccessible = true
        return constructor.newInstance(encoder)
    }
    private fun bytes(value: PreparedRecoverySecret): ByteArray = value.javaClass.getDeclaredField("bytes")
        .apply { isAccessible = true }.get(value) as ByteArray
    private fun pool(authority: PrimarySessionAuthority): ReleasePool = authority.javaClass.getDeclaredField("ioReleasePool")
        .apply { isAccessible = true }.get(authority) as ReleasePool
    private fun clean(authority: PrimarySessionAuthority) {
        val end = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (!authority.cleanupComplete && System.nanoTime() < end) Thread.yield()
        assertTrue(authority.cleanupComplete); assertEquals(0, pool(authority).occupied)
    }
    @Test fun legacyNormalizationAndUtf8IncludingJoinedAndMalformedSurrogatesAreIdentical() {
        val whitespace = CharArray(65536) { it.toChar() }.filter { it.isWhitespace() }.joinToString("")
        val cases = listOf("", "abc-def_+/=", " a\tb\nc\r ", whitespace, "\u00a0\u2007\u202f", "\ud83d\ude00",
            "\ud800", "\udc00", "\ud800A\udc00", "\ud83d \t\ude00", "é漢字\ud83d\ude00".repeat(1024))
        for (text in cases) {
            val input = text.toCharArray()
            val expected = RecoveryKey.normalise(input).concatToString().encodeToByteArray()
            val authority = testPrimaryAuthority(); val attempt = authority.beginAuthentication()
            val owned = RecoverySecretEncoding.standard().prepare(authority, attempt, input)
            val actual = bytes(owned.value)
            assertArrayEquals(expected, actual); assertArrayEquals(CharArray(input.size), input)
            assertEquals(1, pool(authority).occupied)
            owned.close(); assertTrue(owned.retirement.await(5, TimeUnit.SECONDS))
            assertArrayEquals(ByteArray(actual.size), actual); clean(authority); authority.revoke()
        }
    }
    @Test fun wholeManifestMustBeFundedBeforeEncoderOrSecretMutation() {
        val authority = testPrimaryAuthority(); val attempt = authority.beginAuthentication()
        val held = authority.createAttemptOwned(attempt, OwnedResourceManifest.io(*Array(12) { "hold$it" })) {
            val root = attach("hold0", AutoCloseable {})
            for (i in 1..11) attach("hold$i", AutoCloseable {})
            root
        }
        var acquisitions = 0; val input = " a b ".toCharArray()
        try {
            assertThrows(Exception::class.java) { source { acquisitions++; StandardCharsets.UTF_8.newEncoder() }.prepare(authority, attempt, input) }
            assertEquals(0, acquisitions); assertArrayEquals(CharArray(input.size), input)
        } finally { held.close(); assertTrue(held.retirement.await(5, TimeUnit.SECONDS)); authority.revoke(); clean(authority) }
    }
    @Test fun cancellationDuringActualEncodingPreservesInputAndScratchUntilLateWriteReturns() {
        for (throwAfterWrite in listOf(false, true)) {
            val authority = testPrimaryAuthority(); val attempt = authority.beginAuthentication()
            val entered = CountDownLatch(1); val release = CountDownLatch(1)
            val failure = AtomicReference<Throwable>(); val scratch = AtomicReference<ByteArray>()
            val input = " a b ".toCharArray()
            val encoder = object : CharsetEncoder(StandardCharsets.UTF_8, 1f, 3f) {
                override fun encodeLoop(chars: CharBuffer, output: ByteBuffer): CoderResult {
                    scratch.set(output.array()); assertEquals(5, pool(authority).occupied)
                    output.put(7.toByte()); entered.countDown(); check(release.await(10, TimeUnit.SECONDS))
                    assertEquals('a', chars[0]); assertEquals('b', chars[1]); assertEquals(7.toByte(), output.array()[0])
                    output.put(9.toByte()); if (throwAfterWrite) throw IllegalStateException("late encode failure")
                    chars.position(chars.limit()); return CoderResult.UNDERFLOW
                }
            }
            val worker = Thread { try { source { encoder }.prepare(authority, attempt, input); failure.set(AssertionError("stale encoding returned")) }
                catch (problem: Throwable) { failure.set(problem) } }
            worker.start()
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS)); authority.cancelAuthentication(attempt)
                assertEquals('a', input[0]); assertEquals(7.toByte(), scratch.get()[0]); assertFalse(authority.cleanupComplete)
                assertThrows(IllegalStateException::class.java) { authority.open(ByteArray(32)) }
            } finally { release.countDown(); worker.join(10000) }
            assertFalse(worker.isAlive); assertNotNull(failure.get()); clean(authority)
            assertArrayEquals(CharArray(input.size), input); assertArrayEquals(ByteArray(scratch.get().size), scratch.get()); authority.revoke()
        }
    }
    @Test fun foreignStaleAndInheritedGatesNeverAcquireEncoder() {
        for (kind in listOf("foreign", "stale", "authority", "storage")) {
            val authority = testPrimaryAuthority(); val other = testPrimaryAuthority()
            val attempt = if (kind == "foreign") other.beginAuthentication() else authority.beginAuthentication()
            if (kind == "stale") authority.cancelAuthentication(attempt)
            var acquisitions = 0; val input = " secret ".toCharArray()
            val action = { assertThrows(IllegalStateException::class.java) { source { acquisitions++; StandardCharsets.UTF_8.newEncoder() }.prepare(authority, attempt, input) } }
            when (kind) {
                "authority" -> synchronized(authority.javaClass.getDeclaredField("gate").apply { isAccessible = true }.get(authority)) { action() }
                "storage" -> synchronized(PrimaryVaultSetupGuard.storageLock) { action() }
                else -> action()
            }
            assertEquals(0, acquisitions); assertArrayEquals(CharArray(input.size), input)
            authority.revoke(); other.revoke(); clean(authority); clean(other)
        }
    }
    @Test fun interruptedMandatoryPhaseRetirementKeepsInterruptAndOriginalCharge() {
        val held = CountDownLatch(1); val release = CountDownLatch(1); val first = java.util.concurrent.atomic.AtomicBoolean(true)
        val authority = testPrimaryAuthority()
        val isolated = ReleasePool(16, allocateInvocation = { action, returned -> ReleaseInvocation({ action()
            if (first.compareAndSet(true, false)) { held.countDown(); check(release.await(10, TimeUnit.SECONDS)) } }, returned) })
        authority.javaClass.getDeclaredField("ioReleasePool").apply { isAccessible = true }.set(authority, isolated)
        val attempt = authority.beginAuthentication(); val failure = AtomicReference<Throwable>()
        val interrupted = java.util.concurrent.atomic.AtomicBoolean(); val input = "secret".toCharArray()
        val worker = Thread { try { RecoverySecretEncoding.standard().prepare(authority, attempt, input) }
            catch (problem: Throwable) { failure.set(problem); interrupted.set(Thread.currentThread().isInterrupted) } }
        worker.start()
        try {
            assertTrue(held.await(5, TimeUnit.SECONDS)); worker.interrupt(); worker.join(5000)
            assertFalse(worker.isAlive); assertTrue(failure.get() is InterruptedException)
            assertTrue("Interrupted mandatory cleanup must preserve the original interrupt", interrupted.get())
            assertTrue(isolated.occupied > 0); assertFalse(authority.cleanupComplete)
        } finally { release.countDown(); worker.join(10000) }
        clean(authority); assertArrayEquals(CharArray(input.size), input); authority.revoke()
    }
    @Test fun failedActualChildReleaseRemainsChargedAndDeniesFreshPreparation() {
        val fail = java.util.concurrent.atomic.AtomicBoolean(true)
        val isolated = ReleasePool(16, allocateInvocation = { action, returned ->
            ReleaseInvocation({ if (fail.compareAndSet(true, false)) error("controlled encoding release failure"); action() }, returned) })
        val authority = testPrimaryAuthority()
        authority.javaClass.getDeclaredField("ioReleasePool").apply { isAccessible = true }.set(authority, isolated)
        val attempt = authority.beginAuthentication(); val input = " secret ".toCharArray()
        assertThrows(java.io.IOException::class.java) { RecoverySecretEncoding.standard().prepare(authority, attempt, input) }
        assertTrue(isolated.hasFailedRetirement); assertTrue(isolated.occupied > 0); assertFalse(authority.cleanupComplete)
        var acquisitions = 0
        repeat(2) {
            val fresh = authority.beginAuthentication(); val next = " secret ".toCharArray()
            assertThrows(IllegalStateException::class.java) { source { acquisitions++; StandardCharsets.UTF_8.newEncoder() }.prepare(authority, fresh, next) }
            assertArrayEquals(CharArray(next.size), next)
        }
        assertEquals(0, acquisitions); assertTrue(isolated.hasFailedRetirement); authority.revoke()
    }
    @Test fun constructorFlushAndCheckedScratchOverflowFailuresWipeEveryAcquiredInputAndScratch() {
        for (point in listOf("constructor", "flush", "overflow")) {
            val authority = testPrimaryAuthority(); val attempt = authority.beginAuthentication()
            val input = " secret ".toCharArray(); val captured = AtomicReference<ByteArray>(); var encodes = 0
            val expected = IllegalStateException("controlled encoder failure")
            val encoder = object : CharsetEncoder(StandardCharsets.UTF_8, 1f, if (point == "overflow") Float.MAX_VALUE else 3f) {
                override fun encodeLoop(chars: CharBuffer, output: ByteBuffer): CoderResult {
                    encodes++; captured.set(output.array()); output.put(7.toByte()); chars.position(chars.limit()); return CoderResult.UNDERFLOW
                }
                override fun implFlush(output: ByteBuffer): CoderResult { throw expected }
            }
            val selected = source { if (point == "constructor") throw expected; encoder }
            val failure = assertThrows(Throwable::class.java) { selected.prepare(authority, attempt, input) }
            if (point == "constructor" || point == "flush") assertSame(expected, failure)
            if (point == "overflow") assertTrue(failure is IllegalArgumentException)
            if (point != "flush") assertEquals(0, encodes)
            captured.get()?.let { assertArrayEquals(ByteArray(it.size), it) }
            clean(authority); assertArrayEquals(CharArray(input.size), input); authority.revoke()
        }
    }
    @Test fun completedChildStagesCannotReplaceActualPhaseReleaseReturn() {
        val held = CountDownLatch(1); val release = CountDownLatch(1); val first = java.util.concurrent.atomic.AtomicBoolean(true)
        val authority = testPrimaryAuthority()
        val isolated = ReleasePool(16, allocateInvocation = { action, returned -> ReleaseInvocation({ action()
            if (first.compareAndSet(true, false)) { held.countDown(); check(release.await(10, TimeUnit.SECONDS)) } }, returned) })
        authority.javaClass.getDeclaredField("ioReleasePool").apply { isAccessible = true }.set(authority, isolated)
        val attempt = authority.beginAuthentication(); val result = AtomicReference<OwnedResource<PreparedRecoverySecret>>()
        val failure = AtomicReference<Throwable>()
        val worker = Thread { try { result.set(RecoverySecretEncoding.standard().prepare(authority, attempt, "secret".toCharArray())) }
            catch (problem: Throwable) { failure.set(problem) } }
        worker.start()
        try { assertTrue(held.await(5, TimeUnit.SECONDS)); assertNull(result.get()); assertFalse(authority.cleanupComplete) }
        finally { release.countDown(); worker.join(10000) }
        assertFalse(worker.isAlive); assertNull(failure.get()); result.get().close()
        assertTrue(result.get().retirement.await(5, TimeUnit.SECONDS)); clean(authority); authority.revoke()
    }
}
