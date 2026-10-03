package uk.co.traynor.privategallery.core.security

import java.io.ByteArrayOutputStream
import java.io.ByteArrayInputStream
import org.junit.Assert.*
import org.junit.Test

class ScopedIoGuardTest {
    @Test fun `outbound next chunk denied on deadline without cancellation task`() {
        var now = 0L
        val authority = PrimarySessionAuthority { now }
        authority.open(ByteArray(32))
        val operation = checkNotNull(authority.operationOrNull(uk.co.traynor.privategallery.core.security.PrimaryScope.entries.toSet()))
        val target = ByteArrayOutputStream()
        val output = ScopedIoGuard(operation, PrimaryScope.EGRESS).output { target }
        output.write(byteArrayOf(1,2))
        authority.onBackgrounded(10); now = 10
        assertThrows(IllegalStateException::class.java) { output.write(byteArrayOf(3,4)) }
        assertArrayEquals(byteArrayOf(1,2), target.toByteArray())
        output.close()
    }
    @Test fun `resource owned before blocking admission closes on revoke and stale reader denied`() {
        val authority = PrimarySessionAuthority { 0 }
        authority.open(ByteArray(32))
        val guard = ScopedIoGuard(checkNotNull(authority.operationOrNull(uk.co.traynor.privategallery.core.security.PrimaryScope.entries.toSet())), PrimaryScope.READ)
        var closes = 0
        guard.own(AutoCloseable { closes++ })
        val input = guard.input { ByteArrayInputStream(byteArrayOf(1,2)) }
        authority.revoke(); input.close(); authority.open(ByteArray(32))
        assertEquals(1, closes)
        assertThrows(IllegalStateException::class.java) { input.read(ByteArray(2)) }
        authority.revoke()
    }
    @Test fun `read that races revocation wipes buffer and cannot deliver`() {
        val authority = PrimarySessionAuthority { 0 }
        authority.open(ByteArray(32))
        val guard = ScopedIoGuard(checkNotNull(authority.operationOrNull(uk.co.traynor.privategallery.core.security.PrimaryScope.entries.toSet())), PrimaryScope.READ)
        val input = guard.input { object : ByteArrayInputStream(byteArrayOf(7,8)) {
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                val count = super.read(b, off, len)
                authority.revoke()
                return count
            }
        } }
        val buffer = ByteArray(2)
        assertThrows(IllegalStateException::class.java) { input.read(buffer) }
        assertArrayEquals(ByteArray(2), buffer)
        input.close()
    }
}
