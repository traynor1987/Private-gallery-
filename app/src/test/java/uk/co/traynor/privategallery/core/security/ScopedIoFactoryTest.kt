package uk.co.traynor.privategallery.core.security

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test

class ScopedIoFactoryTest {
    @Test fun `revoked input cannot reach native skip available mark or reset`() {
        val authority = testPrimaryAuthority(); authority.open(ByteArray(32))
        val op = authority.operationOrNull()!!; val guard = ScopedIoGuard(op, PrimaryScope.READ)
        var accesses = 0
        val input = guard.input { object : ByteArrayInputStream(byteArrayOf(7,8)) {
            override fun skip(n: Long): Long { accesses++; return super.skip(n) }
            override fun available(): Int { accesses++; return super.available() }
            override fun mark(readlimit: Int) { accesses++; super.mark(readlimit) }
            override fun reset() { accesses++; super.reset() }
        } }
        authority.revoke(); input.close()
        assertThrows(IllegalStateException::class.java) { input.skip(1) }
        assertThrows(IllegalStateException::class.java) { input.available() }
        assertThrows(IllegalStateException::class.java) { input.mark(1) }
        assertThrows(IllegalStateException::class.java) { input.reset() }
        assertEquals(0, accesses)
    }
    @Test fun `normally retired input cannot still access live original lease`() {
        val authority = testPrimaryAuthority(); authority.open(ByteArray(32))
        val op = authority.operationOrNull()!!; val guard = ScopedIoGuard(op, PrimaryScope.READ)
        val input = guard.input { ByteArrayInputStream(byteArrayOf(7,8)) }
        input.close()
        assertTrue(op.isCurrent)
        assertThrows(IllegalStateException::class.java) { input.skip(1) }
        assertThrows(IllegalStateException::class.java) { input.available() }
        assertThrows(IllegalStateException::class.java) { input.reset() }
        authority.revoke()
    }

    @Test fun `closed original operation denies before provider input creation`() {
        val authority = testPrimaryAuthority(); authority.open(ByteArray(32))
        val op = authority.operationOrNull()!!; val guard = ScopedIoGuard(op, PrimaryScope.READ)
        op.close(); var created = false
        assertThrows(IllegalStateException::class.java) { guard.input { created = true; ByteArrayInputStream(byteArrayOf(7)) } }
        assertFalse(created); authority.revoke()
    }
    @Test fun `release capacity denies before provider output creation`() {
        val authority = testPrimaryAuthority(); authority.open(ByteArray(32))
        val op = authority.operationOrNull(PrimaryScope.entries.toSet())!!
        val held = (0 until 16).map { op.createOwned { attach -> AutoCloseable {}.also(attach) } }
        var created = false
        try {
            assertThrows(IllegalStateException::class.java) { ScopedIoGuard(op, PrimaryScope.EGRESS).output { created = true; ByteArrayOutputStream() } }
            assertFalse(created)
        } finally { held.forEach { it.close() }; authority.revoke() }
    }
    @Test fun `actual input close failure never becomes successful normal disposal`() {
        val authority = testPrimaryAuthority(); authority.open(ByteArray(32))
        val op = authority.operationOrNull()!!; val guard = ScopedIoGuard(op, PrimaryScope.READ)
        val input = guard.input { object : ByteArrayInputStream(byteArrayOf(7)) {
            override fun close(): Unit = throw IOException("native close failed")
        } }
        assertThrows(IOException::class.java) { input.close() }
        authority.revoke()
        val key = ByteArray(32) { 8 }
        assertThrows(IllegalStateException::class.java) { authority.open(key) }
        assertArrayEquals(ByteArray(32), key)
    }
    @Test fun `native stream close can reacquire authority outside its gate`() {
        val authority = testPrimaryAuthority(); authority.open(ByteArray(32))
        val op = authority.operationOrNull()!!; val guard = ScopedIoGuard(op, PrimaryScope.READ)
        val closed = CountDownLatch(1)
        val input = guard.input { object : ByteArrayInputStream(byteArrayOf(7)) {
            override fun close() { authority.cleanupComplete; closed.countDown() }
        } }
        input.close()
        assertTrue(closed.await(1, TimeUnit.SECONDS)); authority.revoke()
        assertTrue(authority.cleanupComplete)
    }
}
