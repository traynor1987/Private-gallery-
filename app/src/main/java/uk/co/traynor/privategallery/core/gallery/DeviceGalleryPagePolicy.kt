package uk.co.traynor.privategallery.core.gallery

/** Bounded policy for MediaStore paging on large personal libraries. */
object DeviceGalleryPagePolicy {
    const val PAGE_SIZE = 120
    const val MAX_RESIDENT_ITEMS = 480

    fun offsetForPage(page: Int): Int = page.coerceAtLeast(0) * PAGE_SIZE
    fun hasNextPage(returnedCount: Int): Boolean = returnedCount >= PAGE_SIZE
}
