package uk.co.traynor.privategallery.core.vault

import java.io.File
import java.security.MessageDigest
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import uk.co.traynor.privategallery.core.crypto.*

/** Reads frozen independently authored bytes; no serializer generates the input. */
class Phase0FrozenLegacyTest {
    @get:Rule val temporary = TemporaryFolder()
    private val expected = JSONObject(bytes("expected.json").toString(Charsets.UTF_8))
    private val key get() = hex(expected.getString("keyHex"))

    @Test fun frozenCorpusDigestsMatchManifest() {
        bytes("SHA256SUMS").toString(Charsets.UTF_8).lineSequence().filter { it.isNotBlank() }.forEach { line ->
            val (digest, name) = line.split("  ", limit = 2)
            assertEquals(name, digest, sha(bytes(name)))
        }
    }

    @Test fun allSixFrozenIndexFormatsReadWithoutChangingCiphertext() {
        for (version in 1..6) {
            val root = temporary.newFolder("v$version")
            val original = bytes("index-v$version.enc")
            File(root, "vault-index.enc").writeBytes(original)
            val snapshot = EncryptedIndexStore(root).loadSnapshot(key)
            assertMetadata(snapshot, version)
            assertArrayEquals(original, File(root, "vault-index.enc").readBytes())
        }
    }

    @Test fun authenticatedParserCorruptionFailsClosedWithoutReplacingIndex() {
        listOf("legacy-duplicate", "legacy-negative-size", "legacy-trailing", "v6-trailing", "v6-negative-size",
            "legacy-trash-without-date", "unknown-version", "negative-count", "excessive-count", "index-tag", "index-truncated").forEach { name ->
            val root = temporary.newFolder(name)
            val original = bytes("corrupt/$name.enc")
            File(root, "vault-index.enc").writeBytes(original)
            assertThrows("$name must be unavailable, never an empty writable snapshot", Exception::class.java) { EncryptedIndexStore(root).loadSnapshot(key) }
            assertArrayEquals(original, File(root, "vault-index.enc").readBytes())
            assertFalse(File(root, "vault-index.new").exists())
        }
    }

    @Test fun frozenPinAndRecoveryEnvelopesUnwrapExactSyntheticVdek() {
        val pin = bytes("pin-envelope.bin")
        assertArrayEquals(key, PinEnvelope.unwrap(expected.getString("pin").toCharArray(), PinWrappedKey(pin.copyOfRange(0,16),pin.copyOfRange(16,28),pin.copyOfRange(28,76))))
        val recovery = bytes("recovery-envelope.bin")
        assertArrayEquals(key, RecoveryEnvelope.unwrap(expected.getString("recovery").toCharArray(), RecoveryWrappedKey(recovery.copyOfRange(0,16),recovery.copyOfRange(16,28),recovery.copyOfRange(28,76))))
    }

    @Test fun frozenArchiveRestoresActualPgvideo1AndAllMetadata() {
        val stage = File(temporary.root,"restored")
        val restored = VaultBackupArchive.read(bytes("backup-v1.pgvault").inputStream(), stage, expected.getString("recovery").toCharArray())
        try {
            assertArrayEquals(key, restored.key)
            val snapshot = EncryptedIndexStore(stage).loadSnapshot(restored.key)
            assertMetadata(snapshot, 6)
            snapshot.items.forEach { item ->
                val stored = StoredPayload(item.id, File(stage,"payloads/${item.id}.vault"),item.plaintextSize,item.plaintextSha256,item.payloadNonce)
                assertTrue(EncryptedPayloadStore(stage).verify(stored,restored.key))
                val plain = EncryptedPayloadStore(stage).decryptToBytes(stored,restored.key)
                assertEquals(item.plaintextSize, plain.size.toLong())
                assertEquals(if (item.mimeType.startsWith("video")) expected.getString("videoSha256") else expected.getString("imageSha256"), sha(plain))
                plain.fill(0)
                assertEquals(item.mimeType.startsWith("video"), ChunkedVaultVideoStore.isChunked(stored.file))
            }
            assertEquals(setOf("vault-index.enc", "payloads/${expected.getString("imageId")}.vault", "payloads/${expected.getString("videoId")}.vault"), stage.walkTopDown().filter { it.isFile }.map { it.relativeTo(stage).invariantSeparatorsPath }.toSet())
        } finally { restored.key.fill(0) }
    }

