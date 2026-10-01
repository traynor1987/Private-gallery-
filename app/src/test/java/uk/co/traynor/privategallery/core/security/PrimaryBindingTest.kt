package uk.co.traynor.privategallery.core.security

import org.junit.Assert.*
import org.junit.Test

class PrimaryBindingTest {
    @Test fun `default operation cannot mutate credentials write or egress`() {
        val authority = PrimarySessionAuthority { 0 }
        authority.open(ByteArray(32))
        val read = checkNotNull(authority.operationOrNull())
        read.requireScope(PrimaryScope.READ)
        for (scope in PrimaryScope.entries.filter { it != PrimaryScope.READ }) {
            assertThrows(IllegalStateException::class.java) { read.requireScope(scope) }
        }
        authority.revoke()
    }

    @Test fun `keyless UI binding can issue only for its original live epoch`() {
        var now = 0L
        val authority = PrimarySessionAuthority { now }
        authority.open(ByteArray(32))
        val binding = checkNotNull(authority.bindingOrNull())
        val read = binding.operation(setOf(PrimaryScope.READ))
        assertEquals(binding.epoch, read.epoch)
        assertThrows(IllegalStateException::class.java) { read.fork(setOf(PrimaryScope.CREDENTIALS)) }
        read.close()
        authority.onBackgrounded(10); now = 10
        assertThrows(IllegalStateException::class.java) { binding.operation(setOf(PrimaryScope.READ)) }
        authority.open(ByteArray(32))
        assertThrows(IllegalStateException::class.java) { binding.operation(setOf(PrimaryScope.READ)) }
        assertThrows(IllegalStateException::class.java) { binding.commit { fail("old callback") } }
        authority.revoke()
    }
}
