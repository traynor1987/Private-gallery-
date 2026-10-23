package uk.co.traynor.privategallery.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.video.Quality
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
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

@OptIn(ExperimentalLayoutApi::class)
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
                if(active&&black)Text(if(controller.recordingNow)"REC ${controller.elapsed}s" else controller.status,
                    Modifier.weight(1f),color=Color.DarkGray,style=MaterialTheme.typography.labelSmall)
            }
            if(!active)Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                GalleryPageTitle("Capture to this vault", "Vault Camera")
                Text("Photos and videos stay in ${controller.access.label}. Locking or leaving the app stops capture.",
                    style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                GalleryCard(Modifier.fillMaxWidth()) {
                    GalleryCardHeading("Camera mode")
                    FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected=!black,onClick={black=false},modifier=Modifier.heightIn(min=48.dp),label={Text("Normal Camera")})
                        FilterChip(selected=black,onClick={black=true},modifier=Modifier.heightIn(min=48.dp),label={Text("Black-Screen Camera")})
                    }
                    Text(if(black)"No preview. Tap the black area or press Volume Down to ${if(video)"start/stop video" else "take a photo"}. Swipe navigation does not capture. Android privacy indicators stay visible."
                        else "Frame your shot with a live preview. Use the capture button to take a photo or start a video.",
                        style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    if(black)Text("Video enters ready, not recording. REC and vibration (if enabled) confirm recording. Close or Android Back stops and exits.",style=MaterialTheme.typography.bodySmall)
                    HorizontalDivider()
                    FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected=!video,onClick={video=false},modifier=Modifier.heightIn(min=48.dp),label={Text("Photo")})
                        FilterChip(selected=video,onClick={video=true},modifier=Modifier.heightIn(min=48.dp),label={Text("Video")})
                    }
                    OutlinedButton(onClick={front=!front},modifier=Modifier.heightIn(min=48.dp)) {
                        Icon(Icons.Default.Cameraswitch,null,Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if(front)"Front camera" else "Rear camera")
                    }
                }
                if(video)GalleryCard(Modifier.fillMaxWidth()) {
                    GalleryCardHeading("Video settings")
                    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text("Record audio",style=MaterialTheme.typography.titleSmall)
                            Text(if(audio)"Audio On · microphone permission required" else "Audio Off · no microphone permission needed",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked=audio,enabled=audioReady&&!audioSaving,modifier=Modifier.semantics {contentDescription="Record audio"},onCheckedChange={enabled->
                            audioSaving=true
                            scope.launch {val result=withContext(Dispatchers.IO){runCatching {controller.access.audio(enabled)}};if(result.isSuccess)audio=enabled else permissionMessage="Audio preference could not be saved.";audioSaving=false}
                        })
                    }
                    HorizontalDivider()
                    Text("Video quality",style=MaterialTheme.typography.titleSmall)
                    FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        listOf("HD" to Quality.HD,"Full HD" to Quality.FHD,"4K" to Quality.UHD).forEach {(label,value)->
                            FilterChip(selected=quality==value,onClick={quality=value},modifier=Modifier.heightIn(min=48.dp),label={Text(label)})
                        }
                    }
                    Text("Uses the nearest supported quality. No time limit; recording stops at the vault's 2 GiB file limit or storage reserve.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
                GalleryCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text("Capture vibration",style=MaterialTheme.typography.titleSmall)
                            Text("Feel a brief confirmation when capture starts or saves.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked=haptics,onCheckedChange={haptics=it},modifier=Modifier.semantics {contentDescription="Capture vibration"})
                    }
                }
                if(permissionMessage.isNotEmpty())CameraMessage(permissionMessage)
                Button(onClick=::open,enabled=!recovering&&audioReady&&!audioSaving,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) {
                    Icon(Icons.Default.PhotoCamera,null,Modifier.size(20.dp));Spacer(Modifier.width(8.dp));Text("Open camera")
                }
                GalleryCard(Modifier.fillMaxWidth()) {
                    GalleryCardHeading("Saving & recovery")
                    Text("CameraX temporarily writes in private app storage. Verified encrypted saving removes that temporary file. A crash may leave private temporary bytes until authenticated recovery; some interrupted videos cannot be played.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedButton(onClick=::recover,enabled=!recovering,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)){Text(if(recovering)"Recovering…" else "Recover interrupted captures")}
                    TextButton(onClick={details=true},modifier=Modifier.heightIn(min=48.dp)){Text("Recording details")}
                    if(recoveryMessage.isNotEmpty())CameraMessage(recoveryMessage)
                }
            } else {
                if(black)Box(Modifier.weight(1f).fillMaxWidth().background(Color.Black)
                    .semantics {contentDescription=if(video)"Black video capture" else "Black photo capture";stateDescription=if(controller.recordingNow)"Recording" else controller.status
                        onClick(if(controller.recordingNow)"Stop recording" else if(video)"Start recording" else "Take photo"){input();true}}
                    .pointerInput(controller){
                        awaitEachGesture {
                            val down=awaitFirstDown()
                            var tap=true
                            while(true) {
                                val event=awaitPointerEvent()
                                val change=event.changes.firstOrNull {it.id==down.id} ?: break
                                if(change.isConsumed || event.changes.count {it.pressed}>1 ||
                                    (change.position-down.position).getDistance()>viewConfiguration.touchSlop)tap=false
                                if(change.changedToUpIgnoreConsumed()) {
                                    if(tap){change.consume();input()}
                                    break
                                }
                                if(!change.pressed)break
                            }
                        }
                    })
                else {
                    BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                        val availableHeight = maxHeight
                        val controls: @Composable () -> Unit = {
                            CameraCaptureControls(
                                video=video,front=front,audio=captureAudio,bound=controller.bound,busy=controller.busy,
                                saving=controller.saving,recording=controller.recordingNow,flash=controller.flash,
                                hasFlash=controller.hasFlash,zoom=controller.zoom,status=controller.status,elapsed=controller.elapsed,
                                onVideoChange={video=it},onSwitchCamera={front=!front},onCapture={input()},
                                onFlash=controller::toggleFlash,onZoom=controller::changeZoom,
                            )
                        }
                        if(maxWidth>=480.dp&&maxWidth>maxHeight) {
                            Row(Modifier.fillMaxSize()) {
                                AndroidView(factory={preview},modifier=Modifier.weight(1f).fillMaxHeight())
                                Column(Modifier.width(248.dp).fillMaxHeight().verticalScroll(rememberScrollState())) {controls()}
                            }
                        } else {
                            Column(Modifier.fillMaxSize()) {
                                AndroidView(factory={preview},modifier=Modifier.weight(1f).fillMaxWidth())
                                Column(Modifier.fillMaxWidth().heightIn(max=availableHeight*0.6f).verticalScroll(rememberScrollState())) {controls()}
                            }
                        }
                    }
                }
            }
        }
    }
    if(details)CaptureDetails(controller.access,onDismiss={details=false})
}

