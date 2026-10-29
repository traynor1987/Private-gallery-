package uk.co.traynor.privategallery.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import uk.co.traynor.privategallery.core.privateapps.*

/** This composable must be keyed by editor and URL. Requests are deliberately not saveable. */
@Composable
internal fun PrivateAppIconEditor(controller:PrivateAppsController,value:String,name:String,onChange:(String)->Unit,onBusy:(Boolean)->Unit,enabled:Boolean=true) {
 val context=LocalContext.current
 val scope=rememberCoroutineScope()
 val requests=remember(controller){PrivateAppIconSource.Requests(controller::isCurrent)}
 val changed by rememberUpdatedState(onChange)
 val busyChanged by rememberUpdatedState(onBusy)
 var pending by remember{mutableStateOf<Long?>(null)}
 var busy by remember{mutableStateOf(false)}
 var status by remember{mutableStateOf("")}
 DisposableEffect(requests){onDispose{requests.close();pending=null}}
 fun finish(ticket:Long,result:Result<String>) {
  if(!requests.current(ticket))return
  requests.cancel()
  busy=false;busyChanged(false)
  result.onSuccess{changed(it);status="Icon ready. Save to apply."}
   .onFailure{status="Could not use that image. Choose an image up to 8 MB and 32 megapixels."}
 }
 fun consume(ticket:Long,bytes:ByteArray?) {
  if(bytes==null){if(requests.current(ticket)){requests.cancel();busy=false;busyChanged(false)};return}
  if(!requests.current(ticket)){bytes.fill(0);return}
  scope.launch {
   val result=withContext(Dispatchers.IO){runCatching{
    check(requests.current(ticket));PrivateAppIcons.normalizeSelected(bytes).also{require(it.isNotEmpty());check(requests.current(ticket))}
   }}
   finish(ticket,result)
  }.invokeOnCompletion{bytes.fill(0)}
 }
 val picker=rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) {uri ->
  val ticket=pending;pending=null
  if(ticket!=null && requests.current(ticket)) {
   if(uri==null){requests.cancel();busy=false;busyChanged(false)}
   else scope.launch {
    val result=withContext(Dispatchers.IO){runCatching {
     require(uri.scheme=="content")
     check(requests.current(ticket))
     val bytes=PrivateAppIconSource.read(checkNotNull(context.contentResolver.openInputStream(uri))){requests.current(ticket) && isActive}
     try {PrivateAppIcons.normalizeSelected(bytes).also{require(it.isNotEmpty());check(requests.current(ticket))}}finally{bytes.fill(0)}
    }}
    finish(ticket,result)
   }
  }
 }
 Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
  PrivateAppIcon(value,name)
  Row {
   TextButton(enabled=enabled && !busy,onClick={
    if(!controller.isCurrent())return@TextButton
    val ticket=requests.begin();busy=true;busyChanged(true);status=""
    try {
     val localPicker=controller.requestIcon
     if(localPicker!=null)localPicker {bytes ->consume(ticket,bytes)}
     else {
      // Hidden never inherits the Primary Activity's resolver or system picker authority.
      check(controller.owner==PrivateAppStore.Owner.PRIMARY)
      pending=ticket;picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
     }
    }catch(_:Exception){pending=null;finish(ticket,Result.failure(IllegalStateException("Image selection unavailable")))}
   }){Text(if(value.isEmpty())"Choose icon" else "Replace icon")}
   if(value.isNotEmpty() || busy)TextButton(onClick={requests.cancel();pending=null;busy=false;busyChanged(false);changed("");status="Initial-letter icon selected. Save to apply."}){Text("Clear icon")}
  }
  if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
  Text(if(controller.owner==PrivateAppStore.Owner.PRIMARY && controller.requestIcon==null)"Choose a photo to store a small icon in this vault. Locking cancels the selection; unlock and retry if your timeout expires." else "Choose an image from this vault. Only a small icon is saved in its encrypted app catalogue.",style=MaterialTheme.typography.bodySmall)
  if(status.isNotEmpty())Text(status,style=MaterialTheme.typography.bodySmall)
 }
}
