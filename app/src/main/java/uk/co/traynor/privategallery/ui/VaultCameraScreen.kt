package uk.co.traynor.privategallery.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.video.Quality
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.border
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
    var recent by remember {mutableStateOf<android.graphics.Bitmap?>(null)}
    DisposableEffect(recent){val bitmap=recent;onDispose {bitmap?.recycle()}}
    var active by remember {mutableStateOf(false)}
    var black by remember {mutableStateOf(false)}
    var reading by remember {mutableStateOf(false)}
    var initialOpenAttempted by remember {mutableStateOf(false)}
    var permissionRevision by remember {mutableIntStateOf(0)}
    var defaultVideo by remember {mutableStateOf(false)}
    var video by remember {mutableStateOf(false)}
    var front by remember {mutableStateOf(false)}
    var quality by remember {mutableStateOf(Quality.FHD)}
    var audio by remember {mutableStateOf(false)}
    var audioReady by remember {mutableStateOf(false)}
    var audioSaving by remember {mutableStateOf(false)}
    var captureAudio by remember {mutableStateOf(false)}
    var grid by remember {mutableStateOf(false)}
    var fillPreview by remember {mutableStateOf(true)}
    var timerSeconds by remember {mutableIntStateOf(0)}
    var countdown by remember {mutableIntStateOf(0)}
    var photoQuality by remember {mutableIntStateOf(95)}
    var haptics by remember {mutableStateOf(false)}
    var permissionMessage by remember {mutableStateOf("")}
    var details by remember {mutableStateOf(false)}
    var recoveryMessage by remember {mutableStateOf("")}
    var recovering by remember {mutableStateOf(false)}
    val preview=remember {PreviewView(context).apply {implementationMode=PreviewView.ImplementationMode.COMPATIBLE;scaleType=PreviewView.ScaleType.FILL_CENTER}}
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {grants ->
        if(grants.isNotEmpty()&&grants.values.all {it})permissionRevision++
        permissionMessage=if(grants.values.any {!it}) "Permission denied. Silent video needs camera permission only." else "Permission granted. Tap Open camera."
    }
    LaunchedEffect(controller){
        val options=withContext(Dispatchers.IO){runCatching {controller.access.options()}.getOrDefault(CameraOptions())}
        photoQuality=options.photoQuality;quality=listOf(Quality.HD,Quality.FHD,Quality.UHD)[options.videoQuality]
        grid=options.grid;timerSeconds=options.timer;haptics=options.vibration;defaultVideo=options.defaultVideo;video=defaultVideo
        audio=withContext(Dispatchers.IO){runCatching {controller.access.audio()}.getOrDefault(false)};audioReady=true
    }
    LaunchedEffect(audioReady,photoQuality,quality,grid,timerSeconds,haptics,defaultVideo) {
        if(audioReady) {
            val options=CameraOptions(photoQuality,listOf(Quality.HD,Quality.FHD,Quality.UHD).indexOf(quality),grid,timerSeconds,haptics,defaultVideo)
            kotlinx.coroutines.delay(300)
            val result=withContext(Dispatchers.IO){runCatching {controller.access.options(options)}}
            if(result.isFailure&&controller.access.allowed())permissionMessage="Camera preferences could not be saved."
        }
    }
    DisposableEffect(controller){onDispose {activity?.cameraVolumeAction=null;controller.revoke()}}
    val input by rememberUpdatedState<()->Unit>({controller.input(video,captureAudio)})
    DisposableEffect(active,black,reading){if(active&&(black||reading))activity?.cameraVolumeAction={input()};onDispose {activity?.cameraVolumeAction=null}}
    LaunchedEffect(active,front,video,quality,black,reading){if(active)controller.bind(owner,if(black||reading)null else preview,video,front,quality)}
    LaunchedEffect(active,controller.completedRevision) {
        if(active) {
            var owned:android.graphics.Bitmap?=null
            try {
                withContext(Dispatchers.IO){owned=runCatching {controller.access.recentPreview()}.getOrNull()}
                if(controller.access.allowed()){recent=owned;owned=null}
            } finally {owned?.recycle()}
        }
    }
    BackHandler {onExit()}
    fun open() {
        if(!audioReady||audioSaving)return
        val required=mutableListOf(Manifest.permission.CAMERA)
        if(video&&audio)required+=Manifest.permission.RECORD_AUDIO
        val missing=required.filter {ContextCompat.checkSelfPermission(context,it)!=PackageManager.PERMISSION_GRANTED}
        if(missing.isNotEmpty()){permission.launch(missing.toTypedArray());return}
        controller.haptics=haptics;controller.jpegQuality=photoQuality;captureAudio=audio
        if(controller.enter())active=true
    }
    LaunchedEffect(audioReady,permissionRevision) {
        if (audioReady && activity != null && ((!controller.startInSettings&&!initialOpenAttempted) || permissionRevision>0)) { initialOpenAttempted = true; open() }
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
            if(!active || !reading) Row(Modifier.fillMaxWidth().heightIn(min=48.dp).padding(horizontal=8.dp),verticalAlignment=Alignment.CenterVertically) {
                IconButton(onClick=onExit){Icon(Icons.Default.Close,"Stop and exit camera",tint=if(active&&black)Color.Gray else LocalContentColor.current)}
                if(!active||!black)Text(if(active) "" else "Camera",Modifier.weight(1f),style=MaterialTheme.typography.titleMedium)
                else Spacer(Modifier.weight(1f))
                if(active&&!black&&!reading) {
                    if(controller.hasFlash)IconButton(onClick=controller::toggleFlash,enabled=!controller.busy){Icon(if(controller.flash)Icons.Default.FlashOn else Icons.Default.FlashOff,"Flash")}
                    TextButton(onClick={timerSeconds=when(timerSeconds){0->3;3->10;else->0}},enabled=!controller.busy&&countdown==0){Text(if(timerSeconds==0)"Timer" else "${timerSeconds}s")}
                    TextButton(onClick={fillPreview=!fillPreview;preview.scaleType=if(fillPreview)PreviewView.ScaleType.FILL_CENTER else PreviewView.ScaleType.FIT_CENTER},enabled=!controller.busy){Text(if(fillPreview)"Fill" else "Fit")}
                }
                if(active&&!controller.busy&&countdown==0) IconButton(onClick={if(controller.pauseForSettings())active=false}) { Icon(Icons.Default.Tune,"Camera modes and settings") }
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
                        FilterChip(selected=!black&&!reading,onClick={black=false;reading=false},modifier=Modifier.heightIn(min=48.dp),label={Text("Normal Camera")})
                        FilterChip(selected=black,onClick={black=true;reading=false},modifier=Modifier.heightIn(min=48.dp),label={Text("Black-Screen Camera")})
                        FilterChip(selected=reading,onClick={reading=true;black=false},modifier=Modifier.heightIn(min=48.dp),label={Text("Reading View Camera")})
                    }
                    Text(if(reading) "Original local reading page, without a preview. Choose Photo or Video before entering. Tap the article text for a photo; use the explicit start/stop control for video. Volume Down also captures or starts/stops. Close or Back exits. Android camera/microphone indicators remain visible. Locking stops capture." else if(black)"No preview. Tap the black area or press Volume Down to ${if(video)"start/stop video" else "take a photo"}. Swipe navigation does not capture. Android privacy indicators stay visible."
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
                    Row(verticalAlignment=Alignment.CenterVertically) {Text("Camera grid",Modifier.weight(1f));Switch(grid,{grid=it})}
                    Row(verticalAlignment=Alignment.CenterVertically) {Text("Start in video mode",Modifier.weight(1f));Switch(defaultVideo,{defaultVideo=it})}
                    Text("Photo quality",style=MaterialTheme.typography.titleSmall)
                    FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {listOf(85,95,100).forEach {value->FilterChip(selected=photoQuality==value,onClick={photoQuality=value},label={Text("$value%")})}}
                    Text("Fill previews crop to fit the display; Fit shows the full camera framing. Neither changes capture resolution. Frame-rate selection is unavailable until verified against device capabilities.",style=MaterialTheme.typography.bodySmall)
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
                if(reading) ReadingCameraView(video,controller.recordingNow,controller.saving,controller.bound,controller.status,controller.elapsed,captureAudio,{input()},onExit)
                else if(black)Box(Modifier.weight(1f).fillMaxWidth().background(Color.Black)
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
                    val controls: @Composable () -> Unit = {
                            CameraCaptureControls(
                                video=video,front=front,audio=captureAudio,bound=controller.bound,busy=controller.busy||countdown>0,
                                saving=controller.saving,recording=controller.recordingNow,flash=controller.flash,
                                hasFlash=false,zoom=controller.zoom,status=if(countdown>0)"Capture in $countdown" else controller.status,elapsed=controller.elapsed,
                                onVideoChange={video=it},onSwitchCamera={front=!front},onCapture={
                                    if(countdown==0) {
                                        if(video||timerSeconds==0)input()
                                        else scope.launch {
                                            try {for(second in timerSeconds downTo 1){if(!controller.access.allowed())break;countdown=second;kotlinx.coroutines.delay(1000)};if(controller.access.allowed())input()}
                                            finally {countdown=0}
                                        }
                                    }
                                },
                                onFlash=controller::toggleFlash,onZoom=controller::changeZoom,
                                zoomPresets=controller.zoomPresets(),onZoomRatio=controller::changeZoomRatio,
                                recentPreview=recent?.takeIf {controller.access.allowed()},
                            )
                        }
                    CameraViewfinderLayout(Modifier.weight(1f).fillMaxWidth(),
                        preview = {
                            AndroidView(factory={preview},modifier=Modifier.fillMaxSize())
                            if(grid)androidx.compose.foundation.Canvas(Modifier.fillMaxSize().semantics {contentDescription="Camera grid"}) {
                                for(part in 1..2) {
                                    drawLine(Color.White.copy(alpha=0.4f),androidx.compose.ui.geometry.Offset(size.width*part/3,0f),androidx.compose.ui.geometry.Offset(size.width*part/3,size.height))
                                    drawLine(Color.White.copy(alpha=0.4f),androidx.compose.ui.geometry.Offset(0f,size.height*part/3),androidx.compose.ui.geometry.Offset(size.width,size.height*part/3))
                                }
                            }
                        }, controls = controls)

                }
            }
        }
    }
    if(details)CaptureDetails(controller.access,onDismiss={details=false})
}

