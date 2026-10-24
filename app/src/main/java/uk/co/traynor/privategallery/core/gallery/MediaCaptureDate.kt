package uk.co.traynor.privategallery.core.gallery

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.media.MediaMetadataRetriever
import android.media.ExifInterface
import uk.co.traynor.privategallery.core.ui.MediaOrder

/** Reads only the granted URI; no media scan, write, plaintext file or guessed EXIF timezone. */
internal object MediaCaptureDate {
 internal fun parseExifTimestamp(date:String?,offset:String?):Long? = runCatching {
  if(date==null||offset==null)return null
  val format=java.time.format.DateTimeFormatter.ofPattern("uuuu:MM:dd HH:mm:ss").withResolverStyle(java.time.format.ResolverStyle.STRICT)
  java.time.LocalDateTime.parse(date,format).toInstant(java.time.ZoneOffset.of(offset)).toEpochMilli().takeIf {MediaOrder.valid(it)>0}
 }.getOrNull()
 fun read(context:Context,uri:Uri,mime:String):Long? {
  if(uri.scheme!="content")return null
  val mediaStore=runCatching {context.contentResolver.query(uri,arrayOf(MediaStore.MediaColumns.DATE_TAKEN),null,null,null)?.use {if(it.moveToFirst())it.getLong(0) else 0}}.getOrNull()
  mediaStore?.let {if(MediaOrder.valid(it)>0)return it}
  return runCatching {
   if(mime.startsWith("image/")) context.contentResolver.openInputStream(uri)?.use {input->
    val exif=ExifInterface(input)
    parseExifTimestamp(exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL),exif.getAttribute("OffsetTimeOriginal"))
   } else if(mime.startsWith("video/")) {
    val retriever=MediaMetadataRetriever()
    try {
     retriever.setDataSource(context,uri)
     val value=retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE) ?: return null
     val instant=runCatching {java.time.Instant.parse(value)}.getOrElse {
      java.time.OffsetDateTime.parse(value,java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss[.SSS]X")).toInstant()
     }
     instant.toEpochMilli().takeIf {MediaOrder.valid(it)>0}
    } finally {retriever.release()}
   } else null
  }.getOrNull()
 }
}
