package uk.co.traynor.privategallery.core.privateapps

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Base64
import kotlin.math.roundToInt

/** No disk cache. Only tiny normalized raster bytes are persisted inside the encrypted catalogue. */
internal object PrivateAppIcons {
 fun normalize(bytes:ByteArray):String = normalize(bytes,PrivateAppDiscovery.MAX_DOCUMENT,1024,1024L*1024)
 fun normalizeSelected(bytes:ByteArray):String = normalize(bytes,PrivateAppIconSource.MAX_SOURCE_BYTES,8192,32L*1024*1024)
 private fun normalize(bytes:ByteArray,maxBytes:Int,maxDimension:Int,maxPixels:Long):String {
  require(bytes.size in 1..maxBytes)
  val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true}
  BitmapFactory.decodeByteArray(bytes,0,bytes.size,bounds)
  require(bounds.outWidth in 1..maxDimension && bounds.outHeight in 1..maxDimension && bounds.outWidth.toLong()*bounds.outHeight<=maxPixels && bounds.outMimeType in setOf("image/png","image/jpeg","image/webp","image/x-ico","image/x-icon","image/heif","image/heic","image/avif"))
  // Decode a small sampled bitmap, never allocate the source image's full pixel area.
  var sample=1
  while(maxOf(bounds.outWidth,bounds.outHeight)/sample>128)sample*=2
  val decoded=checkNotNull(BitmapFactory.decodeByteArray(bytes,0,bytes.size,BitmapFactory.Options().apply{inSampleSize=sample;inPreferredConfig=Bitmap.Config.ARGB_8888}))
  val orientation=readOrientation(bytes)
  val bitmap=try {orient(decoded,orientation)}catch(error:Exception){decoded.recycle();throw error}
  if(bitmap!==decoded)decoded.recycle()
  val swapsAxes=orientation in setOf(ExifInterface.ORIENTATION_TRANSPOSE,ExifInterface.ORIENTATION_ROTATE_90,ExifInterface.ORIENTATION_TRANSVERSE,ExifInterface.ORIENTATION_ROTATE_270)
  val sourceWidth=if(swapsAxes)bounds.outHeight else bounds.outWidth
  val sourceHeight=if(swapsAxes)bounds.outWidth else bounds.outHeight
  try {
   for(size in listOf(64,32)) {
    // Sampled decode rounds each axis independently. Preserve the original image's aspect,
    // and round once at the final icon size instead of truncating that already rounded sample.
    val ratio=minOf(1.0,size.toDouble()/maxOf(sourceWidth,sourceHeight))
    val scaled=Bitmap.createScaledBitmap(bitmap,maxOf(1,(sourceWidth*ratio).roundToInt()),maxOf(1,(sourceHeight*ratio).roundToInt()),true)
    try {
     val data=ByteArrayOutputStream().use {out ->check(scaled.compress(Bitmap.CompressFormat.PNG,100,out));out.toByteArray()}
     try {if(data.size<=6144)return Base64.getEncoder().encodeToString(data)}finally{data.fill(0)}
    }finally{if(scaled!==bitmap)scaled.recycle()}
   }
   return ""
  }finally{bitmap.recycle()}
 }
 private fun readOrientation(bytes:ByteArray):Int = runCatching{ByteArrayInputStream(bytes).use{ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION,ExifInterface.ORIENTATION_NORMAL)}}.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
 private fun orient(bitmap:Bitmap,orientation:Int):Bitmap {
  val matrix=Matrix()
  when(orientation) {
   ExifInterface.ORIENTATION_FLIP_HORIZONTAL->matrix.setScale(-1f,1f)
   ExifInterface.ORIENTATION_ROTATE_180->matrix.setRotate(180f)
   ExifInterface.ORIENTATION_FLIP_VERTICAL->matrix.setScale(1f,-1f)
   ExifInterface.ORIENTATION_TRANSPOSE->{matrix.setRotate(90f);matrix.postScale(-1f,1f)}
   ExifInterface.ORIENTATION_ROTATE_90->matrix.setRotate(90f)
   ExifInterface.ORIENTATION_TRANSVERSE->{matrix.setRotate(-90f);matrix.postScale(-1f,1f)}
   ExifInterface.ORIENTATION_ROTATE_270->matrix.setRotate(-90f)
   else->return bitmap
  }
  return Bitmap.createBitmap(bitmap,0,0,bitmap.width,bitmap.height,matrix,true)
 }
 fun decode(value:String):Bitmap?=runCatching {
  require(value.length in 1..8192)
  val bytes=Base64.getDecoder().decode(value)
  try {
   require(bytes.size<=6144)
   val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true};BitmapFactory.decodeByteArray(bytes,0,bytes.size,bounds)
   require(bounds.outWidth in 1..64 && bounds.outHeight in 1..64 && bounds.outMimeType=="image/png")
   BitmapFactory.decodeByteArray(bytes,0,bytes.size)
  }finally{bytes.fill(0)}
 }.getOrNull()
}
