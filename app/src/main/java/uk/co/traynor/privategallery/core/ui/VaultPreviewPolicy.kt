package uk.co.traynor.privategallery.core.ui

/** Prevents the grid from eagerly decrypting large video payloads solely for a thumbnail. */
object VaultPreviewPolicy {
    private const val MAX_VIDEO_PREVIEW_BYTES = 24L * 1024 * 1024

    fun shouldGenerate(mimeType: String, plaintextSize: Long): Boolean =
        mimeType.startsWith("image/") ||
            (mimeType.startsWith("video/") && plaintextSize in 1..MAX_VIDEO_PREVIEW_BYTES)
}
