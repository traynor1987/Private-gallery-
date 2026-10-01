package uk.co.traynor.privategallery.core.vault

import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import uk.co.traynor.privategallery.core.security.*
import uk.co.traynor.privategallery.core.crypto.RecoveryEnvelope

class Phase1AsyncDestinationTest {
    @Test fun oldAiAndBrowserResultsCannotImportAfterReauthentication() = isolated { context, root ->
        val authority = PrimarySessionAuthority { 0 }
        try {
            authority.open(ByteArray(32) { 19 })
            val original = checkNotNull(authority.operationOrNull())
            val destination = AndroidVaultRepository(context, original)
            destination.createCollection("original")
            val before = root.resolve("vault/vault-index.enc").readBytes()
            authority.revoke(); authority.open(ByteArray(32) { 19 })
            assertThrows(IllegalStateException::class.java) {
                destination.importAiGeneratedImage(ByteArray(1024), "synthetic/model", false) { false }
            }
            var opened = 0
            assertThrows(IllegalStateException::class.java) {
                VaultImportCoordinator(destination).acquire(VaultImportSource("download", "image/png", { opened++; byteArrayOf(1).inputStream() }))
            }
            assertEquals(0, opened)
            assertArrayEquals(before, root.resolve("vault/vault-index.enc").readBytes())
            assertTrue(AndroidVaultRepository(context, checkNotNull(authority.operationOrNull())).items().isEmpty())
        } finally { authority.revoke() }
    }

    @Test fun deadlineDuringBrowserAcquisitionCannotPromoteIndexOrPayload() = isolated { context, root ->
        var now = 0L
        val authority = PrimarySessionAuthority { now }
        try {
            authority.open(ByteArray(32) { 19 })
            val destination = AndroidVaultRepository(context, checkNotNull(authority.operationOrNull()))
            destination.createCollection("retained")
            val before = root.resolve("vault/vault-index.enc").readBytes()
            authority.onBackgrounded(10)
            assertThrows(IllegalStateException::class.java) {
                destination.importVerified(VaultImportSource("download", "image/png", { error("unscoped network open") },
                    openScopedStream = { guard ->
                        guard.own(AutoCloseable {})
                        now = 10 // No timer task runs; guard must reject the next read.
                        byteArrayOf(1,2,3).inputStream()
                    }))
            }
            assertArrayEquals(before, root.resolve("vault/vault-index.enc").readBytes())
            assertTrue(root.resolve("vault/payloads").listFiles().orEmpty().none { it.name.endsWith(".vault") })
        } finally { authority.revoke() }
    }

    @Test fun primaryBackupCannotEnumerateSyntheticForeignRootOrPlaintextCanaries() = isolated { context, root ->
        val authority = PrimarySessionAuthority { 0 }
        val key = ByteArray(32) { 19 }
        val secret = "synthetic-recovery-for-test-only".toCharArray()
        try {
            authority.open(key.copyOf())
            root.resolve("synthetic-foreign/payloads/foreign-canary").apply { parentFile!!.mkdirs(); writeText("FOREIGN") }
            root.resolve("browser/plaintext-canary").apply { parentFile!!.mkdirs(); writeText("BROWSER") }
            val operation = checkNotNull(authority.operationOrNull())
            val repository = AndroidVaultRepository(context, operation)
            repository.createCollection("Primary")
            val envelope = RecoveryEnvelope.create(secret.copyOf(), key)
            val output = java.io.ByteArrayOutputStream()
            repository.exportBackup(secret.copyOf(), envelope, output)
            val stage = root.resolve("synthetic-archive-read")
            val restored = VaultBackupArchive.read(output.toByteArray().inputStream(), stage, secret.copyOf())
            try {
                assertEquals("Primary", EncryptedIndexStore(stage).loadSnapshot(restored.key).collections.single().name)
                assertEquals(setOf("vault-index.enc"), stage.walkTopDown().filter { it.isFile }.map { it.relativeTo(stage).invariantSeparatorsPath }.toSet())
            } finally { restored.key.fill(0) }
        } finally { key.fill(0); secret.fill('\u0000'); authority.revoke() }
    }

    private fun isolated(test: (Context, File) -> Unit) {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val root = File(base.cacheDir, "phase1-destination-${java.util.UUID.randomUUID()}").apply { mkdirs() }
        val context = object : ContextWrapper(base) { override fun getFilesDir() = root }
        try { test(context, root) } finally { root.deleteRecursively() }
    }
}
