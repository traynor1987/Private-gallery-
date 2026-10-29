package uk.co.traynor.privategallery.core.vault

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import java.io.ByteArrayOutputStream

/** An explicit document tile, not a media decode failure. Contains no document content. */
object VaultDocumentPreview {
    fun tile(mime: String): Bitmap = Bitmap.createBitmap(320,240,Bitmap.Config.ARGB_8888).also {bitmap ->
        val canvas=Canvas(bitmap)
        canvas.drawColor(Color.rgb(36,45,59))
        val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply {color=Color.rgb(225,233,243)}
        canvas.drawRoundRect(116f,30f,204f,148f,8f,8f,paint)
        paint.color=Color.rgb(74,94,121);paint.strokeWidth=5f
        for(y in listOf(62f,82f,102f,122f))canvas.drawLine(133f,y,187f,y,paint)
        paint.color=Color.WHITE;paint.textSize=22f;paint.textAlign=Paint.Align.CENTER
        canvas.drawText(VaultDocumentPolicy.label(mime),160f,193f,paint)
    }
    fun encodedTile(mime: String): ByteArray {val bitmap=tile(mime);return try {ByteArrayOutputStream().use {check(bitmap.compress(Bitmap.CompressFormat.PNG,100,it));it.toByteArray()}}finally {bitmap.recycle()}}
}
