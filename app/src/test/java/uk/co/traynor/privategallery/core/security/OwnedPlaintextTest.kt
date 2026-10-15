package uk.co.traynor.privategallery.core.security

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.*
import org.junit.Test

class OwnedPlaintextTest {
    @Test fun samePoolReservationCannotAuthorizeAnUnattachedPlaintextCell() = isolated { _, operation ->
        var reservation: ReleaseReservation? = null
        val hold = operation.createSessionOwned(OwnedResourceManifest.presentation("hold")) {
            reservation = original
            attach("hold", AutoCloseable {})
        }
        val cell = OriginalPlaintext(operation, checkNotNull(reservation))
        cell.bind(byteArrayOf(4))
        cell.finishFactory()
        var uses = 0
        try {
            assertThrows(IllegalStateException::class.java) { cell.useBytes { uses++ } }
            assertEquals(0, uses)
        } finally {
            cell.close()
            hold.close()
            assertTrue(hold.retirement.await(5, TimeUnit.SECONDS))
        }
    }

    @Test fun admittedSessionSourceSurvivesProducerLeaseCloseUntilEpochRevocation() = isolated { authority, operation ->
        val actual = byteArrayOf(5)
        val owned = operation.createOwnedPlaintext { actual }
        operation.close()
        owned.useBytes { assertArrayEquals(byteArrayOf(5), it) }
        authority.revoke()
        assertThrows(IllegalStateException::class.java) { owned.useBytes { fail("revoked source") } }
        assertTrue(owned.retirement.await(5, TimeUnit.SECONDS))
        assertArrayEquals(ByteArray(1), actual)
    }

    @Test fun failedBorrowDoesNotDestroyStillOwnedSource() = isolated { _, operation ->
        val actual = byteArrayOf(6)
        val owned = operation.createOwnedPlaintext { actual }
        try {
            assertThrows(UnsupportedOperationException::class.java) { owned.useBytes { throw UnsupportedOperationException("decode") } }
            owned.useBytes { assertArrayEquals(byteArrayOf(6), it) }
        } finally { owned.close(); assertTrue(owned.retirement.await(5, TimeUnit.SECONDS)) }
        assertArrayEquals(ByteArray(1), actual)
    }

    @Test fun repeatedNormalRetirementReturnsPresentationCapacity() = isolated { _, operation ->
        repeat(40) {
            val actual = byteArrayOf(1, 2, 3)
            val owned = operation.createOwnedPlaintext { actual }
            owned.useBytes { assertSame(actual, it) }
            owned.close()
            assertTrue(owned.retirement.await(5, TimeUnit.SECONDS))
            assertArrayEquals(ByteArray(3), actual)
            assertThrows(IllegalStateException::class.java) { owned.useBytes { fail("retired borrow") } }
        }
    }

    @Test fun capacityDeniesBeforeDecryptFactory() = isolated { _, operation ->
        val holds = List(16) { operation.createSessionOwned(OwnedResourceManifest.presentation("hold")) { attach("hold", AutoCloseable {}) } }
        var calls = 0
        try {
            assertThrows(IllegalStateException::class.java) { operation.createOwnedPlaintext { calls++; byteArrayOf(1) } }
            assertEquals(0, calls)
        } finally { holds.forEach { it.close(); assertTrue(it.retirement.await(5, TimeUnit.SECONDS)) } }
    }

    @Test fun revokeDuringBorrowDefersWipeUntilNativeReturns() = isolated { authority, operation ->
        val closeReturned = CountDownLatch(1)
        val pool = ReleasePool(16, allocateInvocation = { action, returned ->
            ReleaseInvocation({ action(); closeReturned.countDown() }, returned)
        })
        authority.javaClass.getDeclaredField("presentationReleasePool").apply { isAccessible = true }.set(authority, pool)
        val actual = byteArrayOf(9)
        val owned = operation.createOwnedPlaintext { actual }
        assertThrows(IllegalStateException::class.java) {
            owned.useBytes {
                authority.revoke()
                assertTrue(closeReturned.await(5, TimeUnit.SECONDS))
                assertArrayEquals(byteArrayOf(9), it)
                assertFalse(owned.retirement.isComplete)
                assertFalse(authority.cleanupComplete)
            }
        }
        assertTrue(owned.retirement.await(5, TimeUnit.SECONDS))
        assertArrayEquals(ByteArray(1), actual)
    }

