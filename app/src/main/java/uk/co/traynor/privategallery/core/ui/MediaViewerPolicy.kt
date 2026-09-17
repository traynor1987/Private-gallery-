package uk.co.traynor.privategallery.core.ui

enum class MediaViewerSource { GALLERY, VAULT }

/** Pure viewer navigation policy; source lists are deliberately never combined. */
object MediaViewerPolicy {
    fun initialPage(requestedIndex: Int, itemCount: Int): Int =
        requestedIndex.coerceIn(0, (itemCount - 1).coerceAtLeast(0))

    fun toggleControls(currentlyVisible: Boolean): Boolean = !currentlyVisible

    fun canPageAcross(from: MediaViewerSource, to: MediaViewerSource): Boolean = from == to
}
