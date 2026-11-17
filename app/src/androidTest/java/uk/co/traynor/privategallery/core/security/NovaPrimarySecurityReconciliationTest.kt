package uk.co.traynor.privategallery.core.security

import android.content.Context
import android.content.ContextWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NovaPrimarySecurityReconciliationTest {
    @Test fun setupCannotReplacePartialCredentialsOrOrphanedCiphertext() = isolated { context ->
        val preferences = context.getSharedPreferences("vault-key-envelope", Context.MODE_PRIVATE)
        check(preferences.edit().putString("nonce", "retained").commit())
        val pin = "123456".toCharArray()
        assertThrows(IllegalStateException::class.java) { PinVaultKeyStore(context).create(pin) }
        assertEquals("retained", preferences.getString("nonce", null))
        assertTrue(pin.all { it == '\u0000' })
        check(preferences.edit().clear().commit())
        val payload = File(context.filesDir, "vault/payloads/retained.vault")
        check(payload.parentFile.mkdirs())
        payload.writeBytes(byteArrayOf(1, 2, 3))
        assertThrows(IllegalStateException::class.java) { PinVaultKeyStore(context).create("123456".toCharArray()) }
        assertArrayEquals(byteArrayOf(1, 2, 3), payload.readBytes())
        assertTrue(preferences.all.isEmpty())
    }

    @Test fun recoveryConfirmationSurvivesStoreRecreationWithoutChangingVaultKey() = isolated { context ->
        val key = ByteArray(32) { it.toByte() }
        val first = RecoveryVaultKeyStore(context)
        val secret = first.create(key)
        try {
            assertEquals(RecoverySetupState.PENDING_CONFIRMATION, RecoveryVaultKeyStore(context).setupState)
            assertFalse(first.isConfigured)
            RecoveryVaultKeyStore(context).confirm(secret.copyOf(), key)
            val reopened = RecoveryVaultKeyStore(context)
            assertTrue(reopened.isConfigured)
            val restored = reopened.unlock(secret.copyOf())
            try { assertArrayEquals(key, restored) } finally { restored.fill(0) }
            assertThrows(IllegalStateException::class.java) { reopened.clearFailedRestore() }
            assertTrue(RecoveryVaultKeyStore(context).isConfigured)
        } finally { secret.fill('\u0000'); key.fill(0) }
    }

    @Test fun unrelatedRestoreCannotClearExistingPinEnvelope() = isolated { context ->
        val keys = PinVaultKeyStore(context)
        val key = keys.create("123456".toCharArray())
        try {
            assertThrows(IllegalStateException::class.java) { PinVaultKeyStore(context).clearFailedRestore() }
            val reopened = PinVaultKeyStore(context).unlock("123456".toCharArray())
            try { assertArrayEquals(key, reopened) } finally { reopened.fill(0) }
        } finally { key.fill(0) }
    }

    private fun isolated(test: (Context) -> Unit) {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
        val id = UUID.randomUUID().toString()
        val root = File(app.cacheDir, "primary-security-$id").apply { check(mkdirs()) }
        val names = mutableSetOf<String>()
        val context = object : ContextWrapper(app) {
            override fun getFilesDir(): File = root
            override fun getNoBackupFilesDir(): File = File(root, "no-backup").apply { mkdirs() }
            override fun getSharedPreferences(name: String, mode: Int) =
                app.getSharedPreferences("primary-security-$id-$name", mode).also { names.add("primary-security-$id-$name") }
        }
        try { test(context) }
        finally { names.forEach { app.deleteSharedPreferences(it) }; root.deleteRecursively() }
    }
}
