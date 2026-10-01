package uk.co.traynor.privategallery.core.security

import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import java.io.File
import org.junit.Assert.*
import org.junit.Test

class Phase1PrimarySlotsTest {
    @Test fun foreignIdentityRejectedBeforeAnyRootAccess() {
        var accesses = 0
        val context = object : ContextWrapper(ApplicationProvider.getApplicationContext<Context>()) {
            override fun getFilesDir(): File { accesses++; error("foreign root consulted") }
        }
        assertThrows(IllegalStateException::class.java) { LegacyPrimaryContainer(context, ContainerId.synthetic()) }
        assertEquals(0, accesses)
    }

    @Test fun partialRecoveryOrBiometricSlotsNeverPermitFreshSetup() = isolated { context ->
        for (slot in listOf("vault-recovery-envelope", "vault-biometric-envelope", "vault-key-envelope")) {
            val prefs = context.getSharedPreferences(slot, Context.MODE_PRIVATE)
            prefs.edit().putString("partial", "synthetic-canary").commit()
            val keys = PinVaultKeyStore(context)
            assertTrue(keys.isConfigured)
            assertThrows(IllegalStateException::class.java) { keys.create("123456".toCharArray()) }
            assertEquals("synthetic-canary", prefs.getString("partial", null))
            prefs.edit().clear().commit()
        }
    }

    @Test fun pinChangeAndRecoveryTouchOnlyFixedPrimarySlotsAndPreserveVdek() = isolated { context ->
        val foreign = context.getSharedPreferences("synthetic-foreign-slot", Context.MODE_PRIVATE)
        foreign.edit().putString("canary", "untouched").commit()
        val before = foreign.all.toMap()
        val slots = PrimaryKeySlots(context)
        val original = slots.pin.create("123456".toCharArray())
        val authority = PrimarySessionAuthority()
        var secret: CharArray? = null
        try {
            authority.open(original.copyOf())
            checkNotNull(authority.operationOrNull(uk.co.traynor.privategallery.core.security.PrimaryScope.entries.toSet())).use { operation ->
                secret = slots.prepareRecovery(operation, false)
                slots.confirmRecovery(operation, checkNotNull(secret).copyOf())
                slots.changePin(operation, "123456".toCharArray(), "654321".toCharArray())
                val changed = slots.pin.unlock("654321".toCharArray())
                try { assertArrayEquals(original, changed) } finally { changed.fill(0) }
                operation.fork(setOf(PrimaryScope.READ)).use { readOnly ->
                    assertThrows(IllegalStateException::class.java) { slots.changePin(readOnly, "654321".toCharArray(), "111111".toCharArray()) }
                    assertThrows(IllegalStateException::class.java) { slots.confirmRecovery(readOnly, checkNotNull(secret).copyOf()) }
                }
            }
            authority.revoke()
            val recovered = slots.recover(checkNotNull(secret).copyOf(), "222222".toCharArray()) { it() }
            try { assertArrayEquals(original, recovered) } finally { recovered.fill(0) }
            val after = slots.pin.unlock("222222".toCharArray())
            try { assertArrayEquals(original, after) } finally { after.fill(0) }
            assertEquals(before, foreign.all)
        } finally { original.fill(0); secret?.fill('\u0000'); authority.revoke() }
    }

    @Test fun delayedPinPromotionCannotCommitAfterDeadline() = isolated { context ->
        val slots = PrimaryKeySlots(context)
        val key = slots.pin.create("123456".toCharArray())
        var now = 0L
        val authority = PrimarySessionAuthority { now }
        try {
            authority.open(key.copyOf())
            val operation = checkNotNull(authority.operationOrNull(setOf(PrimaryScope.CREDENTIALS)))
            val before = context.getSharedPreferences("vault-key-envelope", 0).all.toMap()
            authority.onBackgrounded(10)
            assertThrows(IllegalStateException::class.java) {
                slots.pin.changePin("123456".toCharArray(), "654321".toCharArray()) { commit ->
                    now = 10 // Preparation succeeded; no timer callback was delivered.
                    operation.commit(commit)
                }
            }
            assertEquals(before, context.getSharedPreferences("vault-key-envelope", 0).all)
            val restored = slots.pin.unlock("123456".toCharArray())
            try { assertArrayEquals(key, restored) } finally { restored.fill(0) }
        } finally { authority.revoke(); key.fill(0) }
    }

    @Test fun inaccessibleNestedPrimaryMaterialCannotPermitFreshKeyCreation() = isolated { context ->
        val nested = File(context.filesDir, "vault/payloads").apply { mkdirs() }
        val sole = File(nested, "sole.vault").apply { writeBytes(byteArrayOf(4, 5, 6)) }
        check(nested.setReadable(false, false) && nested.setExecutable(false, false))
        try {
            assertNull("Synthetic app UID must actually be unable to inventory the directory", nested.listFiles())
            assertFalse(PrimaryVaultSetupGuard.canCreate(context.filesDir, false))
            assertThrows(IllegalStateException::class.java) {
                uk.co.traynor.privategallery.core.vault.EncryptedIndexStore(File(context.filesDir, "vault")).loadSnapshot(ByteArray(32))
            }
            assertThrows(IllegalStateException::class.java) { PinVaultKeyStore(context).create("123456".toCharArray()) }
            assertFalse(PinVaultKeyStore(context).hasEnvelopeMaterial)
        } finally {
            check(nested.setReadable(true, false) && nested.setExecutable(true, false))
            assertArrayEquals(byteArrayOf(4, 5, 6), sole.readBytes())
        }
    }

