package uk.co.traynor.privategallery.core.security

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.*
import org.junit.Test
import uk.co.traynor.privategallery.core.vault.runScopedImport

class OwnedManifestInputTest {
    @Test fun completeFourteenChildAdmissionDeniesEveryFactoryWhenOnlyThirteenSlotsFree() = isolated { _, guard, _ ->
        val holds = (1..3).map { guard.createOwned(OwnedResourceManifest.io("hold")) { attach("hold", AutoCloseable {}) } }
        var entered = false
        try {
            assertThrows(IllegalStateException::class.java) { guard.createOwnedInput(manifest(14)) {
                entered = true
                create("root", { it: InputStream -> it.close() }) { ByteArrayInputStream(byteArrayOf(42)) }
            } }
            assertFalse(entered)
        } finally { holds.forEach { guard.retire(it) } }
    }

    @Test fun fourteenChildrenBesideTwoActualPreclaimedJobsNeedNoForwardingWorker() = isolated { _, guard, op ->
        val holds = (1..2).map { op.createOwnedJob { attach -> kotlinx.coroutines.Job().also(attach) } }
        val closed = CountDownLatch(1);var commits = 0
        try {
            val result = runScopedImport(guard, Any(), { error("raw source") }, { it.read() }, { commits++; it }, ownedSource = {
                guard.createOwnedInput(manifest(14)) {
                    val root = create("root", { it: InputStream -> it.close() }) { object : ByteArrayInputStream(byteArrayOf(42)) {
                        override fun close() { closed.countDown() }
                    } }
                    discardChildren(*(1..13).map { "child$it" }.toTypedArray())
                    root
                }
            })
            assertEquals(42, result);assertEquals(1, commits);assertEquals(0L, closed.count)
        } finally { holds.forEach { it.cancel() } }
    }

    @Test fun foreignUnclaimedRootCannotBeRestampedByAnotherLiveGuard() = isolated { _, guardA, op ->
        val guardB = ScopedIoGuard(op, PrimaryScope.WRITE);var foreign: ReservedValue<InputStream>? = null;var closes = 0
        val originalA = guardA.createOwned(OwnedResourceManifest.io("root")) {
            create("root", { it: InputStream -> it.close() }) { object : ByteArrayInputStream(byteArrayOf(42)) {
                override fun close() { closes++ }
            } }.also { foreign = it }
        }
        try {
            assertThrows(IllegalStateException::class.java) { guardB.createOwnedInput(OwnedResourceManifest.io("root")) { checkNotNull(foreign) } }
            assertEquals(42, originalA.value.value.read());assertEquals(0, closes)
        } finally { guardA.retire(originalA) }
        assertEquals(1, closes)
    }

    @Test fun secondaryAdapterCannotSubstituteForTheExactRoot() = isolated { _, guard, _ ->
        val closes = java.util.concurrent.atomic.AtomicInteger()
        assertThrows(IllegalStateException::class.java) { guard.createOwnedInput(OwnedResourceManifest.io("root", "secondary")) {
            create("root", { it: InputStream -> it.close() }) { object : ByteArrayInputStream(byteArrayOf(1)) { override fun close() { closes.incrementAndGet() } } }
            create("secondary", { it: InputStream -> it.close() }) { object : ByteArrayInputStream(byteArrayOf(2)) { override fun close() { closes.incrementAndGet() } } }
        } }
        assertEquals(2, closes.get())
    }

    @Test fun retiringConstructionCleansLateRootAndReturnsNoStampedHandle() = isolated { authority, guard, _ ->
        val entered=CountDownLatch(1);val finish=CountDownLatch(1);val closed=CountDownLatch(1);val failure=AtomicReference<Throwable?>();val handle=AtomicReference<OwnedInput?>()
        val worker=Thread {
            try { handle.set(guard.createOwnedInput(OwnedResourceManifest.io("root")) {
                create("root", { it: InputStream -> it.close() }) {
                    entered.countDown();assertTrue(finish.await(5, TimeUnit.SECONDS))
                    object : ByteArrayInputStream(byteArrayOf(42)) { override fun close() { closed.countDown() } }
                }
            }) } catch(t:Throwable) { failure.set(t) }
        }
        worker.start();assertTrue(entered.await(5,TimeUnit.SECONDS));authority.revoke();assertFalse(authority.cleanupComplete);finish.countDown();worker.join(5000)
        assertFalse(worker.isAlive);assertNull(handle.get());assertNotNull(failure.get());assertEquals(0L,closed.count)
    }

