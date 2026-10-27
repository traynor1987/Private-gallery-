package uk.co.traynor.privategallery.core.media
import android.content.Intent
import android.net.Uri
import org.junit.Test
import org.junit.Assert.*
class ExternalEditorIntents1761Test {
 @Test fun editorChooserUsesImplicitEditAndReadOnlyContentGrant() {
  val uri=Uri.parse("content://synthetic.fileprovider/external_edit_copy/00000000-0000-0000-0000-000000000000.png")
  val chooser=ExternalEditorIntents.editor(uri)
  assertEquals(Intent.ACTION_CHOOSER,chooser.action)
  @Suppress("DEPRECATION") val edit=chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
  assertEquals(Intent.ACTION_EDIT,edit.action);assertEquals(uri,edit.data);assertEquals("image/png",edit.type)
  assertNull(edit.`package`);assertNull(edit.component)
  for(i in listOf(edit,chooser)){assertTrue(i.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION!=0);assertEquals(0,i.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION);assertEquals(uri,i.clipData!!.getItemAt(0).uri)}
 }
 @Test fun samsungEditTargetsOnlyGalleryAndGrantsReadOnlyAccess() {
  val uri=Uri.parse("content://synthetic.fileprovider/external_edit_copy/photo.png")
  val edit=ExternalEditorIntents.samsungEditor(uri)
  assertEquals(Intent.ACTION_EDIT,edit.action);assertEquals("com.sec.android.gallery3d",edit.`package`)
  assertEquals(uri,edit.data);assertEquals("image/png",edit.type)
  assertTrue(edit.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
  assertEquals(0,edit.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
 }
 @Test fun samsungGalleryViewTargetsGalleryWithReadOnlyGrant() {
  val uri=Uri.parse("content://media/external/images/media/123")
  val view=ExternalEditorIntents.samsungGallery(uri)
  assertEquals(Intent.ACTION_VIEW,view.action);assertEquals("com.sec.android.gallery3d",view.`package`)
  assertEquals(uri,view.data);assertEquals(0,view.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
  assertTrue(view.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
 }
 @Test fun publicGalleryFallbackUsesViewWithoutClaimingDirectEditingOrSend() {
  val uri=Uri.parse("content://media/external/images/media/123")
  val chooser=ExternalEditorIntents.gallery(uri)
  @Suppress("DEPRECATION") val view=chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
  assertEquals(Intent.ACTION_VIEW,view.action);assertEquals(uri,view.data);assertNull(view.`package`)
  assertNotEquals(Intent.ACTION_SEND,view.action)
 }
 @Test fun fileAndNetworkUrisCannotEnterEditingChooser() {
  for(uri in listOf("file:///private/real.png","https://example.com/photo.png")) {
   assertTrue(runCatching {ExternalEditorIntents.editor(Uri.parse(uri))}.isFailure)
   assertTrue(runCatching {ExternalEditorIntents.samsungEditor(Uri.parse(uri))}.isFailure)
   assertTrue(runCatching {ExternalEditorIntents.samsungGallery(Uri.parse(uri))}.isFailure)
   assertTrue(runCatching {ExternalEditorIntents.gallery(Uri.parse(uri))}.isFailure)
  }
 }
}
