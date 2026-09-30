package uk.co.traynor.privategallery.core.vault

import android.content.Context
import android.content.ContextWrapper
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.SecureRandom
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.traynor.privategallery.core.crypto.RecoveryEnvelope
import uk.co.traynor.privategallery.core.crypto.RecoveryKey
import uk.co.traynor.privategallery.core.security.PinVaultKeyStore
import uk.co.traynor.privategallery.core.security.RecoveryVaultKeyStore

@RunWith(AndroidJUnit4::class)
class VaultBackupRestoreTest {
    @Test fun interruptedRestoreOnlyFinishesWhenExistingCiphertextMatchesArchive() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val testId = UUID.randomUUID().toString()
        val folder = File(app.cacheDir, "backup-interrupted-$testId").apply { mkdirs() }
        val isolated = object : ContextWrapper(app) {
            override fun getFilesDir(): File = folder
            override fun getSharedPreferences(name: String, mode: Int) =
                app.getSharedPreferences("backup-interrupted-$testId-$name", mode)
        }
        val source = File(app.cacheDir, "backup-interrupted-source-$testId").apply { mkdirs() }
        val key = ByteArray(32).also(SecureRandom()::nextBytes)
        val recovery = RecoveryKey.generate()
        try {
            EncryptedIndexStore(source).saveSnapshot(VaultIndexSnapshot(emptyList()), key)
            val envelope = RecoveryEnvelope.create(recovery.copyOf(), key)
            val archive = ByteArrayOutputStream().also { VaultBackupArchive.write(source, key, envelope, it) }.toByteArray()
            val existing = File(folder, "vault").apply { mkdirs() }
            File(source, "vault-index.enc").copyTo(File(existing, "vault-index.enc"))
            val keys = PinVaultKeyStore(isolated)
            val recoveryKeys = RecoveryVaultKeyStore(isolated)
            File(existing, "vault-index.enc").appendBytes(byteArrayOf(1))
            assertThrows(Exception::class.java) {
                AndroidVaultRepository.restoreBackup(isolated, ByteArrayInputStream(archive), recovery.copyOf(), "123456".toCharArray(), keys, recoveryKeys)
            }
            assertFalse(keys.hasEnvelopeMaterial)
            assertTrue(keys.isConfigured) // Existing ciphertext blocks fresh setup despite absent PIN slot.
            assertTrue(File(existing, "vault-index.enc").exists())
            File(source, "vault-index.enc").copyTo(File(existing, "vault-index.enc"), overwrite = true)
            val restored = AndroidVaultRepository.restoreBackup(isolated, ByteArrayInputStream(archive), recovery.copyOf(), "123456".toCharArray(), keys, recoveryKeys)
            assertArrayEquals(key, restored)
            restored.fill(0); archive.fill(0)
        } finally {
            source.deleteRecursively(); folder.deleteRecursively(); key.fill(0); recovery.fill('\u0000')
            app.getSharedPreferences("backup-interrupted-$testId-vault-key-envelope", Context.MODE_PRIVATE).edit().clear().commit()
            app.getSharedPreferences("backup-interrupted-$testId-vault-recovery-envelope", Context.MODE_PRIVATE).edit().clear().commit()
        }
    }

    @Test fun freshRestoreInstallsNewPinOnlyAfterCiphertextAuthenticates() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val testId = UUID.randomUUID().toString()
        val folder = File(app.cacheDir, "backup-restore-$testId").apply { mkdirs() }
        val isolated = object : ContextWrapper(app) {
            override fun getFilesDir(): File = folder
            override fun getSharedPreferences(name: String, mode: Int) =
                app.getSharedPreferences("backup-test-$testId-$name", mode)
        }
        val source = File(app.cacheDir, "backup-source-$testId").apply { mkdirs() }
        val key = ByteArray(32).also(SecureRandom()::nextBytes)
        val recovery = RecoveryKey.generate()
        val pin = "735209".toCharArray()
        try {
            val stored = EncryptedPayloadStore(source).writeAndVerify(UUID.randomUUID().toString(),
                ByteArrayInputStream(byteArrayOf(1, 2, 3, 4)), key)
            val item = VaultItem(stored.id, "image/jpeg", "sample.jpg", 1, stored.plaintextSize,
                stored.plaintextSha256, stored.nonce, VaultItemState.COMPLETE)
            EncryptedIndexStore(source).saveSnapshot(VaultIndexSnapshot(listOf(item)), key)
            val envelope = RecoveryEnvelope.create(recovery.copyOf(), key)
            val archive = ByteArrayOutputStream().also { VaultBackupArchive.write(source, key, envelope, it) }.toByteArray()
            val keys = PinVaultKeyStore(isolated)
            val recoveryKeys = RecoveryVaultKeyStore(isolated)
            val badArchive = archive.copyOf(archive.size / 2)
            assertThrows(Exception::class.java) {
                AndroidVaultRepository.restoreBackup(isolated, ByteArrayInputStream(badArchive), recovery.copyOf(), pin.copyOf(), keys, recoveryKeys)
            }
            assertFalse(keys.isConfigured)
            assertFalse(File(folder, "vault").exists())
            val restored = AndroidVaultRepository.restoreBackup(isolated, ByteArrayInputStream(archive), recovery.copyOf(), pin.copyOf(), keys, recoveryKeys)
            assertArrayEquals(key, restored)
            assertArrayEquals(key, keys.unlock(pin.copyOf()))
            assertEquals(item.id, AndroidVaultRepository(isolated, uk.co.traynor.privategallery.core.security.primaryTestOperation(restored)).items().single().id)
            assertTrue(recoveryKeys.isConfigured)
            restored.fill(0); archive.fill(0)
        } finally {
            source.deleteRecursively(); folder.deleteRecursively(); key.fill(0); recovery.fill('\u0000'); pin.fill('\u0000')
            app.getSharedPreferences("backup-test-$testId-vault-key-envelope", Context.MODE_PRIVATE).edit().clear().commit()
            app.getSharedPreferences("backup-test-$testId-vault-recovery-envelope", Context.MODE_PRIVATE).edit().clear().commit()
        }
    }
}