    @Test fun productionBackupExportsOnlyVerifiedCiphertextFromFrozenPgvideo1Vault() {
        val stage=File(temporary.root,"backup-source")
        val restored=VaultBackupArchive.read(bytes("backup-v1.pgvault").inputStream(),stage,expected.getString("recovery").toCharArray())
        try {
            listOf("browser/default/cookies", "provider/settings.json", "staging/interrupted.part", "upload-cache/plain.jpg", "video-migration-interrupted/temporary").forEach { name ->
                File(stage,name).apply { parentFile!!.mkdirs(); writeText("SYNTHETIC-EXCLUDED-CANARY") }
            }
            val exported=java.io.ByteArrayOutputStream().also { VaultBackupArchive.write(stage,restored.key,restored.recoveryEnvelope,it) }.toByteArray()
            val entries=mutableMapOf<String,ByteArray>()
            java.util.zip.ZipInputStream(exported.inputStream()).use { zip ->
                while(true) { val entry=zip.nextEntry ?: break; entries[entry.name]=zip.readBytes() }
            }
            assertEquals((0 until expected.getJSONArray("expectedEntries").length()).map { expected.getJSONArray("expectedEntries").getString(it) }.toSet(),entries.keys)
            assertArrayEquals(bytes("index-v6.enc"),entries.getValue("vault-index.enc"))
            for(id in listOf(expected.getString("imageId"),expected.getString("videoId"))) assertArrayEquals(bytes("payloads/$id.vault"),entries.getValue("payloads/$id.vault"))
            assertFalse(exported.toString(Charsets.ISO_8859_1).contains("SYNTHETIC-EXCLUDED-CANARY"))
            val roundtrip=VaultBackupArchive.read(exported.inputStream(),File(temporary.root,"export-restored"),expected.getString("recovery").toCharArray())
            try { assertMetadata(EncryptedIndexStore(roundtrip.stage).loadSnapshot(roundtrip.key),6) } finally { roundtrip.key.fill(0) }
        } finally { restored.key.fill(0) }
    }

    @Test fun missingIndexWithDurablePayloadFailsClosedWithoutDeletingSoleCiphertext() {
        for(suffix in listOf("vault","deleting","legacy")) {
            val root=temporary.newFolder("missing-index-$suffix")
            val file=File(root,"payloads/${expected.getString("imageId")}.$suffix").apply { parentFile!!.mkdirs(); writeBytes(bytes("payloads/${expected.getString("imageId")}.vault")) }
            val original=file.readBytes()
            assertThrows(IllegalStateException::class.java) { EncryptedIndexStore(root).loadSnapshot(key) }
            assertArrayEquals(original,file.readBytes())
            assertFalse(File(root,"vault-index.enc").exists())
            assertFalse(File(root,"vault-index.new").exists())
        }
        val fresh=temporary.newFolder("fresh-part-only")
        File(fresh,"staging/interrupted.part").apply { parentFile!!.mkdirs(); writeBytes(byteArrayOf(1,2,3)) }
        // Phase 1 never admits a partial root as empty. Explicit reconciliation of known
        // interrupted staging is separate from index admission and preserves durable payloads.
        assertThrows(IllegalStateException::class.java) { EncryptedIndexStore(fresh).loadSnapshot(key) }
        EncryptedPayloadStore(fresh).reconcileInterruptedWrites()
        assertTrue(EncryptedIndexStore(fresh).loadSnapshot(key).items.isEmpty())
    }

    @Test fun oversizedLocalIndexFailsBeforeDecryptOrRewrite() {
        val root=temporary.newFolder("oversized-index")
        val file=File(root,"vault-index.enc")
        java.io.RandomAccessFile(file,"rw").use { it.setLength(64L*1024*1024+29) }
        assertThrows(IllegalArgumentException::class.java) { EncryptedIndexStore(root).loadSnapshot(key) }
        assertEquals(64L*1024*1024+29,file.length())
        assertFalse(File(root,"vault-index.new").exists())
    }

