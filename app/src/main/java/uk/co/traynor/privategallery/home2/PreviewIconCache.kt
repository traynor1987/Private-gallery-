package uk.co.traynor.privategallery.home2
import uk.co.traynor.privategallery.R

import android.content.Context
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

/** Only signed, build-time verified catalogue assets. No runtime network metadata. */
internal object PreviewIconCache {
 private val bitmaps=object:LruCache<String,ImageBitmap>(12*1024*1024){
  override fun sizeOf(key:String,value:ImageBitmap)=value.width*value.height*4
 }
 fun load(context:Context,path:String?):ImageBitmap? {
  if(path==null||!path.matches(Regex("icons/[a-z0-9-]+\\.png")))return null
  bitmaps.get(path)?.let{return it}
  return runCatching{
   val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true}
   context.assets.open(path).use{BitmapFactory.decodeStream(it,null,bounds)}
   require(bounds.outWidth in 96..512&&bounds.outHeight in 96..512)
   context.assets.open(path).use{BitmapFactory.decodeStream(it)?.asImageBitmap()}?.also{bitmaps.put(path,it)}
  }.getOrNull()
 }
}
