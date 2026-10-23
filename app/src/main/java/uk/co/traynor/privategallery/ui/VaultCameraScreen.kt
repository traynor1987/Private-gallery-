package uk.co.traynor.privategallery.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.video.Quality
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uk.co.traynor.privategallery.MainActivity
import uk.co.traynor.privategallery.core.camera.*
import java.text.DateFormat
import java.util.Date

@Composable
internal fun VaultCameraScreen(controller:VaultCameraController,onExit:()->Unit) {
    val context=LocalContext.current
    val activity=androidx.activity.compose.LocalActivity.current as? MainActivity
    val owner=LocalLifecycleOwner.current
    val scope=rememberCoroutineScope()
    var active by remember {mutableStateOf(false)}
    var black by remember {mutableStateOf(false)}
    var video by remember {mutableStateOf(false)}
    var front by remember {mutableStateOf(false)}
    var quality by remember {mutableStateOf(Quality.FHD)}
    var audio by remember {mutableStateOf(false)}
    var audioReady by remember {mutableStateOf(false)}
    var audioSaving by remember {mutableStateOf(false)}
    var captureAudio by remember {mutableStateOf(false)}
    var haptics by remember {mutableStateOf(false)}
    var permissionMessage by remember {mutableStateOf("")}
    var details by remember {mutableStateOf(false)}
    var recoveryMessage by remember {mutableStateOf("")}
    var recovering by remember {mutableStateOf(false)}
    val preview=remember {PreviewView(context).apply {implementationMode=PreviewView.ImplementationMode.COMPATIBLE;scaleType=PreviewView.ScaleType.FIT_CENTER}}
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {grants ->
        permissionMessage=if(grants.values.any {!it}) "Permission denied. Silent video needs camera permission only." else "Permission granted. Tap Open camera."
    }
    LaunchedEffect(controller){audio=withContext(Dispatchers.IO){runCatching {controller.access.audio()}.getOrDefault(false)};audioReady=true}
    DisposableEffect(controller){onDispose {activity?.cameraVolumeAction=null;controller.revoke()}}
    val input by rememberUpdatedState<()->Unit>({controller.input(video,captureAudio)})
    DisposableEffect(active,black){if(active&&black)activity?.cameraVolumeAction={input()};onDispose {activity?.cameraVolumeAction=null}}
    LaunchedEffect(active,front,video,quality){if(active)controller.bind(owner,if(black)null else preview,video,front,quality)}
    BackHandler {onExit()}
    fun open() {
        if(!audioReady||audioSaving)return
        val required=mutableListOf(Manifest.permission.CAMERA)
        if(video&&audio)required+=Manifest.permission.RECORD_AUDIO
        val missing=required.filter {ContextCompat.checkSelfPermission(context,it)!=PackageManager.PERMISSION_GRANTED}
        if(missing.isNotEmpty()){permission.launch(missing.toTypedArray());return}
        controller.haptics=haptics;captureAudio=audio
        if(controller.enter())active=true
    }
    fun recover() {
        if(recovering)return
        recovering=true
        scope.launch {
            val message=withContext(Dispatchers.IO) {
                var saved=0;var pending=0
                controller.access.journal.entries().forEach {id ->
                    runCatching {controller.access.recover(id).use {ticket ->
                        if(ticket.hash==null)controller.access.journal.seal(ticket,false,0)
                        else if(ticket.plain.exists())controller.access.journal.seal(ticket,ticket.complete,ticket.duration)
                        if(ticket.complete){controller.access.import(ticket);saved++}else pending++
                    }}.onFailure {pending++}
                }
                "$saved saved to this vault · $pending incomplete or awaiting recovery"
            }
            if(controller.access.allowed())recoveryMessage=message
            recovering=false
        }
    }
    Surface(Modifier.fillMaxSize(),color=if(active&&black)Color.Black else MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            Row(Modifier.fillMaxWidth().heightIn(min=48.dp).padding(horizontal=8.dp),verticalAlignment=Alignment.CenterVertically) {
                IconButton(onClick=onExit){Icon(Icons.Default.Close,"Stop and exit camera",tint=if(active&&black)Color.Gray else LocalContentColor.current)}
                if(!active||!black)Text(controller.access.label,Modifier.weight(1f),style=MaterialTheme.typography.titleMedium)
                else Spacer(Modifier.weight(1f))
                if(active)Text(if(controller.recordingNow)"REC ${controller.elapsed}s" else controller.status,
                    Modifier.weight(1f),color=if(black)Color.DarkGray else LocalContentColor.current,style=MaterialTheme.typography.labelSmall)
            }
            if(!active)Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Text("Vault Camera",style=MaterialTheme.typography.headlineSmall)
                Text("Photos and videos stay in ${controller.access.label}. Locking or leaving the app stops capture.")
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected=!black,onClick={black=false},label={Text("Normal Camera")})
                    FilterChip(selected=black,onClick={black=true},label={Text("Black-Screen Camera")})
                }
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected=!video,onClick={video=false},label={Text("Photo")})
                    FilterChip(selected=video,onClick={video=true},label={Text("Video")})
                }
                if(video) {
                    Row(verticalAlignment=Alignment.CenterVertically){Text("Record audio",Modifier.weight(1f));Switch(checked=audio,enabled=audioReady&&!audioSaving,onCheckedChange={enabled->
                        audioSaving=true
                        scope.launch {val result=withContext(Dispatchers.IO){runCatching {controller.access.audio(enabled)}};if(result.isSuccess)audio=enabled else permissionMessage="Audio preference could not be saved.";audioSaving=false}
                    })}
                    Text(if(audio)"Audio On · microphone permission required" else "Audio Off · no microphone permission needed",style=MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){listOf("HD" to Quality.HD,"Full HD" to Quality.FHD,"4K" to Quality.UHD).forEach {(label,value)->FilterChip(selected=quality==value,onClick={quality=value},label={Text(label)})}}
                    Text("Uses the nearest supported quality. No time limit; recording stops at the vault's 2 GiB file limit or storage reserve.",style=MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick={front=!front}){Text(if(front)"Front camera" else "Rear camera")}
                if(black) {
                    Text("No preview. Tap the black area or press Volume Down to ${if(video)"start/stop video" else "take a photo"}. Swipe navigation does not capture. Android privacy indicators stay visible.")
                    Text("Video enters ready, not recording. REC and vibration (if enabled) confirm recording. Close or Android Back stops and exits.",style=MaterialTheme.typography.bodySmall)
                }
                Row(verticalAlignment=Alignment.CenterVertically){Text("Capture vibration",Modifier.weight(1f));Switch(checked=haptics,onCheckedChange={haptics=it})}
                if(permissionMessage.isNotEmpty())Text(permissionMessage)
                Button(onClick=::open,enabled=!recovering&&audioReady&&!audioSaving,modifier=Modifier.fillMaxWidth()){Text("Open camera")}
                OutlinedButton(onClick=::recover,enabled=!recovering){Text(if(recovering)"Recovering…" else "Recover interrupted captures")}
                TextButton(onClick={details=true}){Text("Recording details")}
                if(recoveryMessage.isNotEmpty())Text(recoveryMessage)
                Text("CameraX temporarily writes in private app storage. Verified encrypted saving removes that temporary file. A crash may leave private temporary bytes until authenticated recovery; some interrupted videos cannot be played.",style=MaterialTheme.typography.bodySmall)
            } else {
                if(black)Box(Modifier.weight(1f).fillMaxWidth().background(Color.Black)
                    .semantics {contentDescription=if(video)"Black video capture" else "Black photo capture";stateDescription=if(controller.recordingNow)"Recording" else controller.status
                        onClick(if(controller.recordingNow)"Stop recording" else if(video)"Start recording" else "Take photo"){input();true}}
                    .pointerInput(controller){detectTapGestures(onTap={input()})})
                else {
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.Center) {
                        FilterChip(selected=!video,onClick={video=false},enabled=!controller.busy,label={Text("Photo")})
                        Spacer(Modifier.width(12.dp))
                        FilterChip(selected=video,onClick={video=true},enabled=!controller.busy,label={Text("Video")})
                    }
                    AndroidView(factory={preview},modifier=Modifier.weight(1f).fillMaxWidth())
                    Row(Modifier.fillMaxWidth().padding(horizontal=16.dp),verticalAlignment=Alignment.CenterVertically) {
                        Text("Zoom",style=MaterialTheme.typography.labelMedium)
                        Slider(value=controller.zoom,onValueChange=controller::changeZoom,modifier=Modifier.weight(1f),enabled=controller.bound)
                    }
                    Row(Modifier.fillMaxWidth().padding(8.dp),horizontalArrangement=Arrangement.SpaceEvenly,verticalAlignment=Alignment.CenterVertically) {
                        IconButton(onClick={front=!front},enabled=!controller.busy){Icon(Icons.Default.Cameraswitch,"Switch camera")}
                        Button(onClick={input()},enabled=controller.bound&&!controller.saving){Text(if(video){if(controller.recordingNow)"Stop" else "Record"}else "Capture")}
                        IconButton(onClick=controller::toggleFlash,enabled=controller.hasFlash&&!controller.busy){Icon(if(controller.flash)Icons.Default.FlashOn else Icons.Default.FlashOff,"Flash")}
                    }
                }
            }
        }
    }
    if(details)CaptureDetails(controller.access,onDismiss={details=false})
}

