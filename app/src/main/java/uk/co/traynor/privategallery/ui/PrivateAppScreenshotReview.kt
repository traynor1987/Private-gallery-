package uk.co.traynor.privategallery.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicBoolean
import uk.co.traynor.privategallery.core.browser.v2.BrowserScreenshotPolicy
import uk.co.traynor.privategallery.core.vault.VaultImportSource

/** One captured document and Vault authority. Receipts are never paths or public content URIs. */
internal class PrivateAppScreenshotRequest(
    val source:VaultImportSource, val vaultName:String, val current:()->Boolean,
    val save:(VaultImportSource,(Result<String>)->Unit)->Unit,
    val read:(String)->ByteArray, val cancel:(String?)->Unit={},
)

/** Memory-only protected review. The saved-image view decrypts the committed item, not this preview. */
@Composable
internal fun PrivateAppScreenshotReview(request:PrivateAppScreenshotRequest,onClose:()->Unit) {
    val scope=rememberCoroutineScope()
    val alive=remember(request){AtomicBoolean(true)}
    val consumed=remember(request){AtomicBoolean(false)}
    val receipt=remember(request){java.util.concurrent.atomic.AtomicReference<String?>(null)}
    var preview by remember(request){mutableStateOf<Bitmap?>(null)}
    var busy by remember(request){mutableStateOf(true)}
    var saved by remember(request){mutableStateOf(false)}
    var viewing by remember(request){mutableStateOf(false)}
    var message by remember(request){mutableStateOf("")}
    val close by rememberUpdatedState(onClose)
    val lifecycle=androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    fun consume(){if(consumed.compareAndSet(false,true))request.source.onConsumed()}
    fun current()=alive.get() && request.current()
    fun dismiss(){alive.set(false);request.cancel(receipt.get());consume();close()}
    val owned=remember(request){request.source.copy(
        isCancelled={!alive.get() || request.source.isCancelled()},
        onConsumed={if(consumed.compareAndSet(false,true))request.source.onConsumed()},
        publishIfAllowed={commit ->check(alive.get());request.source.publishIfAllowed {check(alive.get());commit()}})}
    DisposableEffect(request,lifecycle) {
        val observer=androidx.lifecycle.LifecycleEventObserver {_,event ->if(event==androidx.lifecycle.Lifecycle.Event.ON_STOP)dismiss()}
        lifecycle.addObserver(observer)
        onDispose {alive.set(false);request.cancel(receipt.get());consume();preview?.let {it.eraseColor(0);it.recycle()};preview=null;lifecycle.removeObserver(observer)}
    }
    LaunchedEffect(request) {while(isActive){delay(100);if(!current()){dismiss();break}}}
    LaunchedEffect(request) {
        val result=withContext(Dispatchers.IO){runCatching {
            request.source.openStream().use {input ->
                val bytes=input.readBytesBounded()
                try {decodeScreenshotPreview(bytes)}finally{bytes.fill(0)}
            }
        }}
        if(!current()){result.getOrNull()?.let {it.eraseColor(0);it.recycle()};dismiss();return@LaunchedEffect}
        result.onSuccess {preview=it}.onFailure {message="The captured image could not be validated. Cancel and try again."}
        busy=false
    }
    GalleryAlertDialog(properties=DialogProperties(securePolicy=SecureFlagPolicy.SecureOn),onDismissRequest=::dismiss,
        title={Text(if(viewing)"Saved screenshot" else if(saved)"Screenshot saved" else "Review screenshot")},
        text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
            preview?.let {Image(it.asImageBitmap(),"Protected screenshot preview",Modifier.fillMaxWidth().heightIn(max=280.dp),contentScale=ContentScale.Fit)}
            Text(if(saved)"Saved encrypted in ${request.vaultName}." else "Save this visible app content encrypted in ${request.vaultName}. No public Gallery copy is created.")
            if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
            if(message.isNotBlank())Text(message,color=MaterialTheme.colorScheme.error)
            if(saved && !viewing)TextButton(enabled=!busy,onClick={
                busy=true;message=""
                val id=receipt.get() ?: return@TextButton
                scope.launch {
                    val result=withContext(Dispatchers.IO){runCatching {
                        val bytes=request.read(id)
                        try {decodeScreenshotPreview(bytes)}finally{bytes.fill(0)}
                    }}
                    if(!current()){result.getOrNull()?.let {it.eraseColor(0);it.recycle()};dismiss();return@launch}
                    result.onSuccess {bitmap ->preview?.let {it.eraseColor(0);it.recycle()};preview=bitmap;viewing=true}
                        .onFailure {message="The saved screenshot is unavailable. Open this authenticated Vault to view it."}
                    busy=false
                }
            }){Text("View Screenshot")}
        }},
        confirmButton={if(saved)TextButton(onClick=::dismiss){Text("Done")} else TextButton(enabled=!busy && preview!=null,onClick={
            if(!current()){dismiss();return@TextButton}
            busy=true;message=""
            request.save(owned) {result ->
                if(!current()){result.getOrNull()?.let {request.cancel(it)};return@save}
                result.onSuccess {receipt.set(it);saved=true}.onFailure {message="Screenshot could not be saved. Cancel and try again."}
                busy=false
            }
        }){Text("Save screenshot")}},
        dismissButton={if(!saved)TextButton(onClick=::dismiss){Text("Cancel")}})
}

private fun java.io.InputStream.readBytesBounded():ByteArray {
    val output=uk.co.traynor.privategallery.core.browser.v2.BrowserScreenshotBuffer(BrowserScreenshotPolicy.MAX_PNG_BYTES)
    val chunk=ByteArray(16*1024)
    try {while(true){val n=read(chunk);if(n<0)break;output.write(chunk,0,n)};return output.input().readBytes()}
    finally{chunk.fill(0);output.wipe()}
}

internal fun decodeScreenshotPreview(bytes:ByteArray):Bitmap {
    require(BrowserScreenshotPolicy.pngHeader(bytes,bytes.size.toLong()) && bytes.size>=12 &&
        BrowserScreenshotPolicy.pngTrailer(bytes.copyOfRange(bytes.size-12,bytes.size)))
    val options=BitmapFactory.Options().apply {inJustDecodeBounds=true;inMutable=true}
    BitmapFactory.decodeByteArray(bytes,0,bytes.size,options)
    require(options.outWidth>0 && options.outHeight>0 && options.outWidth.toLong()*options.outHeight<=BrowserScreenshotPolicy.MAX_PIXELS)
    var sample=1
    while(maxOf(options.outWidth,options.outHeight)/sample>512)sample*=2
    options.inJustDecodeBounds=false;options.inSampleSize=sample
    return checkNotNull(BitmapFactory.decodeByteArray(bytes,0,bytes.size,options))
}
