package uk.co.traynor.privategallery.core.media

import android.content.ClipData
import android.content.Intent
import android.net.Uri

/** Public Android contracts only: SEND shares rather than edits; VIEW is an explicit fallback. */
internal object ExternalEditorIntents {
 fun editTarget(uri:Uri):Intent=target(Intent.ACTION_EDIT,uri)
 fun samsungEditor(uri:Uri):Intent=editTarget(uri).setPackage("com.sec.android.gallery3d")
 fun editor(uri:Uri):Intent=chooser(editTarget(uri),"Choose photo editor")
 fun gallery(uri:Uri):Intent=chooser(target(Intent.ACTION_VIEW,uri),"Open exported photo")
 fun samsungGallery(uri:Uri):Intent=target(Intent.ACTION_VIEW,uri).setPackage("com.sec.android.gallery3d")
 private fun target(action:String,uri:Uri):Intent {
  require(uri.scheme=="content") {"Content URI required"}
  return Intent(action).apply {
   setDataAndType(uri,"image/png")
   clipData=ClipData.newRawUri("Temporary photo copy",uri)
   addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
  }
 }
 private fun chooser(target:Intent,title:String)=Intent.createChooser(target,title).apply {
  clipData=target.clipData;addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
 }
}
