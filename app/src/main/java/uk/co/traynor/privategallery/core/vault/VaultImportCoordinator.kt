package uk.co.traynor.privategallery.core.vault

import java.io.InputStream

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
)

interface VaultImportSink {
    fun importVerified(source: VaultImportSource): ImportResult
}

class VaultImportCoordinator(private val sink: VaultImportSink) {
    fun acquire(source: VaultImportSource): ImportResult {
        require(source.displayName.isNotBlank()) { "A display name is required" }
        require(source.mimeType.contains('/')) { "A MIME type is required" }
        return try { sink.importVerified(source) } finally { source.onConsumed() }
    }
}
