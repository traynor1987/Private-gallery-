package uk.co.traynor.privategallery.core.media
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.ExifInterface
import androidx.core.content.FileProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

class MediaPrivacy174Test {
 @Test fun realPngCleanCopyRemainsDecodableWithAdjustedDateAndNoPrivateText() {
  val bitmap=Bitmap.createBitmap(24,16,Bitmap.Config.ARGB_8888).apply {eraseColor(Color.GREEN)}
  try {
   val bytes=ByteArrayOutputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it);it.toByteArray()}
   val clean=PngPrivacy.clean(bytes,1,"2026:10:24 12:34:56")
   try {
    val decoded=BitmapFactory.decodeByteArray(clean,0,clean.size)
    try {assertTrue(bitmap.sameAs(decoded))}finally{decoded?.recycle()}
    val exif=ExifInterface(ByteArrayInputStream(clean))
    assertEquals("2026:10:24 12:34:56",exif.getAttribute("DateTimeOriginal"))
    assertFalse(exif.getLatLong(FloatArray(2)))
   }finally{bytes.fill(0);clean.fill(0)}
  }finally{bitmap.recycle()}
 }
 @Test fun protectedVideoMetadataUsesRevocableSessionWithoutTemporaryFiles() {
  val bytes=InstrumentationRegistry.getInstrumentation().context.assets.open("browser-media/valid.mp4").use {it.readBytes()}
  var closed=false
  val session=object:uk.co.traynor.privategallery.core.vault.ProtectedVideoSession {
   override val sourceFactory=androidx.media3.datasource.DataSource.Factory {androidx.media3.datasource.ByteArrayDataSource(bytes)}
   override fun close(){closed=true}
  }
  try {
   val fields=ProtectedMetadataReader.read(session,bytes.size.toLong()){true}
   assertTrue(fields.any {it.first=="Resolution"});assertTrue(fields.any {it.first=="Duration"});assertTrue(closed)
   assertTrue(runCatching {ProtectedMetadataReader.read(session,bytes.size.toLong()){false}}.isFailure)
  }finally{bytes.fill(0)}
 }

 @Test fun realJpegRetainsPixelsAndOrientationButDropsLocationDeviceAndXmp() {
  val context=InstrumentationRegistry.getInstrumentation().targetContext
  val file=File(context.cacheDir,"privacy-test-${UUID.randomUUID()}.jpg")
  val bitmap=Bitmap.createBitmap(32,24,Bitmap.Config.ARGB_8888).apply {eraseColor(Color.BLUE)}
  try {
   file.outputStream().use {bitmap.compress(Bitmap.CompressFormat.JPEG,95,it)}
   ExifInterface(file.absolutePath).apply {
    setAttribute("GPSLatitude","51/1,30/1,0/1");setAttribute("GPSLatitudeRef","N")
    setAttribute("GPSLongitude","0/1,7/1,0/1");setAttribute("GPSLongitudeRef","W")
    setAttribute("Make","PRIVATE CAMERA");setAttribute("Model","PRIVATE MODEL");setAttribute("Orientation","6");saveAttributes()
   }
   val original=file.readBytes();val hash=java.security.MessageDigest.getInstance("SHA-256").digest(original)
   val clean=JpegPrivacy.clean(original,6,"2026:10:24 12:34:56")
   try {
    val exif=ExifInterface(ByteArrayInputStream(clean));val gps=FloatArray(2)
    assertFalse(exif.getLatLong(gps));assertNull(exif.getAttribute("Make"));assertNull(exif.getAttribute("Model"))
    assertEquals("2026:10:24 12:34:56",exif.getAttribute("DateTimeOriginal"));assertEquals(6,exif.getAttributeInt("Orientation",1))
    val a=BitmapFactory.decodeByteArray(original,0,original.size);val b=BitmapFactory.decodeByteArray(clean,0,clean.size)
    try {assertNotNull(b);assertTrue(a.sameAs(b))}finally{a.recycle();b?.recycle()}
    assertArrayEquals(hash,java.security.MessageDigest.getInstance("SHA-256").digest(file.readBytes()))
   }finally{original.fill(0);clean.fill(0)}
  }finally{bitmap.recycle();file.delete()}
 }
 @Test fun providerRejectsExpiredCopiesAndWritingWhileKeepingFreshCopyReadable() {
  val context=InstrumentationRegistry.getInstrumentation().targetContext
  val dir=File(context.cacheDir,"share-copies").apply {mkdirs()}
  val file=File(dir,"${UUID.randomUUID()}.jpg").apply {writeBytes(byteArrayOf(1,2,3))}
  val uri=FileProvider.getUriForFile(context,"${context.packageName}.fileprovider",file)
  try {
   assertArrayEquals(byteArrayOf(1,2,3),context.contentResolver.openInputStream(uri)!!.use {it.readBytes()})
   assertTrue(runCatching {context.contentResolver.openFileDescriptor(uri,"w")!!.close()}.isFailure)
   assertTrue(file.setLastModified(System.currentTimeMillis()-16*60*1000))
   assertTrue(runCatching {context.contentResolver.openInputStream(uri)!!.close()}.isFailure)
   assertFalse(file.exists())
  } finally {file.delete()}
 }
}
