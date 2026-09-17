package uk.co.traynor.privategallery.core.ui

/** Keeps media tiles legible across narrow phones and Fold inner displays. */
object VaultGridPolicy {
    fun columnsFor(widthDp: Int): Int = when {
        widthDp < 360 -> 2
        widthDp < 600 -> 3
        widthDp < 760 -> 4
        else -> 6
    }
}