    @Test fun frozenArchiveFailuresRemoveStageAndPreserveOriginalArchive() {
        listOf("archive-truncated", "archive-duplicate", "archive-payload-truncated", "archive-video-tag-rehashed").forEach { name ->
            val original = bytes("corrupt/$name.pgvault")
            val stage = File(temporary.root,name)
            assertThrows(Exception::class.java) { VaultBackupArchive.read(original.inputStream(),stage,expected.getString("recovery").toCharArray()) }
            assertFalse(stage.exists())
            assertArrayEquals(original,bytes("corrupt/$name.pgvault"))
        }
        val stage = File(temporary.root,"wrong-recovery")
        assertThrows(Exception::class.java) { VaultBackupArchive.read(bytes("backup-v1.pgvault").inputStream(),stage,"wrong-synthetic-secret".toCharArray()) }
        assertFalse(stage.exists())
    }

    private fun assertMetadata(snapshot: VaultIndexSnapshot, version: Int) {
        assertEquals(listOf(expected.getString("imageId"),expected.getString("videoId")), snapshot.items.map { it.id })
        val image = snapshot.items.first(); val video = snapshot.items.last()
        assertEquals("synthetic-image.jpg",image.displayName); assertEquals("image/jpeg",image.mimeType)
        assertEquals(expected.getLong("importedAt"),image.importedAtEpochMillis)
        assertEquals(expected.getLong("imageSize"),image.plaintextSize); assertEquals(expected.getLong("videoSize"),video.plaintextSize)
        assertEquals(expected.getString("imageSha256"),shaHex(image.plaintextSha256)); assertEquals(expected.getString("videoSha256"),shaHex(video.plaintextSha256))
        assertEquals(expected.getString("imageNonceHex"),shaHex(image.payloadNonce)); assertEquals(expected.getString("videoNonceHex"),shaHex(video.payloadNonce))
        assertEquals("content://synthetic/image",image.sourceUri); assertEquals("content://synthetic/video",video.sourceUri)
        assertEquals(if(version>=5) MediaOrigin.LOCAL_EDIT else MediaOrigin.IMPORTED,image.origin)
        assertEquals(if(version>=5) MediaOrigin.REMOTE_AI_EDIT else MediaOrigin.IMPORTED,video.origin)
        assertEquals(version>=5,video.vaultOnly); assertFalse(image.vaultOnly)
        assertEquals(if(version>=6) VaultItemState.TRASHED else VaultItemState.COMPLETE,video.state)
        assertEquals(if(version>=6) expected.getLong("deletedAt") else null,video.deletedAtEpochMillis)
        assertEquals(if(version>=2) 1 else 0,snapshot.collections.size)
        if(version>=2) {
            val collection=snapshot.collections.single(); assertEquals(expected.getString("collectionId"),collection.id)
            assertEquals("Synthetic favourites",collection.name); assertEquals(image.id,collection.coverVaultItemId)
            assertEquals(1700000000100L,collection.createdAtEpochMillis); assertNull(collection.pinnedDestination)
            assertEquals(listOf(image.id,video.id),snapshot.memberships.map { it.vaultItemId })
            assertEquals(listOf(1700000000101L,1700000000102L),snapshot.memberships.map { it.addedAtEpochMillis })
        }
        if(version>=3) {
            assertEquals(NormalizedCrop(.1f,.2f,.8f,.9f),snapshot.imageEdits.getValue(image.id).crop)
            assertEquals(NormalizedCrop.ORIGINAL,snapshot.imageEdits.getValue(image.id).previousCrop)
        } else assertTrue(snapshot.imageEdits.isEmpty())
        assertEquals(if(version>=4) expected.getString("collectionId") else null,snapshot.favouriteCollectionId)
    }
    private fun bytes(name:String)=checkNotNull(javaClass.getResourceAsStream("/phase0/legacy-v1/$name")) { name }.use { it.readBytes() }
    private fun hex(s:String)=s.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    private fun sha(b:ByteArray)=shaHex(MessageDigest.getInstance("SHA-256").digest(b))
    private fun shaHex(b:ByteArray)=b.joinToString("") { "%02x".format(it) }
}
