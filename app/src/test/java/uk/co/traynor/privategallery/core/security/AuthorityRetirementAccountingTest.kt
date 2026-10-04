package uk.co.traynor.privategallery.core.security

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test
import uk.co.traynor.privategallery.core.domain.SecondarySessionAuthority

class AuthorityRetirementAccountingTest {
    @Test fun `Primary pending counter survives removal until owning callback actually returns`() {
        val authority = testPrimaryAuthority(); authority.open(ByteArray(32))
        val operation = authority.operationOrNull()!!
        val owned = operation.createOwned { attach -> AutoCloseable {}.also(attach) }
        val blocked = CountDownLatch(1); val finish = CountDownLatch(1)
        blockOwningReturn(owned, blocked, finish)
        owned.close(); assertTrue(blocked.await(5, TimeUnit.SECONDS))
        try {
            assertTrue("strict bookkeeping actually removed the registry entry", resources(authority).isEmpty())
            authority.revoke(); assertFalse(authority.cleanupComplete)
            val key = ByteArray(32) { 9 }
            assertThrows(IllegalStateException::class.java) { authority.open(key) }
            assertArrayEquals(ByteArray(32), key)
        } finally { finish.countDown() }
        assertTrue(owned.retirement.await(5, TimeUnit.SECONDS)); waitFor { authority.cleanupComplete }
    }

    @Test fun `Hidden pending counter survives removal until owning callback actually returns`() {
        val authority = testSecondaryAuthority { 0 }
        val attempt = authority.beginAuthentication(); authority.completeAuthentication(attempt, ByteArray(32))
        val operation = authority.operationOrNull()!!
        val owned = operation.createOwned { attach -> AutoCloseable {}.also(attach) }
        val blocked = CountDownLatch(1); val finish = CountDownLatch(1)
        blockOwningReturn(owned, blocked, finish)
        owned.close(); assertTrue(blocked.await(5, TimeUnit.SECONDS))
        try {
            assertTrue(resources(authority).isEmpty())
            authority.revoke(); assertFalse(authority.cleanupComplete)
            val fresh = authority.beginAuthentication()
            assertThrows(IllegalStateException::class.java) { authority.checkAuthentication(fresh) }
            val key = ByteArray(32) { 9 }
            assertThrows(IllegalStateException::class.java) { authority.completeAuthentication(fresh, key) }
            assertArrayEquals(ByteArray(32), key)
        } finally { finish.countDown() }
        assertTrue(owned.retirement.await(5, TimeUnit.SECONDS)); waitFor { authority.cleanupComplete }
    }

    @Test fun `partial Hidden registry removal failure denies fresh exact attempt and wipes key`() {
        val pool = ReleasePool(1); val authority = testSecondaryAuthority { 0 }
        SecondarySessionAuthority::class.java.getDeclaredField("ioReleasePool").apply { isAccessible = true }.set(authority, pool)
        val attempt = authority.beginAuthentication(); authority.completeAuthentication(attempt, ByteArray(32))
        val operation = authority.operationOrNull()!!
        val owned = operation.createOwned { attach -> AutoCloseable {}.also(attach) }
        val field = SecondarySessionAuthority::class.java.getDeclaredField("reservedResources").apply { isAccessible = true }
        val original = resources(authority); val removed = CountDownLatch(1)
        field.set(authority, object : MutableSet<ReleaseReservation> by original {
            override fun remove(element: ReleaseReservation): Boolean {
                original.remove(element); removed.countDown()
                throw OutOfMemoryError("Hidden registry failed after actual removal")
            }
        })
        owned.close(); assertTrue(removed.await(5, TimeUnit.SECONDS)); waitFor { owned.releaseFailed }
        assertFalse(owned.retirement.isComplete); assertEquals(1, pool.occupied)
        authority.revoke(); assertFalse(authority.cleanupComplete)
        val fresh = authority.beginAuthentication()
        assertThrows(IllegalStateException::class.java) { authority.checkAuthentication(fresh) }
        val key = ByteArray(32) { 9 }
        assertThrows(IllegalStateException::class.java) { authority.completeAuthentication(fresh, key) }
        assertArrayEquals(ByteArray(32), key)
        assertThrows(IllegalStateException::class.java) { pool.reserve(Any()) }
    }

