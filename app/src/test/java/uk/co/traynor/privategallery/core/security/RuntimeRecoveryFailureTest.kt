package uk.co.traynor.privategallery.core.security

import java.io.IOException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Job
import org.junit.Assert.*
import org.junit.Test
import uk.co.traynor.privategallery.core.domain.SecondarySessionAuthority

class RuntimeRecoveryFailureTest {
    @Test fun `normal retirement is process-visible before its first indexed dispatch`() {
        val pool = ReleasePool(2)
        val first = testPrimaryAuthority(); val next = testPrimaryAuthority()
        sharePool(first, pool); sharePool(next, pool)
        first.open(ByteArray(32)); val op = first.operationOrNull()!!
        val owned = op.createOwned { attach -> AutoCloseable {}.also(attach) }
        val field = ReleaseReservation::class.java.getDeclaredField("tickets").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST") val original = field.get(owned.original) as List<ReleaseTicket>
        val entered = CountDownLatch(1); val finish = CountDownLatch(1); val returned = CountDownLatch(1)
        field.set(owned.original, object : List<ReleaseTicket> by original {
            override fun get(index: Int): ReleaseTicket {
                entered.countDown(); check(finish.await(5, TimeUnit.SECONDS)); return original[index]
            }
        })
        kotlin.concurrent.thread(isDaemon = true) { try { owned.close() } finally { returned.countDown() } }
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS)); assertTrue(owned.original.retiring)
            val key = ByteArray(32) { 8 }
            assertThrows(IllegalStateException::class.java) { next.open(key) }
            assertArrayEquals(ByteArray(32), key)
        } finally { finish.countDown(); assertTrue(returned.await(5, TimeUnit.SECONDS)); field.set(owned.original, original); owned.close(); next.revoke(); first.revoke() }
    }

    @Test fun `iterator allocation cannot lose a just-created attached child`() {
        val reservation = ReleaseReservation(ReleasePool(1).reserveAll(Any(), 1))
        failTicketIterator(reservation)
        val closed = CountDownLatch(1); val actual = AutoCloseable { closed.countDown() }
        try { reservation.construct { attach(0, actual) } } catch (_: OutOfMemoryError) { }
        reservation.release()
        assertTrue("known actual child must retain its pre-funded release", closed.await(1, TimeUnit.SECONDS))
    }

    @Test fun `iterator allocation cannot lose a malformed factory result`() {
        val reservation = ReleaseReservation(ReleasePool(1).reserveAll(Any(), 1))
        failTicketIterator(reservation)
        val closed = CountDownLatch(1); val actual = AutoCloseable { closed.countDown() }
        try { reservation.construct { verifyResult(0, actual) } } catch (_: Throwable) { }
        reservation.release()
        assertTrue("malformed actual result must retain its pre-funded release", closed.await(1, TimeUnit.SECONDS))
    }

    private fun failTicketIterator(reservation: ReleaseReservation) {
        val field = ReleaseReservation::class.java.getDeclaredField("tickets").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST") val original = field.get(reservation) as List<ReleaseTicket>
        field.set(reservation, object : List<ReleaseTicket> by original {
            override fun iterator(): Iterator<ReleaseTicket> = throw OutOfMemoryError("actual-child iterator failed")
        })
    }

    @Test fun `normal close dispatch cannot lose its process barrier to iterator allocation`() {
        val pool = ReleasePool(2)
        val first = testPrimaryAuthority(); val next = testPrimaryAuthority()
        sharePool(first, pool); sharePool(next, pool)
        first.open(ByteArray(32)); val op = first.operationOrNull()!!
        val entered = CountDownLatch(1); val finish = CountDownLatch(1)
        val owned = op.createOwned { attach -> AutoCloseable { entered.countDown(); check(finish.await(5, TimeUnit.SECONDS)) }.also(attach) }
        val field = ReleaseReservation::class.java.getDeclaredField("tickets").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST") val original = field.get(owned.original) as List<ReleaseTicket>
        field.set(owned.original, object : List<ReleaseTicket> by original {
            override fun iterator(): Iterator<ReleaseTicket> = throw OutOfMemoryError("normal dispatch iterator failed")
        })
        try {
            try { owned.close() } catch (_: OutOfMemoryError) { }
            val key = ByteArray(32) { 8 }
            assertThrows(IllegalStateException::class.java) { next.open(key) }
            assertArrayEquals(ByteArray(32), key)
            assertTrue("dispatch requires no iterator allocation", entered.await(5, TimeUnit.SECONDS))
        } finally { field.set(owned.original, original); finish.countDown(); owned.close(); next.revoke() }
    }

    @Test fun `new Primary authority denies during an original revocation snapshot`() {
        val pool = ReleasePool(2)
        val first = testPrimaryAuthority(); val next = testPrimaryAuthority()
        sharePool(first, pool); sharePool(next, pool)
        first.open(ByteArray(32)); val op = first.operationOrNull()!!
        val owned = op.createOwned { attach -> AutoCloseable {}.also(attach) }
        val entered = CountDownLatch(1); val finish = CountDownLatch(1); val returned = CountDownLatch(1)
        blockSnapshot(op, entered, finish)
        kotlin.concurrent.thread(isDaemon = true) { try { first.revoke() } finally { returned.countDown() } }
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            val key = ByteArray(32) { 8 }
            assertThrows(IllegalStateException::class.java) { next.open(key) }
            assertArrayEquals(ByteArray(32), key)
        } finally { finish.countDown(); assertTrue(returned.await(5, TimeUnit.SECONDS)); owned.close(); next.revoke() }
    }

    @Test fun `new Hidden authority denies during an original revocation snapshot`() {
        val pool = ReleasePool(2)
        val first = testSecondaryAuthority { 0 }; val next = testSecondaryAuthority { 0 }
        sharePool(first, pool); sharePool(next, pool)
        assertTrue(first.completeAuthentication(first.beginAuthentication(), ByteArray(32)))
        val op = first.operationOrNull()!!
        val owned = op.createOwned { attach -> AutoCloseable {}.also(attach) }
        val entered = CountDownLatch(1); val finish = CountDownLatch(1); val returned = CountDownLatch(1)
        blockSnapshot(op, entered, finish)
        kotlin.concurrent.thread(isDaemon = true) { try { first.revoke() } finally { returned.countDown() } }
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            val attempt = next.beginAuthentication(); val key = ByteArray(32) { 8 }
            assertThrows(IllegalStateException::class.java) { next.completeAuthentication(attempt, key) }
            assertArrayEquals(ByteArray(32), key)
        } finally { finish.countDown(); assertTrue(returned.await(5, TimeUnit.SECONDS)); owned.close(); next.revoke() }
    }

    private fun blockSnapshot(operation: Any, entered: CountDownLatch, finish: CountDownLatch) {
        val field = operation.javaClass.getDeclaredField("reservations").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST") val original = field.get(operation) as MutableSet<ReleaseReservation>
        field.set(operation, object : MutableSet<ReleaseReservation> by original {
            override fun iterator(): MutableIterator<ReleaseReservation> {
                entered.countDown(); check(finish.await(5, TimeUnit.SECONDS)); return original.iterator()
            }
        })
    }

    @Test fun `Primary snapshot failure cannot disappear through a new authority`() {
        val pool = ReleasePool(2)
        val first = testPrimaryAuthority(); val next = testPrimaryAuthority()
        sharePool(first, pool); sharePool(next, pool)
        first.open(ByteArray(32)); val op = first.operationOrNull()!!
        val owned = op.createOwned { attach -> AutoCloseable {}.also(attach) }
        failSnapshot(op)
        assertThrows(OutOfMemoryError::class.java) { first.revoke() }
        val key = ByteArray(32) { 8 }
        try {
            assertThrows(IllegalStateException::class.java) { next.open(key) }
            assertArrayEquals(ByteArray(32), key)
            assertFalse(next.cleanupComplete)
        } finally { owned.close(); next.revoke() }
    }

    @Test fun `Hidden snapshot failure cannot disappear through a new authority`() {
        val pool = ReleasePool(2)
        val first = testSecondaryAuthority { 0 }; val next = testSecondaryAuthority { 0 }
        sharePool(first, pool); sharePool(next, pool)
        assertTrue(first.completeAuthentication(first.beginAuthentication(), ByteArray(32)))
        val op = first.operationOrNull()!!
        val owned = op.createOwned { attach -> AutoCloseable {}.also(attach) }
        failSnapshot(op)
        assertThrows(OutOfMemoryError::class.java) { first.revoke() }
        val attempt = next.beginAuthentication(); val key = ByteArray(32) { 8 }
        try {
            assertThrows(IllegalStateException::class.java) { next.completeAuthentication(attempt, key) }
            assertArrayEquals(ByteArray(32), key)
            assertFalse(next.cleanupComplete)
        } finally { owned.close(); next.revoke() }
    }

    @Test fun `Primary postconstruction completion hook failure retires actual Job immediately`() {
        val authority = testPrimaryAuthority(); authority.open(ByteArray(32))
        val op = authority.operationOrNull()!!; val cancelled = CountDownLatch(1)
        assertThrows(IOException::class.java) {
            op.createOwnedJob { attach ->
                Job().also { job ->
                    attach(job)
                    job.invokeOnCompletion { cancelled.countDown() }
                    failRetirementHook(op.reservations.single())
                }
            }
        }
        try { assertTrue("failure must cancel without waiting for later lease close", cancelled.await(1, TimeUnit.SECONDS)) }
        finally { authority.revoke() }
    }

    @Test fun `Hidden postconstruction completion hook failure retires actual Job immediately`() {
        val authority = testSecondaryAuthority { 0 }
        assertTrue(authority.completeAuthentication(authority.beginAuthentication(), ByteArray(32)))
        val op = authority.operationOrNull()!!; val cancelled = CountDownLatch(1)
        assertThrows(IOException::class.java) {
            op.createOwnedJob { attach ->
                Job().also { job ->
                    attach(job)
                    job.invokeOnCompletion { cancelled.countDown() }
                    failRetirementHook(op.reservations.single())
                }
            }
        }
        try { assertTrue("failure must cancel without waiting for later lease close", cancelled.await(1, TimeUnit.SECONDS)) }
        finally { authority.revoke() }
    }

    private fun sharePool(authority: Any, pool: ReleasePool) {
        authority.javaClass.getDeclaredField("ioReleasePool").apply { isAccessible = true }.set(authority, pool)
    }
    private fun failSnapshot(operation: Any) {
        val field = operation.javaClass.getDeclaredField("reservations").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST") val original = field.get(operation) as MutableSet<ReleaseReservation>
        field.set(operation, object : MutableSet<ReleaseReservation> by original {
            override fun iterator(): MutableIterator<ReleaseReservation> = throw OutOfMemoryError("snapshot allocation failed")
        })
    }
    private fun failRetirementHook(reservation: ReleaseReservation) {
        @Suppress("UNCHECKED_CAST") val tickets = ReleaseReservation::class.java.getDeclaredField("tickets").apply { isAccessible = true }.get(reservation) as List<ReleaseTicket>
        val release = ReleaseTicket::class.java.getDeclaredField("resource").apply { isAccessible = true }.get(tickets.single()) as ReservedJobRelease
        ReservedJobRelease::class.java.getDeclaredField("completed").apply { isAccessible = true }.set(release, object : CompletableFuture<Unit>() {
            override fun <U> newIncompleteFuture(): CompletableFuture<U> = throw IOException("dependent completion-hook allocation failed")
        })
    }
}
