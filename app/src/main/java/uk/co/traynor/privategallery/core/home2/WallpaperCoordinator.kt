package uk.co.traynor.privategallery.core.home2

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.concurrent.atomic.AtomicBoolean
import uk.co.traynor.privategallery.core.evidence.EvidenceAccess
import uk.co.traynor.privategallery.core.evidence.EvidenceCatalog
import uk.co.traynor.privategallery.core.editor.PhotoRenderer
import uk.co.traynor.privategallery.ui.VaultImageEdits
import uk.co.traynor.privategallery.core.vault.NormalizedCrop
import uk.co.traynor.privategallery.core.vault.VaultItem
import uk.co.traynor.privategallery.home2.Wallpaper

/** Metadata-only owner survives an explicit system picker; decoded presentations never do. */
internal class WallpaperOwner(
 val owner:EvidenceCatalog.Owner,
 private val authenticationCurrent:()->Boolean,
 private val accessFactory:()->EvidenceAccess?,
 private val ownsAccess:Boolean=true,
) : AutoCloseable {
 private val closed=AtomicBoolean()
 fun current():Boolean {
  if(closed.get())return false
  if(!runCatching(authenticationCurrent).getOrDefault(false)){close();return false}
  return true
 }
 fun openBackup(context:Context):uk.co.traynor.privategallery.core.backup.BackupCoordinator? {
  if(!current())return null
  val access=accessFactory()?:return null
  return try {check(access.owner==owner&&current()&&access.current());uk.co.traynor.privategallery.core.backup.BackupCoordinator(context,access,ownsAccess)}
  catch(failure:Throwable){access.close();throw failure}
 }
 fun open(context:Context):WallpaperCoordinator? {
  if(!current())return null
  val access=accessFactory()?:return null
  return try {
   check(access.owner==owner&&current()&&access.current())
   WallpaperCoordinator(context,access,ownsAccess)
  }catch(failure:Throwable){if(ownsAccess)access.close();throw failure}
 }
 override fun close(){closed.set(true)}
}

/** All sources are existing authenticated Gallery originals. No URI/path/payload is persisted. */
internal class WallpaperCoordinator(context:Context,val access:EvidenceAccess,private val ownsAccess:Boolean=false):AutoCloseable {
 private val closed=AtomicBoolean()
 private val providerContext=context
 internal val space=SpaceContext(context,access)
 private val store=WallpaperStore(space)
 fun current()=!closed.get()&&space.current()
 private fun checkCurrent(){check(current()) {"Wallpaper presentation retired"}}
 fun wallpaper():Wallpaper {checkCurrent();return store.read().also{checkCurrent()}}
 fun images():List<VaultItem> {checkCurrent();return access.sources.list().map{it.item}.filter{it.mimeType.startsWith("image/")&&it.plaintextSize in 1..PhotoRenderer.MAX_SOURCE_BYTES.toLong()}.also{checkCurrent()}}
 fun image(id:String,thumbnail:Boolean=false):WallpaperImage {
  checkCurrent()
  var bytes:ByteArray?=null
  try {
   access.reader{!current()}.readVerified(id){original,input->
    require(original.item.mimeType.startsWith("image/")&&original.item.plaintextSize in 1..PhotoRenderer.MAX_SOURCE_BYTES.toLong())
    bytes=readBounded(input)
   }
   checkCurrent()
   val crop=access.sources.imageCrop(id)
   return decode(checkNotNull(bytes),thumbnail,crop).also{candidate->try{checkCurrent()}catch(failure:Throwable){candidate.close();throw failure}}
  }finally{bytes?.fill(0)}
 }
 fun systemImage(uri:Uri):WallpaperImage {
  checkCurrent();require(uri.scheme=="content")
  var bytes:ByteArray?=null
  try {
   access.providerStream(providerContext,uri).use{bytes=readBounded(it)}
   checkCurrent();return decode(checkNotNull(bytes),false).also{candidate->try{checkCurrent()}catch(failure:Throwable){candidate.close();throw failure}}
  }finally{bytes?.fill(0)}
 }
 fun apply(draft:Wallpaper,image:WallpaperImage?):Wallpaper {
  checkCurrent()
  val owned=image?.encodedCopy()
  return try{store.apply(draft,owned).also{checkCurrent()}}finally{owned?.fill(0)}
 }
 private fun readBounded(input:InputStream):ByteArray=WipingOutput(PhotoRenderer.MAX_SOURCE_BYTES).use{out->
  val buffer=ByteArray(65536)
  try{while(true){checkCurrent();val count=input.read(buffer);if(count<0)break;out.write(buffer,0,count)};checkCurrent();out.toByteArray()}finally{buffer.fill(0)}
 }
 private fun decode(bytes:ByteArray,thumbnail:Boolean,crop:NormalizedCrop?=null):WallpaperImage {
  var decoded:Bitmap?=null;var oriented:Bitmap?=null;var cropped:Bitmap?=null;var mutable:Bitmap?=null
  try {
   val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true}
   BitmapFactory.decodeByteArray(bytes,0,bytes.size,bounds)
   require(bounds.outWidth in 1..40000&&bounds.outHeight in 1..40000&&bounds.outWidth.toLong()*bounds.outHeight<=200_000_000L) {"Image dimensions unavailable"}
   val limit=if(thumbnail)320 else 2048
   var sample=1;while(maxOf(bounds.outWidth,bounds.outHeight)/sample>limit)sample*=2
   checkCurrent()
   decoded=checkNotNull(BitmapFactory.decodeByteArray(bytes,0,bytes.size,BitmapFactory.Options().apply{inSampleSize=sample;inMutable=true}))
   oriented=VaultImageEdits.visuallyOrient(bytes,decoded)
   cropped=VaultImageEdits.crop(oriented,crop)
   mutable=if(cropped.isMutable)cropped else checkNotNull(cropped.copy(Bitmap.Config.ARGB_8888,true))
   checkCurrent()
   val output=checkNotNull(mutable)
   val encoded=if(thumbnail)ByteArray(0)else WipingOutput(8*1024*1024).use{out->check(output.compress(Bitmap.CompressFormat.JPEG,90,out));out.toByteArray()}
   val result=WallpaperImage(output,encoded);mutable=null
   if(decoded===result.bitmap)decoded=null
   if(oriented===result.bitmap)oriented=null
   if(cropped===result.bitmap)cropped=null
   return result
  }finally{listOfNotNull(decoded,oriented,cropped,mutable).distinct().forEach{wipe(it)}}
 }
 override fun close(){if(closed.compareAndSet(false,true)){space.close();if(ownsAccess)access.close()}}
 private class WipingOutput(private val limit:Int):ByteArrayOutputStream() {
  override fun write(b:ByteArray,off:Int,len:Int){require(count.toLong()+len<=limit);super.write(b,off,len)}
  override fun close(){buf.fill(0);reset();super.close()}
 }
 companion object {internal fun wipe(bitmap:Bitmap){if(!bitmap.isRecycled){if(bitmap.isMutable)runCatching{bitmap.eraseColor(android.graphics.Color.TRANSPARENT)};bitmap.recycle()}}}
}
internal class WallpaperImage(val bitmap:Bitmap,private val encoded:ByteArray):AutoCloseable {
 private val closed=AtomicBoolean()
 internal fun encodedCopy():ByteArray=synchronized(this){check(!closed.get());encoded.copyOf()}
 override fun close(){val ended=synchronized(this){if(closed.compareAndSet(false,true)){encoded.fill(0);true}else false};if(ended)WallpaperCoordinator.wipe(bitmap)}
}
