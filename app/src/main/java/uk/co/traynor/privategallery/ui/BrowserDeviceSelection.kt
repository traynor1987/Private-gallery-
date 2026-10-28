package uk.co.traynor.privategallery.ui

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.CancellationSignal
import android.os.Process
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import uk.co.traynor.privategallery.core.browser.v2.BrowserDeviceUriPolicy
import java.util.Locale
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.FutureTask
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/** Explicit system document UI; third-party generic picker activities never get this request. */
internal class BrowserDeviceDocumentContract(private val multiple:Boolean):ActivityResultContract<Array<String>,List<Uri>>() {
    override fun createIntent(context:Context,input:Array<String>):Intent {
        val types=devicePickerMimeTypes(input)
        val intent=Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(if(types.size==1)types[0] else "*/*")
            .putExtra(Intent.EXTRA_MIME_TYPES,types).putExtra(Intent.EXTRA_ALLOW_MULTIPLE,multiple)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        @Suppress("DEPRECATION")
        val candidates=context.packageManager.queryIntentActivities(intent,PackageManager.MATCH_DEFAULT_ONLY).mapNotNull {it.activityInfo}
            .filter {it.exported && it.enabled && it.applicationInfo.enabled &&
                (it.applicationInfo.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP))!=0 &&
                (it.permission==null || context.checkSelfPermission(it.permission)==PackageManager.PERMISSION_GRANTED)}
        val chosen=candidates.sortedWith(compareBy<android.content.pm.ActivityInfo> {
            when(it.packageName){"com.google.android.documentsui"->0;"com.android.documentsui"->1;else->2}
        }.thenBy {it.packageName}.thenBy {it.name}).firstOrNull()
            ?: throw SecurityException("A trusted Android document picker is unavailable. Use Choose from Vault instead.")
        return intent.setComponent(ComponentName(chosen.packageName,chosen.name))
    }
    override fun parseResult(resultCode:Int,intent:Intent?):List<Uri> {
        if(resultCode!=Activity.RESULT_OK || intent==null)return emptyList()
        val values=mutableListOf<Uri>()
        intent.data?.let(values::add)
        intent.clipData?.let {clip ->for(i in 0 until minOf(clip.itemCount,4)){clip.getItemAt(i).uri?.let(values::add)}}
        return values.distinct().take(if(multiple)4 else 1)
    }
}

private fun acceptTokens(requested:Array<String>)=requested.flatMap {it.split(',')}.map {it.substringBefore(';').trim().lowercase(Locale.ROOT)}.filter {it.isNotEmpty()}
internal fun devicePickerMimeTypes(requested:Array<String>):Array<String> {
    val tokens=acceptTokens(requested)
    if(tokens.isEmpty())return arrayOf("*/*")
    val types=tokens.mapNotNull {token ->
        when {
            token=="*/*"->token
            token.startsWith('.') && token.drop(1).matches(Regex("[a-z0-9]{1,32}"))->MimeTypeMap.getSingleton().getMimeTypeFromExtension(token.drop(1)) ?: "*/*"
            token.matches(Regex("[a-z0-9!#$&^_.+-]+/(?:[a-z0-9!#$&^_.+-]+|\\*)"))->token
            else->null
        }
    }.distinct()
    require(types.isNotEmpty()){"This website requests an unsupported document type."}
    return types.toTypedArray()
}
private fun acceptsDevice(mime:String,name:String,requested:Array<String>):Boolean {
    val tokens=acceptTokens(requested)
    if(tokens.isEmpty())return true
    val actual=mime.substringBefore(';').lowercase(Locale.ROOT)
    return tokens.any {token ->token=="*/*" || token==actual ||
        (token.endsWith("/*") && actual.startsWith(token.dropLast(1))) ||
        (token.startsWith('.') && token.drop(1).matches(Regex("[a-z0-9]{1,32}")) &&
            (name.lowercase(Locale.ROOT).endsWith(token) || MimeTypeMap.getSingleton().getMimeTypeFromExtension(token.drop(1))==actual))}
}
internal data class BrowserDeviceValidation(val uris:List<Uri>?=null,val rejection:String?=null)
private val deviceValidationWorkers=ThreadPoolExecutor(2,2,15,TimeUnit.SECONDS,ArrayBlockingQueue(4),{work ->Thread(work,"browser-document-validation").apply {isDaemon=true}},ThreadPoolExecutor.AbortPolicy()).apply {allowCoreThreadTimeOut(true)}

