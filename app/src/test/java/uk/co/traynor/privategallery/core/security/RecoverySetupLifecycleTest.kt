package uk.co.traynor.privategallery.core.security

import java.util.Base64
import org.junit.Assert.*
import org.junit.Test
import uk.co.traynor.privategallery.core.crypto.InvalidRecoveryKeyException
import uk.co.traynor.privategallery.core.crypto.RecoveryEnvelope
import uk.co.traynor.privategallery.core.crypto.RecoveryWrappedKey

class RecoverySetupLifecycleTest {
    private val vdek = ByteArray(32) { (it + 1).toByte() }
    private class MemoryPersistence : RecoveryEnvelopePersistence {
        var values = emptyMap<String, String>()
        var reject = false
        override fun read() = values.toMap()
        override fun commit(expected: Map<String, String>, values: Map<String, String>): Boolean {
            if (reject || this.values != expected) return false
            this.values = values.toMap()
            return true
        }
    }
    private fun denied(action: () -> Unit) {
        try { action(); fail("Expected rejection") } catch (_: IllegalStateException) { }
    }
    private fun legacy(envelope: RecoveryWrappedKey) = mapOf(
        "salt" to Base64.getEncoder().encodeToString(envelope.salt),
        "nonce" to Base64.getEncoder().encodeToString(envelope.nonce),
        "ciphertext" to Base64.getEncoder().encodeToString(envelope.ciphertext),
    )

    @Test fun `failed preference write published in memory cannot establish confirmation`() {
        val disk = MemoryPersistence()
        val lifecycle = RecoverySetupLifecycle(disk)
        val secret = lifecycle.create(vdek)
        val volatilePersistence = object : RecoveryEnvelopePersistence {
            var memory = disk.values
            override fun read() = memory
            override fun commit(expected: Map<String, String>, values: Map<String, String>): Boolean { memory = values; return false }
        }
        val volatileLifecycle = RecoverySetupLifecycle(FailClosedRecoveryPersistence(disk, volatilePersistence))
        denied { volatileLifecycle.confirm(secret.copyOf(), vdek) }
        assertEquals(RecoverySetupState.CORRUPT, volatileLifecycle.setupState)
        denied { volatileLifecycle.exportEnvelope() }
        val reconstructed = RecoverySetupLifecycle(FailClosedRecoveryPersistence(disk, volatilePersistence))
        assertEquals(RecoverySetupState.CORRUPT, reconstructed.setupState)
        denied { reconstructed.exportEnvelope() }
        assertEquals(RecoverySetupState.PENDING_CONFIRMATION, RecoverySetupLifecycle(disk).setupState)
        secret.fill('\u0000')
    }

    @Test fun `failed restoration rollback cannot erase a preexisting legacy record`() {
        val envelope = RecoveryEnvelope.create("legacy-secret".toCharArray(), vdek)
        val disk = MemoryPersistence().apply { values = legacy(envelope) }
        val lifecycle = RecoverySetupLifecycle(disk)
        denied { lifecycle.clearFailedRestore() }
        assertEquals(legacy(envelope), disk.values)
    }

    @Test fun `creation interrupted before display remains pending and cannot unlock or export`() {
        val disk = MemoryPersistence()
        val lifecycle = RecoverySetupLifecycle(disk)
        val secret = lifecycle.create(vdek)
        assertEquals(RecoverySetupState.PENDING_CONFIRMATION, lifecycle.setupState)
        assertFalse(disk.values.values.any { it == secret.concatToString() })
        val reconstructed = RecoverySetupLifecycle(disk)
        assertEquals(RecoverySetupState.PENDING_CONFIRMATION, reconstructed.setupState)
        denied { reconstructed.exportEnvelope() }
        denied { reconstructed.unlock(secret.copyOf()) }
        secret.fill('\u0000')
    }

    @Test fun `owner re-entry promotes pending only after matching active VDEK`() {
        val disk = MemoryPersistence()
        val lifecycle = RecoverySetupLifecycle(disk)
        val secret = lifecycle.create(vdek)
        try { lifecycle.confirm("wrong".toCharArray(), vdek); fail() } catch (_: InvalidRecoveryKeyException) { }
        assertEquals(RecoverySetupState.PENDING_CONFIRMATION, lifecycle.setupState)
        try { lifecycle.confirm(secret.copyOf(), ByteArray(32)); fail() } catch (_: InvalidRecoveryKeyException) { }
        assertEquals(RecoverySetupState.PENDING_CONFIRMATION, lifecycle.setupState)
        lifecycle.confirm(secret.copyOf(), vdek)
        assertEquals(RecoverySetupState.CONFIRMED, RecoverySetupLifecycle(disk).setupState)
        assertArrayEquals(vdek, lifecycle.unlock(secret.copyOf()))
        secret.fill('\u0000')
    }

