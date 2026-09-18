package uk.co.traynor.privategallery.core.ui

/** Keeps authenticated Vault grids thumbnail-first without leaking names or redundant encryption labels. */
object ProtectedMediaTilePolicy {
    fun showVideoIndicator(mimeType: String): Boolean = mimeType.startsWith("video/")
    const val showEncryptionFooter: Boolean = false
}
