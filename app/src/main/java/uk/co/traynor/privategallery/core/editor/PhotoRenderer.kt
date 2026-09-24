package uk.co.traynor.privategallery.core.editor

import android.graphics.*
import android.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/** No files, EXIF copy, paths or metadata. Decode once, render state, encode only for output. */
object PhotoRenderer {
    const val MAX_SOURCE_BYTES = 48 * 1024 * 1024
    const val MAX_FINAL_PIXELS = 16_000_000L
    const val PREVIEW_PIXELS = 1_200_000L
    fun render(bytes: ByteArray, edit: PhotoEdit, preview: Boolean): Bitmap {
        require(bytes.isNotEmpty() && bytes.size <= MAX_SOURCE_BYTES) { "Image is too large to edit safely." }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0 && bounds.outWidth <= 40000 && bounds.outHeight <= 40000) { "Unsupported image." }
        val pixels = bounds.outWidth.toLong() * bounds.outHeight
        require(pixels <= 200_000_000L) { "Image dimensions are too large." }
        val limit = if (preview) PREVIEW_PIXELS else MAX_FINAL_PIXELS
        // Final output is full resolution within the explicit safety budget, otherwise sampled.
        var sample = 1
        while (pixels / sample / sample > limit) sample *= 2
        val decoded = requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.ARGB_8888 })) { "Unable to decode image." }
        var oriented: Bitmap? = null
        var cropped: Bitmap? = null
        var transformed: Bitmap? = null
        try {
            val orientation = runCatching { ExifInterface(ByteArrayInputStream(bytes)).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1) }.getOrDefault(1)
            val orient = Matrix().apply { when (orientation) {
                2 -> setScale(-1f, 1f)
                3 -> setRotate(180f)
                4 -> setScale(1f, -1f)
                5 -> { setRotate(90f); postScale(-1f, 1f) }
                6 -> setRotate(90f)
                7 -> { setRotate(90f); postScale(1f, -1f) }
                8 -> setRotate(270f)
            } }
            oriented = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, orient, true)
            val c = edit.crop
            val left = (oriented.width * c.left).toInt().coerceIn(0, oriented.width - 1)
            val top = (oriented.height * c.top).toInt().coerceIn(0, oriented.height - 1)
            val right = (oriented.width * c.right).toInt().coerceIn(left + 1, oriented.width)
            val bottom = (oriented.height * c.bottom).toInt().coerceIn(top + 1, oriented.height)
            cropped = Bitmap.createBitmap(oriented, left, top, right-left, bottom-top)
            val matrix = Matrix().apply { setRotate(edit.quarterTurns * 90f); if (edit.flipHorizontal) postScale(-1f, 1f) }
            transformed = Bitmap.createBitmap(cropped, 0, 0, cropped.width, cropped.height, matrix, true)
            val output = Bitmap.createBitmap(transformed.width, transformed.height, Bitmap.Config.ARGB_8888)
            try {
                val saturation = ColorMatrix().apply { setSaturation(edit.saturation) }
                val contrast = edit.contrast
                val offset = 255f * edit.brightness + 128f * (1f - contrast)
                val adjustment = ColorMatrix(floatArrayOf(contrast,0f,0f,0f,offset, 0f,contrast,0f,0f,offset, 0f,0f,contrast,0f,offset, 0f,0f,0f,1f,0f))
                adjustment.postConcat(saturation)
                Canvas(output).drawBitmap(transformed, 0f, 0f, Paint(Paint.ANTI_ALIAS_FLAG).apply { colorFilter = ColorMatrixColorFilter(adjustment) })
                return output
            } catch (failure: Throwable) { output.recycle(); throw failure }
        } finally {
            listOfNotNull(decoded, oriented, cropped, transformed).distinct().forEach { if (!it.isRecycled) it.recycle() }
        }
    }
    fun encode(bitmap: Bitmap): ByteArray = WipingOutput().use { output ->
        check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) { "Unable to encode image." }
        output.toByteArray()
    }
    fun output(bytes: ByteArray, edit: PhotoEdit): ByteArray {
        val bitmap = render(bytes, edit, false)
        return try { encode(bitmap) } finally { bitmap.recycle() }
    }
    fun sanitize(bytes: ByteArray): ByteArray = output(bytes, PhotoEdit())
    private class WipingOutput : ByteArrayOutputStream() {
        override fun write(b: ByteArray, off: Int, len: Int) { check(count.toLong() + len <= MAX_SOURCE_BYTES) { "Encoded image is too large." }; super.write(b, off, len) }
        override fun close() { buf.fill(0); reset(); super.close() }
    }
}
