package uk.co.traynor.privategallery.core.media

import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileNotFoundException

/** Expiry is enforced on every open, including after process death. Other existing roots are unchanged. */
class ExpiringShareProvider:FileProvider() {
 private fun enforce(uri:Uri) {
  if(uri.pathSegments.firstOrNull()=="external_edit_copy") {
   val name=uri.lastPathSegment ?: throw FileNotFoundException()
   if(!name.matches(Regex("[a-f0-9-]{36}\\.png")))throw FileNotFoundException()
   val file=File(requireNotNull(context).cacheDir,"external-edit/$name")
   if(!ExternalEditLease.allowed(file,System.currentTimeMillis())){file.delete();throw FileNotFoundException("Edit copy expired")}
   return
  }
  if(uri.pathSegments.firstOrNull()!="temporary_share_copy")return
  val name=uri.lastPathSegment ?: throw FileNotFoundException()
  if(!name.matches(Regex("[a-f0-9-]{36}\\.(jpg|png)")))throw FileNotFoundException()
  val file=File(requireNotNull(context).cacheDir,"share-copies/$name")
  if(!file.isFile || System.currentTimeMillis()-file.lastModified()>=15*60*1000L) {
   file.delete();throw FileNotFoundException("Share copy expired")
  }
 }
 override fun openFile(uri:Uri,mode:String):ParcelFileDescriptor {
  if(uri.pathSegments.firstOrNull() in setOf("temporary_share_copy","external_edit_copy")&&mode!="r")throw FileNotFoundException("Read-only share copy")
  enforce(uri);return super.openFile(uri,mode) ?: throw FileNotFoundException("Share copy unavailable")
 }
 override fun query(uri:Uri,projection:Array<out String>?,selection:String?,selectionArgs:Array<out String>?,sortOrder:String?):android.database.Cursor {
  enforce(uri);return super.query(uri,projection,selection,selectionArgs,sortOrder)
 }
}
