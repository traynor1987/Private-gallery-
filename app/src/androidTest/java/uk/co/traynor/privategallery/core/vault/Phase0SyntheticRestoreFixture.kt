package uk.co.traynor.privategallery.core.vault

import android.content.Context
import android.content.ContextWrapper
import androidx.test.platform.app.InstrumentationRegistry
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import org.json.JSONObject
import org.junit.Assert.*

/** All disk/pref access stays in disposable, random test namespaces, including applicationContext. */
internal class Phase0SyntheticRestoreContext(label: String) : AutoCloseable {
    private val app = InstrumentationRegistry.getInstrumentation().targetContext
    private val namespace = "phase0-clean-restore-$label-${UUID.randomUUID()}"
    private val preferences = mutableSetOf<String>()
    val folder = File(app.cacheDir, namespace).apply { check(mkdirs()) }
    val context = object : ContextWrapper(app) {
        override fun getApplicationContext(): Context = this
        override fun getFilesDir() = folder
        override fun getCacheDir() = File(folder, "cache").apply { check(isDirectory || mkdirs()) }
        override fun getNoBackupFilesDir() = File(folder, "no-backup").apply { check(isDirectory || mkdirs()) }
        override fun getSharedPreferences(name: String, mode: Int) =
            app.getSharedPreferences("$namespace-$name".also(preferences::add), mode)
    }

    fun envelopePreferences() = listOf("vault-key-envelope", "vault-recovery-envelope")
        .associateWith { context.getSharedPreferences(it, Context.MODE_PRIVATE).all.toMap() }

    fun files(): Map<String, String> = folder.walkTopDown().filter { it.isFile }.associate {
        it.relativeTo(folder).invariantSeparatorsPath to phase0Sha256(it.readBytes())
    }

    override fun close() {
        check(folder.deleteRecursively())
        preferences.forEach { check(app.deleteSharedPreferences(it)) }
    }
}

internal class Phase0FrozenRestoreFixture {
    private val assets = InstrumentationRegistry.getInstrumentation().context.assets
    private val hashes = assets.open("phase0/SHA256SUMS").use { it.readBytes().toString(Charsets.UTF_8) }
        .lineSequence().filter { it.isNotBlank() }.associate {
            val fields = it.split("  ", limit = 2); fields[1] to fields[0]
        }
    val expected = JSONObject(verifiedAsset("expected.json").toString(Charsets.UTF_8))
    val key = phase0HexBytes(expected.getString("keyHex"))
    val entries = phase0ArchiveEntries(verifiedAsset("backup-v1.pgvault"))
    val ciphertextNames = entries.keys - "manifest.json"

    private fun verifiedAsset(name: String): ByteArray = assets.open("phase0/$name").use { it.readBytes() }
        .also { assertEquals("Frozen fixture digest: $name", hashes.getValue(name), phase0Sha256(it)) }

    /** Seeds existing immutable ciphertext, never calls a Kotlin payload/index writer. */
    fun seedCiphertext(root: File) {
        ciphertextNames.forEach { name ->
            val destination = File(root, name)
            check(destination.parentFile!!.isDirectory || destination.parentFile!!.mkdirs())
            destination.writeBytes(entries.getValue(name))
        }
    }

