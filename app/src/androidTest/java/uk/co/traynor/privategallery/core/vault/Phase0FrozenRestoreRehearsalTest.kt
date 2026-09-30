package uk.co.traynor.privategallery.core.vault

import android.content.Context
import android.content.ContextWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.traynor.privategallery.core.security.PinVaultKeyStore
import uk.co.traynor.privategallery.core.security.RecoveryVaultKeyStore

/** Only random test namespaces under cache; never accesses filesDir/vault or owner PIN slots. */
@RunWith(AndroidJUnit4::class)
class Phase0FrozenRestoreRehearsalTest {
    @Test fun independentlyFrozenBackupRestoresVideoTrashProvenanceAndRecoveryBytes() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext
        val namespace = "phase0-synthetic-restore-${UUID.randomUUID()}"
        val folder = File(app.cacheDir,namespace).apply { check(mkdirs()) }
        val context = object: ContextWrapper(app) {
            override fun getFilesDir() = folder
            override fun getSharedPreferences(name:String,mode:Int) = app.getSharedPreferences("$namespace-$name",mode)
        }
        val expected = instrumentation.context.assets.open("phase0/expected.json").use { JSONObject(it.readBytes().toString(Charsets.UTF_8)) }
        val key = expected.getString("keyHex").chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val recovery = expected.getString("recovery").toCharArray()
        val archive = instrumentation.context.assets.open("phase0/backup-v1.pgvault").use { it.readBytes() }
        val frozenHashes = instrumentation.context.assets.open("phase0/SHA256SUMS").use { it.readBytes().toString(Charsets.UTF_8) }
            .lineSequence().filter { it.isNotBlank() }.associate { val fields=it.split("  ",limit=2); fields[1] to fields[0] }
        assertEquals(frozenHashes.getValue("backup-v1.pgvault"),hex(MessageDigest.getInstance("SHA-256").digest(archive)))
        val expectedBytes=instrumentation.context.assets.open("phase0/expected.json").use { it.readBytes() }
        assertEquals(frozenHashes.getValue("expected.json"),hex(MessageDigest.getInstance("SHA-256").digest(expectedBytes)))
        val pinKeys = PinVaultKeyStore(context)
        val recoveryKeys = RecoveryVaultKeyStore(context)
        try {
            assertFalse(pinKeys.isConfigured)
            val restored = AndroidVaultRepository.restoreBackup(context,archive.inputStream(),recovery.copyOf(),expected.getString("pin").toCharArray(),pinKeys,recoveryKeys)
            assertArrayEquals(key,restored)
            restored.fill(0)
            // Reconstruction is a restart-equivalent adapter test, not a claimed Android process-kill measurement.
            val reopened = PinVaultKeyStore(context).unlock(expected.getString("pin").toCharArray())
            try {
                assertArrayEquals(key,reopened)
                val root = File(folder,"vault")
                val snapshot = EncryptedIndexStore(root).loadSnapshot(reopened)
                assertEquals(2,snapshot.items.size)
                assertEquals("Synthetic favourites",snapshot.collections.single().name)
                assertEquals(expected.getString("collectionId"),snapshot.favouriteCollectionId)
                assertEquals(2,snapshot.memberships.size)
                assertEquals(NormalizedCrop(.1f,.2f,.8f,.9f),snapshot.imageEdits.getValue(expected.getString("imageId")).crop)
                assertEquals(NormalizedCrop.ORIGINAL,snapshot.imageEdits.getValue(expected.getString("imageId")).previousCrop)
                val image = snapshot.items.first(); val video = snapshot.items.last()
                assertEquals(MediaOrigin.LOCAL_EDIT,image.origin); assertEquals(MediaOrigin.REMOTE_AI_EDIT,video.origin)
                assertFalse(image.vaultOnly); assertTrue(video.vaultOnly)
                assertEquals(VaultItemState.TRASHED,video.state); assertEquals(expected.getLong("deletedAt"),video.deletedAtEpochMillis)
                assertEquals("content://synthetic/video",video.sourceUri)
                snapshot.items.forEach { item ->
                    val stored = StoredPayload(item.id,File(root,"payloads/${item.id}.vault"),item.plaintextSize,item.plaintextSha256,item.payloadNonce)
                    assertTrue(EncryptedPayloadStore(root).verify(stored,reopened))
                    assertEquals(item.id==video.id,ChunkedVaultVideoStore.isChunked(stored.file))
                    val plaintext = EncryptedPayloadStore(root).decryptToBytes(stored,reopened)
                    assertEquals(if(item.id==video.id) expected.getString("videoSha256") else expected.getString("imageSha256"),hex(MessageDigest.getInstance("SHA-256").digest(plaintext)))
                    plaintext.fill(0)
                }
                assertEquals(setOf("vault-index.enc","payloads/${image.id}.vault","payloads/${video.id}.vault"),root.walkTopDown().filter { it.isFile }.map { it.relativeTo(root).invariantSeparatorsPath }.toSet())
                val exported = recoveryKeys.exportEnvelope()
                java.util.zip.ZipInputStream(archive.inputStream()).use { zip ->
                    while(true) {
                        val entry=zip.nextEntry ?: break
                        if(entry.name=="manifest.json") {
                            val wrap=JSONObject(zip.readBytes().toString(Charsets.UTF_8)).getJSONObject("recovery")
                            assertArrayEquals(java.util.Base64.getDecoder().decode(wrap.getString("salt")),exported.salt)
                            assertArrayEquals(java.util.Base64.getDecoder().decode(wrap.getString("nonce")),exported.nonce)
                            assertArrayEquals(java.util.Base64.getDecoder().decode(wrap.getString("ciphertext")),exported.ciphertext)
                        }
                    }
                }
            } finally { reopened.fill(0) }
        } finally {
            key.fill(0); recovery.fill('\u0000'); archive.fill(0)
            folder.deleteRecursively()
            app.getSharedPreferences("$namespace-vault-key-envelope",Context.MODE_PRIVATE).edit().clear().commit()
            app.getSharedPreferences("$namespace-vault-recovery-envelope",Context.MODE_PRIVATE).edit().clear().commit()
        }
    }
    private fun hex(bytes:ByteArray)=bytes.joinToString("") { "%02x".format(it) }
}
