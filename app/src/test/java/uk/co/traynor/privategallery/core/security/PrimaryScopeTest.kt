package uk.co.traynor.privategallery.core.security

import org.junit.Assert.*
import org.junit.Test

class PrimaryScopeTest {
    @Test fun `foreign handle denies before storage or cache lookup even for colliding id`() {
        val authority = PrimarySessionAuthority { 0 }
        authority.open(ByteArray(32))
        val operation = checkNotNull(authority.operationOrNull(uk.co.traynor.privategallery.core.security.PrimaryScope.entries.toSet()))
        val primary = operation.handle("same-id", "revision")
        val foreign = primary.copy(containerId = ContainerId.synthetic())
        var lookups = 0
        assertThrows(IllegalStateException::class.java) {
            operation.resolve(foreign, { lookups++; "revision" }) { fail("foreign read ran") }
        }
        assertEquals(0, lookups)
        assertThrows(IllegalStateException::class.java) { operation.cacheIdentity(foreign) }
        assertEquals(ContainerId.PRIMARY, operation.cacheIdentity(primary).containerId)
        authority.revoke()
    }

    @Test fun `changed revision denies before payload side effect`() {
        val authority = PrimarySessionAuthority { 0 }
        authority.open(ByteArray(32))
        val operation = checkNotNull(authority.operationOrNull(uk.co.traynor.privategallery.core.security.PrimaryScope.entries.toSet()))
        val handle = operation.handle("same-id", "old-revision")
        assertThrows(IllegalStateException::class.java) {
            operation.resolve(handle, { "new-revision" }) { fail("changed payload read ran") }
        }
        assertEquals(7, operation.resolve(handle, { "old-revision" }) { 7 })
        authority.revoke()
    }

    @Test fun `capability attenuation cannot escalate or mutate credentials or egress`() {
        val authority = PrimarySessionAuthority { 0 }
        authority.open(ByteArray(32))
        val parent = checkNotNull(authority.operationOrNull(uk.co.traynor.privategallery.core.security.PrimaryScope.entries.toSet()))
        val read = parent.fork(setOf(PrimaryScope.READ))
        assertEquals(parent.epoch, read.epoch)
        read.requireScope(PrimaryScope.READ)
        for (scope in listOf(PrimaryScope.WRITE, PrimaryScope.EGRESS, PrimaryScope.CREDENTIALS, PrimaryScope.BACKUP)) {
            assertThrows(IllegalStateException::class.java) { read.requireScope(scope) }
            assertThrows(IllegalStateException::class.java) { read.fork(setOf(scope)) }
        }
        authority.revoke()
        assertThrows(IllegalStateException::class.java) { read.requireScope(PrimaryScope.READ) }
    }

    @Test fun `deadline and ABA deny cache and result destination without timer`() {
        var now = 0L
        val authority = PrimarySessionAuthority { now }
        authority.open(ByteArray(32))
        val old = checkNotNull(authority.operationOrNull(uk.co.traynor.privategallery.core.security.PrimaryScope.entries.toSet()))
        val handle = old.handle("item", "revision")
        authority.onBackgrounded(100)
        now = 100
        assertThrows(IllegalStateException::class.java) { old.cacheIdentity(handle) }
        authority.open(ByteArray(32))
        val current = checkNotNull(authority.operationOrNull(uk.co.traynor.privategallery.core.security.PrimaryScope.entries.toSet()))
        assertThrows(IllegalStateException::class.java) { current.resolve(handle, { fail("old lookup"); "revision" }) {} }
        assertThrows(IllegalStateException::class.java) { old.commit { fail("old result destination ran") } }
        authority.revoke()
    }
}
