package uk.co.traynor.privategallery.ui

import android.graphics.Bitmap
import android.graphics.Color
import org.junit.Assert.*
import org.junit.Test
import uk.co.traynor.privategallery.core.security.PrimarySessionAuthority

class ProtectedMediaOwnershipTest {
    @Test fun retainedBitmapWipesOnRevocationWithoutWaitingForUiDisposal() {
        val authority = PrimarySessionAuthority()
        val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        try {
            authority.open(ByteArray(32) { 3 })
            checkNotNull(authority.operationOrNull(uk.co.traynor.privategallery.core.security.PrimaryScope.entries.toSet())).use { it.ownProtectedBitmap(bitmap) }
            assertEquals(Color.RED, bitmap.getPixel(0, 0))
            authority.revoke()
            assertEquals(Color.TRANSPARENT, bitmap.getPixel(0, 0))
            assertTrue(authority.cleanupComplete)
        } finally { authority.revoke(); bitmap.recycle() }
    }
    @Test fun oldEpochCannotHandOffPixelsDuringNewSession() {
        val authority = PrimarySessionAuthority()
        val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        try {
            authority.open(ByteArray(32) { 3 })
            val old = checkNotNull(authority.operationOrNull(uk.co.traynor.privategallery.core.security.PrimaryScope.entries.toSet()))
            authority.revoke(); authority.open(ByteArray(32) { 3 })
            assertThrows(IllegalStateException::class.java) { old.ownProtectedBitmap(bitmap) }
            assertEquals(Color.TRANSPARENT, bitmap.getPixel(0, 0))
        } finally { authority.revoke(); bitmap.recycle() }
    }
}