    /** Literal wire-format expectations from the independent fixture author, not a round-trip writer. */
    fun logicalSnapshot(): VaultIndexSnapshot {
        val image = expected.getString("imageId")
        val video = expected.getString("videoId")
        val collection = expected.getString("collectionId")
        return VaultIndexSnapshot(
            items = listOf(
                VaultItem(image, "image/jpeg", "synthetic-image.jpg", 1700000000000,
                    expected.getLong("imageSize"), phase0HexBytes(expected.getString("imageSha256")),
                    phase0HexBytes(expected.getString("imageNonceHex")), VaultItemState.COMPLETE,
                    "content://synthetic/image", MediaOrigin.LOCAL_EDIT, false, null),
                VaultItem(video, "video/mp4", "synthetic-video.mp4", 1700000000001,
                    expected.getLong("videoSize"), phase0HexBytes(expected.getString("videoSha256")),
                    phase0HexBytes(expected.getString("videoNonceHex")), VaultItemState.TRASHED,
                    "content://synthetic/video", MediaOrigin.REMOTE_AI_EDIT, true, 1700000000999),
            ),
            collections = listOf(VaultCollection(collection, "Synthetic favourites", 1700000000100, null, image)),
            memberships = listOf(VaultCollectionMembership(collection, image, 1700000000101),
                VaultCollectionMembership(collection, video, 1700000000102)),
            imageEdits = mapOf(image to ImageEditState(NormalizedCrop(.1f, .2f, .8f, .9f), NormalizedCrop.ORIGINAL)),
            favouriteCollectionId = collection,
        )
    }
}

/** ByteArray data-class equality is referential; assert every logical field plus byte contents. */
internal fun assertPhase0LogicalSnapshot(expected: VaultIndexSnapshot, actual: VaultIndexSnapshot) {
    assertEquals(expected.items.size, actual.items.size)
    expected.items.zip(actual.items).forEach { (want, got) ->
        assertEquals(want.id, got.id)
        assertEquals(want.mimeType, got.mimeType)
        assertEquals(want.displayName, got.displayName)
        assertEquals(want.importedAtEpochMillis, got.importedAtEpochMillis)
        assertEquals(want.plaintextSize, got.plaintextSize)
        assertArrayEquals(want.plaintextSha256, got.plaintextSha256)
        assertArrayEquals(want.payloadNonce, got.payloadNonce)
        assertEquals(want.state, got.state)
        assertEquals(want.sourceUri, got.sourceUri)
        assertEquals(want.origin, got.origin)
        assertEquals(want.vaultOnly, got.vaultOnly)
        assertEquals(want.deletedAtEpochMillis, got.deletedAtEpochMillis)
    }
    assertEquals(expected.collections, actual.collections)
    assertEquals(expected.memberships, actual.memberships)
    assertEquals(expected.imageEdits, actual.imageEdits)
    assertEquals(expected.favouriteCollectionId, actual.favouriteCollectionId)
}

internal fun phase0ArchiveEntries(bytes: ByteArray): Map<String, ByteArray> = buildMap {
    ZipInputStream(bytes.inputStream()).use { zip ->
        while (true) {
            val entry = zip.nextEntry ?: break
            assertFalse(entry.isDirectory)
            assertFalse("Duplicate archive entry: ${entry.name}", containsKey(entry.name))
            put(entry.name, zip.readBytes())
        }
    }
}

/** Rehashing bypasses the archive's public digest check so real PGVIDEO1 authentication must fail. */
internal fun phase0CorruptVideoAndRehash(archive: ByteArray, videoId: String): ByteArray {
    val entries = phase0ArchiveEntries(archive).toMutableMap()
    val name = "payloads/$videoId.vault"
    val payload = entries.getValue(name)
    payload[payload.lastIndex] = (payload.last().toInt() xor 1).toByte()
    val manifest = JSONObject(entries.getValue("manifest.json").toString(Charsets.UTF_8))
    manifest.getJSONObject("hashes").put(name, phase0Sha256(payload))
    entries["manifest.json"] = manifest.toString().toByteArray(Charsets.UTF_8)
    return ByteArrayOutputStream().also { output ->
        ZipOutputStream(output).use { zip ->
            entries.forEach { (entryName, bytes) ->
                zip.putNextEntry(ZipEntry(entryName)); zip.write(bytes); zip.closeEntry()
            }
        }
    }.toByteArray()
}

internal fun phase0Sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes)
    .joinToString("") { "%02x".format(it) }
internal fun phase0HexBytes(value: String) = value.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
