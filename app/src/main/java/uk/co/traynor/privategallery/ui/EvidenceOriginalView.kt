package uk.co.traynor.privategallery.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import kotlinx.coroutines.*
import uk.co.traynor.privategallery.core.evidence.EvidenceController
import uk.co.traynor.privategallery.core.vault.VaultPdfPreview

/** Temporary protected view; original is never cropped, recompressed or written to a file. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable internal fun EvidenceOriginalView(controller:EvidenceController,id:String,onClose:()->Unit) {
 val context=LocalContext.current.applicationContext
 var bitmap by remember(controller,id){mutableStateOf<Bitmap?>(null)}
 var text by remember(controller,id){mutableStateOf("")}
 var page by remember(controller,id){mutableIntStateOf(0)}
 var pages by remember(controller,id){mutableIntStateOf(1)}
 var status by remember(controller,id){mutableStateOf("Opening verified original…")}
 val alive=remember(controller,id){java.util.concurrent.atomic.AtomicBoolean(true)}
 DisposableEffect(controller,id){onDispose{alive.set(false);bitmap?.let(::clearEvidenceBitmap);bitmap=null;text=""}}
 LaunchedEffect(controller,id,page){
  var pending:Bitmap?=null
  try {
   controller.withOriginalBytes(id){bytes ->
    var transcript="";var count=1
    check(alive.get()&&controller.current());val item=controller.state.value.catalog!!.evidence().single{it.id()==id}
    if(item.mime()=="application/pdf")VaultPdfPreview.page(context,bytes,page){alive.get()&&controller.current()&&isActive}.let{pending=it.first;count=it.second}
    else if(item.mime().startsWith("image/")) {
     val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true};BitmapFactory.decodeByteArray(bytes,0,bytes.size,bounds)
     require(bounds.outWidth in 1..100000&&bounds.outHeight in 1..100000&&bounds.outWidth.toLong()*bounds.outHeight<=512000000)
     var sample=1;while((bounds.outWidth.toLong()+sample-1)/sample>1600||(bounds.outHeight.toLong()+sample-1)/sample>1600)sample*=2
     pending=BitmapFactory.decodeByteArray(bytes,0,bytes.size,BitmapFactory.Options().apply{inSampleSize=sample;inMutable=true})?:error("Unsupported image")
    } else {
     val decoder=Charsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT).onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
     val chars=decoder.decode(java.nio.ByteBuffer.wrap(bytes));transcript=try{chars.toString().take(100000)}finally{if(chars.hasArray())chars.array().fill('\u0000')}
    }
    withContext(Dispatchers.Main.immediate){
     val previous=bitmap
     if(!alive.get()||!controller.access.publishIfCurrent {
      bitmap=pending;pending=null;text=transcript;pages=count;status="Verified original bytes · display rendering only"
     })throw CancellationException("Evidence access retired")
     previous?.let(::clearEvidenceBitmap)
    }
   }
  }catch(cancelled:CancellationException){throw cancelled
  }catch(_:Throwable){withContext(Dispatchers.Main.immediate){if(alive.get())controller.access.publishIfCurrent{status="Original unavailable, changed, unsupported, or exceeds the 32 MiB protected preview limit. Original bytes were preserved."}}}
  finally{pending?.let(::clearEvidenceBitmap)}
 }
 if(!controller.current())return
 EvidencePage(onBack=onClose) {
  Surface(Modifier.fillMaxSize().systemBarsPadding(),color=MaterialTheme.colorScheme.background){Column(Modifier.fillMaxSize().padding(16.dp)){
   Row(Modifier.fillMaxWidth()){Text("Original evidence",Modifier.weight(1f),style=MaterialTheme.typography.titleLarge);EvidenceSecurityControls();TextButton(onClick=onClose){Text("Close")}}
   Text(status,style=MaterialTheme.typography.bodySmall)
   bitmap?.let{Image(it.asImageBitmap(),"Original evidence content",Modifier.weight(1f).fillMaxWidth(),contentScale=ContentScale.Fit)}
   if(text.isNotEmpty())Text(text,Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()))
   if(pages>1)FlowRow{TextButton(enabled=page>0,onClick={page--}){Text("Previous page")};Text("${page+1} / $pages");TextButton(enabled=page+1<pages,onClick={page++}){Text("Next page")}}
  }}
 }
}
private fun clearEvidenceBitmap(bitmap:Bitmap){runCatching{if(bitmap.isMutable)bitmap.eraseColor(android.graphics.Color.TRANSPARENT)};runCatching{bitmap.recycle()}}