/** Bounded asynchronous read checks. Cancellation cannot deliver a late result or grow workers. */
internal suspend fun validateBrowserDeviceSelection(context:Context,uris:List<Uri>,accept:Array<String>):BrowserDeviceValidation =
    withTimeout(5_000) {suspendCancellableCoroutine {continuation ->
        val signal=CancellationSignal()
        val task=FutureTask {
            val checked=try {validateBrowserDeviceSelectionBlocking(context,uris,accept,signal)}catch(_:Exception){BrowserDeviceValidation(rejection="The selected document could not be verified or read. Choose it again.")}
            if(continuation.isActive)continuation.resumeWith(Result.success(checked))
        }
        continuation.invokeOnCancellation {signal.cancel();task.cancel(true)}
        try {deviceValidationWorkers.execute(task)}catch(_:java.util.concurrent.RejectedExecutionException){continuation.resumeWith(Result.success(BrowserDeviceValidation(rejection="A document provider is not responding. Cancel and try again later.")))}
    }}

internal fun validateBrowserDeviceSelectionBlocking(context:Context,uris:List<Uri>,accept:Array<String>,signal:CancellationSignal=CancellationSignal()):BrowserDeviceValidation {
    val selected=uris.take(4)
    val opened=java.util.concurrent.atomic.AtomicReference<android.content.res.AssetFileDescriptor?>(null)
    signal.setOnCancelListener {runCatching {opened.getAndSet(null)?.close()}}
    try {for(uri in selected) {
        signal.throwIfCanceled()
        if(!uri.scheme.equals("content",true) || uri.authority.isNullOrEmpty() || uri.authority!!.contains('@'))
            return BrowserDeviceValidation(rejection=BrowserDeviceUriPolicy.rejection(uri.scheme,uri.authority,null,Process.myUid(),false))
        @Suppress("DEPRECATION")
        val provider=uri.authority?.takeUnless {it.contains('@')}?.let {context.packageManager.resolveContentProvider(it,0)}
        val grant=context.checkUriPermission(uri,Process.myPid(),Process.myUid(),Intent.FLAG_GRANT_READ_URI_PERMISSION)==PackageManager.PERMISSION_GRANTED
        BrowserDeviceUriPolicy.rejection(uri.scheme,uri.authority,provider?.applicationInfo?.uid,Process.myUid(),grant)?.let {return BrowserDeviceValidation(rejection=it)}
        val mime=context.contentResolver.getType(uri).orEmpty()
        signal.throwIfCanceled()
        val name=context.contentResolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null,signal)?.use {cursor ->if(cursor.moveToFirst())cursor.getString(0).orEmpty() else ""}.orEmpty()
        if(!acceptsDevice(mime,name,accept))return BrowserDeviceValidation(rejection="A selected document does not match this website's accepted file types.")
        val asset=context.contentResolver.openAssetFileDescriptor(uri,"r",signal)
            ?: return BrowserDeviceValidation(rejection="The selected document is not readable.")
        opened.set(asset)
        try {signal.throwIfCanceled();asset.createInputStream().use {it.read()}}finally{opened.compareAndSet(asset,null);runCatching{asset.close()}}
        signal.throwIfCanceled()
        if(context.checkUriPermission(uri,Process.myPid(),Process.myUid(),Intent.FLAG_GRANT_READ_URI_PERMISSION)!=PackageManager.PERMISSION_GRANTED)return BrowserDeviceValidation(rejection="Read access to the selected document ended. Choose it again.")
    }
    return BrowserDeviceValidation(uris=selected.toList())
    }finally{signal.setOnCancelListener(null);runCatching {opened.getAndSet(null)?.close()}}
}

@Composable internal fun BrowserDeviceValidationDialog(onCancel:()->Unit) {
    GalleryAlertDialog(onDismissRequest=onCancel,title={Text("Checking selected files")},text={Column {Text("Verifying read access and file types. Nothing is sent to the website yet.");LinearProgressIndicator(Modifier.fillMaxWidth())}},
        confirmButton={},dismissButton={TextButton(onClick=onCancel){Text("Cancel")}})
}
