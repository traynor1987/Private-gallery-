package uk.co.traynor.privategallery.ui

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.media.ExifInterface
import androidx.core.content.FileProvider
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import uk.co.traynor.privategallery.core.media.JpegPrivacy
import uk.co.traynor.privategallery.core.media.PngPrivacy
import uk.co.traynor.privategallery.core.vault.*

/** Only random exported copies are granted. Vault paths and keys never reach FileProvider. */
@Composable
internal fun PrivacyShareDialog(id:String,
 load:(String,(Result<VaultItem>)->Unit)->Unit,
 read:(String,(Result<ByteArray>)->Unit)->Unit,onClose:()->Unit) {
 val context=LocalContext.current
 val scope=rememberCoroutineScope()
 val shareToken=remember(id){UUID.randomUUID().toString()}
 val alive=remember(id){AtomicBoolean(true)}
 val lifecycleOwner=androidx.lifecycle.compose.LocalLifecycleOwner.current
 val closeCurrent by rememberUpdatedState(onClose)
 DisposableEffect(lifecycleOwner,id) {
  val observer=androidx.lifecycle.LifecycleEventObserver {_,event->
   if(event==androidx.lifecycle.Lifecycle.Event.ON_STOP) {
    alive.set(false)
    closeCurrent()
   }
  }
  lifecycleOwner.lifecycle.addObserver(observer)
  onDispose {lifecycleOwner.lifecycle.removeObserver(observer);alive.set(false)}
 }

 DisposableEffect(id){onDispose {alive.set(false)}}
 var item by remember(id){mutableStateOf<VaultItem?>(null)}
 val preferences=remember {context.getSharedPreferences("share-privacy",Context.MODE_PRIVATE)}
 var clean by remember {mutableStateOf(preferences.getBoolean("remove-location",true))}
 var adjusted by remember {mutableStateOf(false)}
 var busy by remember {mutableStateOf(false)}
 var error by remember {mutableStateOf<String?>(null)}
 LaunchedEffect(id){load(id){r->if(alive.get())r.onSuccess {item=it}.onFailure {error="Unlock this vault and retry."}}}
 fun share() {
  val media=item ?: return
  busy=true;error=null
  read(id){r->
   val original=r.getOrNull()
   if(original==null){if(alive.get()){busy=false;error="Media could not be read safely."};return@read}
   if(!alive.get()){original.fill(0);return@read}
   scope.launch {
    var staged:File?=null
    try {
     withContext(Dispatchers.IO) {
      VaultEgressPolicy.requireAllowed(media,VaultEgress.SHARE)
      check(media.mimeType in setOf("image/jpeg","image/png")) {"Verified sharing currently supports JPEG and non-animated PNG. Use the existing restore flow for other formats."}
      check(original.size<=64*1024*1024)
      val exif=ExifInterface(ByteArrayInputStream(original))
      val orientation=exif.getAttributeInt(ExifInterface.TAG_ORIENTATION,1).coerceIn(1,8)
      val date=if(clean&&adjusted)media.adjustedTakenAtEpochMillis?.let {Instant.ofEpochMilli(it).atZone(ZoneId.of(media.adjustedTimeZone ?: ZoneId.systemDefault().id)).format(DateTimeFormatter.ofPattern("uuuu:MM:dd HH:mm:ss"))} else null
      val output=if(clean){if(media.mimeType=="image/png")PngPrivacy.clean(original,orientation,date) else JpegPrivacy.clean(original,orientation,date)} else original.copyOf()
      try {
       if(clean) {
        val checked=ExifInterface(ByteArrayInputStream(output));val latLong=FloatArray(2)
        check(!checked.getLatLong(latLong)) {"Location sanitisation could not be verified"}
        check(checked.getAttribute("Make")==null && checked.getAttribute("Model")==null)
       }
       check(alive.get())
       val dir=File(context.cacheDir,"share-copies").apply {check(isDirectory||mkdirs())}
       cleanupShareCopies(context)
       staged=File(dir,"${shareToken}.${if(media.mimeType=="image/png")"png" else "jpg"}")
       java.io.FileOutputStream(checkNotNull(staged)).use {it.write(output);it.fd.sync()}
       uk.co.traynor.privategallery.core.media.ShareCleanupJob.schedule(context)
      } finally {output.fill(0)}
     }
     check(alive.get())
     val file=checkNotNull(staged)
     val uri=FileProvider.getUriForFile(context,"${context.packageName}.fileprovider",file)
     val intent=Intent(Intent.ACTION_SEND).apply {
      type=media.mimeType;putExtra(Intent.EXTRA_STREAM,uri)
      clipData=ClipData.newRawUri("Exported image",uri)
      addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
     }
     context.startActivity(Intent.createChooser(intent,"Share exported copy"))
     staged=null // Lease survives chooser/auto-lock; private copy expires and is swept on next app entry.
     android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
      runCatching {context.revokeUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);file.delete()}
     },15*60*1000L)
     onClose()
    } catch(_:Exception){if(alive.get()){busy=false;error="This copy could not be shared safely. The original is unchanged."}}
    finally {original.fill(0);staged?.delete()}
   }.invokeOnCompletion {original.fill(0)}
  }
 }
 AlertDialog(onDismissRequest={if(!busy)onClose()},title={Text("Review share")},text={Column(Modifier.heightIn(max=420.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
  item?.let {v->
   Text("Destination: Android share sheet")
   Text("Filename: $shareToken.${if(v.mimeType=="image/png")"png" else "jpg"}")
   Text("Format: ${v.mimeType} · ${if(v.plaintextSize>=0)"approximately ${v.plaintextSize} bytes" else "size unavailable until prepared"}")
   Row {Text("Remove GPS and optional metadata",Modifier.weight(1f));Switch(clean,{clean=it;preferences.edit().putBoolean("remove-location",it).apply()},enabled=!busy)}
   Text(if(clean)"Clean copy: EXIF, XMP, IPTC, camera/device information and comments removed. Only display orientation and the optional adjusted date are written back. Colour profiles are removed; colour appearance may change." else "Original metadata retained, including any location or device information.")
   if(v.adjustedTakenAtEpochMillis!=null)Row {Text("Apply adjusted date",Modifier.weight(1f));Switch(adjusted,{adjusted=it},enabled=clean&&!busy)}
   Text("Dates are editable, not proof of capture time. EXIF dates do not change message timestamps or cloud records. Visual location clues and receiving-service records remain.",style=MaterialTheme.typography.bodySmall)
   if(v.mimeType !in setOf("image/jpeg","image/png"))Text("Verified privacy export is currently available for JPEG and non-animated PNG.",color=MaterialTheme.colorScheme.error)
  }
  error?.let {Text(it,color=MaterialTheme.colorScheme.error)}
 }},confirmButton={TextButton(onClick=::share,enabled=!busy&&item?.mimeType in setOf("image/jpeg","image/png")){Text(if(busy)"Preparing…" else "Share copy")}},dismissButton={TextButton(onClick=onClose,enabled=!busy){Text("Cancel")}})
}

internal fun cleanupShareCopies(context:Context) {
 val cutoff=System.currentTimeMillis()-15*60*1000L
 File(context.cacheDir,"share-copies").listFiles()?.filter {it.isFile&&it.lastModified()<=cutoff}?.forEach {file->
  runCatching {val uri=FileProvider.getUriForFile(context,"${context.packageName}.fileprovider",file);context.revokeUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION)}
  file.delete()
 }
}
