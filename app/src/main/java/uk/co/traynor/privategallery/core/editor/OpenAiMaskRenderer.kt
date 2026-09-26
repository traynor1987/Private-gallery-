package uk.co.traynor.privategallery.core.editor

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import java.io.ByteArrayOutputStream

/** UI strokes are normalized against the displayed edited image, which has the same aspect and
 * orientation as the encoded source sent to the provider. No screen pixels leave this boundary.
 */
object OpenAiMaskRenderer {
    /** Replicate Fill requires opaque white edit regions and opaque black preserved regions. */
    fun renderReplicateFill(sourceImage: ByteArray, strokes: List<MaskStroke>): ByteArray {
        require(strokes.isNotEmpty())
        val dimensions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(sourceImage, 0, sourceImage.size, dimensions)
        require(dimensions.outWidth in 1..40000 && dimensions.outHeight in 1..40000 &&
            dimensions.outWidth.toLong() * dimensions.outHeight <= 16_000_000L)
        val mask = Bitmap.createBitmap(dimensions.outWidth, dimensions.outHeight, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(mask)
            canvas.drawColor(android.graphics.Color.BLACK)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.WHITE
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }
            for (stroke in strokes) {
                paint.strokeWidth = stroke.width * mask.width
                val path = android.graphics.Path()
                stroke.points.forEachIndexed { index, point ->
                    val x = point.x * mask.width; val y = point.y * mask.height
                    if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                if (stroke.points.size == 1) canvas.drawPoint(stroke.points[0].x * mask.width, stroke.points[0].y * mask.height, paint)
                else canvas.drawPath(path, paint)
            }
            val output = ByteArrayOutputStream()
            check(mask.compress(Bitmap.CompressFormat.PNG, 100, output))
            return output.toByteArray().also { require(it.size <= 4 * 1024 * 1024) }
        } finally { mask.recycle() }
    }
    fun render(sourcePng: ByteArray, strokes: List<MaskStroke>): ByteArray {
        require(strokes.isNotEmpty())
        val dimensions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(sourcePng, 0, sourcePng.size, dimensions)
        require(dimensions.outWidth in 1..40000 && dimensions.outHeight in 1..40000 &&
            dimensions.outWidth.toLong() * dimensions.outHeight <= 16_000_000L)
        val mask = Bitmap.createBitmap(dimensions.outWidth, dimensions.outHeight, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(mask)
            canvas.drawColor(android.graphics.Color.WHITE)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
                xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
            }
            for (stroke in strokes) {
                paint.strokeWidth = stroke.width * mask.width
                val path = android.graphics.Path()
                stroke.points.forEachIndexed { index, point ->
                    val x = point.x * mask.width; val y = point.y * mask.height
                    if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                if (stroke.points.size == 1) {
                    val point = stroke.points[0]
                    canvas.drawPoint(point.x * mask.width, point.y * mask.height, paint)
                } else canvas.drawPath(path, paint)
            }
            val output = ByteArrayOutputStream()
            check(mask.compress(Bitmap.CompressFormat.PNG, 100, output))
            return output.toByteArray().also { require(it.size <= 4 * 1024 * 1024) { "Selection mask is too large" } }
        } finally { mask.recycle() }
    }
}
