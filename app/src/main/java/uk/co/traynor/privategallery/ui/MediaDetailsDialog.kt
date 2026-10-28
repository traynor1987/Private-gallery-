package uk.co.traynor.privategallery.ui

import uk.co.traynor.privategallery.ui.GalleryAlertDialog as AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import android.graphics.BitmapFactory
import android.media.ExifInterface
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicBoolean
import uk.co.traynor.privategallery.core.vault.VaultItem

/** Authenticated caller owns lifetime. No saved state, public files or original media mutation. */
@Composable
internal fun MediaDetailsDialog(
 id:String,label:String,
 load:(String,(Result<VaultItem>)->Unit)->Unit,
 adjust:((String,Long?,String?,(Result<Unit>)->Unit)->Unit)?,
 loadImage:((String,(Result<ByteArray>)->Unit)->Unit)?,
 changed:()->Unit,onClose:()->Unit,
 loadVideo:((String,()->Boolean,(Result<uk.co.traynor.privategallery.core.vault.ProtectedVideoSession>)->Unit)->Unit)?=null,
 galleryUri:android.net.Uri?=null,

) {
 val context=LocalContext.current
 val ownerActivity=androidx.activity.compose.LocalActivity.current
 val scope=rememberCoroutineScope()
 var editingDate by remember(id){mutableStateOf<ZonedDateTime?>(null)}
 var thumbnail by remember(id){mutableStateOf<Bitmap?>(null)}
 DisposableEffect(thumbnail){val old=thumbnail;onDispose {old?.recycle()}}
 val alive=remember(id){AtomicBoolean(true)}
 val lifecycleOwner=androidx.lifecycle.compose.LocalLifecycleOwner.current
 val closeCurrent by rememberUpdatedState(onClose)
 DisposableEffect(lifecycleOwner,id) {
  val observer=androidx.lifecycle.LifecycleEventObserver {_,event->
   if(event==androidx.lifecycle.Lifecycle.Event.ON_STOP) {
    alive.set(false)
    editingDate=null
    closeCurrent()
   }
  }
  lifecycleOwner.lifecycle.addObserver(observer)
  onDispose {lifecycleOwner.lifecycle.removeObserver(observer);alive.set(false)}
 }

 DisposableEffect(id){onDispose {alive.set(false)}}
 var item by remember(id){mutableStateOf<VaultItem?>(null)}
 var error by remember(id){mutableStateOf<String?>(null)}
 var busy by remember(id){mutableStateOf(false)}
 var extracted by remember(id){mutableStateOf<List<Pair<String,String>>>(emptyList())}
 fun refresh(){load(id){r->if(alive.get()){r.onSuccess {item=it}.onFailure {error="Media information unavailable. Unlock this vault and retry."}}}}
 LaunchedEffect(id){refresh()}
 LaunchedEffect(item?.id) {
  val media=item
  if(media?.mimeType?.startsWith("video/")==true)loadVideo?.invoke(id,{!alive.get()}){result->
   val session=result.getOrNull()
   if(session!=null) {
    if(!alive.get())session.close()
    else scope.launch {
     try {
      val fields=withContext(Dispatchers.IO){uk.co.traynor.privategallery.core.media.ProtectedMetadataReader.read(session,media.plaintextSize,alive::get)}
      if(alive.get())extracted=fields
     }catch(_:Exception){if(alive.get())error="Some video metadata could not be read. Original media is unchanged."}
     finally {session.close()}
    }.invokeOnCompletion {session.close()}
   }
  }

  if(media?.mimeType?.startsWith("video/")==true&&loadVideo==null&&galleryUri!=null) {
   val fields=withContext(Dispatchers.IO){runCatching {
    val retriever=android.media.MediaMetadataRetriever()
    try {retriever.setDataSource(context,galleryUri);uk.co.traynor.privategallery.core.media.ProtectedMetadataReader.fields(retriever)}finally {retriever.release()}
   }}
   if(alive.get())fields.onSuccess {extracted=it}.onFailure {error="Some video metadata could not be read."}
  }
  if(item?.mimeType?.startsWith("image/")==true) loadImage?.invoke(id){r ->
   val bytes=r.getOrNull()
   try {if(bytes!=null&&alive.get()) {
    extracted=extractImageDetails(bytes)
    val bounds=BitmapFactory.Options().apply {inJustDecodeBounds=true};BitmapFactory.decodeByteArray(bytes,0,bytes.size,bounds)
    val options=BitmapFactory.Options();var sample=1
    while(maxOf(bounds.outWidth,bounds.outHeight)/sample>256)sample*=2
    options.inSampleSize=sample
    thumbnail=BitmapFactory.decodeByteArray(bytes,0,bytes.size,options)
   }} finally {bytes?.fill(0)}
  }
 }
 fun save(timestamp:Long?,zone:String?) {
  if(busy)return
  busy=true;error=null
  adjust?.invoke(id,timestamp,zone){r->if(alive.get()){busy=false;r.onSuccess {refresh();changed()}.onFailure {error="The date could not be saved. Original media is unchanged."}}}
 }
 fun chooseDate(value:VaultItem) {
  val zone=runCatching {ZoneId.of(value.adjustedTimeZone)}.getOrDefault(ZoneId.systemDefault())
  val initial=Instant.ofEpochMilli(value.adjustedTakenAtEpochMillis ?: value.capturedAtEpochMillis ?: System.currentTimeMillis()).atZone(zone)
  editingDate=initial

 }
 editingDate?.let {initial ->
  val secure=android.view.WindowManager.LayoutParams.FLAG_SECURE
  val ownerFlags=ownerActivity?.window?.attributes?.flags ?: secure
  MediaDisplayDateTimeDialog(initial,
   properties=DialogProperties(securePolicy=if(ownerFlags and secure != 0)SecureFlagPolicy.SecureOn else SecureFlagPolicy.SecureOff),
   onDismiss={editingDate=null},onSelect={selected,hour,minute ->
    editingDate=null
    if(alive.get())runCatching {uk.co.traynor.privategallery.core.media.DisplayDateTime.resolve(selected.year,selected.monthValue,selected.dayOfMonth,hour,minute,0,initial.zone.id)}
     .onSuccess {save(it,initial.zone.id)}.onFailure {error="This date/time is invalid, missing or ambiguous because of daylight saving. Choose another time."}
   })
 }
 val format=DateTimeFormatter.ofPattern("d MMM uuuu, HH:mm:ss XXX '['VV']'")
 fun date(time:Long?,zone:String?=null):String?=time?.takeIf {uk.co.traynor.privategallery.core.ui.MediaOrder.valid(it)>0}?.let {Instant.ofEpochMilli(it).atZone(runCatching {ZoneId.of(zone)}.getOrDefault(ZoneId.systemDefault())).format(format)}
 AlertDialog(scrollBody = false, onDismissRequest=onClose,title={Text("Media details")},text={Column(Modifier.fillMaxWidth().heightIn(max=520.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
  if(item==null&&error==null)CircularProgressIndicator()
  thumbnail?.let {Image(it.asImageBitmap(),"Media thumbnail",Modifier.fillMaxWidth().height(120.dp))}
  item?.let {v ->
   val rows=buildList {
    add("Filename" to v.displayName);add("Format" to v.mimeType);if(v.plaintextSize>=0)add("File size" to "${v.plaintextSize} bytes")
    date(v.adjustedTakenAtEpochMillis ?: v.capturedAtEpochMillis,v.adjustedTimeZone)?.let {add("Date Taken${if(v.adjustedTakenAtEpochMillis!=null) " · Adjusted" else ""}" to it)}
    if(v.adjustedTakenAtEpochMillis!=null)date(v.capturedAtEpochMillis)?.let {add("Original Date Taken" to it)}
    date(v.addedAtEpochMillis ?: v.importedAtEpochMillis)?.let {add("Date Added" to it)}
    date(v.modifiedAtEpochMillis)?.let {add("Last Modified" to it)}
    addAll(extracted)
    v.sourceUri?.takeIf {it.startsWith("capture:v1|")&&v.mimeType.startsWith("video/")&&extracted.none {field->field.first=="Duration"}}?.split('|')?.getOrNull(3)?.toLongOrNull()?.let {add("Duration" to "${it/1000.0} seconds")}
    add("Storage" to label)
    if(v.plaintextSha256.size==32)add("Integrity" to "SHA-256 recorded at verified import; not rechecked now")
   }
   rows.forEach {(name,value)->Column {Text(name,style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant);Text(value,style=MaterialTheme.typography.bodyMedium)}}
   TextButton(onClick={
    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Media details",rows.filter {it.first in setOf("Filename","Format","File size","Dimensions","Resolution")}.joinToString("\n"){"${it.first}: ${it.second}"}))
   }){Text("Copy filename and file information")}
   if(adjust!=null) {
    TextButton(onClick={chooseDate(v)},enabled=!busy){Text("Edit Date Taken")}
    if(v.adjustedTakenAtEpochMillis!=null)TextButton(onClick={save(null,null)},enabled=!busy){Text("Restore Original Date")}
   }
   Text(if(adjust!=null) "Dates can be adjusted and are not proof of capture time. This changes encrypted display metadata only, without a watermark or changes to the original media." else "Import this media to a vault to adjust its protected display date. The original phone media is unchanged.",style=MaterialTheme.typography.bodySmall)
  }
  error?.let {Text(it,color=MaterialTheme.colorScheme.error)}
 }},confirmButton={TextButton(onClick=onClose){Text("Close")}})
}

/** Two explicit, themed stages. DatePicker's input mode fits narrow windows; time fields
 * use ordinary scalable typography rather than fixed-size clock digits. Nothing is saved on cancel. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MediaDisplayDateTimeDialog(
 initial:ZonedDateTime,
 properties:DialogProperties=DialogProperties(),
 onDismiss:()->Unit,
 onSelect:(LocalDate,Int,Int)->Unit,
) {
 var dateStage by remember(initial){mutableStateOf(true)}
 var selectedDate by remember(initial){mutableStateOf(initial.toLocalDate())}
 if(dateStage) {
  val state=rememberDatePickerState(
   initialSelectedDateMillis=initial.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
   initialDisplayMode=DisplayMode.Input,yearRange=1..9999)
  AlertDialog(onDismissRequest=onDismiss,properties=properties,title={Text("Choose Date Taken")},
   text={DatePicker(state,Modifier.fillMaxWidth().testTag("media-date-picker"),title=null,headline=null,showModeToggle=false)},
   confirmButton={TextButton(enabled=state.selectedDateMillis!=null,onClick={
    state.selectedDateMillis?.let {selectedDate=Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate();dateStage=false}
   }){Text("Next")}},dismissButton={TextButton(onClick=onDismiss){Text("Cancel")}})
 } else {
  var hour by remember(initial){mutableStateOf(initial.hour.toString().padStart(2,'0'))}
  var minute by remember(initial){mutableStateOf(initial.minute.toString().padStart(2,'0'))}
  val hourValue=hour.toIntOrNull()?.takeIf {it in 0..23}
  val minuteValue=minute.toIntOrNull()?.takeIf {it in 0..59}
  val focus=LocalFocusManager.current
  var submitted by remember(initial){mutableStateOf(false)}
  fun submit(){if(!submitted && hourValue!=null && minuteValue!=null){submitted=true;focus.clearFocus();onSelect(selectedDate,hourValue,minuteValue)}}
  AlertDialog(onDismissRequest=onDismiss,properties=properties,title={Text("Choose time")},
   text={Column(Modifier.testTag("media-time-picker"),verticalArrangement=Arrangement.spacedBy(12.dp)) {
    Text("$selectedDate · ${initial.zone.id}")
    Text("24-hour time · hour 00–23, minute 00–59")
    OutlinedTextField(hour,{hour=it.filter(Char::isDigit).take(2)},Modifier.fillMaxWidth().testTag("media-hour"),
     label={Text("Hour")},singleLine=true,isError=hourValue==null,
     keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number,imeAction=ImeAction.Next),
     keyboardActions=KeyboardActions(onNext={focus.moveFocus(FocusDirection.Down)}))
    OutlinedTextField(minute,{minute=it.filter(Char::isDigit).take(2)},Modifier.fillMaxWidth().testTag("media-minute"),
     label={Text("Minute")},singleLine=true,isError=minuteValue==null,
     keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number,imeAction=ImeAction.Done),
     keyboardActions=KeyboardActions(onDone={submit()}))
    if(hourValue==null || minuteValue==null)Text("Enter a valid 24-hour time.",color=MaterialTheme.colorScheme.error)
   }},confirmButton={TextButton(enabled=!submitted && hourValue!=null && minuteValue!=null,onClick=::submit){Text("Save date")}},dismissButton={TextButton(onClick=onDismiss){Text("Cancel")}})
 }
}

internal fun extractImageDetails(bytes:ByteArray):List<Pair<String,String>> = buildList {
 val bounds=BitmapFactory.Options().apply {inJustDecodeBounds=true}
 BitmapFactory.decodeByteArray(bytes,0,bytes.size,bounds)
 if(bounds.outWidth>0&&bounds.outHeight>0)add("Dimensions" to "${bounds.outWidth} × ${bounds.outHeight}")
 runCatching {
  val exif=ExifInterface(ByteArrayInputStream(bytes))
  listOf("Camera manufacturer" to "Make","Camera model" to "Model","Lens" to "LensModel","ISO" to "PhotographicSensitivity","Exposure time" to "ExposureTime","Aperture" to "FNumber","Focal length" to "FocalLength").forEach {(label,tag)->(exif.getAttribute(tag) ?: if(tag=="PhotographicSensitivity")exif.getAttribute("ISOSpeedRatings") else null)?.takeIf {it.isNotBlank()}?.let {add(label to it)}}
  val coordinates=FloatArray(2)
  if(exif.getLatLong(coordinates))add("GPS location" to "${coordinates[0]}, ${coordinates[1]}")
 }
}
