package uk.co.traynor.privategallery.ui

import android.graphics.Bitmap
import android.graphics.Color
import android.media.ExifInterface
import androidx.test.platform.app.InstrumentationRegistry
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import uk.co.traynor.privategallery.core.editor.*
import uk.co.traynor.privategallery.core.vault.*

class PhotoEditorSecurityTest {
    private fun image(): ByteArray {
        val bitmap = Bitmap.createBitmap(80, 40, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.RED)
        for (x in 40 until 80) for (y in 0 until 40) bitmap.setPixel(x, y, Color.BLUE)
        return ByteArrayOutputStream().use { stream -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream); bitmap.recycle(); stream.toByteArray() }
    }
    @Test fun renderAppliesCropRotationFlipAndAdjustments() {
        val source = image()
        val rotated = PhotoRenderer.render(source, PhotoEdit(quarterTurns = 1), false)
        assertEquals(40, rotated.width); assertEquals(80, rotated.height); rotated.recycle()
        val flipped = PhotoRenderer.render(source, PhotoEdit(flipHorizontal = true), false)
        assertEquals(Color.BLUE, flipped.getPixel(0, 0)); flipped.recycle()
        val crop = PhotoRenderer.render(source, PhotoEdit(crop = NormalizedCrop(0f, 0f, .5f, 1f), saturation = 0f), false)
        assertEquals(40, crop.width)
        val pixel = crop.getPixel(0, 0); assertEquals(Color.red(pixel), Color.blue(pixel)); crop.recycle(); source.fill(0)
    }
    @Test fun sanitizerRemovesGpsAndRejectsInvalidImages() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "metadata-test.jpg")
        try {
            val bitmap = Bitmap.createBitmap(40, 40, Bitmap.Config.ARGB_8888)
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }; bitmap.recycle()
            ExifInterface(file).apply { setAttribute(ExifInterface.TAG_GPS_LATITUDE, "53/1,0/1,0/1"); setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, "N"); setAttribute(ExifInterface.TAG_MAKE, "PRIVATE DEVICE"); saveAttributes() }
            val source = file.readBytes()
            assertNotNull(ExifInterface(ByteArrayInputStream(source)).getAttribute(ExifInterface.TAG_GPS_LATITUDE))
            val clean = PhotoRenderer.sanitize(source)
            val exif = ExifInterface(ByteArrayInputStream(clean))
            assertNull(exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE)); assertNull(exif.getAttribute(ExifInterface.TAG_MAKE))
            assertThrows(IllegalArgumentException::class.java) { PhotoRenderer.sanitize(byteArrayOf(1, 2, 3)) }
            source.fill(0); clean.fill(0)
        } finally { file.delete() }
    }
    @Test fun copyImportIsDistinctEncryptedAndOriginalUntouched() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        // Isolated private directory; never touch an owner's Vault.
        val isolated = object : android.content.ContextWrapper(context) {
            override fun getFilesDir(): File = File(context.cacheDir, "editor-vault-test").apply { mkdirs() }
        }
        val key = ByteArray(32) { (it + 1).toByte() }
        val bytes = image()
        try {
            val repository = AndroidVaultRepository(isolated, key)
            val first = (VaultImportCoordinator(repository).acquire(VaultImportSource("original.png", "image/png", { ByteArrayInputStream(bytes) })) as ImportResult.Imported).item
            val before = repository.readForViewing(first)
            val copy = (VaultImportCoordinator(repository).acquire(VaultImportSource("edited.png", "image/png", { ByteArrayInputStream(bytes) }, createDistinctCopy = true, sourceReference = "editedFrom:${first.id}")) as ImportResult.Imported).item
            assertNotEquals(first.id, copy.id)
            assertArrayEquals(before, repository.readForViewing(first))
            assertArrayEquals(bytes, repository.readForViewing(copy))
            assertEquals(2, repository.items().size)
            assertTrue(isolated.filesDir.walkTopDown().filter { it.isFile }.none { it.extension == "png" || it.extension == "jpg" })
            var cancelled = false
            assertThrows(java.io.IOException::class.java) {
                VaultImportCoordinator(repository).acquire(VaultImportSource("cancelled.png", "image/png", { cancelled = true; ByteArrayInputStream(bytes) }, createDistinctCopy = true, isCancelled = { cancelled }))
            }
            assertEquals(2, repository.items().size)
            before.fill(0)
        } finally { bytes.fill(0); key.fill(0); isolated.filesDir.deleteRecursively() }
    }
}