    @Test fun inaccessibleEmptyNestedDirectoryCannotPermitFreshKeyCreation() = isolated { context ->
        val nested = File(context.filesDir, "vault/payloads/empty").apply { mkdirs() }
        check(nested.setReadable(false, false) && nested.setExecutable(false, false))
        try {
            assertNull(nested.listFiles())
            assertFalse(PrimaryVaultSetupGuard.canCreate(context.filesDir, false))
            val keys = PinVaultKeyStore(context)
            assertThrows(IllegalStateException::class.java) { keys.create("123456".toCharArray()) }
            assertFalse(keys.hasEnvelopeMaterial)
        } finally { check(nested.setReadable(true, false) && nested.setExecutable(true, false)) }
    }

    @Test fun unsearchableNestedDirectoryCannotBeMistakenForEmpty() = isolated { context ->
        val nested = File(context.filesDir, "vault/payloads").apply { mkdirs() }
        val sole = File(nested, "unknown.part").apply { writeBytes(byteArrayOf(7, 8)) }
        check(nested.setExecutable(false, false))
        try {
            assertFalse(PrimaryVaultSetupGuard.canCreate(context.filesDir, false))
            val keys = PinVaultKeyStore(context)
            assertThrows(IllegalStateException::class.java) { keys.create("123456".toCharArray()) }
            assertFalse(keys.hasEnvelopeMaterial)
        } finally {
            check(nested.setExecutable(true, false))
            assertArrayEquals(byteArrayOf(7, 8), sole.readBytes())
        }
    }

    @Test fun genuineEmptyInstallationStillCreatesAndUnlocksSamePrimaryKey() = isolated { context ->
        File(context.filesDir, "vault/payloads").mkdirs()
        File(context.filesDir, "vault/staging").mkdirs()
        val keys = PinVaultKeyStore(context)
        assertFalse(keys.isConfigured)
        val created = keys.create("123456".toCharArray())
        try {
            assertTrue(keys.isConfigured)
            val unlocked = keys.unlock("123456".toCharArray())
            try { assertArrayEquals(created, unlocked) } finally { unlocked.fill(0) }
        } finally { created.fill(0) }
    }

    @Test fun unsearchableEmptyDirectoryCannotAuthorizeInitialSetup() = isolated { context ->
        val nested = File(context.filesDir, "vault/payloads").apply { mkdirs() }
        check(nested.setExecutable(false, false))
        try {
            assertFalse(nested.canExecute())
            assertFalse(PrimaryVaultSetupGuard.canCreate(context.filesDir, false))
            assertThrows(IllegalStateException::class.java) { PinVaultKeyStore(context).create("123456".toCharArray()) }
            assertFalse(PinVaultKeyStore(context).hasEnvelopeMaterial)
        } finally { check(nested.setExecutable(true, false)) }
    }

    @Test fun setupCannotSaveCredentialsWhileRestoreOwnsPrimaryTransaction() = isolated { context ->
        val resolvingRoot = java.util.concurrent.CountDownLatch(1)
        val resumeRestore = java.util.concurrent.CountDownLatch(1)
        val attemptingSetup = java.util.concurrent.CountDownLatch(1)
        val restoreContext = object : ContextWrapper(context) {
            override fun getFilesDir(): File {
                resolvingRoot.countDown()
                check(resumeRestore.await(15, java.util.concurrent.TimeUnit.SECONDS))
                return context.filesDir
            }
        }
        val keys = PinVaultKeyStore(context)
        val recovery = RecoveryVaultKeyStore(context)
        val pool = java.util.concurrent.Executors.newFixedThreadPool(2)
        val restore = pool.submit<Throwable?> {
            runCatching {
                uk.co.traynor.privategallery.core.vault.AndroidVaultRepository.restoreBackup(
                    restoreContext, java.io.ByteArrayInputStream(byteArrayOf()), "synthetic".toCharArray(),
                    "654321".toCharArray(), keys, recovery)
            }.exceptionOrNull()
        }
        var setup: java.util.concurrent.Future<ByteArray>? = null
        try {
            assertTrue(resolvingRoot.await(10, java.util.concurrent.TimeUnit.SECONDS))
            val pendingSetup = pool.submit<ByteArray> { attemptingSetup.countDown(); keys.create("123456".toCharArray()) }
            setup = pendingSetup
            assertTrue(attemptingSetup.await(10, java.util.concurrent.TimeUnit.SECONDS))
            assertThrows(java.util.concurrent.TimeoutException::class.java) {
                pendingSetup.get(3, java.util.concurrent.TimeUnit.SECONDS)
            }
            assertFalse(keys.hasEnvelopeMaterial)
            resumeRestore.countDown()
            assertNotNull(restore.get(10, java.util.concurrent.TimeUnit.SECONDS))
            val created = pendingSetup.get(10, java.util.concurrent.TimeUnit.SECONDS)
            try { assertTrue(keys.isConfigured) } finally { created.fill(0) }
        } finally {
            resumeRestore.countDown()
            runCatching { setup?.get(10, java.util.concurrent.TimeUnit.SECONDS)?.fill(0) }
            pool.shutdownNow()
            check(pool.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS))
        }
    }

    private fun isolated(test: (Context) -> Unit) {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val id = "phase1-slots-${java.util.UUID.randomUUID()}"
        val root = File(base.cacheDir, id).apply { mkdirs() }
        val names = mutableSetOf<String>()
        val context = object : ContextWrapper(base) {
            override fun getFilesDir() = root
            override fun getSharedPreferences(name: String, mode: Int): android.content.SharedPreferences {
                val scoped = "$id-$name"; names += scoped
                return base.getSharedPreferences(scoped, mode)
            }
        }
        try { test(context) } finally { names.forEach { base.getSharedPreferences(it, 0).edit().clear().commit() }; root.deleteRecursively() }
    }
}
