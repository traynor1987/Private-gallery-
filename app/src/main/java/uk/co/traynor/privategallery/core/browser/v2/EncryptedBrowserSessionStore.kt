package uk.co.traynor.privategallery.core.browser.v2

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

data class BrowserSessionSnapshot(val tabs: List<BrowserTab>, val selectedTabId: String?)

/** Encrypted metadata-only session restore; no WebView state, cache, DOM or page data is stored. */
class EncryptedBrowserSessionStore(private val root: File, private val key: ByteArray) {
    private val file = File(root, "browser-session-v3.enc")
    private val legacyFile = File(root, "browser-session-v2.enc")

    /** Historical binary API; automatic presentation must use loadForPresentation instead. */
    fun load(): BrowserSessionSnapshot? = loadForPresentation() ?: read(legacyFile, LEGACY_AAD, false)

    /** Only authenticated, explicitly ordinary-owner metadata may select or navigate Browser. */
    fun loadForPresentation(): BrowserSessionSnapshot? = read(file, AAD, true)

    /** Legacy ownerless records stay quarantined until the user explicitly chooses one. */
    fun legacyRecoveryEntries(): List<BrowserTab> = read(legacyFile, LEGACY_AAD, false)?.tabs.orEmpty()

    private fun read(source: File, aad: ByteArray, trustedOrdinary: Boolean): BrowserSessionSnapshot? {
        if (!source.exists()) return null
        val plain = ByteArrayOutputStream()
        try {
            FileInputStream(source).use { input ->
                val nonce = ByteArray(EncryptionHeader.NONCE_BYTES)
                var offset = 0
                while (offset < nonce.size) {
                    val count = input.read(nonce, offset, nonce.size - offset)
                    if (count < 0) break
                    offset += count
                }
                require(offset == nonce.size) { "Corrupt Browser session" }
                VaultCipher.decrypt(input, plain, key, aad, EncryptionHeader(nonce))
            }
            val bytes = plain.toByteArray()
            try {
                return DataInputStream(ByteArrayInputStream(bytes)).use { data ->
                    if (trustedOrdinary) {
                        require(data.readInt() == 3 && data.readUTF() == ORDINARY_OWNER) { "Untrusted Browser ownership" }
                    }
                    val selected = data.readUTF().ifBlank { null }
                    val count = data.readInt().also { require(it in 0..MAX_TABS) }
                    BrowserSessionSnapshot(List(count) {
                        BrowserTab(
                            id = data.readUTF(), url = data.readUTF(), title = data.readUTF(),
                            desktopSite = data.readBoolean(),
                        )
                    }, selected)
                }
            } finally { bytes.fill(0) }
        } finally { plain.reset() }
    }

    fun save(snapshot: BrowserSessionSnapshot) {
        // Session changes are emitted on the main thread but encrypted writes run on IO.
        // Serialise the complete staged write: a shared staging name otherwise lets one save
        // move another save's file, which must never turn a non-critical restore write into a
        // process-fatal exception.
        synchronized(SAVE_LOCK) {
            val tabs = snapshot.tabs.take(MAX_TABS)
            val plain = ByteArrayOutputStream().use { output ->
                DataOutputStream(output).use { data ->
                    data.writeInt(3); data.writeUTF(ORDINARY_OWNER)
                    data.writeUTF(snapshot.selectedTabId.orEmpty()); data.writeInt(tabs.size)
                    tabs.forEach { tab -> data.writeUTF(tab.id); data.writeUTF(tab.url); data.writeUTF(tab.title); data.writeBoolean(tab.desktopSite) }
                }; output.toByteArray()
            }
            // New saves never replace or delete the ownerless historical ciphertext.
            val pending = File(root, "browser-session-v3.new")
            root.mkdirs(); val nonce = SecureRandom().generateSeed(EncryptionHeader.NONCE_BYTES)
            try {
                FileOutputStream(pending).use { out -> out.write(nonce); VaultCipher.encrypt(ByteArrayInputStream(plain), out, key, AAD, nonce); out.fd.sync() }
                check(pending.renameTo(file)) { "Unable to save Browser session" }
            } finally { plain.fill(0); pending.delete() }
        }
    }

    private companion object {
        const val MAX_TABS = 8
        const val ORDINARY_OWNER = "ordinary-browser"
        val AAD = "private-gallery:browser-session:v3:ordinary-browser".encodeToByteArray()
        val LEGACY_AAD = "private-gallery:browser-session:v2".encodeToByteArray()
        val SAVE_LOCK = Any()
    }
}
