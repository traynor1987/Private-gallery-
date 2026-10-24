package uk.co.traynor.privategallery.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
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
) {
 val context=LocalContext.current
 var dateDialog by remember(id){mutableStateOf<DatePickerDialog?>(null)}
 var timeDialog by remember(id){mutableStateOf<TimePickerDialog?>(null)}
 var thumbnail by remember(id){mutableStateOf<Bitmap?>(null)}
 DisposableEffect(id){onDispose {dateDialog?.dismiss();timeDialog?.dismiss()}}
 DisposableEffect(thumbnail){val old=thumbnail;onDispose {old?.recycle()}}
 val alive=remember(id){AtomicBoolean(true)}
 DisposableEffect(id){onDispose {alive.set(false)}}
 var item by remember(id){mutableStateOf<VaultItem?>(null)}
 var error by remember(id){mutableStateOf<String?>(null)}
 var busy by remember(id){mutableStateOf(false)}
 var extracted by remember(id){mutableStateOf<List<Pair<String,String>>>(emptyList())}
 fun refresh(){load(id){r->if(alive.get()){r.onSuccess {item=it}.onFailure {error="Media information unavailable. Unlock this vault and retry."}}}}
 LaunchedEffect(id){refresh()}
 LaunchedEffect(item?.id) {
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
  dateDialog=DatePickerDialog(context,{_,year,month,day->
   if(alive.get()) {timeDialog=TimePickerDialog(context,{_,hour,minute->
    if(alive.get()) {
     val local=LocalDateTime.of(year,month+1,day,hour,minute,0)
     val offsets=zone.rules.getValidOffsets(local)
     if(offsets.size!=1)error="This local time is missing or ambiguous because of daylight saving. Choose another time."
     else save(local.toInstant(offsets.single()).toEpochMilli(),zone.id)
    }
   },initial.hour,initial.minute,true);timeDialog?.show()}
  },initial.year,initial.monthValue-1,initial.dayOfMonth);dateDialog?.show()
 }
 val format=DateTimeFormatter.ofPattern("d MMM uuuu, HH:mm:ss XXX '['VV']'")
 fun date(time:Long?,zone:String?=null):String?=time?.let {Instant.ofEpochMilli(it).atZone(runCatching {ZoneId.of(zone)}.getOrDefault(ZoneId.systemDefault())).format(format)}
 AlertDialog(onDismissRequest=onClose,title={Text("Media details")},text={Column(Modifier.fillMaxWidth().heightIn(max=520.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
  if(item==null&&error==null)CircularProgressIndicator()
  thumbnail?.let {Image(it.asImageBitmap(),"Media thumbnail",Modifier.fillMaxWidth().height(120.dp))}
  item?.let {v ->
   val rows=buildList {
    add("Filename" to v.displayName);add("Format" to v.mimeType);add("File size" to "${v.plaintextSize} bytes")
    date(v.adjustedTakenAtEpochMillis ?: v.capturedAtEpochMillis,v.adjustedTimeZone)?.let {add("Date Taken${if(v.adjustedTakenAtEpochMillis!=null) " · Adjusted" else ""}" to it)}
    if(v.adjustedTakenAtEpochMillis!=null)date(v.capturedAtEpochMillis)?.let {add("Original Date Taken" to it)}
    date(v.addedAtEpochMillis ?: v.importedAtEpochMillis)?.let {add("Date Added" to it)}
    addAll(extracted)
    v.sourceUri?.takeIf {it.startsWith("capture:v1|")&&v.mimeType.startsWith("video/")}?.split('|')?.getOrNull(3)?.toLongOrNull()?.let {add("Duration" to "${it/1000.0} seconds")}
    add("Storage" to label)
    if(v.plaintextSha256.size==32)add("Integrity" to "SHA-256 recorded at verified import; not rechecked now")
   }
   rows.forEach {(name,value)->Column {Text(name,style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant);Text(value,style=MaterialTheme.typography.bodyMedium)}}
   TextButton(onClick={
    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Media details",rows.take(3).joinToString("\n"){"${it.first}: ${it.second}"}))
   }){Text("Copy filename and file information")}
   if(adjust!=null) {
    TextButton(onClick={chooseDate(v)},enabled=!busy){Text("Edit Date Taken")}
    if(v.adjustedTakenAtEpochMillis!=null)TextButton(onClick={save(null,null)},enabled=!busy){Text("Restore Original Date")}
   }
   Text("Dates can be adjusted and are not proof of capture time. This changes encrypted display metadata only, without a watermark or changes to the original media.",style=MaterialTheme.typography.bodySmall)
  }
  error?.let {Text(it,color=MaterialTheme.colorScheme.error)}
 }},confirmButton={TextButton(onClick=onClose){Text("Close")}})
}

internal fun extractImageDetails(bytes:ByteArray):List<Pair<String,String>> = buildList {
 val bounds=BitmapFactory.Options().apply {inJustDecodeBounds=true}
 BitmapFactory.decodeByteArray(bytes,0,bytes.size,bounds)
 if(bounds.outWidth>0&&bounds.outHeight>0)add("Dimensions" to "${bounds.outWidth} × ${bounds.outHeight}")
 runCatching {
  val exif=ExifInterface(ByteArrayInputStream(bytes))
  listOf("Camera manufacturer" to "Make","Camera model" to "Model","Lens" to "LensModel","ISO" to "PhotographicSensitivity","Exposure time" to "ExposureTime","Aperture" to "FNumber","Focal length" to "FocalLength").forEach {(label,tag)->exif.getAttribute(tag)?.takeIf {it.isNotBlank()}?.let {add(label to it)}}
  val coordinates=FloatArray(2)
  if(exif.getLatLong(coordinates))add("GPS location" to "${coordinates[0]}, ${coordinates[1]}")
 }
}
