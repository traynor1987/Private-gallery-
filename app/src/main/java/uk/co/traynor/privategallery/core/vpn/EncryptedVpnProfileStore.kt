package uk.co.traynor.privategallery.core.vpn

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.SecureRandom
import uk.co.traynor.privategallery.core.crypto.EncryptionHeader
import uk.co.traynor.privategallery.core.crypto.VaultCipher

data class VpnProfileSnapshot(val profiles: List<VpnProfile> = emptyList(), val activeProfileId: String? = null)

/** Encrypted app-private profile/configuration ledger. UI receives profile metadata only. */
class EncryptedVpnProfileStore(private val root: File) {
    private val file = File(root, "vpn-profiles.enc")
    fun load(key: ByteArray): VpnProfileSnapshot {
        if (!file.exists()) return VpnProfileSnapshot()
        val plain = ByteArrayOutputStream()
        FileInputStream(file).use { input ->
            val nonce = input.readNBytes(EncryptionHeader.NONCE_BYTES)
            require(nonce.size == EncryptionHeader.NONCE_BYTES) { "Corrupt VPN profile store" }
            VaultCipher.decrypt(input, plain, key, "private-gallery:vpn-profiles:v1".encodeToByteArray(), EncryptionHeader(nonce))
        }
        return DataInputStream(ByteArrayInputStream(plain.toByteArray())).use { input ->
            val count = input.readInt().also { require(it in 0..100) }
            val profiles = List(count) { VpnProfile(input.readUTF(), input.readUTF(), VpnProtocol.entries[input.readInt()], input.readUTF()) }
            VpnProfileSnapshot(profiles, if (input.readBoolean()) input.readUTF() else null).also { require(it.activeProfileId == null || it.activeProfileId in profiles.map { p -> p.id }) }
        }
    }
    fun save(snapshot: VpnProfileSnapshot, key: ByteArray) {
        require(snapshot.activeProfileId == null || snapshot.activeProfileId in snapshot.profiles.map { it.id })
        val plain = ByteArrayOutputStream().use { buffer -> DataOutputStream(buffer).use { out ->
            out.writeInt(snapshot.profiles.size); snapshot.profiles.forEach { p -> out.writeUTF(p.id); out.writeUTF(p.displayName); out.writeInt(p.protocol.ordinal); out.writeUTF(p.privateConfiguration) }
            out.writeBoolean(snapshot.activeProfileId != null); snapshot.activeProfileId?.let(out::writeUTF)
        }; buffer.toByteArray() }
        root.mkdirs(); val temp = File(root, "vpn-profiles.new"); val nonce = SecureRandom().generateSeed(EncryptionHeader.NONCE_BYTES)
        try { FileOutputStream(temp).use { out -> out.write(nonce); VaultCipher.encrypt(ByteArrayInputStream(plain), out, key, "private-gallery:vpn-profiles:v1".encodeToByteArray(), nonce); out.fd.sync() }; check(temp.renameTo(file)) { "Unable to commit VPN profile store" } }
        finally { plain.fill(0); temp.delete() }
    }
}
