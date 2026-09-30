package uk.co.traynor.privategallery.ui

import android.graphics.Bitmap
import java.lang.ref.WeakReference
import uk.co.traynor.privategallery.core.security.PrimaryOperation

/** Weak registration wipes retained UI pixels without pinning every evicted bitmap in memory. */
internal fun PrimaryOperation.ownProtectedBitmap(bitmap: Bitmap): Bitmap {
    val reference = WeakReference(bitmap)
    ownForSession(AutoCloseable {
        reference.get()?.let { if (!it.isRecycled && it.isMutable) it.eraseColor(android.graphics.Color.TRANSPARENT) }
    })
    return bitmap
}
