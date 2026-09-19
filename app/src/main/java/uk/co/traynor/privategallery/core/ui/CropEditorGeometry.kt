package uk.co.traynor.privategallery.core.ui

import uk.co.traynor.privategallery.core.vault.NormalizedCrop

/** Pure geometry used by the inset crop workspace and its regression tests. */
data class CropEditorRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
}

object CropEditorGeometry {
    fun fitImage(workspaceWidth: Float, workspaceHeight: Float, imageAspect: Float): CropEditorRect {
        require(workspaceWidth > 0f && workspaceHeight > 0f && imageAspect > 0f)
        val workspaceAspect = workspaceWidth / workspaceHeight
        return if (imageAspect > workspaceAspect) {
            val height = workspaceWidth / imageAspect
            CropEditorRect(0f, (workspaceHeight - height) / 2f, workspaceWidth, (workspaceHeight + height) / 2f)
        } else {
            val width = workspaceHeight * imageAspect
            CropEditorRect((workspaceWidth - width) / 2f, 0f, (workspaceWidth + width) / 2f, workspaceHeight)
        }
    }

    fun cropBounds(image: CropEditorRect, crop: NormalizedCrop): CropEditorRect = CropEditorRect(
        image.left + image.width * crop.left,
        image.top + image.height * crop.top,
        image.left + image.width * crop.right,
        image.top + image.height * crop.bottom,
    )

    fun normalizePoint(image: CropEditorRect, x: Float, y: Float): Pair<Float, Float> =
        ((x - image.left) / image.width) to ((y - image.top) / image.height)
}