    @Test fun revokedBlockedConstructorWipesLateArrayWithoutPublishing() = isolated { authority, operation ->
        val entered = CountDownLatch(1); val allow = CountDownLatch(1)
        val actual = byteArrayOf(7)
        val result = AtomicReference<OwnedPlaintext?>(); val failure = AtomicReference<Throwable?>()
        val thread = Thread {
            try { result.set(operation.createOwnedPlaintext { entered.countDown(); check(allow.await(5, TimeUnit.SECONDS)); actual }) }
            catch (problem: Throwable) { failure.set(problem) }
        }
        thread.start()
        try { assertTrue(entered.await(5, TimeUnit.SECONDS)); authority.revoke(); assertFalse(authority.cleanupComplete) }
        finally { allow.countDown(); thread.join(5000) }
        assertFalse(thread.isAlive); assertNull(result.get()); assertNotNull(failure.get())
        assertArrayEquals(ByteArray(1), actual)
    }

    @Test fun boundedOverlappingReadersRetainOneOriginalUntilEveryReturn() = isolated { authority, operation ->
        val actual = byteArrayOf(8)
        val owned = operation.createOwnedPlaintext { actual }
        fun borrow(depth: Int) {
            owned.useBytes {
                assertArrayEquals(byteArrayOf(8), it)
                if (depth < 16) borrow(depth + 1) else {
                    assertThrows(IllegalStateException::class.java) { owned.useBytes { fail("borrow limit") } }
                    authority.revoke()
                    assertFalse(owned.retirement.isComplete)
                }
                assertArrayEquals(byteArrayOf(8), it)
            }
        }
        assertThrows(IllegalStateException::class.java) { borrow(1) }
        assertTrue(owned.retirement.await(5, TimeUnit.SECONDS))
        assertArrayEquals(ByteArray(1), actual)
    }

    @Test fun concurrentReaderReturnCannotWipeAnotherLiveBorrow() = isolated { authority, operation ->
        val actual = byteArrayOf(9)
        val owned = operation.createOwnedPlaintext { actual }
        val entered = CountDownLatch(1); val allow = CountDownLatch(1)
        val failed = AtomicReference<Throwable?>()
        val reader = Thread {
            try { owned.useBytes { entered.countDown(); check(allow.await(5, TimeUnit.SECONDS)); assertArrayEquals(byteArrayOf(9), it) } }
            catch (failure: Throwable) { failed.set(failure) }
        }
        reader.start()
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            owned.useBytes { assertArrayEquals(byteArrayOf(9), it) }
            authority.revoke()
            assertFalse(owned.retirement.isComplete)
            assertArrayEquals(byteArrayOf(9), actual)
        } finally { allow.countDown(); reader.join(5000) }
        assertFalse(reader.isAlive)
        assertTrue(failed.get() is IllegalStateException) // postcheck rejects revoked work
        assertTrue(owned.retirement.await(5, TimeUnit.SECONDS))
        assertArrayEquals(ByteArray(1), actual)
    }

    @Test fun extractorPositionsBeyondEndReturnEof() = isolated { _, operation ->
        val owned = operation.createOwnedPlaintext { byteArrayOf(1) }
        try { assertEquals(-1, owned.readAt(Long.MAX_VALUE, ByteArray(1), 0, 1)) }
        finally { owned.close(); assertTrue(owned.retirement.await(5, TimeUnit.SECONDS)) }
    }

    private fun isolated(block: (PrimarySessionAuthority, PrimaryOperation) -> Unit) {
        val authority = testPrimaryAuthority(); authority.open(ByteArray(32) { 4 })
        val operation = checkNotNull(authority.operationOrNull(setOf(PrimaryScope.READ)))
        try { block(authority, operation) } finally { operation.close(); authority.revoke() }
    }
}
