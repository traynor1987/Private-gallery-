package uk.co.traynor.privategallery.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import uk.co.traynor.privategallery.core.vault.VaultDocumentPolicy
import uk.co.traynor.privategallery.core.vault.VaultPdfPreview
import java.util.concurrent.atomic.AtomicBoolean

/** Inert text and rendered PDF pages, scoped to the authenticated viewer composition. */
@Composable
internal fun ProtectedDocumentPage(id: String, mime: String, load: ((String,(Result<ByteArray>)->Unit)->Unit)?, current: () -> Boolean) {
    val context=LocalContext.current.applicationContext
    val alive=remember(id){AtomicBoolean(true)}
    val currentCheck by rememberUpdatedState(current)
    var bytes by remember(id){mutableStateOf<ByteArray?>(null)}
    var page by remember(id){mutableIntStateOf(0)}
    var pageCount by remember(id){mutableIntStateOf(0)}
    var bitmap by remember(id){mutableStateOf<Bitmap?>(null)}
    var failure by remember(id){mutableStateOf(false)}
    fun allowed()=alive.get() && currentCheck()
    LaunchedEffect(id) {
        if(load==null){failure=true;return@LaunchedEffect}
        load(id) {result ->
            val data=result.getOrNull()
            if(!allowed() || data==null || data.size>VaultDocumentPolicy.maxBytes(mime)) {data?.fill(0);if(allowed())failure=true}
            else bytes=data
        }
    }
    LaunchedEffect(bytes,page) {
        val data=bytes ?: return@LaunchedEffect
        if(mime!="application/pdf"){pageCount=(data.size+16383)/16384;return@LaunchedEffect}
        failure=false
        bitmap?.let {it.eraseColor(android.graphics.Color.TRANSPARENT);it.recycle()};bitmap=null
        var owned:Bitmap?=null
        try {
            val rendered=withContext(Dispatchers.IO) {VaultPdfPreview.page(context,data,page,::allowed).also {owned=it.first}}
            if(allowed()){bitmap=rendered.first;pageCount=rendered.second;owned=null}
        } catch(error:Exception) {if(error is kotlinx.coroutines.CancellationException)throw error;if(allowed())failure=true}
        finally {owned?.let {it.eraseColor(android.graphics.Color.TRANSPARENT);it.recycle()}}
    }
    DisposableEffect(id) {onDispose {alive.set(false);bytes?.fill(0);bytes=null;bitmap?.let {it.eraseColor(android.graphics.Color.TRANSPARENT);it.recycle()};bitmap=null}}
    Column(Modifier.fillMaxSize().padding(top=80.dp,bottom=100.dp,start=16.dp,end=16.dp),horizontalAlignment=Alignment.CenterHorizontally) {
        Text(VaultDocumentPolicy.label(mime),color=Color.White)
        if(failure)Text("Document could not be opened. The encrypted original is retained.",color=Color.White)
        else if(mime=="application/pdf") {
            bitmap?.let {Image(it.asImageBitmap(),"Document page ${page+1}",Modifier.weight(1f).fillMaxWidth(),contentScale=ContentScale.Fit)} ?: CircularProgressIndicator()
        } else bytes?.let {data ->
            // Bound paragraph layout as well as the file. Align byte boundaries to UTF-8 characters.
            var start=(page*16384).coerceAtMost(data.size)
            var end=((page+1)*16384).coerceAtMost(data.size)
            while(start<data.size && (data[start].toInt() and 0xc0) == 0x80)start++
            while(end<data.size && (data[end].toInt() and 0xc0) == 0x80)end++
            // URLs, markup, formulas and CSV cells are displayed literally, never executed.
            key(page) {Text(String(data,start,end-start,Charsets.UTF_8),color=Color.White,modifier=Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()))}
        } ?: CircularProgressIndicator()
        if(!failure && pageCount>0)Row(verticalAlignment=Alignment.CenterVertically) {
            TextButton(onClick={page--},enabled=page>0){Text("Previous")}
            Text("${page+1} / $pageCount",color=Color.White)
            TextButton(onClick={page++},enabled=page+1<pageCount){Text("Next")}
        }
    }
}
