package uk.co.traynor.privategallery.core.vault

import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import uk.co.traynor.privategallery.core.security.PrimarySessionAuthority

/** Synthetic isolated directory only; never production Vault. */
class PrimaryRepositorySessionTest {
    @Test fun missingIndexBackupCannotManufactureEmptyMetadataOverCiphertext() {
        val base = ApplicationProvider.getApplicationContext<android.content.Context>()
        val root = java.io.File(base.cacheDir, "phase0-missing-${java.util.UUID.randomUUID()}").apply { mkdirs() }
        val context = object : ContextWrapper(base) { override fun getFilesDir() = root }
        val authority = PrimarySessionAuthority()
        val key = ByteArray(32) { 23 }
        val secret = "synthetic-primary-recovery-only".toCharArray()
        try {
            val envelope = uk.co.traynor.privategallery.core.crypto.RecoveryEnvelope.create(secret.copyOf(), key)
            authority.open(key.copyOf())
            val sole = root.resolve("vault/payloads/sole.vault").apply { parentFile!!.mkdirs(); writeBytes(byteArrayOf(4, 5, 6)) }
            checkNotNull(authority.operationOrNull()).use { operation ->
                assertThrows(Exception::class.java) {
                    AndroidVaultRepository(context, operation).exportBackup(secret.copyOf(), envelope, java.io.ByteArrayOutputStream())
                }
            }
            assertFalse(root.resolve("vault/vault-index.enc").exists())
            assertArrayEquals(byteArrayOf(4, 5, 6), sole.readBytes())
        } finally { key.fill(0); secret.fill('\u0000'); authority.revoke(); root.deleteRecursively() }
    }
    @Test fun staleRepositoryCannotReadOrCommitAfterSameKeyReauthentication() {
        val base = ApplicationProvider.getApplicationContext<android.content.Context>()
        val root = java.io.File(base.cacheDir, "phase0-repository-${java.util.UUID.randomUUID()}").apply { mkdirs() }
        val context = object : ContextWrapper(base) { override fun getFilesDir() = root }
        val authority = PrimarySessionAuthority()
        try {
            authority.open(ByteArray(32) { 17 })
            val operation = checkNotNull(authority.operationOrNull())
            val repository = AndroidVaultRepository(context, operation)
            repository.createCollection("synthetic")
            val index = root.resolve("vault/vault-index.enc")
            val before = index.readBytes()
            authority.revoke()
            authority.open(ByteArray(32) { 17 })
            assertThrows(IllegalStateException::class.java) { repository.collections() }
            assertThrows(IllegalStateException::class.java) { repository.createCollection("stale") }
            assertArrayEquals(before, index.readBytes())
            checkNotNull(authority.operationOrNull()).use { current ->
                assertEquals("synthetic", AndroidVaultRepository(context, current).collections().single().name)
            }
        } finally { authority.revoke(); root.deleteRecursively() }
    }
    @Test fun corruptionNeverBecomesEmptyWritableVault() {
        val base = ApplicationProvider.getApplicationContext<android.content.Context>()
        val root = java.io.File(base.cacheDir, "phase0-corruption-${java.util.UUID.randomUUID()}").apply { mkdirs() }
        val context = object : ContextWrapper(base) { override fun getFilesDir() = root }
        val authority = PrimarySessionAuthority()
        try {
            authority.open(ByteArray(32) { 11 })
            checkNotNull(authority.operationOrNull()).use { operation ->
                val repository = AndroidVaultRepository(context, operation)
                repository.createCollection("retained")
                val index = root.resolve("vault/vault-index.enc")
                val corrupted = index.readBytes().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }
                index.writeBytes(corrupted)
                assertThrows(Exception::class.java) { repository.createCollection("must-not-overwrite") }
                assertArrayEquals(corrupted, index.readBytes())
            }
        } finally { authority.revoke(); root.deleteRecursively() }
    }
}
