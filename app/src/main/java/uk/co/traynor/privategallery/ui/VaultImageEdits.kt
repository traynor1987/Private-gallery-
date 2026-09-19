package uk.co.traynor.privategallery.ui

import android.graphics.Bitmap
import android.graphics.Matrix
import android.media.ExifInterface
import java.io.ByteArrayInputStream
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import uk.co.traynor.privategallery.core.vault.NormalizedCrop

/** In-memory-only image presentation helpers. No result is written to disk. */
object VaultImageEdits {
    fun visuallyOrient(bytes: ByteArray, bitmap: Bitmap): Bitmap {
        val orientation = runCatching {
            ExifInterface(ByteArrayInputStream(bytes)).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        val matrix = Matrix().apply {
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> postRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> preScale(-1f, 1f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> preScale(1f, -1f)
                else -> Unit
            }
        }
        return if (matrix.isIdentity) bitmap else Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    fun crop(bitmap: Bitmap, crop: NormalizedCrop?): Bitmap {
        if (crop == null || crop.isOriginal) return bitmap
        val left = (bitmap.width * crop.left).toInt().coerceIn(0, bitmap.width - 1)
        val top = (bitmap.height * crop.top).toInt().coerceIn(0, bitmap.height - 1)
        val right = (bitmap.width * crop.right).toInt().coerceIn(left + 1, bitmap.width)
        val bottom = (bitmap.height * crop.bottom).toInt().coerceIn(top + 1, bitmap.height)
        return Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
    }
}

/** Conservative border detector: it crops only low-variance near black/white edge bands. */
object VaultAutoCrop {
    fun detect(source: Bitmap): NormalizedCrop? {
        val scale = min(1f, 320f / max(source.width, source.height).toFloat())
        val bitmap = if (scale < 1f) Bitmap.createScaledBitmap(source, (source.width * scale).toInt(), (source.height * scale).toInt(), true) else source
        val centre = meanLuma(bitmap, bitmap.width / 4, bitmap.height / 4, bitmap.width * 3 / 4, bitmap.height * 3 / 4)
        val top = scan(bitmap, Edge.TOP, centre)
        val bottom = scan(bitmap, Edge.BOTTOM, centre)
        val left = scan(bitmap, Edge.LEFT, centre)
        val right = scan(bitmap, Edge.RIGHT, centre)
        if (top + bottom == 0 && left + right == 0) return null
        val crop = NormalizedCrop(
            left.toFloat() / bitmap.width,
            top.toFloat() / bitmap.height,
            1f - right.toFloat() / bitmap.width,
            1f - bottom.toFloat() / bitmap.height,
        )
        return crop.takeUnless { it.isOriginal }
    }

    private enum class Edge { TOP, BOTTOM, LEFT, RIGHT }

    private fun scan(bitmap: Bitmap, edge: Edge, centreLuma: Int): Int {
        val axis = if (edge == Edge.TOP || edge == Edge.BOTTOM) bitmap.height else bitmap.width
        val cross = if (edge == Edge.TOP || edge == Edge.BOTTOM) bitmap.width else bitmap.height
        val limit = (axis * .22f).toInt().coerceAtLeast(1)
        var count = 0
        for (step in 0 until limit) {
            val pos = when (edge) {
                Edge.TOP, Edge.LEFT -> step
                Edge.BOTTOM -> bitmap.height - 1 - step
                Edge.RIGHT -> bitmap.width - 1 - step
            }
            var minLuma = 255
            var maxLuma = 0
            var sum = 0L
            var samples = 0
            for (i in 0 until cross step max(1, cross / 96)) {
                val pixel = if (edge == Edge.TOP || edge == Edge.BOTTOM) bitmap.getPixel(i, pos) else bitmap.getPixel(pos, i)
                val luma = (android.graphics.Color.red(pixel) * 299 + android.graphics.Color.green(pixel) * 587 + android.graphics.Color.blue(pixel) * 114) / 1000
                minLuma = min(minLuma, luma); maxLuma = max(maxLuma, luma); sum += luma; samples++
            }
            val mean = (sum / samples).toInt()
            val artificial = mean <= 24 || mean >= 231
            if (!artificial || maxLuma - minLuma > 18 || abs(mean - centreLuma) < 24) break
            count++
        }
        val meaningful = max(2, (axis * .025f).toInt())
        return count.takeIf { it >= meaningful } ?: 0
    }

    private fun meanLuma(bitmap: Bitmap, left: Int, top: Int, right: Int, bottom: Int): Int {
        var sum = 0L; var count = 0
        for (y in top until bottom step max(1, (bottom - top) / 48)) for (x in left until right step max(1, (right - left) / 48)) {
            val p = bitmap.getPixel(x, y)
            sum += (android.graphics.Color.red(p) * 299 + android.graphics.Color.green(p) * 587 + android.graphics.Color.blue(p) * 114) / 1000
            count++
        }
        return (sum / count.coerceAtLeast(1)).toInt()
    }
}