    @Test fun `pending restart after death replaces pending secret but preserves existing VDEK`() {
        val disk = MemoryPersistence()
        val first = RecoverySetupLifecycle(disk).create(vdek)
        val reconstructed = RecoverySetupLifecycle(disk)
        val replacement = reconstructed.restartPending(vdek)
        try { reconstructed.confirm(first, vdek); fail() } catch (_: InvalidRecoveryKeyException) { }
        reconstructed.confirm(replacement.copyOf(), vdek)
        assertArrayEquals(vdek, reconstructed.unlock(replacement))
    }

    @Test fun `legacy envelope remains readable unchanged and possession verification never rotates it`() {
        val secret = "synthetic-legacy-secret".toCharArray()
        val envelope = RecoveryEnvelope.create(secret.copyOf(), vdek)
        val disk = MemoryPersistence().apply { values = legacy(envelope) }
        val lifecycle = RecoverySetupLifecycle(disk)
        assertEquals(RecoverySetupState.CONFIRMED, lifecycle.setupState)
        assertTrue(lifecycle.isLegacyExisting)
        assertFalse(lifecycle.isPossessionVerified)
        assertArrayEquals(vdek, lifecycle.unlock(secret.copyOf()))
        denied { lifecycle.create(vdek) }
        denied { lifecycle.restartPending(vdek) }
        assertEquals(legacy(envelope), disk.values)
        lifecycle.confirm(secret, vdek)
        assertTrue(lifecycle.isLegacyExisting)
        assertTrue(lifecycle.isPossessionVerified)
        val exported = lifecycle.exportEnvelope()
        assertArrayEquals(envelope.salt, exported.salt)
        assertArrayEquals(envelope.nonce, exported.nonce)
        assertArrayEquals(envelope.ciphertext, exported.ciphertext)
    }

    @Test fun `failed durable confirmation keeps pending and can retry after reconstruction`() {
        val disk = MemoryPersistence()
        val lifecycle = RecoverySetupLifecycle(disk)
        val secret = lifecycle.create(vdek)
        val before = disk.values
        disk.reject = true
        denied { lifecycle.confirm(secret.copyOf(), vdek) }
        assertEquals(before, disk.values)
        assertEquals(RecoverySetupState.PENDING_CONFIRMATION, RecoverySetupLifecycle(disk).setupState)
        disk.reject = false
        RecoverySetupLifecycle(disk).confirm(secret, vdek)
        assertEquals(RecoverySetupState.CONFIRMED, RecoverySetupLifecycle(disk).setupState)
    }

    @Test fun `revoked final commit cannot create or confirm recovery`() {
        val disk = MemoryPersistence()
        val lifecycle = RecoverySetupLifecycle(disk)
        denied { lifecycle.create(vdek) { throw IllegalStateException("revoked") } }
        assertEquals(RecoverySetupState.NOT_CONFIGURED, lifecycle.setupState)
        val secret = lifecycle.create(vdek)
        val before = disk.values
        denied { lifecycle.confirm(secret, vdek) { throw IllegalStateException("revoked") } }
        assertEquals(before, disk.values)
    }

    @Test fun `partial corrupt unknown and mixed state never permit fresh setup`() {
        val good = legacy(RecoveryEnvelope.create("synthetic".toCharArray(), vdek))
        val cases = listOf(mapOf("salt" to good.getValue("salt")), good + ("state" to "pending"),
            good + ("state" to "unknown"), good + ("nonce" to "invalid"), mapOf("unknown" to "value"))
        for (bad in cases) {
            val disk = MemoryPersistence().apply { values = bad }
            val lifecycle = RecoverySetupLifecycle(disk)
            assertEquals(RecoverySetupState.CORRUPT, lifecycle.setupState)
            denied { lifecycle.create(vdek) }
            assertEquals(bad, disk.values)
        }
    }

    @Test fun `restored envelope keeps original bytes and records already authenticated possession`() {
        val envelope = RecoveryEnvelope.create("restored-secret".toCharArray(), vdek)
        val lifecycle = RecoverySetupLifecycle(MemoryPersistence())
        lifecycle.installForRestoredVault(envelope)
        assertTrue(lifecycle.isPossessionVerified)
        assertFalse(lifecycle.isLegacyExisting)
        assertArrayEquals(envelope.salt, lifecycle.exportEnvelope().salt)
        assertArrayEquals(envelope.nonce, lifecycle.exportEnvelope().nonce)
        assertArrayEquals(envelope.ciphertext, lifecycle.exportEnvelope().ciphertext)
    }
}
