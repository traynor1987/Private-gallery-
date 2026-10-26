package uk.co.traynor.privategallery.core.media
import androidx.test.platform.app.InstrumentationRegistry
import android.graphics.Bitmap
import android.provider.MediaStore
import java.io.ByteArrayOutputStream
import java.io.File
import org.junit.Assert.*
import org.junit.Test
class ExternalPhotoExport1761Test {
 @Test fun galleryFallbackPublishesOnlyVerifiedGenericCopyAndRetainsPrivateLease() {
  val ctx=InstrumentationRegistry.getInstrumentation().targetContext
  val bitmap=Bitmap.createBitmap(8,8,Bitmap.Config.ARGB_8888)
  val bytes=try {ByteArrayOutputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it);it.toByteArray()}}finally{bitmap.recycle()}
  val file=ExternalEditLease.create(File(ctx.cacheDir,"external-edit"),bytes,System.currentTimeMillis())
  var uri:android.net.Uri?=null
  try {
   uri=ExternalPhotoExport.toGallery(ctx,file,{true},{it()})
   ctx.contentResolver.query(uri,arrayOf(MediaStore.MediaColumns.DISPLAY_NAME,MediaStore.MediaColumns.IS_PENDING),null,null,null)!!.use {
    assertTrue(it.moveToFirst());assertTrue(it.getString(0).startsWith("Photo-edit-"));assertEquals(0,it.getInt(1))
   }
   assertArrayEquals(bytes,ctx.contentResolver.openInputStream(uri)!!.use {it.readBytes()});assertTrue(file.isFile)
  } finally {uri?.let {ctx.contentResolver.delete(it,null,null)};file.delete();bytes.fill(0)}
 }
 @Test fun revokedAuthorityAndNonLeaseFilesNeverPublish() {
  val ctx=InstrumentationRegistry.getInstrumentation().targetContext
  val file=ExternalEditLease.create(File(ctx.cacheDir,"external-edit"),byteArrayOf(1,2,3),System.currentTimeMillis())
  val outside=File(ctx.cacheDir,"not-edit-${java.util.UUID.randomUUID()}.png").apply {writeBytes(byteArrayOf(1))}
  try {assertTrue(runCatching {ExternalPhotoExport.toGallery(ctx,file,{false},{it()})}.isFailure);assertTrue(file.isFile)
   assertTrue(runCatching {ExternalPhotoExport.toGallery(ctx,outside,{true},{it()})}.isFailure)
  } finally {file.delete();outside.delete()}
 }
}
