package uk.co.traynor.privategallery.core.vault

/**
 * A crop in the visually-oriented source image coordinate space. Values are
 * deliberately normalized so the same edit works on every display size.
 */
data class NormalizedCrop(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    init {
        require(left.isFinite() && top.isFinite() && right.isFinite() && bottom.isFinite()) { "Crop bounds must be finite" }
        require(left >= 0f && top >= 0f && right <= 1f && bottom <= 1f) { "Crop bounds must be normalized" }
        require(right - left >= MINIMUM_SIDE && bottom - top >= MINIMUM_SIDE) { "Crop is too small" }
    }

    val isOriginal: Boolean get() = left == 0f && top == 0f && right == 1f && bottom == 1f

    companion object {
        const val MINIMUM_SIDE = 0.05f
        val ORIGINAL = NormalizedCrop(0f, 0f, 1f, 1f)
    }
}

/**
 * Protected index metadata only. The encrypted payload and its digest are
 * never rewritten by image editing. previousCrop provides a one-step undo.
 */
data class ImageEditState(
    val crop: NormalizedCrop,
    val previousCrop: NormalizedCrop? = null,
)
