package uk.co.traynor.privategallery.core.media

import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.ResolveInfo
import android.net.Uri
import org.junit.Assert.*
import org.junit.Test

class ExternalEditorDiscovery1762Test {
 private fun receiver(pkg:String,action:String=Intent.ACTION_EDIT,enabled:Boolean=true,exported:Boolean=true,permission:String?=null)=ResolveInfo().apply {
  activityInfo=ActivityInfo().apply {packageName=pkg;name="$pkg.Editor";this.enabled=enabled;this.exported=exported;this.permission=permission;applicationInfo=ApplicationInfo().apply {this.enabled=true}}
  filter=IntentFilter(action).apply {addDataType("image/png")}
 }
 @Test fun matchingEditorsAreExplicitDeduplicatedAndSamsungFirst() {
  val other=receiver("example.editor");val samsung=receiver("com.sec.android.gallery3d")
  val found=ExternalEditorIntents.compatibleEditors(listOf(other,samsung,other),"self",{true},{it.activityInfo.packageName})
  assertEquals(listOf("com.sec.android.gallery3d","example.editor"),found.map {it.component.packageName})
  val uri=Uri.parse("content://synthetic.provider/external_edit_copy/photo.png")
  val edit=ExternalEditorIntents.editorFor(uri,found.last().component)
  assertEquals(ComponentName("example.editor","example.editor.Editor"),edit.component)
  assertEquals(Intent.ACTION_EDIT,edit.action);assertEquals(uri,edit.data)
  assertEquals(0,edit.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
  assertTrue(edit.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
  assertEquals(uri,edit.clipData!!.getItemAt(0).uri)
 }
 @Test fun unrelatedDisabledPrivateAndPermissionDeniedReceiversAreExcluded() {
  val denied=receiver("denied",permission="restricted")
  val inputs=listOf(receiver("view",Intent.ACTION_VIEW),receiver("share",Intent.ACTION_SEND),receiver("disabled",enabled=false),receiver("private",exported=false),receiver("self"),denied,receiver("valid"))
  assertEquals(listOf("valid"),ExternalEditorIntents.compatibleEditors(inputs,"self",{false},{it.activityInfo.packageName}).map {it.component.packageName})
 }
 @Test fun applicationDisabledAndUnresolvedFilterCannotEnterList() {
  val disabled=receiver("disabled.application").apply {activityInfo.applicationInfo.enabled=false}
  val noFilter=receiver("missing.filter").apply {filter=null}
  assertTrue(ExternalEditorIntents.compatibleEditors(listOf(disabled,noFilter),"self",{true},{"unused"}).isEmpty())
 }
 @Test fun permittedProtectedEditorRemainsAvailable() {
  val candidate=receiver("allowed",permission="granted.permission")
  assertEquals(1,ExternalEditorIntents.compatibleEditors(listOf(candidate),"self",{it=="granted.permission"},{"Allowed editor"}).size)
 }
 @Test fun emptyDiscoveryOffersNoImplicitReceiver() {
  assertTrue(ExternalEditorIntents.compatibleEditors(emptyList(),"self",{true},{"unused"}).isEmpty())
 }
}
