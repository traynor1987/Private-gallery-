package uk.co.traynor.privategallery.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import uk.co.traynor.privategallery.core.evidence.*
import uk.co.traynor.privategallery.core.vault.VaultPdfPreview
import java.util.concurrent.atomic.AtomicBoolean

private val sourcePreviewAdmission=Semaphore(1)
/** These bitmaps belong exclusively to this tile, never to the shared Vault cache. */
@Composable internal fun EvidenceSourceSelectionTile(controller:EvidenceController,original:EvidenceOriginal,selected:Boolean,enabled:Boolean,onSelect:()->Unit) {
 val item=original.item
 val context=LocalContext.current.applicationContext
 var shown by remember(controller,item){mutableStateOf<Bitmap?>(null)}
 var expanded by remember(controller,item){mutableStateOf(false)}
 val alive=remember(controller,item){AtomicBoolean(true)}
 DisposableEffect(controller,item){
  val retirement=controller.access.onRetire{android.os.Handler(android.os.Looper.getMainLooper()).post{shown?.let(::wipeSourcePreview);shown=null;expanded=false}}
  onDispose{alive.set(false);retirement.close();shown?.let(::wipeSourcePreview);shown=null}
 }
 LaunchedEffect(controller,item){
  var pending:Bitmap?=null
  try {
   sourcePreviewAdmission.withPermit {
    if(item.mimeType.startsWith("image/")||item.mimeType=="application/pdf")controller.withSourcePreviewBytes(original){bytes ->
     if(item.mimeType=="application/pdf") {
      val page=VaultPdfPreview.page(context,bytes,0){alive.get()&&controller.current()&&isActive}.first
      try{pending=Bitmap.createScaledBitmap(page,192,(192L*page.height/page.width).toInt().coerceIn(1,256),true)}finally{if(pending!==page)wipeSourcePreview(page)}
     }else {
      val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true};BitmapFactory.decodeByteArray(bytes,0,bytes.size,bounds)
      require(bounds.outWidth in 1..100000&&bounds.outHeight in 1..100000&&bounds.outWidth.toLong()*bounds.outHeight<=512000000)
      var sample=1;while((bounds.outWidth.toLong()+sample-1)/sample>256||(bounds.outHeight.toLong()+sample-1)/sample>256)sample*=2
      pending=BitmapFactory.decodeByteArray(bytes,0,bytes.size,BitmapFactory.Options().apply{inSampleSize=sample;inMutable=true})
     }
     withContext(Dispatchers.Main.immediate){if(!alive.get()||!controller.access.publishIfCurrent{shown=pending;pending=null})throw CancellationException("Retired source preview")}
    }
   }
  }catch(cancelled:CancellationException){throw cancelled}catch(_:Throwable){}finally{pending?.let(::wipeSourcePreview)}
 }
 Column(Modifier.fillMaxWidth().semantics{contentDescription="Select original ${item.displayName}";this.selected=selected}) {
  Card(Modifier.fillMaxWidth().aspectRatio(1f).clickable(enabled=enabled,onClick=onSelect),border=if(selected)androidx.compose.foundation.BorderStroke(2.dp,MaterialTheme.colorScheme.primary)else null){
   Box(Modifier.fillMaxSize()){
    shown?.takeUnless{it.isRecycled}?.let{Image(it.asImageBitmap(),item.displayName,Modifier.fillMaxSize(),contentScale=ContentScale.Crop)}?:Text(if(item.mimeType=="application/pdf")"PDF" else if(item.mimeType.startsWith("image/"))"Preview unavailable" else "Text document",Modifier.padding(12.dp))
    Checkbox(selected,{if(enabled)onSelect()},Modifier.align(androidx.compose.ui.Alignment.TopEnd),enabled=enabled)
    TextButton(onClick={expanded=true},enabled=shown!=null,modifier=Modifier.align(androidx.compose.ui.Alignment.BottomEnd)){Text("Preview")}
   }
  }
  Text(item.displayName,maxLines=2,style=MaterialTheme.typography.labelSmall)
 }
 if(expanded&&controller.current())GalleryFullPageDialog(onDismissRequest={expanded=false},properties=DialogProperties(usePlatformDefaultWidth=false,securePolicy=SecureFlagPolicy.Inherit),scrollBody=false){
   Text(item.displayName,maxLines=2,style=MaterialTheme.typography.titleMedium)
   Box(Modifier.weight(1f).fillMaxWidth(),contentAlignment=androidx.compose.ui.Alignment.Center){
    shown?.takeUnless{it.isRecycled}?.let{Image(it.asImageBitmap(),item.displayName,Modifier.fillMaxSize(),contentScale=ContentScale.Fit)}
   }
   Text("${item.mimeType} · ${item.plaintextSize} bytes",style=MaterialTheme.typography.bodySmall)
   Text("Bounded original thumbnail · registration happens after review",style=MaterialTheme.typography.bodySmall)
   TextButton(onClick={expanded=false}){Text("Close preview")}
 }

}
private fun wipeSourcePreview(bitmap:Bitmap){runCatching{if(bitmap.isMutable)bitmap.eraseColor(android.graphics.Color.TRANSPARENT)};runCatching{bitmap.recycle()}}
