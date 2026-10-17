@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
package uk.co.traynor.privategallery.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.media.MediaDataSource
import android.media.MediaMetadataRetriever
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.datasource.ByteArrayDataSource
import androidx.media3.datasource.DataSource
import java.io.ByteArrayOutputStream
import uk.co.traynor.privategallery.BuildConfig
import uk.co.traynor.privategallery.core.hidden.SyntheticHiddenGallery

/** INSECURE DEVELOPMENT PROTOTYPE. Only generated/bundled fixtures; no picker, files, export or Primary APIs. */
@Composable
internal fun SyntheticHiddenGalleryDialog(onDismiss: () -> Unit) {
    if (!BuildConfig.HIDDEN_PROTOTYPE) return
    val context = LocalContext.current
    val gallery = remember { SyntheticHiddenGallery(BuildConfig.HIDDEN_PROTOTYPE) }
    var revision by remember { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf<SyntheticHiddenGallery.Item?>(null) }
    var filter by remember { mutableStateOf("All") }
    var collection by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val dismiss by rememberUpdatedState(onDismiss)
    val close = { selected = null; gallery.close(); dismiss() }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) close() }
        lifecycle.addObserver(observer)
        onDispose { gallery.close(); lifecycle.removeObserver(observer) }
    }
    val entries = remember(revision,filter) { gallery.items().filter { filter == "All" || (filter == "Favourites" && it.favourite) || it.collection == filter } }
    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth=false, securePolicy=SecureFlagPolicy.SecureOn)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text("TEST ONLY — INSECURE — SYNTHETIC MEDIA", color=MaterialTheme.colorScheme.error, style=MaterialTheme.typography.titleSmall)
                Text("Hidden Vault", style=MaterialTheme.typography.headlineSmall)
                Text("Disposable memory only. Nothing survives close, background or process death.")
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick=close) { Text("Close") }
                    Text("Return to Primary", Modifier.padding(top=12.dp))
                    TextButton(onClick={ selected=null; gallery.close(); revision++; filter="All"; status="Prototype reset" }) { Text("Reset test data") }
                }
                val viewing=selected
                if(viewing != null) {
                    TextButton(onClick={ selected=null; status="" }) { Text("Back to Hidden gallery") }
                    val bytes=remember(viewing.id) { gallery.read(viewing.id) }
                    DisposableEffect(bytes) { onDispose { bytes.fill(0) } }
                    if(viewing.video) {
                        val factory=remember(bytes) { DataSource.Factory { ByteArrayDataSource(bytes) } }
                        val media=remember(viewing.id) { MediaItem.Builder().setUri("memory://hidden-prototype/${viewing.id}").setMimeType("video/mp4").build() }
                        if(status.isNotEmpty()) Text(status)
                        Box(Modifier.weight(1f)) {
                            PrivateVideoPlayer(media, factory, onClose={ selected=null; status="" }, onReady={ status="Synthetic video ready" })
                        }
                    } else {
                        val bitmap=remember(bytes) { BitmapFactory.decodeByteArray(bytes,0,bytes.size) }
                        DisposableEffect(bitmap) { onDispose { bitmap?.recycle() } }
                        bitmap?.let { Image(it.asImageBitmap(),"Synthetic photo viewer",Modifier.fillMaxWidth().weight(1f)) }
                    }
                } else {
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        Button(onClick={
                            runCatching { gallery.importFixture(false,syntheticPhoto()) }
                                .onSuccess { revision++; status="Synthetic photo imported" }.onFailure { status="Test import failed" }
                        }) { Text("Import test photo") }
                        Button(onClick={
                            runCatching { context.assets.open("hidden-prototype/synthetic-video.mp4").use { gallery.importFixture(true,it.readBytes()) } }
                                .onSuccess { revision++; status="Synthetic video imported" }.onFailure { status="Test import failed" }
                        }) { Text("Import test video") }
                    }
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick={filter="All"}) { Text("All") }
                        TextButton(onClick={filter="Favourites"}) { Text("Favourites") }
                        TextButton(onClick={filter="Test collection"}) { Text("Test collection") }
                    }
                    OutlinedTextField(collection,{collection=it.take(40)},label={Text("Test collection name")},singleLine=true)
                    Row {
                        TextButton(onClick={runCatching {gallery.addCollection(collection)}.onSuccess {filter=collection.trim();collection="";revision++}}) {Text("Create collection")}
                        gallery.collections().filter{it!="Test collection"}.forEach { name -> TextButton(onClick={filter=name}){Text(name)} }
                    }
                    Text("Hidden items: ${entries.size}")
                    if(status.isNotEmpty()) Text(status)
                    LazyVerticalGrid(GridCells.Fixed(2),Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(8.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        items(entries,key={it.id}) { item ->
                            Card {
                                SyntheticThumbnail(gallery,item) {selected=item;status=""}
                                Text(if(item.video) "Test video" else "Test photo",Modifier.padding(8.dp))
                                TextButton(onClick={gallery.toggleFavourite(item.id);revision++}) {Text(if(item.favourite) "Unfavourite" else "Favourite")}
                                if(filter!="All" && filter!="Favourites") TextButton(onClick={gallery.assignCollection(item.id,filter);revision++}) {Text("Move to $filter")}
                                // Assignment stays available while viewing all items.
                                gallery.collections().filter{it!="Test collection"}.forEach {name ->
                                    TextButton(onClick={gallery.assignCollection(item.id,name);revision++}) {Text("Move to $name")}
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable private fun SyntheticThumbnail(gallery:SyntheticHiddenGallery,item:SyntheticHiddenGallery.Item,open:()->Unit) {
    val bitmap=remember(item.id) {
        val bytes=gallery.read(item.id)
        try {
            if(!item.video) BitmapFactory.decodeByteArray(bytes,0,bytes.size)
            else MediaMetadataRetriever().let { reader ->
                try {
                    reader.setDataSource(object:MediaDataSource() {
                        override fun getSize()=bytes.size.toLong()
                        override fun close()=Unit
                        override fun readAt(position:Long,buffer:ByteArray,offset:Int,size:Int):Int {
                            if(position<0 || position>=bytes.size) return -1
                            val count=minOf(size,bytes.size-position.toInt()); bytes.copyInto(buffer,offset,position.toInt(),position.toInt()+count);return count
                        }
                    })
                    reader.getFrameAtTime(0)
                } finally { reader.release() }
            }
        } finally {bytes.fill(0)}
    }
    DisposableEffect(bitmap) {onDispose{bitmap?.recycle()}}
    if(bitmap!=null) Image(bitmap.asImageBitmap(),if(item.video)"Open test video" else "Open test photo",Modifier.fillMaxWidth().height(140.dp).clickable(onClick=open))
    else TextButton(onClick=open,modifier=Modifier.semantics { contentDescription=if(item.video) "Open test video" else "Open test photo" }){Text(if(item.video)"Open test video" else "Open test photo")}
}
private fun syntheticPhoto():ByteArray {
    val bitmap=Bitmap.createBitmap(320,240,Bitmap.Config.ARGB_8888)
    return try {
        Canvas(bitmap).apply {drawColor(Color.rgb(30,100,180));drawCircle(160f,100f,65f,Paint().apply{color=Color.YELLOW});drawText("SYNTHETIC TEST",40f,210f,Paint().apply{color=Color.WHITE;textSize=25f})}
        ByteArrayOutputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it);it.toByteArray()}
    } finally {bitmap.recycle()}
}