    @Test fun allPartialUnblockersDispatchBeforeFactoryFailureReturns() = isolated { _, guard, _ ->
        val disconnected=CountDownLatch(1);val inputClosed=CountDownLatch(1);val storage=Any()
        assertThrows(IOException::class.java) { guard.createOwnedInput<InputStream>(OwnedResourceManifest.io("root","transport")) {
            create("root", { it: InputStream -> it.close() }) { object : ByteArrayInputStream(byteArrayOf(42)) {
                override fun close() { assertFalse(Thread.holdsLock(storage));assertTrue(disconnected.await(5,TimeUnit.SECONDS));inputClosed.countDown() }
            } }
            create("transport", { _:Any -> assertFalse(Thread.holdsLock(storage));disconnected.countDown() }) { Any() }
            throw IOException("synthetic producer failure")
        } }
        assertEquals(0L,inputClosed.count)
    }

    @Test fun failedRootRetirementDeniesSelectionAndFutureAuthentication() = isolated { authority, guard, _ ->
        var committed = 0
        assertThrows(IOException::class.java) { runScopedImport(guard,Any(),{ error("raw") },{ it.read() },{ committed++ },ownedSource={
            guard.createOwnedInput(OwnedResourceManifest.io("root")) {
                create("root", { it: InputStream -> it.close() }) { object : ByteArrayInputStream(byteArrayOf(42)) {
                    override fun close():Unit = throw IOException("synthetic close failure")
                } }
            }
        }) }
        assertEquals(0,committed);authority.revoke();assertFalse(authority.cleanupComplete)
        val key=ByteArray(32){7};assertThrows(IllegalStateException::class.java){authority.open(key)};assertArrayEquals(ByteArray(32),key)
    }

    @Test fun phaseNativeFailurePreventsFinalRootAndRetainsFailedAccounting() = isolated { authority, guard, _ ->
        var rootFactory = 0
        assertThrows(IOException::class.java) { guard.createOwnedInput(OwnedResourceManifest.io("root","transport")) {
            create("transport", { _:Any -> throw IOException("synthetic phase failure") }) { Any() }
            retireChildren("transport")
            create("root", { it:InputStream -> it.close() }) { rootFactory++;ByteArrayInputStream(byteArrayOf(42)) }
        } }
        assertEquals(0,rootFactory);authority.revoke();assertFalse(authority.cleanupComplete)
    }

    @Test fun exactGuardMismatchDeniesUseAndWipesRequestedDestinationSliceAfterClose() = isolated { _, guard, op ->
        val handle=guard.createOwnedInput(OwnedResourceManifest.io("root")) { create("root",{it:InputStream -> it.close()}) {ByteArrayInputStream(byteArrayOf(42))} }
        val facade=handle.adopt(guard);assertThrows(IllegalStateException::class.java){handle.adopt(ScopedIoGuard(op,PrimaryScope.WRITE))};handle.close()
        val out=ByteArray(4){9};assertThrows(IllegalStateException::class.java){facade.read(out,1,2)};assertArrayEquals(byteArrayOf(9,0,0,9),out)
    }
    private fun manifest(count:Int)=OwnedResourceManifest.io("root",*(1 until count).map{"child$it"}.toTypedArray())
    private fun isolated(test:(PrimarySessionAuthority,ScopedIoGuard,PrimaryOperation)->Unit) {
        val authority=testPrimaryAuthority();authority.open(ByteArray(32));val op=authority.operationOrNull(PrimaryScope.entries.toSet())!!
        try {test(authority,ScopedIoGuard(op,PrimaryScope.WRITE),op)} finally {op.close();authority.revoke()}
    }
}
