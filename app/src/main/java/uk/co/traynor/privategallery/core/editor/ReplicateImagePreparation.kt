package uk.co.traynor.privategallery.core.editor

import android.graphics.*
import java.io.ByteArrayOutputStream

/** Metadata-free, bounded inline input. Replicate recommends data URIs below 1 MB.
 * JPEG flattens alpha onto white; only this remote representation is resized/re-encoded.
 */
object ReplicateImagePreparation {
    fun prepare(source: ByteArray): ByteArray {
        var bitmap = PhotoRenderer.render(source, PhotoEdit(), preview = true)
        try {
            val opaque = Bitmap.createBitmap(bitmap.width,bitmap.height,Bitmap.Config.ARGB_8888)
            try { Canvas(opaque).apply { drawColor(Color.WHITE); drawBitmap(bitmap,0f,0f,null) } }
            catch (failure: Throwable) { opaque.recycle(); throw failure }
            bitmap.recycle(); bitmap = opaque
            repeat(5) {
                val encoded = WipingOutput().use { output ->
                    check(bitmap.compress(Bitmap.CompressFormat.JPEG, if (it == 0) 92 else 85, output))
                    output.toByteArray()
                }
                if (encoded.size <= ReplicateSeedreamApi.MAX_INLINE_BYTES) return encoded
                encoded.fill(0)
                val smaller = Bitmap.createScaledBitmap(bitmap, (bitmap.width * .75f).toInt().coerceAtLeast(1), (bitmap.height * .75f).toInt().coerceAtLeast(1), true)
                if (smaller !== bitmap) bitmap.recycle()
                bitmap = smaller
            }
            throw AiEditFailure("Could not prepare this image for Replicate.")
        } finally { bitmap.recycle() }
    }
    private class WipingOutput : ByteArrayOutputStream(3 * 1024 * 1024) {
        override fun write(bytes: ByteArray, offset: Int, length: Int) {
            check(count.toLong() + length <= 3 * 1024 * 1024) { "Image encoding exceeds memory budget." }
            super.write(bytes,offset,length)
        }
        override fun close() { buf.fill(0); reset(); super.close() }
    }
}
