package uk.co.traynor.privategallery.core.security

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import org.junit.Assert.*
import org.junit.Test

class ReservedValueFactoryTest {
    @Test fun unavailableNativeChildKeepsItsReleaseInvocationIdle() {
        val invoked = CountDownLatch(1)
        val pool = ReleasePool(16, allocateInvocation = { action, returned ->
            ReleaseInvocation({ invoked.countDown(); action() }, returned)
        })
        val authority = testPrimaryAuthority()
        PrimarySessionAuthority::class.java.getDeclaredField("ioReleasePool").apply { isAccessible = true }.set(authority, pool)
        authority.open(ByteArray(32)); val op = authority.operationOrNull(setOf(PrimaryScope.READ))!!
        val entered = CountDownLatch(1); val allow = CountDownLatch(1); val done = CountDownLatch(1)
        val worker = thread(isDaemon = true) {
            try { op.createOwned(OwnedResourceManifest.io("native")) {
                create("native", { _: Any -> }) { entered.countDown(); allow.await(); Any() }
            }; fail("revoked original must not return") } catch (_: IllegalStateException) { }
            finally { done.countDown() }
        }
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS)); authority.revoke()
            assertFalse("release must not wait for unavailable native construction", invoked.await(200, TimeUnit.MILLISECONDS))
            assertFalse(authority.cleanupComplete); assertEquals(1, pool.occupied)
            allow.countDown(); assertTrue(invoked.await(5, TimeUnit.SECONDS)); assertTrue(done.await(5, TimeUnit.SECONDS))
            awaitCleanup(authority); assertEquals(0, pool.occupied)
        } finally { allow.countDown(); worker.interrupt(); assertTrue(done.await(5, TimeUnit.SECONDS)) }
    }

    @Test fun lateActualNativeChildReleasesBeforeEnclosingFactoryReturns() {
        val authority = testPrimaryAuthority(); authority.open(ByteArray(32))
        val op = authority.operationOrNull(setOf(PrimaryScope.READ))!!
        val creating = CountDownLatch(1); val created = CountDownLatch(1); val enclosing = CountDownLatch(1)
        val allowReturn = CountDownLatch(1); val disposed = CountDownLatch(1); val done = CountDownLatch(1)
        val failure = AtomicReference<Throwable>()
        val worker = thread(isDaemon = true) {
            try { op.createOwned(OwnedResourceManifest.io("native")) {
                val child = create("native", { _: Any -> disposed.countDown() }) { creating.countDown(); created.await(); Any() }
                enclosing.countDown(); allowReturn.await(); child
            }; fail("revoked original must not return") } catch (problem: Throwable) { failure.set(problem) }
            finally { done.countDown() }
        }
        try {
            assertTrue(creating.await(5, TimeUnit.SECONDS)); authority.revoke()
            assertEquals(1L, disposed.count); assertFalse(authority.cleanupComplete)
            created.countDown(); assertTrue(enclosing.await(5, TimeUnit.SECONDS))
            assertTrue(disposed.await(5, TimeUnit.SECONDS)); assertFalse(authority.cleanupComplete)
            assertEquals(1L, done.count)
            allowReturn.countDown(); assertTrue(done.await(5, TimeUnit.SECONDS)); awaitCleanup(authority)
            assertTrue(failure.get() is IllegalStateException)
        } finally { created.countDown(); allowReturn.countDown(); worker.interrupt(); assertTrue(done.await(5, TimeUnit.SECONDS)) }
    }

    @Test fun revocationBetweenChildrenDeniesNextNativeFactoryBeforeCreation() {
        val authority = testPrimaryAuthority(); authority.open(ByteArray(32))
        val op = authority.operationOrNull(setOf(PrimaryScope.READ))!!; var secondCalls = 0
        assertThrows(IllegalStateException::class.java) {
            op.createOwned(OwnedResourceManifest.io("first", "second")) {
                val first = create("first", { _: Any -> }) { Any() }
                authority.revoke()
                create("second", { _: Any -> }) { secondCalls++; Any() }
                first
            }
        }
        awaitCleanup(authority); assertEquals(0, secondCalls)
    }

    @Test fun malformedResultCannotReacquireAnotherAdaptersActualValue() {
        val authority = testPrimaryAuthority(); authority.open(ByteArray(32))
        val op = authority.operationOrNull(setOf(PrimaryScope.READ))!!; val closed = AtomicInteger()
        val shared = AutoCloseable { closed.incrementAndGet() }
        assertThrows(IllegalStateException::class.java) {
            op.createOwned(OwnedResourceManifest.io("unused", "native")) {
                create("native", { child: AutoCloseable -> child.close() }) { shared }
                shared
            }
        }
        authority.revoke(); awaitCleanup(authority); assertEquals(1, closed.get())
    }

    @Test fun nativeAdapterCannotAcquireAlreadyOwnedCloseableChild() {
        val authority = testPrimaryAuthority(); authority.open(ByteArray(32))
        val op = authority.operationOrNull(setOf(PrimaryScope.READ))!!; val closed = AtomicInteger()
        val shared = AutoCloseable { closed.incrementAndGet() }
        assertThrows(IllegalStateException::class.java) {
            op.createOwned(OwnedResourceManifest.io("first", "second")) {
                attach("first", shared)
                create("second", { child: AutoCloseable -> child.close() }) { shared }
                shared
            }
        }
        authority.revoke(); awaitCleanup(authority); assertEquals(1, closed.get())
    }

    @Test fun nativeActualValueCannotBeAttachedUnderAnotherChildToken() {
        val authority = testPrimaryAuthority(); authority.open(ByteArray(32))
        val op = authority.operationOrNull(setOf(PrimaryScope.READ))!!; val closed = AtomicInteger()
        val shared = AutoCloseable { closed.incrementAndGet() }
        assertThrows(IllegalStateException::class.java) {
            op.createOwned(OwnedResourceManifest.io("first", "second")) {
                val first = create("first", { child: AutoCloseable -> child.close() }) { shared }
                attach("second", shared)
                first
            }
        }
        authority.revoke(); awaitCleanup(authority); assertEquals(1, closed.get())
    }

    @Test fun duplicateActualNativeValueKeepsItsSingleOriginalDisposal() {
        val authority = testPrimaryAuthority(); authority.open(ByteArray(32))
        val op = authority.operationOrNull(setOf(PrimaryScope.READ))!!
        val shared = Any(); val closed = AtomicInteger()
        assertThrows(IllegalStateException::class.java) {
            op.createOwned(OwnedResourceManifest.io("first", "second")) {
                val first = create("first", { _: Any -> closed.incrementAndGet() }) { shared }
                create("second", { _: Any -> closed.incrementAndGet() }) { shared }
                first
            }
        }
        authority.revoke()
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (!authority.cleanupComplete && System.nanoTime() < deadline) Thread.yield()
        assertTrue(authority.cleanupComplete); assertEquals(1, closed.get())
    }

    @Test fun exhaustedNativeAdmissionDoesNotInvokeFactory() {
        val authority = testPrimaryAuthority(); authority.open(ByteArray(32))
        val op = authority.operationOrNull(setOf(PrimaryScope.READ))!!
        val owners = (1..16).map { op.createOwned(OwnedResourceManifest.io("native")) {
            create("native", { _: Any -> }) { Any() }
        } }
        var calls = 0
        assertThrows(IllegalStateException::class.java) {
            op.createOwned(OwnedResourceManifest.io("native")) { create("native", { _: Any -> }) { calls++; Any() } }
        }
        assertEquals(0, calls)
        owners.forEach { it.close() }; owners.forEach { assertTrue(it.retirement.await(5, TimeUnit.SECONDS)) }
        op.close(); authority.revoke()
    }

    @Test fun revokeDuringNativeCreationWaitsForActualLateChildRelease() {
        val authority = testPrimaryAuthority(); authority.open(ByteArray(32))
        val op = authority.operationOrNull(setOf(PrimaryScope.READ))!!
        val entered = CountDownLatch(1); val allow = CountDownLatch(1); val closeEntered = CountDownLatch(1)
        val allowClose = CountDownLatch(1); val done = CountDownLatch(1)
        val actual = Any(); val closed = AtomicInteger(); val failure = AtomicReference<Throwable>()
        val worker = thread(isDaemon = true) {
            try { op.createOwned(OwnedResourceManifest.io("native")) {
                create("native", { child: Any ->
                    assertSame(actual, child); closeEntered.countDown(); allowClose.await(); closed.incrementAndGet()
                }) { entered.countDown(); allow.await(); actual }
            }; fail("revoked original must not return") } catch (problem: Throwable) { failure.set(problem) }
            finally { done.countDown() }
        }
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS)); authority.revoke()
            assertFalse(authority.cleanupComplete); assertEquals(0, closed.get())
            val key = ByteArray(32) { 9 }; assertThrows(IllegalStateException::class.java) { authority.open(key) }
            assertArrayEquals(ByteArray(32), key)
            allow.countDown(); assertTrue(closeEntered.await(5, TimeUnit.SECONDS)); assertFalse(authority.cleanupComplete)
            allowClose.countDown(); assertTrue(done.await(5, TimeUnit.SECONDS))
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
            while (!authority.cleanupComplete && System.nanoTime() < deadline) Thread.yield()
            assertTrue(authority.cleanupComplete); assertEquals(1, closed.get())
            assertTrue(failure.get() is IllegalStateException)
        } finally { allow.countDown(); allowClose.countDown(); worker.interrupt(); assertTrue(done.await(5, TimeUnit.SECONDS)) }
    }

    @Test fun factoryFailureReleasesItsUnusedManifestWithoutCallingNativeDisposal() {
        val authority = testPrimaryAuthority(); authority.open(ByteArray(32))
        val op = authority.operationOrNull(setOf(PrimaryScope.READ))!!; var disposed = 0
        assertThrows(java.io.IOException::class.java) {
            op.createOwned(OwnedResourceManifest.io("native")) {
                create("native", { _: Any -> disposed++ }) { throw java.io.IOException("synthetic creation failure") }
            }
        }
        authority.revoke()
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (!authority.cleanupComplete && System.nanoTime() < deadline) Thread.yield()
        assertTrue(authority.cleanupComplete); assertEquals(0, disposed)
    }

    private fun awaitCleanup(authority: PrimarySessionAuthority) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (!authority.cleanupComplete && System.nanoTime() < deadline) Thread.yield()
        assertTrue(authority.cleanupComplete)
    }
}
