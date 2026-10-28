package uk.co.traynor.privategallery.core.media

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.content.ClipData
import android.content.Intent
import android.net.Uri

/** Public Android contracts only: SEND shares rather than edits; VIEW is an explicit fallback. */
internal object ExternalEditorIntents {
 data class EditorTarget(val component:ComponentName,val label:String)
 /** Query EDIT only: sharing receivers and Gallery VIEW handlers are not editors. */
 @Suppress("DEPRECATION")
 fun discoverEditors(context:Context,uri:Uri):List<EditorTarget> {
  val pm=context.packageManager
  val matches=pm.queryIntentActivities(editTarget(uri),PackageManager.MATCH_DEFAULT_ONLY or PackageManager.GET_RESOLVED_FILTER)
  return compatibleEditors(matches,context.packageName,
   {permission ->pm.checkPermission(permission,context.packageName)==PackageManager.PERMISSION_GRANTED},
   {it.loadLabel(pm).toString()})
 }
 internal fun compatibleEditors(matches:List<ResolveInfo>,ownPackage:String,permitted:(String)->Boolean,label:(ResolveInfo)->String):List<EditorTarget> =
  matches.filter {resolved ->
   val activity=resolved.activityInfo
   activity!=null && activity.packageName!=ownPackage && activity.enabled && activity.applicationInfo?.enabled==true && activity.exported &&
    resolved.filter?.hasAction(Intent.ACTION_EDIT)==true && (activity.permission.isNullOrBlank() || permitted(activity.permission))
  }.map {EditorTarget(ComponentName(it.activityInfo.packageName,it.activityInfo.name),label(it))}
   .distinctBy {it.component}
   .sortedWith(compareBy<EditorTarget> {if(it.component.packageName=="com.sec.android.gallery3d")0 else 1}.thenBy {it.label.lowercase()}.thenBy {it.component.flattenToString()})
 fun editorFor(uri:Uri,component:ComponentName):Intent=editTarget(uri).setComponent(component)
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