/** Shared by production and Fold screenshot fixtures: the controls overlay the full viewfinder. */
@Composable
internal fun CameraViewfinderLayout(modifier: Modifier = Modifier, preview: @Composable () -> Unit, controls: @Composable () -> Unit) {
    BoxWithConstraints(modifier) {
        val availableHeight = maxHeight
        val landscape = maxWidth > maxHeight
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            preview()
            Surface(modifier=Modifier.align(if(landscape) Alignment.CenterEnd else Alignment.BottomCenter)
                .then(if(landscape) Modifier.width(190.dp).fillMaxHeight() else Modifier.fillMaxWidth().heightIn(max=availableHeight*0.38f)),
                color=Color.Black.copy(alpha=0.65f), contentColor=Color.White) {
                Column(Modifier.verticalScroll(rememberScrollState())) { controls() }
            }
        }
    }
}

/** Presentation only. The controller still decides whether a capture input is accepted. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CameraCaptureControls(
    video:Boolean,front:Boolean,audio:Boolean,bound:Boolean,busy:Boolean,saving:Boolean,
    recording:Boolean,flash:Boolean,hasFlash:Boolean,zoom:Float,status:String,elapsed:Long,
    onVideoChange:(Boolean)->Unit,onSwitchCamera:()->Unit,onCapture:()->Unit,
    onFlash:()->Unit,onZoom:(Float)->Unit,
    zoomPresets:List<Float> = listOf(1f),onZoomRatio:((Float)->Unit)? = null,
    recentPreview:android.graphics.Bitmap? = null,
) {
    Column(Modifier.fillMaxWidth().padding(8.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
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
        Row(Modifier.fillMaxWidth().semantics {
            contentDescription="Zoom"
            progressBarRangeInfo=ProgressBarRangeInfo(zoom,0f..1f)
            setProgress {if(bound){onZoom(it);true}else false}
        },horizontalArrangement=Arrangement.Center) {
            zoomPresets.forEach {ratio->TextButton(onClick={onZoomRatio?.invoke(ratio)},enabled=bound) {Text("${ratio}×")}}
        }

        if(hasFlash)Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End) {
            OutlinedIconButton(onClick=onFlash,enabled=!busy,modifier=Modifier.size(48.dp).semantics {stateDescription=if(flash)"On" else "Off"}) {Icon(if(flash)Icons.Default.FlashOn else Icons.Default.FlashOff,"Flash")}
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly,verticalAlignment=Alignment.CenterVertically) {
            Box(Modifier.size(48.dp),contentAlignment=Alignment.Center) {recentPreview?.let {Image(it.asImageBitmap(),"Recent encrypted capture",Modifier.fillMaxSize())}}
            FilledIconButton(onClick=onCapture,enabled=bound&&!saving,colors=IconButtonDefaults.filledIconButtonColors(containerColor=if(video)Color(0xFFD32F2F) else Color.White,contentColor=Color.Black),shape=androidx.compose.foundation.shape.CircleShape,modifier=Modifier.size(72.dp).border(3.dp,Color.White,androidx.compose.foundation.shape.CircleShape).semantics {contentDescription=if(video){if(recording)"Stop recording" else "Start recording"}else "Take photo"}) {
                if(saving) Text("Saving…",style=MaterialTheme.typography.labelSmall)
                else Icon(if(video) {if(recording)Icons.Default.Stop else Icons.Default.Videocam} else Icons.Default.PhotoCamera, null, Modifier.size(36.dp))
            }
            OutlinedIconButton(onClick=onSwitchCamera,enabled=!busy,modifier=Modifier.size(48.dp).semantics {stateDescription=if(front)"Front camera" else "Rear camera"}) {
                Icon(Icons.Default.Cameraswitch,"Switch camera")
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
