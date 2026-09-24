package uk.co.traynor.privategallery.core.vault

import java.io.InputStream
import java.io.IOException

/**
 * Shared acquisition boundary for device imports, Browser downloads and Browser screenshots.
 * Implementations stream directly into encrypted staging; no caller receives a public plaintext
 * filename and a VaultItem becomes COMPLETE only after authenticated verification and index commit.
 */
data class VaultImportSource(
    val displayName: String,
    val mimeType: String,
    val openStream: () -> InputStream,
    val sourceReference: String? = null,
    val onConsumed: () -> Unit = {},
    /** Explicit editor Save copy: preserve a distinct identity even for identical pixels. */
    val createDistinctCopy: Boolean = false,
    /** Checked while streaming so lock/VPN loss cannot commit a complete item. */
    val isCancelled: () -> Boolean = { false },
)

interface VaultImportSink {
    fun importVerified(source: VaultImportSource): ImportResult
}

class VaultImportCoordinator(private val sink: VaultImportSink) {
    fun acquire(source: VaultImportSource): ImportResult {
        require(source.displayName.isNotBlank()) { "A display name is required" }
        require(source.mimeType.contains('/')) { "A MIME type is required" }
        return try {
            if (source.isCancelled()) throw IOException("Vault acquisition cancelled")
            sink.importVerified(source.copy(openStream = {
                if (source.isCancelled()) throw IOException("Vault acquisition cancelled")
                CancellationCheckingInputStream(source.openStream(), source.isCancelled)
            }))
        } finally { source.onConsumed() }
    }
}

private class CancellationCheckingInputStream(
    delegate: InputStream,
    private val isCancelled: () -> Boolean,
) : java.io.FilterInputStream(delegate) {
    private fun ensureActive() {
        if (isCancelled()) throw IOException("Vault acquisition cancelled")
    }

    override fun read(): Int { ensureActive(); return super.read() }
    override fun read(buffer: ByteArray, offset: Int, length: Int): Int { ensureActive(); return super.read(buffer, offset, length) }
}
