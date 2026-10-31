package uk.co.traynor.privategallery.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import uk.co.traynor.privategallery.core.evidence.EvidenceController
import uk.co.traynor.privategallery.core.vault.VaultPdfPreview

private val evidenceThumbnailAdmission=Semaphore(1)
/** Memory-only thumbnails: bounded work is serialized and bytes never survive the scoped reader. */
@Composable internal fun EvidenceThumbnail(controller:EvidenceController,id:String) {
 val context=LocalContext.current.applicationContext
 var shown by remember(controller,id){mutableStateOf<Bitmap?>(null)}
 val alive=remember(controller,id){java.util.concurrent.atomic.AtomicBoolean(true)}
 DisposableEffect(controller,id){onDispose{alive.set(false);shown?.let(::wipeEvidenceThumbnail);shown=null}}
 LaunchedEffect(controller,id){
  var pending:Bitmap?=null
  try {
   evidenceThumbnailAdmission.withPermit {
    controller.withOriginalBytes(id){bytes ->
     val item=controller.state.value.catalog?.evidence()?.firstOrNull{it.id()==id}?:return@withOriginalBytes
     if(item.mime()=="application/pdf") {
      val page=VaultPdfPreview.page(context,bytes,0){alive.get()&&controller.current()&&isActive}.first
      try{pending=Bitmap.createScaledBitmap(page,192,(192L*page.height/page.width).toInt().coerceIn(1,256),true)}finally{if(pending!==page)wipeEvidenceThumbnail(page)}
     }else if(item.mime().startsWith("image/")) {
      val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true};BitmapFactory.decodeByteArray(bytes,0,bytes.size,bounds)
      require(bounds.outWidth in 1..100000&&bounds.outHeight in 1..100000&&bounds.outWidth.toLong()*bounds.outHeight<=512000000)
      var sample=1;while((bounds.outWidth.toLong()+sample-1)/sample>256||(bounds.outHeight.toLong()+sample-1)/sample>256)sample*=2
      pending=BitmapFactory.decodeByteArray(bytes,0,bytes.size,BitmapFactory.Options().apply{inSampleSize=sample;inMutable=true})
     }
     withContext(Dispatchers.Main.immediate){
      if(!alive.get()||!controller.access.publishIfCurrent{shown=pending;pending=null})throw CancellationException("Retired thumbnail")
     }
    }
   }
  }catch(cancelled:CancellationException){throw cancelled}catch(_:Throwable){}finally{pending?.let(::wipeEvidenceThumbnail)}
 }
 if(controller.current())shown?.let{Image(it.asImageBitmap(),"Evidence thumbnail",Modifier.size(56.dp),contentScale=ContentScale.Fit)}?:Text("▧",Modifier.size(48.dp))
}
private fun wipeEvidenceThumbnail(bitmap:Bitmap){runCatching{if(bitmap.isMutable)bitmap.eraseColor(android.graphics.Color.TRANSPARENT)};runCatching{bitmap.recycle()}}
