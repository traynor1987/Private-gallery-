package uk.co.traynor.privategallery.core.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize

enum class MediaViewerSource { GALLERY, VAULT }

/** Pure viewer navigation policy; source lists are deliberately never combined. */
object MediaViewerPolicy {
    const val minimumPhotoScale = 1f
    const val maximumPhotoScale = 5f
    const val doubleTapPhotoScale = 2.5f
    fun initialPage(requestedIndex: Int, itemCount: Int): Int =
        requestedIndex.coerceIn(0, (itemCount - 1).coerceAtLeast(0))

    fun toggleControls(currentlyVisible: Boolean): Boolean = !currentlyVisible

    /** A fit-to-screen image must hand horizontal drags to its surrounding pager. */
    fun canSwipePager(isImageZoomed: Boolean): Boolean = !isImageZoomed

    fun canPageAcross(from: MediaViewerSource, to: MediaViewerSource): Boolean = from == to

    fun clampedScale(value: Float): Float = value.coerceIn(minimumPhotoScale, maximumPhotoScale)
    fun doubleTapScale(current: Float): Float = if (current > 1.01f) minimumPhotoScale else doubleTapPhotoScale

    /** Translation bounds for a fit-to-view image enlarged around the viewer centre. */
    fun boundedPan(candidate: Offset, scale: Float, viewport: IntSize): Offset {
        if (scale <= 1.01f || viewport.width <= 0 || viewport.height <= 0) return Offset.Zero
        val horizontal = viewport.width * (scale - 1f) / 2f
        val vertical = viewport.height * (scale - 1f) / 2f
        return Offset(candidate.x.coerceIn(-horizontal, horizontal), candidate.y.coerceIn(-vertical, vertical))
    }
}
