package uk.co.traynor.privategallery.ui

import android.graphics.Bitmap
import android.graphics.Matrix
import android.media.ExifInterface
import java.io.ByteArrayInputStream
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import uk.co.traynor.privategallery.core.vault.NormalizedCrop
import uk.co.traynor.privategallery.core.ui.VaultAutoCropPolicy
import uk.co.traynor.privategallery.core.ui.DominantContentCropPolicy

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
        if (top + bottom != 0 || left + right != 0) {
            val crop = NormalizedCrop(
                left.toFloat() / bitmap.width,
                top.toFloat() / bitmap.height,
                1f - right.toFloat() / bitmap.width,
                1f - bottom.toFloat() / bitmap.height,
            )
            if (!crop.isOriginal) return crop
        }
        return detectDominantContentRectangle(bitmap)
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
            if (!VaultAutoCropPolicy.isConfidentEdgeBand(mean, minLuma, maxLuma, centreLuma)) break
            count++
        }
        val meaningful = VaultAutoCropPolicy.minimumMeaningfulBand(axis)
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

    /**
     * Stage B for screenshot-like media: finds a large, textured rectangle bounded by
     * sustained luminance transitions and calmer surrounding chrome. It deliberately
     * requires at least two structural boundaries; darkness by itself never qualifies.
     */
    private fun detectDominantContentRectangle(bitmap: Bitmap): NormalizedCrop? {
        if (bitmap.width < 32 || bitmap.height < 32) return null
        val luma = Array(bitmap.height) { y -> IntArray(bitmap.width) { x -> luma(bitmap.getPixel(x, y)) } }
        val rows = FloatArray(bitmap.height - 1) { y -> averageDifference(luma[y], luma[y + 1]) }
        val columns = FloatArray(bitmap.width - 1) { x ->
            var sum = 0f
            for (y in 0 until bitmap.height) sum += abs(luma[y][x] - luma[y][x + 1])
            sum / bitmap.height
        }
        val rowThreshold = transitionThreshold(rows)
        val columnThreshold = transitionThreshold(columns)
        val top = candidates(rows, 0, (bitmap.height * .46f).toInt(), rowThreshold, 0)
        val bottom = candidates(rows, (bitmap.height * .54f).toInt(), rows.size, rowThreshold, bitmap.height)
        val left = candidates(columns, 0, (bitmap.width * .46f).toInt(), columnThreshold, 0)
        val right = candidates(columns, (bitmap.width * .54f).toInt(), columns.size, columnThreshold, bitmap.width)
        var best: Pair<NormalizedCrop, Float>? = null
        for (t in top) for (b in bottom) for (l in left) for (r in right) {
            if (b.position - t.position < bitmap.height * .25f || r.position - l.position < bitmap.width * .25f) continue
            val area = ((r.position - l.position).toFloat() * (b.position - t.position) / (bitmap.width * bitmap.height)).coerceIn(0f, 1f)
            val boundaries = listOf(t, b, l, r).filter { it.strength > 0f }
            val averageStrength = boundaries.map { it.strength }.average().toFloat() / 128f
            val variance = regionVariance(luma, l.position, t.position, r.position, b.position, inside = true)
            val outsideVariance = regionVariance(luma, l.position, t.position, r.position, b.position, inside = false)
            val confidence = DominantContentCropPolicy.confidence(area, boundaries.size, averageStrength, variance, outsideVariance) ?: continue
            val crop = NormalizedCrop(
                l.position.toFloat() / bitmap.width,
                t.position.toFloat() / bitmap.height,
                r.position.toFloat() / bitmap.width,
                b.position.toFloat() / bitmap.height,
            )
            if (best == null || confidence > best!!.second) best = crop to confidence
        }
        return best?.first
    }

    private data class Boundary(val position: Int, val strength: Float)

    private fun candidates(values: FloatArray, start: Int, end: Int, threshold: Float, outer: Int): List<Boundary> {
        val found = (start until end).filter { values[it] >= threshold }
            .sortedByDescending { values[it] }
            .take(6)
            .map { Boundary(it + 1, values[it]) }
        return (listOf(Boundary(outer, 0f)) + found).distinctBy { it.position }
    }

    private fun transitionThreshold(values: FloatArray): Float {
        val mean = values.average().toFloat()
        val deviation = kotlin.math.sqrt(values.map { (it - mean) * (it - mean) }.average()).toFloat()
        return max(24f, mean + deviation * 1.25f)
    }

    private fun averageDifference(first: IntArray, second: IntArray): Float {
        var sum = 0f
        for (x in first.indices) sum += abs(first[x] - second[x])
        return sum / first.size
    }

    private fun regionVariance(luma: Array<IntArray>, left: Int, top: Int, right: Int, bottom: Int, inside: Boolean): Float {
        var sum = 0f; var sumSquares = 0f; var count = 0
        val stride = max(1, max(luma.size, luma[0].size) / 72)
        for (y in luma.indices step stride) for (x in luma[y].indices step stride) {
            val included = x in left until right && y in top until bottom
            if (included != inside) continue
            val value = luma[y][x].toFloat(); sum += value; sumSquares += value * value; count++
        }
        if (count == 0) return 0f
        val mean = sum / count
        return (sumSquares / count - mean * mean).coerceAtLeast(0f)
    }

    private fun luma(pixel: Int): Int =
        (android.graphics.Color.red(pixel) * 299 + android.graphics.Color.green(pixel) * 587 + android.graphics.Color.blue(pixel) * 114) / 1000
}
