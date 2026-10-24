package uk.co.traynor.privategallery.core.gallery

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.media.MediaMetadataRetriever
import androidx.exifinterface.media.ExifInterface
import uk.co.traynor.privategallery.core.ui.MediaOrder

/** Reads only the granted URI; no media scan, write, plaintext file or guessed EXIF timezone. */
internal object MediaCaptureDate {
 fun read(context:Context,uri:Uri,mime:String):Long? {
  if(uri.scheme!="content")return null
  val mediaStore=runCatching {context.contentResolver.query(uri,arrayOf(MediaStore.MediaColumns.DATE_TAKEN),null,null,null)?.use {if(it.moveToFirst())it.getLong(0) else 0}}.getOrNull()
  mediaStore?.let {if(MediaOrder.valid(it)>0)return it}
  return runCatching {
   if(mime.startsWith("image/")) context.contentResolver.openInputStream(uri)?.use {input->
    val exif=ExifInterface(input)
    if(exif.getAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL)==null)null else exif.dateTimeOriginal?.takeIf {MediaOrder.valid(it)>0}
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