@Composable
private fun CaptureDetails(access:VaultCaptureAccess,onDismiss:()->Unit) {
    var text by remember {mutableStateOf("Loading…")}
    var integrity by remember {mutableStateOf("Not rechecked this session")}
    var verifying by remember {mutableStateOf(false)}
    val scope=rememberCoroutineScope()
    LaunchedEffect(access) {
        val result=withContext(Dispatchers.IO){runCatching {
            val originals=access.recordings().joinToString("\n\n") {item ->
                val parts=item.sourceUri!!.split('|');val date=DateFormat.getDateTimeInstance().format(Date(parts[2].toLong()))
                "Original capture · $date\nDuration: ${parts[3].toLong()/1000}s · ${item.plaintextSize} bytes\nSHA-256 recorded at verified import:\n${item.plaintextSha256.joinToString(""){"%02x".format(it)}}"
            }
            val pending=access.journal.entries().joinToString("\n") {id ->runCatching {access.open(id).use {t->
                "${DateFormat.getDateTimeInstance().format(Date(t.timestamp))} · ${if(t.complete)"Complete, awaiting import" else "Incomplete / recovery required"} · ${t.size} encrypted bytes"
            }}.getOrDefault("Capture requires recovery; original bytes retained")}
            listOf(originals,pending).filter {it.isNotBlank()}.joinToString("\n\n").ifBlank {"No camera recordings in this vault."}
        }.getOrDefault("Unlock this vault to view recording details.")}
        if(access.allowed())text=result
    }
    AlertDialog(onDismissRequest=onDismiss,title={Text("Recording details")},text={Column(Modifier.heightIn(max=420.dp).verticalScroll(rememberScrollState())) {
        Text(text,style=MaterialTheme.typography.bodySmall)
        Text(integrity,style=MaterialTheme.typography.labelMedium)
        TextButton(enabled=!verifying,onClick={verifying=true;scope.launch {
            val result=withContext(Dispatchers.IO){runCatching {access.verifyRecordings()}}
            if(access.allowed())integrity=result.fold({"Verified $it originals: encrypted data and SHA-256 match"},{"Verification did not complete. Media retained."})
            verifying=false
        }}){Text(if(verifying)"Verifying…" else "Verify integrity")}
        Spacer(Modifier.height(16.dp));Text("Hashes detect changes when checked against the original. They do not prove who recorded the media or legal admissibility. Edited copies are separate items.",style=MaterialTheme.typography.bodySmall)
    }},confirmButton={TextButton(onClick=onDismiss){Text("Done")}})
}
