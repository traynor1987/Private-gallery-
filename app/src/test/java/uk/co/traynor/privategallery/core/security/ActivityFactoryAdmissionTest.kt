package uk.co.traynor.privategallery.core.security

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import uk.co.traynor.privategallery.launchOwned

class ActivityFactoryAdmissionTest {
    @Test fun `Activity denies protected Job creation when physical release capacity is occupied`() = runBlocking {
        val authority = testPrimaryAuthority(); authority.open(ByteArray(32) { 8 })
        val op = authority.operationOrNull()!!
        val held = (0 until 16).map { op.createOwned { attach -> AutoCloseable {}.also(attach) } }
        var ran = false
        try {
            assertThrows(IllegalStateException::class.java) { launchOwned(op, this, Dispatchers.Unconfined) { ran = true } }
            assertFalse("no protected work before reservation", ran)
            assertArrayEquals(ByteArray(32), op.key)
        } finally { held.forEach { it.close() }; authority.revoke() }
    }
}
