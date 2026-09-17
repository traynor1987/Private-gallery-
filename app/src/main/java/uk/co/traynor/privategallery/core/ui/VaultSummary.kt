package uk.co.traynor.privategallery.core.ui

import uk.co.traynor.privategallery.core.vault.VaultItem

data class VaultSummary(
    val photos: Int,
    val videos: Int,
    val bytes: Long,
) {
    companion object {
        fun from(items: List<VaultItem>): VaultSummary = VaultSummary(
            photos = items.count { it.mimeType.startsWith("image/") },
            videos = items.count { it.mimeType.startsWith("video/") },
            bytes = items.sumOf { it.plaintextSize },
        )
    }
}
