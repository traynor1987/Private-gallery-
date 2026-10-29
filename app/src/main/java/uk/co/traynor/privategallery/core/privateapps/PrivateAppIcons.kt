package uk.co.traynor.privategallery.core.privateapps

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import java.util.Base64

/** No disk cache. Only tiny normalized raster bytes are persisted inside the encrypted catalogue. */
internal object PrivateAppIcons {
 fun normalize(bytes:ByteArray):String {
  require(bytes.size in 1..PrivateAppDiscovery.MAX_DOCUMENT)
  val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true}
  BitmapFactory.decodeByteArray(bytes,0,bytes.size,bounds)
  require(bounds.outWidth in 1..1024 && bounds.outHeight in 1..1024 && bounds.outMimeType in setOf("image/png","image/jpeg","image/webp","image/x-ico","image/x-icon"))
  val bitmap=checkNotNull(BitmapFactory.decodeByteArray(bytes,0,bytes.size,BitmapFactory.Options().apply{inSampleSize=maxOf(1,maxOf(bounds.outWidth,bounds.outHeight)/64)}))
  try {
   for(size in listOf(64,32)) {
    val ratio=minOf(1f,size.toFloat()/maxOf(bitmap.width,bitmap.height))
    val scaled=Bitmap.createScaledBitmap(bitmap,maxOf(1,(bitmap.width*ratio).toInt()),maxOf(1,(bitmap.height*ratio).toInt()),true)
    try {
     val data=ByteArrayOutputStream().use {out ->check(scaled.compress(Bitmap.CompressFormat.PNG,100,out));out.toByteArray()}
     try {if(data.size<=6144)return Base64.getEncoder().encodeToString(data)}finally{data.fill(0)}
    }finally{if(scaled!==bitmap)scaled.recycle()}
   }
   return ""
  }finally{bitmap.recycle()}
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
