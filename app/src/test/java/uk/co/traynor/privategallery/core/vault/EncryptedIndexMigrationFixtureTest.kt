package uk.co.traynor.privategallery.core.vault

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import uk.co.traynor.privategallery.core.crypto.EncryptionHeader
import uk.co.traynor.privategallery.core.crypto.VaultCipher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Realistic encrypted v2/v3 ledgers prove upgrades preserve media and collection meaning. */
class EncryptedIndexMigrationFixtureTest {
    private val key = ByteArray(32) { (it + 3).toByte() }

    @Test fun `v2 fixture preserves items collections and memberships then saves v5`() {
        val root = fixtureRoot()
        writeLegacySnapshot(root, version = 2, includeEdit = false)
        val store = EncryptedIndexStore(root, syncOutput = {})
        val snapshot = store.loadSnapshot(key)
        assertEquals(listOf("item-1"), snapshot.items.map { it.id })
        assertEquals(listOf("collection-1"), snapshot.collections.map { it.id })
        assertEquals(listOf("item-1"), snapshot.memberships.map { it.vaultItemId })
        assertTrue(snapshot.imageEdits.isEmpty())
        store.saveSnapshot(snapshot, key)
        val rewritten = store.loadSnapshot(key)
        assertEquals(snapshot.items.map { it.id }, rewritten.items.map { it.id })
        assertEquals(snapshot.collections, rewritten.collections)
        assertEquals(snapshot.memberships, rewritten.memberships)
        assertEquals(snapshot.imageEdits, rewritten.imageEdits)
        assertEquals(5, currentVersion(root))
        assertEquals(MediaOrigin.IMPORTED, rewritten.items.single().origin)
        assertTrue(!rewritten.items.single().vaultOnly)
    }

    @Test fun `v3 fixture preserves image edit and serialises current v5`() {
        val root = fixtureRoot()
        writeLegacySnapshot(root, version = 3, includeEdit = true)
        val store = EncryptedIndexStore(root, syncOutput = {})
        val snapshot = store.loadSnapshot(key)
        assertEquals(NormalizedCrop(.1f, .2f, .8f, .9f), snapshot.imageEdits.getValue("item-1").crop)
        assertEquals(NormalizedCrop(0f, 0f, 1f, 1f), snapshot.imageEdits.getValue("item-1").previousCrop)
        store.saveSnapshot(snapshot, key)
        val rewritten = store.loadSnapshot(key)
        assertEquals(snapshot.items.map { it.id }, rewritten.items.map { it.id })
        assertEquals(snapshot.collections, rewritten.collections)
        assertEquals(snapshot.memberships, rewritten.memberships)
        assertEquals(snapshot.imageEdits, rewritten.imageEdits)
        assertEquals(5, currentVersion(root))
        assertEquals(MediaOrigin.IMPORTED, rewritten.items.single().origin)
        assertTrue(!rewritten.items.single().vaultOnly)
    }

    @Test fun `v4 fixture preserves favourite while adding unrestricted provenance`() {
        val root = fixtureRoot()
        try {
            writeLegacySnapshot(root, 4, true)
            val store = EncryptedIndexStore(root)
            val snapshot = store.loadSnapshot(key)
            assertEquals("collection-1", snapshot.favouriteCollectionId)
            store.saveSnapshot(snapshot, key)
            val loaded = store.loadSnapshot(key)
            assertEquals("collection-1", loaded.favouriteCollectionId)
            assertTrue(!loaded.items.single().vaultOnly)
            assertEquals(MediaOrigin.IMPORTED, loaded.items.single().origin)
            assertEquals(5, currentVersion(root))
        } finally { root.deleteRecursively() }
    }

    private fun fixtureRoot(): File = Files.createTempDirectory("index-legacy-fixture").toFile()

    private fun writeLegacySnapshot(root: File, version: Int, includeEdit: Boolean) {
        val plain = ByteArrayOutputStream().use { bytes ->
            DataOutputStream(bytes).use { out ->
                out.writeInt(-0x5047_0002)
                out.writeInt(version)
                out.writeInt(1)
                writeItem(out)
                out.writeInt(1)
                out.writeUTF("collection-1"); out.writeUTF("Trips"); out.writeLong(42); out.writeInt(-1); out.writeBoolean(false)
                out.writeInt(1)
                out.writeUTF("collection-1"); out.writeUTF("item-1"); out.writeLong(43)
                if (includeEdit) {
                    out.writeInt(1); out.writeUTF("item-1")
                    writeCrop(out, NormalizedCrop(.1f, .2f, .8f, .9f))
                    out.writeBoolean(true); writeCrop(out, NormalizedCrop(0f, 0f, 1f, 1f))
                }
                if (version >= 4) { out.writeBoolean(true); out.writeUTF("collection-1") }
            }
            bytes.toByteArray()
        }
        val nonce = ByteArray(EncryptionHeader.NONCE_BYTES) { 9 }
        FileOutputStream(File(root, "vault-index.enc")).use { output ->
            output.write(nonce)
            VaultCipher.encrypt(ByteArrayInputStream(plain), output, key, "private-gallery:index:v1".encodeToByteArray(), nonce)
        }
        plain.fill(0)
    }

    private fun writeItem(out: DataOutputStream) {
        out.writeUTF("item-1"); out.writeUTF("image/jpeg"); out.writeUTF("fixture.jpg")
        out.writeLong(1); out.writeLong(3); out.writeInt(32); out.write(ByteArray(32) { 1 })
        out.writeInt(EncryptionHeader.NONCE_BYTES); out.write(ByteArray(EncryptionHeader.NONCE_BYTES) { 2 })
        out.writeInt(VaultItemState.COMPLETE.ordinal); out.writeBoolean(false)
    }
    private fun writeCrop(out: DataOutputStream, crop: NormalizedCrop) {
        out.writeFloat(crop.left); out.writeFloat(crop.top); out.writeFloat(crop.right); out.writeFloat(crop.bottom)
    }
    private fun currentVersion(root: File): Int {
        val encrypted = File(root, "vault-index.enc").readBytes()
        val output = ByteArrayOutputStream()
        VaultCipher.decrypt(ByteArrayInputStream(encrypted.copyOfRange(EncryptionHeader.NONCE_BYTES, encrypted.size)), output, key, "private-gallery:index:v1".encodeToByteArray(), EncryptionHeader(encrypted.copyOf(EncryptionHeader.NONCE_BYTES)))
        return java.io.DataInputStream(ByteArrayInputStream(output.toByteArray())).use { input ->
            assertEquals(-0x5047_0002, input.readInt()); input.readInt()
        }
    }
}