    @Suppress("UNCHECKED_CAST") private fun resources(authority: Any): MutableSet<ReleaseReservation> =
        authority.javaClass.getDeclaredField("reservedResources").apply { isAccessible = true }.get(authority) as MutableSet<ReleaseReservation>

    private fun blockOwningReturn(owned: OwnedResource<*>, entered: CountDownLatch, finish: CountDownLatch) {
        val ticketsField = ReleaseReservation::class.java.getDeclaredField("tickets").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST") val tickets = ticketsField.get(owned.original) as List<ReleaseTicket>
        val group = ReleaseTicket::class.java.getDeclaredField("retirementGroup").apply { isAccessible = true }.get(tickets.single())
        val field = ReleaseRetirementGroup::class.java.getDeclaredField("accounting").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST") val original = field.get(group) as () -> Unit
        field.set(group, { original(); entered.countDown(); check(finish.await(5, TimeUnit.SECONDS)) })
    }

    @Test fun `partial Primary registry removal failure blocks actual fresh authentication`() {
        val pool = ReleasePool(1)
        val authority = testPrimaryAuthority(); localPool(authority, pool); authority.open(ByteArray(32) { 3 })
        val operation = authority.operationOrNull()!!
        val owned = operation.createOwned { attach -> AutoCloseable {}.also(attach) }
        val field = PrimarySessionAuthority::class.java.getDeclaredField("reservedResources").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST") val original = field.get(authority) as MutableSet<ReleaseReservation>
        val removed = CountDownLatch(1)
        field.set(authority, object : MutableSet<ReleaseReservation> by original {
            override fun remove(element: ReleaseReservation): Boolean {
                original.remove(element)
                removed.countDown()
                throw OutOfMemoryError("registry failed after actual removal")
            }
        })
        owned.close(); assertTrue(removed.await(5, TimeUnit.SECONDS))
        waitFor { owned.releaseFailed }
        assertFalse(owned.retirement.isComplete); assertEquals(1, pool.occupied)
        authority.revoke(); assertFalse(authority.cleanupComplete)
        val rejected = ByteArray(32) { 9 }
        assertThrows(IllegalStateException::class.java) { authority.open(rejected) }
        assertArrayEquals(ByteArray(32), rejected)
        assertThrows(IllegalStateException::class.java) { pool.reserve(Any()) }
    }

    private fun localPool(authority: PrimarySessionAuthority, pool: ReleasePool) {
        PrimarySessionAuthority::class.java.getDeclaredField("ioReleasePool").apply { isAccessible = true }.set(authority, pool)
    }

    @Test fun `fresh Primary key denied while strict original registry callback has not returned`() {
        val authority = testPrimaryAuthority(); authority.open(ByteArray(32) { 3 })
        val operation = authority.operationOrNull()!!
        val owned = operation.createOwned { attach -> AutoCloseable {}.also(attach) }
        val ticketsField = ReleaseReservation::class.java.getDeclaredField("tickets").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST") val tickets = ticketsField.get(owned.original) as List<ReleaseTicket>
        val blocked = CountDownLatch(1); val finish = CountDownLatch(1)
        blockStrictReturn(tickets.single(), blocked, finish)
        owned.close(); assertTrue(blocked.await(5, TimeUnit.SECONDS))
        try {
            authority.revoke()
            val rejected = ByteArray(32) { 9 }
            assertThrows("no fresh key before the exact strict callback returns", IllegalStateException::class.java) { authority.open(rejected) }
            assertArrayEquals(ByteArray(32), rejected); assertFalse(authority.cleanupComplete)
        } finally { finish.countDown(); authority.revoke() }
        assertTrue(owned.retirement.await(5, TimeUnit.SECONDS)); waitFor { authority.cleanupComplete }
    }

    private fun blockStrictReturn(ticket: ReleaseTicket, entered: CountDownLatch, finish: CountDownLatch) {
        val field = ReleaseTicket::class.java.getDeclaredField("retirementAccounting").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST") val original = field.get(ticket) as () -> Unit
        field.set(ticket, { original(); entered.countDown(); check(finish.await(5, TimeUnit.SECONDS)) })
    }

    private fun waitFor(check: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (!check() && System.nanoTime() < deadline) Thread.yield()
        assertTrue("actual retirement did not acknowledge", check())
    }
}
