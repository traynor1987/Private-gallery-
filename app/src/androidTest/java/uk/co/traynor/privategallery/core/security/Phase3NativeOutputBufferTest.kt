package uk.co.traynor.privategallery.core.security

import android.media.MediaDataSource
import android.media.MediaExtractor
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Real JNI callback scheduling, synthetic EOF only; no owner data or network.
 * The separately funded extractor is released on quiescence, never concurrently
 * with setDataSource. This is not a complete production extractor manifest. */
@RunWith(AndroidJUnit4::class)
class Phase3NativeOutputBufferTest {
    @Test fun realNativeReadAtUnblocksIndependentlyAndSampleWipesAfterJniReturns() {
        val authority = PrimarySessionAuthority { 0 }
        for (name in listOf("ioReleasePool", "presentationReleasePool")) {
            PrimarySessionAuthority::class.java.getDeclaredField(name).apply { isAccessible = true }
                .set(authority, ReleasePool(16))
        }
        authority.open(ByteArray(32) { 11 })
        val operation = authority.operationOrNull(setOf(PrimaryScope.WRITE))!!
        val fixtureOperation = authority.operationOrNull(setOf(PrimaryScope.WRITE))!!
        val guard = ScopedIoGuard(operation, PrimaryScope.WRITE)
        val fixtureGuard = ScopedIoGuard(fixtureOperation, PrimaryScope.WRITE)
        val sourceCloseDispatched = CountDownLatch(1)
        val wipeDispatched = CountDownLatch(1)
        val jniReturned = CountDownLatch(1)
        val callerReturned = CountDownLatch(1)
        val bytes = AtomicReference<ByteArray?>()
        val failure = AtomicReference<Throwable?>()
        lateinit var sample: OwnedNativeOutputBuffer
        var sourceForCleanup: BlockingSource? = null
        var callerForCleanup: Thread? = null
        var probeForCleanup: Thread? = null
        var extractorForCleanup: OwnedResource<ReservedValue<MediaExtractor>>? = null
        var ownedForCleanup: OwnedResource<ReservedValue<BlockingSource>>? = null
        val callbackReachedResult = AtomicBoolean(false)
        try {
            val extractor = fixtureGuard.createOwned(OwnedResourceManifest.io("extractor")) {
                create("extractor", { it: MediaExtractor -> it.release() }) { MediaExtractor() }
            }
            extractorForCleanup = extractor
            lateinit var source: BlockingSource
            val owned = guard.createOwned(OwnedResourceManifest.io("source", "sample")) {
                val root = create("source", { it: BlockingSource ->
                    sourceCloseDispatched.countDown()
                    it.close()
                }) { BlockingSource() }
                source = root.value
                sourceForCleanup = source
                sample = create("sample", { it: OwnedNativeOutputBuffer ->
                    wipeDispatched.countDown()
                    it.close()
                }) { OwnedNativeOutputBuffer(guard, original, 1, 16) }.value
                root
            }
            ownedForCleanup = owned
            val caller = Thread {
                try {
                    sample.useBytes { actual ->
                        actual.fill(9)
                        bytes.set(actual)
                        try {
                            extractor.value.value.setDataSource(source)
                        } catch (_: IOException) {
                            // EOF-only fixture must not be accepted as playable media.
                        } finally { jniReturned.countDown() }
                        callbackReachedResult.set(true)
                        1
                    }
                } catch (problem: Throwable) { failure.set(problem) }
                finally { callerReturned.countDown() }
            }
            callerForCleanup = caller
            caller.start()
            assertTrue("real Native readAt entry required", source.entered.await(10, TimeUnit.SECONDS))
            assertEquals(1L, jniReturned.count)
            // A callback-thread holdsLock check cannot detect another thread's lock.
            val readerGate = sample.javaClass.getDeclaredField("gate").apply { isAccessible = true }.get(sample)
            val acquired = CountDownLatch(1)
            val probe = Thread { synchronized(readerGate) { acquired.countDown() } }
            probeForCleanup = probe
            probe.start()
            assertTrue("Native call must not hold the sample gate", acquired.await(5, TimeUnit.SECONDS))
            probe.join(5000)
            operation.close()
            assertTrue(sourceCloseDispatched.await(5, TimeUnit.SECONDS))
            assertTrue(wipeDispatched.await(5, TimeUnit.SECONDS))
            fixtureGuard.check()
            assertEquals(1L, jniReturned.count)
            assertFalse(owned.retirement.isComplete)
            assertTrue(checkNotNull(bytes.get()).all { it == 9.toByte() })
            // Source close unblocks readAt; this extra fixture latch lets us inspect
            // the original while the actual Native callback still has not returned.
            source.allowReturn.countDown()
            assertTrue(callerReturned.await(10, TimeUnit.SECONDS))
            caller.join(5000)
            assertFalse(caller.isAlive)
            assertEquals(0L, jniReturned.count)
            assertNull("Native callback waits must complete by cancellation, not timeout", source.callbackFailure.get())
            assertTrue("Native callback must reach the result before guard denial", callbackReachedResult.get())
            assertTrue("revoked callback must deny its result", failure.get() is IllegalStateException)
            assertTrue(owned.retirement.await(5, TimeUnit.SECONDS))
            assertArrayEquals(ByteArray(16), bytes.get())
        } finally {
            sourceForCleanup?.close()
            sourceForCleanup?.allowReturn?.countDown()
            callerForCleanup?.join(10000)
            probeForCleanup?.join(5000)
            operation.close()
            ownedForCleanup?.close()
            // No concurrent MediaExtractor release is inferred or exercised.
            // A still-live failed caller keeps the fixture's Native charge pinned.
            if (callerForCleanup?.isAlive != true) {
                extractorForCleanup?.let {
                    it.close()
                    assertTrue(it.retirement.await(5, TimeUnit.SECONDS))
                }
                fixtureOperation.close()
                authority.revoke()
            }
        }
    }

    private class BlockingSource : MediaDataSource() {
        val entered = CountDownLatch(1)
        private val cancelled = CountDownLatch(1)
        val allowReturn = CountDownLatch(1)
        val callbackFailure = AtomicReference<Throwable?>()
        @Volatile private var retired = false
        override fun getSize(): Long = 1024
        override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
            if (size == 0) return 0
            if (retired) return -1
            entered.countDown()
            try {
                check(cancelled.await(10, TimeUnit.SECONDS)) { "funded source close did not unblock Native read" }
                check(allowReturn.await(10, TimeUnit.SECONDS)) { "test did not release actual Native callback" }
                check(retired)
                return -1
            } catch (problem: Throwable) {
                callbackFailure.set(problem)
                throw problem
            }
        }
        override fun close() {
            retired = true
            cancelled.countDown()
        }
    }
}
