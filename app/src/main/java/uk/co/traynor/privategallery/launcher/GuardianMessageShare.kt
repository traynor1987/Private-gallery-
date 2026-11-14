package uk.co.traynor.privategallery.launcher

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.ContentValues
import android.net.Uri
import android.os.PersistableBundle
import android.provider.MediaStore
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID

internal fun createGuardianShareFile(context: Context, text: String, image: Boolean): File {
    val directory = File(context.cacheDir, "share-copies").apply { mkdirs() }
    val cutoff = System.currentTimeMillis() - 15 * 60_000L
    directory.listFiles()?.filter { it.name.startsWith("guardian-export-") && it.lastModified() < cutoff }
        ?.forEach { it.delete() }
    val file = File(directory, "guardian-export-${UUID.randomUUID()}.${if (image) "png" else "txt"}")
    try {
        if (!image) {
            file.writeText(text, Charsets.UTF_8)
        } else {
            val width = 960
            val padding = 48
            val paint = TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 32f
            }
            val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, width - 2 * padding)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(8f, 1f)
                .build()
            val height = layout.height + padding * 2
            require(height in 1..16_384) { "This message is too tall for a single image." }
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            try {
                Canvas(bitmap).apply {
                    drawColor(Color.rgb(13, 23, 39))
                    translate(padding.toFloat(), padding.toFloat())
                    layout.draw(this)
                }
                file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            } finally {
                bitmap.recycle()
            }
        }
        return file
    } catch (error: Throwable) {
        file.delete()
        throw error
    }
}

internal fun shareGuardianFile(context: Context, file: File, image: Boolean) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val send = Intent(Intent.ACTION_SEND).setType(if (image) "image/png" else "text/plain")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        .apply { clipData = ClipData.newRawUri("Guardian export", uri) }
    context.startActivity(Intent.createChooser(send, "Share Guardian export"))
    scheduleGuardianExportCleanup(context, uri, file)
}

private fun scheduleGuardianExportCleanup(context: Context, uri: Uri, file: File) {
    val app = context.applicationContext
    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
        app.revokeUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        file.delete()
    }, 15 * 60_000L)
}

internal fun copyGuardianImage(context: Context, file: File) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val clip = ClipData.newUri(context.contentResolver, "Guardian screenshot", uri).apply {
        description.extras = PersistableBundle().apply {
            putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
        }
    }
    checkNotNull(context.getSystemService(ClipboardManager::class.java)).setPrimaryClip(clip)
    scheduleGuardianExportCleanup(context, uri, file)
}

internal fun stageGuardianGalleryImage(context: Context, file: File): Uri {
    val resolver = context.contentResolver
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "Nova Guardian-${UUID.randomUUID()}.png")
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Nova Guardian")
        put(MediaStore.Images.Media.IS_PENDING, 1)
    }
    val uri = checkNotNull(resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values))
    try {
        checkNotNull(resolver.openOutputStream(uri, "w")).use { output ->
            file.inputStream().use { it.copyTo(output) }
        }
        return uri
    } catch (error: Throwable) {
        runCatching { resolver.delete(uri, null, null) }
        throw error
    }
}

internal fun publishGuardianGalleryImage(context: Context, uri: Uri) {
    val values = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
    check(context.contentResolver.update(uri, values, null, null) == 1)
}
