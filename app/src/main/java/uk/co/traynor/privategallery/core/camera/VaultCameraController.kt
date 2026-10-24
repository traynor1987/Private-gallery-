package uk.co.traynor.privategallery.core.camera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.os.SystemClock
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.camera.view.PreviewView
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.*

/** Active foreground camera only. No service, persistent recording or public output URI. */
internal class VaultCameraController(context: Context, val access: VaultCaptureAccess, private val onAuthorityLost:()->Unit = {}) {
    private val app=context.applicationContext
    private val executor=ContextCompat.getMainExecutor(app)
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
    private val gate=CaptureInputGate()
    private var provider:ProcessCameraProvider?=null
    private var photo:ImageCapture?=null
    private var video:VideoCapture<Recorder>?=null
    private var recording:Recording?=null
    private var camera:Camera?=null
    private var ticket:CaptureJournal.Ticket?=null
    private var revoked=false
    private var bindingGeneration=0
    private var startedAt=0L
    private var boundVideo=false
    private var authorityMonitor: Job?=null
    var entered by mutableStateOf(false);private set
    fun enter():Boolean {
        if(revoked||!access.allowed())return false
        if(authorityMonitor?.isActive!=true)authorityMonitor=scope.launch {
            while(isActive&&!revoked) { delay(100);if(!access.allowed()){onAuthorityLost();revoke();break} }
        }
        entered=true;return true
    }
    /** Settings/permission entry only while idle; no camera use case or capture input survives. */
    fun pauseForSettings(): Boolean {
        if(revoked||busy||!access.allowed())return false
        authorityMonitor?.cancel();authorityMonitor=null
        entered=false;bindingGeneration++;bound=false
        camera?.cameraControl?.enableTorch(false);provider?.unbindAll()
        photo=null;video=null;camera=null
        return true
    }
    var bound by mutableStateOf(false);private set
    var recordingNow by mutableStateOf(false);private set
    var saving by mutableStateOf(false);private set
    var status by mutableStateOf("Ready");private set
    var zoom by mutableFloatStateOf(0f);private set
    var minZoomRatio by mutableFloatStateOf(1f);private set
    var maxZoomRatio by mutableFloatStateOf(1f);private set
    fun zoomPresets():List<Float> = listOf(0.6f,1f,2f,3f).filter {it>=minZoomRatio&&it<=maxZoomRatio}
    fun changeZoomRatio(ratio:Float) {
        val state=camera?.cameraInfo?.zoomState?.value ?: return
        if(!revoked&&bound&&access.allowed()&&ratio in state.minZoomRatio..state.maxZoomRatio) {
            camera?.cameraControl?.setZoomRatio(ratio)
        }
    }
    var flash by mutableStateOf(false);private set
    var hasFlash by mutableStateOf(false);private set
    var elapsed by mutableLongStateOf(0L);private set
    var completedRevision by mutableIntStateOf(0);private set
    var haptics=false
    val busy get()=recordingNow||saving
    fun bind(owner:LifecycleOwner,preview:PreviewView?,isVideo:Boolean,front:Boolean,quality:Quality) {
        if(revoked||busy||!entered||!access.allowed())return
        bound=false
        val generation=++bindingGeneration
        val future=ProcessCameraProvider.getInstance(app)
        future.addListener({
            if(revoked||generation!=bindingGeneration||!access.allowed())return@addListener
            runCatching {
                provider=future.get();provider!!.unbindAll()
                val selector=if(front)CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
                val uses=mutableListOf<UseCase>()
                photo=null;video=null
                if(preview!=null)uses+=Preview.Builder().build().also { it.surfaceProvider=preview.surfaceProvider }
                if(isVideo) {
                    val recorder=Recorder.Builder().setQualitySelector(QualitySelector.from(quality,FallbackStrategy.lowerQualityOrHigherThan(quality))).build()
                    video=VideoCapture.withOutput(recorder);uses+=video!!
                } else {photo=ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY).build();uses+=photo!!}
                camera=provider!!.bindToLifecycle(owner,selector,*uses.toTypedArray())
                camera!!.cameraInfo.zoomState.value?.let {minZoomRatio=it.minZoomRatio;maxZoomRatio=it.maxZoomRatio}
                hasFlash=camera!!.cameraInfo.hasFlashUnit();flash=false;zoom=0f;boundVideo=isVideo;bound=true;status="Ready"
            }.onFailure {bound=false;status="Camera unavailable. Check permission or choose the other camera."}
        },executor)
    }
    fun changeZoom(value:Float){if(!revoked&&bound&&access.allowed()){zoom=value.coerceIn(0f,1f);camera?.cameraControl?.setLinearZoom(zoom)}}
    fun toggleFlash(){if(!busy&&!revoked&&hasFlash&&access.allowed()){flash=!flash;photo?.flashMode=if(flash)ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF; if(video!=null)camera?.cameraControl?.enableTorch(flash)}}
    fun input(isVideo:Boolean,audio:Boolean) {
        if(revoked||!bound||!access.allowed()||isVideo!=boundVideo)return
        if(isVideo&&recordingNow) {
            if(SystemClock.elapsedRealtime()-startedAt>=500){recordingNow=false;saving=true;status="Finalising";recording?.stop()}
            return
        }
        if(!gate.begin(SystemClock.elapsedRealtime()))return
        val microphone=ContextCompat.checkSelfPermission(app,Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED
        if(isVideo&&!CaptureInputGate.audioAllowed(audio,microphone)){gate.finished(SystemClock.elapsedRealtime());status="Microphone permission required for Audio On. Choose Audio Off for silent video.";return}
        val current=runCatching {access.create(if(isVideo)"video/mp4" else "image/jpeg",isVideo&&audio)}.getOrElse {gate.finished(SystemClock.elapsedRealtime());status="Cannot start capture. Check storage and unlock status.";return}
        ticket=current
        if(isVideo) {
            try {
                val pending=checkNotNull(video).output.prepareRecording(app,FileOutputOptions.Builder(current.plain)
                    .setFileSizeLimit(2L*1024*1024*1024-1024*1024).build())
                if(audio)pending.withAudioEnabled()
                saving=true;status="Starting recording"
                recording=pending.start(executor) {event ->
                    when(event) {
                        is VideoRecordEvent.Start -> {saving=false;recordingNow=!revoked;startedAt=SystemClock.elapsedRealtime();status="Recording";vibrate();if(revoked)recording?.stop()}
                        is VideoRecordEvent.Status -> {elapsed=event.recordingStats.recordedDurationNanos/1_000_000_000L
                            if(current.plain.parentFile!!.usableSpace < current.plain.length()+32L*1024*1024 && !saving){saving=true;recordingNow=false;status="Storage reserve reached; finalising";recording?.stop()}}
                        is VideoRecordEvent.Finalize -> {
                            recording=null;recordingNow=false;saving=true
                            val usableError=event.error in setOf(VideoRecordEvent.Finalize.ERROR_NONE,VideoRecordEvent.Finalize.ERROR_SOURCE_INACTIVE,VideoRecordEvent.Finalize.ERROR_FILE_SIZE_LIMIT_REACHED,VideoRecordEvent.Finalize.ERROR_INSUFFICIENT_STORAGE)
                            finish(current,usableError)
                        }
                    }
                }
            }catch(_:Exception){recording=null;recordingNow=false;finish(current,false)}
        } else {
            saving=true;status="Capturing"
            try {checkNotNull(photo).takePicture(ImageCapture.OutputFileOptions.Builder(current.plain).build(),executor,object:ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output:ImageCapture.OutputFileResults){finish(current,true)}
                override fun onError(exception:ImageCaptureException){finish(current,false)}
            })}catch(_:Exception){finish(current,false)}
        }
    }
    private fun finish(current:CaptureJournal.Ticket,reportedComplete:Boolean) {
        scope.launch {
            val result=withContext(Dispatchers.IO) {runCatching {
                val duration=if(current.mime.startsWith("video/")) videoDuration(current.plain.absolutePath) else 0L
                val valid=if(current.mime.startsWith("video/"))runCatching { uk.co.traynor.privategallery.core.browser.v2.VideoFileValidator.inspect(current.plain,current.mime,current.audio); duration>0 }.getOrDefault(false) else BitmapFactory.Options().let {o->o.inJustDecodeBounds=true;BitmapFactory.decodeFile(current.plain.absolutePath,o);o.outWidth>0&&o.outHeight>0}
                access.journal.seal(current,reportedComplete&&valid,duration)
                if(current.complete&&access.allowed())access.import(current) else null
            }}
            current.close();if(ticket===current)ticket=null
            saving=false;gate.finished(SystemClock.elapsedRealtime());completedRevision++
            CaptureCompletionEvents.completed()
            status=when {result.isFailure->"Capture needs recovery. Retained bytes were not deleted.";result.getOrNull()!=null->"Saved encrypted";else->"Encrypted capture retained. Unlock this vault to recover."}
            if(result.getOrNull()!=null)vibrate()
            if(revoked)scope.cancel()
        }
    }
    /** Inputs close synchronously; CameraX callback seals using only the capture-only key. */
    fun revoke() {
        if(revoked)return
        revoked=true;access.revoke();gate.revoke();bindingGeneration++;bound=false
        recording?.stop();recordingNow=false
        camera?.cameraControl?.enableTorch(false);provider?.unbindAll()
        if(ticket==null)scope.cancel()
    }
    private fun vibrate(){if(haptics&&!revoked){(app.getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator)?.vibrate(android.os.VibrationEffect.createOneShot(30,android.os.VibrationEffect.DEFAULT_AMPLITUDE))}}
    companion object {
        fun videoDuration(path:String):Long = runCatching {MediaMetadataRetriever().let {r->try{r.setDataSource(path);r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?:0}finally{r.release()}}}.getOrDefault(0)
    }
}
