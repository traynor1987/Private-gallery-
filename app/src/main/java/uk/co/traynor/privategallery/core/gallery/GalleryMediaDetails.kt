package uk.co.traynor.privategallery.core.gallery
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import uk.co.traynor.privategallery.core.vault.*

/** Metadata from an already granted MediaStore URI. Does not persist or alter public media. */
internal object GalleryMediaDetails {
 fun read(context:Context,id:String,uri:Uri,mime:String):VaultItem {
  val projection=arrayOf(MediaStore.MediaColumns.DISPLAY_NAME,MediaStore.MediaColumns.SIZE,MediaStore.MediaColumns.DATE_ADDED,MediaStore.MediaColumns.DATE_MODIFIED,MediaStore.MediaColumns.DATE_TAKEN)
  return checkNotNull(context.contentResolver.query(uri,projection,null,null,null)).use {cursor->
   check(cursor.moveToFirst())
   fun number(name:String)=cursor.getColumnIndex(name).takeIf {it>=0&&!cursor.isNull(it)}?.let {cursor.getLong(it)}
   val name=cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME).takeIf {it>=0}?.let {cursor.getString(it)} ?: "Media"
   VaultItem(id,mime,name,0,number(MediaStore.MediaColumns.SIZE) ?: -1,byteArrayOf(),byteArrayOf(),VaultItemState.COMPLETE,
    addedAtEpochMillis=number(MediaStore.MediaColumns.DATE_ADDED)?.takeIf {it in 1..253402300799L}?.times(1000),
    capturedAtEpochMillis=number(MediaStore.MediaColumns.DATE_TAKEN)?.takeIf {uk.co.traynor.privategallery.core.ui.MediaOrder.valid(it)>0},modifiedAtEpochMillis=number(MediaStore.MediaColumns.DATE_MODIFIED)?.takeIf {it in 1..253402300799L}?.times(1000))
  }
 }
 fun bytes(context:Context,uri:Uri):ByteArray = checkNotNull(context.contentResolver.openInputStream(uri)).use {input ->
  val buffer=ByteArray(64*1024);val output=java.io.ByteArrayOutputStream()
  try {while(true){val count=input.read(buffer);if(count<0)break;check(count>0&&output.size().toLong()+count<=64L*1024*1024) {"Media exceeds the details limit"};output.write(buffer,0,count)};output.toByteArray()}
  finally {buffer.fill(0);output.reset()}
 }
}
