package uk.co.traynor.privategallery.core.security

import android.media.MediaDataSource
import android.media.MediaExtractor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.IOException
import java.nio.ByteBuffer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Real JNI callback scheduling and public synthetic MP4; no owner data or network.
 * The first case isolates extractor ownership; subsequent cases use the same original
 * with guarded Native quiescence. Neither releases concurrently with setDataSource
 * or establishes a complete production extractor/validation manifest. */
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

    @Test fun ownedNativeExtractorDisposalWaitsForActualJniReturnWhileSourceUnblocks() {
        val authority=PrimarySessionAuthority {0}
        for(name in listOf("ioReleasePool","presentationReleasePool")) {
            authority.javaClass.getDeclaredField(name).apply{isAccessible=true}.set(authority,ReleasePool(16))
        }
        authority.open(ByteArray(32){11})
        val operation=authority.operationOrNull(setOf(PrimaryScope.WRITE))!!
        val guard=ScopedIoGuard(operation,PrimaryScope.WRITE)
        val sourceClose=CountDownLatch(1);val jniReturned=CountDownLatch(1);val nativeReleased=CountDownLatch(1);val callerReturned=CountDownLatch(1)
        val reachedResult=AtomicBoolean(false);val didInvoke=AtomicBoolean(false);val failure=AtomicReference<Throwable?>()
        var observedUse:OwnedNativeUse<MediaExtractor>?=null
        var sourceForCleanup:BlockingSource?=null;var callerForCleanup:Thread?=null;var probeForCleanup:Thread?=null
        var ownedForCleanup:OwnedResource<ReservedValue<BlockingSource>>?=null
        lateinit var use:OwnedNativeUse<MediaExtractor>
        try {
            lateinit var source:BlockingSource
            val owned=guard.createOwned(OwnedResourceManifest.io("source","extractor")) {
                val root=create("source",{it:BlockingSource->sourceClose.countDown();it.close()}){BlockingSource()}
                source=root.value;sourceForCleanup=source
                use=guardedNative("extractor",guard,{it:MediaExtractor->
                    if(didInvoke.get()) {
                        assertEquals("actual JNI must return before Native release",0L,jniReturned.count)
                        val handle=checkNotNull(observedUse)
                        val gate=handle.javaClass.getDeclaredField("gate").apply{isAccessible=true}.get(handle)
                        assertFalse("actual Native release must run outside reader gate",Thread.holdsLock(gate))
                    }
                    // A never-invoked setup failure still disposes its actual Native value.
                    it.release();nativeReleased.countDown()
                }){MediaExtractor()}
                observedUse=use
                root
            }
            ownedForCleanup=owned
            val caller=Thread {
                try {
                    use.useInt {extractor->
                        didInvoke.set(true)
                        try {extractor.setDataSource(source)}catch(_:IOException) {
                            // Synthetic EOF is an invalid media container.
                        }finally{jniReturned.countDown()}
                        reachedResult.set(true);1
                    }
                }catch(t:Throwable){failure.set(t)}finally{callerReturned.countDown()}
            }
            callerForCleanup=caller;caller.start()
            assertTrue("actual Native readAt entry required",source.entered.await(10,TimeUnit.SECONDS))
            assertEquals(1L,jniReturned.count)
            val gate=use.javaClass.getDeclaredField("gate").apply{isAccessible=true}.get(use)
            val acquired=CountDownLatch(1);val probe=Thread{synchronized(gate){acquired.countDown()}}
            probeForCleanup=probe;probe.start();assertTrue(acquired.await(5,TimeUnit.SECONDS));probe.join(5000)
            operation.close();assertTrue(sourceClose.await(5,TimeUnit.SECONDS))
            assertEquals(1L,jniReturned.count);assertEquals(1L,nativeReleased.count);assertFalse(owned.retirement.isComplete)
            source.allowReturn.countDown()
            assertTrue(callerReturned.await(10,TimeUnit.SECONDS));caller.join(5000);assertFalse(caller.isAlive)
            assertNull(source.callbackFailure.get());assertTrue(reachedResult.get());assertTrue(failure.get() is IllegalStateException)
            assertTrue(nativeReleased.await(5,TimeUnit.SECONDS));assertTrue(owned.retirement.await(5,TimeUnit.SECONDS))
        }finally {
            sourceForCleanup?.close();sourceForCleanup?.allowReturn?.countDown()
            callerForCleanup?.join(10000);probeForCleanup?.join(5000)
            operation.close();ownedForCleanup?.close();authority.revoke()
        }
    }

    @Test fun actualNativeSampleWritesOriginalArrayAndRetirementWaitsForBorrowReturn() {
        // This public fixture is test input, not an owner-data or production staging path.
        val fixture = InstrumentationRegistry.getInstrumentation().context.assets
            .open("browser-media/valid.mp4").use { it.readBytes() }
        val authority = PrimarySessionAuthority { 0 }
        for (name in listOf("ioReleasePool", "presentationReleasePool")) {
            authority.javaClass.getDeclaredField(name).apply { isAccessible = true }
                .set(authority, ReleasePool(16))
        }
        authority.open(ByteArray(32) { 11 })
        val operation = authority.operationOrNull(setOf(PrimaryScope.WRITE))!!
        val guard = ScopedIoGuard(operation, PrimaryScope.WRITE)
        val sourceClosed = CountDownLatch(1)
        val sampleWritten = CountDownLatch(1)
        val allowBorrowReturn = CountDownLatch(1)
        val callerReturned = CountDownLatch(1)
        val nativeReleased = CountDownLatch(1)
        val bytes = AtomicReference<ByteArray?>()
        val snapshot = AtomicReference<ByteArray?>()
        val failure = AtomicReference<Throwable?>()
        var ownedForCleanup: OwnedResource<ReservedValue<MediaDataSource>>? = null
        var callerForCleanup: Thread? = null
        lateinit var sample: OwnedNativeOutputBuffer
        lateinit var native: OwnedNativeUse<MediaExtractor>
        try {
            val owned = guard.createOwned(OwnedResourceManifest.io("source", "sample", "extractor")) {
                val root = create("source", { it: MediaDataSource -> it.close() }) {
                    object : MediaDataSource() {
                        @Volatile private var retired = false
                        override fun getSize(): Long = fixture.size.toLong()
                        override fun readAt(position: Long, target: ByteArray, offset: Int, size: Int): Int {
                            if (size == 0) return 0
                            if (retired || position < 0 || position >= fixture.size) return -1
                            val count = minOf(size, fixture.size - position.toInt())
                            System.arraycopy(fixture, position.toInt(), target, offset, count)
                            return count
                        }
                        override fun close() { retired = true; sourceClosed.countDown() }
                    } as MediaDataSource
                }
                sample = nativeOutputBuffer("sample", guard, 256 * 1024).value
                native = guardedNative("extractor", guard, { it: MediaExtractor ->
                    it.release(); nativeReleased.countDown()
                }) { MediaExtractor() }
                root
            }
            ownedForCleanup = owned
            val caller = Thread {
                try {
                    sample.useBytes { actual ->
                        actual.fill(0x6d.toByte()); bytes.set(actual)
                        native.useInt { extractor ->
                            extractor.setDataSource(owned.value.value)
                            check(extractor.trackCount > 0) { "public fixture must have a track" }
                            extractor.selectTrack(0)
                            // This view stays inside the synchronous original-array borrow.
                            val count = extractor.readSampleData(ByteBuffer.wrap(actual), 0)
                            check(count in 1..actual.size) { "real Native sample write required" }
                            check((0 until count).any { actual[it] != 0x6d.toByte() }) { "Native must change original backing bytes" }
                            check((count until actual.size).all { actual[it] == 0x6d.toByte() }) { "Native sample must preserve unused tail" }
                            snapshot.set(actual.copyOf())
                            // Both exact gates must be free while the borrowed callback runs.
                            for (handle in listOf(sample, native)) {
                                val gate = checkNotNull(handle.javaClass.getDeclaredField("gate")
                                    .apply { isAccessible = true }.get(handle))
                                val entered = CountDownLatch(1)
                                val probe = Thread { synchronized(gate) { entered.countDown() } }
                                probe.start()
                                check(entered.await(5, TimeUnit.SECONDS)) { "Native callback holds a reader gate" }
                                probe.join(5000)
                            }
                            sampleWritten.countDown()
                            check(allowBorrowReturn.await(10, TimeUnit.SECONDS)) { "test did not release actual borrow" }
                            count
                        }
                    }
                } catch (problem: Throwable) { failure.set(problem) }
                finally { callerReturned.countDown() }
            }
            callerForCleanup = caller; caller.start()
            assertTrue("actual Native sample bytes required", sampleWritten.await(15, TimeUnit.SECONDS))
            operation.close()
            assertTrue(sourceClosed.await(5, TimeUnit.SECONDS))
            assertEquals(1L, callerReturned.count)
            assertEquals(1L, nativeReleased.count)
            assertFalse(owned.retirement.isComplete)
            assertArrayEquals(checkNotNull(snapshot.get()), checkNotNull(bytes.get()))
            allowBorrowReturn.countDown()
            assertTrue(callerReturned.await(10, TimeUnit.SECONDS)); caller.join(5000)
            assertFalse(caller.isAlive)
            assertTrue("retired original must reject callback result", failure.get() is IllegalStateException)
            assertTrue(nativeReleased.await(5, TimeUnit.SECONDS))
            assertTrue(owned.retirement.await(5, TimeUnit.SECONDS))
            assertArrayEquals(ByteArray(256 * 1024), checkNotNull(bytes.get()))
            assertThrows(IllegalStateException::class.java) { native.useInt { fail("late Native use"); 0 } }
        } finally {
            allowBorrowReturn.countDown(); callerForCleanup?.join(15000)
            operation.close(); ownedForCleanup?.close(); authority.revoke()
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
