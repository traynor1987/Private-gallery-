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
    @Test fun revokedRestoreRetainsVerifiedRootAndCannotRollBackItsSerializedSuccessor() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val id = UUID.randomUUID().toString()
        val folder = File(app.cacheDir, "backup-owned-$id").apply { mkdirs() }
        val source = File(app.cacheDir, "backup-owned-source-$id").apply { mkdirs() }
        val isolated = object : ContextWrapper(app) {
            override fun getFilesDir() = folder
            override fun getSharedPreferences(name: String, mode: Int) = app.getSharedPreferences("backup-owned-$id-$name", mode)
        }
        val key = ByteArray(32) { 29 }
        val secret = RecoveryKey.generate()
        val installed = java.util.concurrent.CountDownLatch(1)
        val release = java.util.concurrent.CountDownLatch(1)
        val secondStarted = java.util.concurrent.CountDownLatch(1)
        val secondFinished = java.util.concurrent.CountDownLatch(1)
        val revoked = java.util.concurrent.atomic.AtomicBoolean(false)
        val firstFailure = java.util.concurrent.atomic.AtomicReference<Throwable?>()
        val secondFailure = java.util.concurrent.atomic.AtomicReference<Throwable?>()
        var first: Thread? = null
        var second: Thread? = null
        try {
            EncryptedIndexStore(source).saveSnapshot(VaultIndexSnapshot(emptyList()), key)
            val envelope = RecoveryEnvelope.create(secret.copyOf(), key)
            val archive = ByteArrayOutputStream().also { VaultBackupArchive.write(source, key, envelope, it) }.toByteArray()
            val keys = PinVaultKeyStore(isolated)
            val recovery = RecoveryVaultKeyStore(isolated)
            first = Thread {
                var commits = 0
                try {
                    AndroidVaultRepository.restoreBackup(isolated, ByteArrayInputStream(archive), secret.copyOf(), "123456".toCharArray(), keys, recovery) { action ->
                        check(!revoked.get()) { "Synthetic authentication attempt revoked" }
                        action()
                        if (++commits == 1) { installed.countDown(); check(release.await(10, java.util.concurrent.TimeUnit.SECONDS)) }
                    }.fill(0)
                } catch (failure: Throwable) { firstFailure.set(failure) }
            }.apply { start() }
            assertTrue(installed.await(10, java.util.concurrent.TimeUnit.SECONDS))
            val verifiedIndex = File(folder, "vault/vault-index.enc").readBytes()
            second = Thread {
                secondStarted.countDown()
                try {
                    AndroidVaultRepository.restoreBackup(isolated, ByteArrayInputStream(archive), secret.copyOf(), "654321".toCharArray(), keys, recovery).fill(0)
                } catch (failure: Throwable) { secondFailure.set(failure) }
                finally { secondFinished.countDown() }
            }.apply { start() }
            assertTrue(secondStarted.await(5, java.util.concurrent.TimeUnit.SECONDS))
            assertFalse(secondFinished.await(200, java.util.concurrent.TimeUnit.MILLISECONDS))
            revoked.set(true); release.countDown()
            first.join(10000); second.join(10000)
            assertFalse(first.isAlive); assertFalse(second.isAlive)
            assertNotNull(firstFailure.get()); assertNull(secondFailure.get())
            assertArrayEquals(verifiedIndex, File(folder, "vault/vault-index.enc").readBytes())
            assertTrue(keys.isConfigured); assertTrue(recovery.isConfigured)
            val recovered = keys.unlock("654321".toCharArray())
            try { assertArrayEquals(key, recovered) } finally { recovered.fill(0) }
            archive.fill(0)
        } finally {
            revoked.set(true); release.countDown(); first?.join(10000); second?.join(10000)
            source.deleteRecursively(); folder.deleteRecursively(); key.fill(0); secret.fill('\u0000')
            app.getSharedPreferences("backup-owned-$id-vault-key-envelope", Context.MODE_PRIVATE).edit().clear().commit()
            app.getSharedPreferences("backup-owned-$id-vault-recovery-envelope", Context.MODE_PRIVATE).edit().clear().commit()
        }
    }
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