/** Presentation only. The controller still decides whether a capture input is accepted. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CameraCaptureControls(
    video:Boolean,front:Boolean,audio:Boolean,bound:Boolean,busy:Boolean,saving:Boolean,
    recording:Boolean,flash:Boolean,hasFlash:Boolean,zoom:Float,status:String,elapsed:Long,
    onVideoChange:(Boolean)->Unit,onSwitchCamera:()->Unit,onCapture:()->Unit,
    onFlash:()->Unit,onZoom:(Float)->Unit,
) {
    Column(Modifier.fillMaxWidth().padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        FlowRow(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            FilterChip(selected=!video,onClick={onVideoChange(false)},enabled=!busy,modifier=Modifier.heightIn(min=48.dp),label={Text("Photo")})
            FilterChip(selected=video,onClick={onVideoChange(true)},enabled=!busy,modifier=Modifier.heightIn(min=48.dp),label={Text("Video")})
        }
        FlowRow(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Text(if(front)"Front camera" else "Rear camera",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
            if(video)Text(if(audio)"Audio On" else "Audio Off",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        // Announce state changes, but do not make every elapsed second a live announcement.
        CameraMessage(status)
        if(recording)Text("REC ${(elapsed/60).toString().padStart(2,'0')}:${(elapsed%60).toString().padStart(2,'0')}",
            color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.titleMedium,
            modifier=Modifier.semantics {contentDescription="Recording, ${elapsed/60} minutes ${elapsed%60} seconds"})
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("Zoom",style=MaterialTheme.typography.labelMedium)
            Slider(value=zoom,onValueChange=onZoom,modifier=Modifier.weight(1f).semantics {contentDescription="Zoom"},enabled=bound)
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically) {
            OutlinedIconButton(onClick=onSwitchCamera,enabled=!busy,modifier=Modifier.size(48.dp).semantics {stateDescription=if(front)"Front camera" else "Rear camera"}) {
                Icon(Icons.Default.Cameraswitch,"Switch camera")
            }
            Button(onClick=onCapture,enabled=bound&&!saving,modifier=Modifier.weight(1f).heightIn(min=56.dp).semantics {contentDescription=if(video){if(recording)"Stop recording" else "Start recording"}else "Take photo"}) {
                Text(if(saving)"Saving…" else if(video){if(recording)"Stop" else "Record"}else "Capture")
            }
            OutlinedIconButton(onClick=onFlash,enabled=hasFlash&&!busy,modifier=Modifier.size(48.dp).semantics {stateDescription=if(!hasFlash)"Unavailable" else if(flash)"On" else "Off"}) {
                Icon(if(flash)Icons.Default.FlashOn else Icons.Default.FlashOff,"Flash")
            }
        }
    }
}

@Composable
private fun CameraMessage(message:String) {
    Surface(color=MaterialTheme.colorScheme.surfaceContainerHigh,shape=GalleryTokens.RowShape,modifier=Modifier.fillMaxWidth()) {
        Text(message,Modifier.padding(horizontal=12.dp,vertical=8.dp).semantics {liveRegion=LiveRegionMode.Polite},
            style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurface)
    }
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
