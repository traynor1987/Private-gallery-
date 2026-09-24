package uk.co.traynor.privategallery.core.editor

import uk.co.traynor.privategallery.core.vault.NormalizedCrop
import uk.co.traynor.privategallery.core.ui.CropEditorGeometry

/** Crop is always in visually oriented ORIGINAL coordinates; transforms follow crop. */
data class PhotoEdit(
    val crop: NormalizedCrop = NormalizedCrop.ORIGINAL,
    val quarterTurns: Int = 0,
    val flipHorizontal: Boolean = false,
    val brightness: Float = 0f,
    val contrast: Float = 1f,
    val saturation: Float = 1f,
) {
    init {
        require(quarterTurns in 0..3)
        require(brightness.isFinite() && brightness in -1f..1f)
        require(contrast.isFinite() && contrast in 0f..2f)
        require(saturation.isFinite() && saturation in 0f..2f)
    }
    fun rotate() = copy(quarterTurns = (quarterTurns + 1) % 4)
}

data class EditHistory(val current: PhotoEdit = PhotoEdit(), val past: List<PhotoEdit> = emptyList(), val future: List<PhotoEdit> = emptyList()) {
    val canUndo get() = past.isNotEmpty()
    val canRedo get() = future.isNotEmpty()
    fun change(edit: PhotoEdit) = if (edit == current) this else EditHistory(edit, (past + current).takeLast(40))
    fun undo() = if (!canUndo) this else EditHistory(past.last(), past.dropLast(1), (listOf(current) + future).take(40))
    fun redo() = if (!canRedo) this else EditHistory(future.first(), (past + current).takeLast(40), future.drop(1))
    fun reset() = change(PhotoEdit())
}

data class MaskPoint(val x: Float, val y: Float) {
    init { require(x.isFinite() && y.isFinite() && x in 0f..1f && y in 0f..1f) }
}
data class MaskStroke(val points: List<MaskPoint>, val width: Float) {
    init { require(points.isNotEmpty() && points.size <= 4096 && width.isFinite() && width in .001f.. .3f) }
}
object MaskGeometry {
    fun point(x: Float, y: Float, width: Float, height: Float, aspect: Float): MaskPoint? {
        if (width <= 0 || height <= 0 || aspect <= 0) return null
        val rect = CropEditorGeometry.fitImage(width, height, aspect)
        val (px, py) = CropEditorGeometry.normalizePoint(rect, x, y)
        return if (px in 0f..1f && py in 0f..1f) MaskPoint(px, py) else null
    }
}
