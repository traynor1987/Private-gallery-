package uk.co.traynor.privategallery.ui

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import uk.co.traynor.privategallery.core.ui.MediaViewerPolicy
import uk.co.traynor.privategallery.core.ui.MediaViewerSource

/** One viewer shell; the source determines whether the current page is a MediaStore URI or Vault bytes. */
data class ViewerMediaEntry(
    val id: String,
    val mimeType: String,
    val uri: Uri? = null,
)

@Composable
fun FullscreenMediaViewer(
    entries: List<ViewerMediaEntry>,
    source: MediaViewerSource,
    initialIndex: Int,
    onClose: (lastIndex: Int) -> Unit,
    onLoadProtectedBytes: ((id: String, onLoaded: (Result<ByteArray>) -> Unit) -> Unit)? = null,
    onCopyToVault: ((ViewerMediaEntry) -> Unit)? = null,
    onMoveToVault: ((ViewerMediaEntry) -> Unit)? = null,
    onRestore: ((ViewerMediaEntry) -> Unit)? = null,
    onRestoreAndRemove: ((ViewerMediaEntry) -> Unit)? = null,
    onDeleteFromVault: ((ViewerMediaEntry) -> Unit)? = null,
) {
    if (entries.isEmpty()) return
    val pagerState = rememberPagerState(
        initialPage = MediaViewerPolicy.initialPage(initialIndex, entries.size),
        pageCount = { entries.size },
    )
    var controlsVisible by remember { mutableStateOf(true) }
    var zoomed by remember { mutableStateOf(false) }
    var showMore by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val current = entries.getOrNull(pagerState.currentPage) ?: return
    BackHandler { onClose(pagerState.currentPage) }

    LaunchedEffect(controlsVisible, pagerState.currentPage) {
        if (controlsVisible) {
            delay(2800)
            controlsVisible = false
        }
    }
    LaunchedEffect(pagerState.currentPage) { zoomed = false }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = !zoomed,
            beyondViewportPageCount = 0,
        ) { page ->
            // The pager composes only the visible item. Vault bytes are never prefetched for neighbours.
            if (page == pagerState.currentPage) {
                val entry = entries[page]
                if (entry.mimeType.startsWith("video/")) {
                    if (source == MediaViewerSource.GALLERY) NormalVideoPage(checkNotNull(entry.uri))
                    else ProtectedVideoPage(entry.id, onLoadProtectedBytes)
                } else {
                    if (source == MediaViewerSource.GALLERY) NormalImagePage(checkNotNull(entry.uri), onTap = { controlsVisible = MediaViewerPolicy.toggleControls(controlsVisible) }) { zoomed = it }
                    else ProtectedImagePage(entry.id, onLoadProtectedBytes, onTap = { controlsVisible = MediaViewerPolicy.toggleControls(controlsVisible) }) { zoomed = it }
                }
            }
        }
        if (controlsVisible) {
            Surface(
                modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth(),
                color = Color.Black.copy(alpha = 0.72f),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = { onClose(pagerState.currentPage) }) { Text("‹  Back") }
                    Text("${pagerState.currentPage + 1} / ${entries.size}", style = MaterialTheme.typography.labelMedium)
                    TextButton(onClick = { showMore = true }) { Text("More") }
                }
            }
            Surface(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                color = Color.Black.copy(alpha = 0.72f),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (source == MediaViewerSource.GALLERY) {
                        onCopyToVault?.let { copy -> TextButton(onClick = { copy(current) }) { Text("Copy to Vault") } }
                        onMoveToVault?.let { move -> TextButton(onClick = { move(current) }) { Text("Move to Vault") } }
                    } else {
                        onRestore?.let { restore -> TextButton(onClick = { restore(current) }) { Text("Restore") } }
                        TextButton(onClick = { showMore = true }) { Text("More") }
                    }
                }
            }
        }
    }
    if (showMore) {
        AlertDialog(
            onDismissRequest = { showMore = false },
            title = { Text(if (source == MediaViewerSource.VAULT) "Vault actions" else "Gallery actions") },
            text = { Text(if (source == MediaViewerSource.VAULT) "Choose an action for this protected item." else "Use Copy or Move to add this item to your Vault.") },
            confirmButton = {
                if (source == MediaViewerSource.VAULT) {
                    TextButton(onClick = { showMore = false; onRestoreAndRemove?.invoke(current) }) { Text("Restore and remove") }
                } else TextButton(onClick = { showMore = false }) { Text("Close") }
            },
            dismissButton = {
                if (source == MediaViewerSource.VAULT) {
                    TextButton(onClick = { showMore = false; confirmDelete = true }) { Text("Delete") }
                } else TextButton(onClick = { showMore = false }) { Text("Close") }
            },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete from Vault?") },
            text = { Text("This permanently deletes the encrypted Vault copy. It cannot be undone unless another copy exists elsewhere.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDeleteFromVault?.invoke(current)
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun NormalImagePage(uri: Uri, onTap: () -> Unit, onZoomChanged: (Boolean) -> Unit) {
    val context = LocalContext.current
    var image by remember(uri) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    LaunchedEffect(uri) {
        image = withContext(Dispatchers.IO) {
            runCatching { context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it)?.asImageBitmap() } }.getOrNull()
        }
    }
    ViewerImage(image, onTap, onZoomChanged)
}

@Composable
private fun ProtectedImagePage(
    id: String,
    load: ((String, (Result<ByteArray>) -> Unit) -> Unit)?,
    onTap: () -> Unit,
    onZoomChanged: (Boolean) -> Unit,
) {
    var bytes by remember(id) { mutableStateOf<ByteArray?>(null) }
    var image by remember(id) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    LaunchedEffect(id) { load?.invoke(id) { bytes = it.getOrNull() } }
    LaunchedEffect(bytes) {
        bytes?.let { clearable ->
            image = withContext(Dispatchers.IO) { BitmapFactory.decodeByteArray(clearable, 0, clearable.size)?.asImageBitmap() }
            clearable.fill(0)
            bytes = null
        }
    }
    ViewerImage(image, onTap, onZoomChanged)
}

@Composable
private fun ViewerImage(image: androidx.compose.ui.graphics.ImageBitmap?, onTap: () -> Unit, onZoomChanged: (Boolean) -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val transform = rememberTransformableState { zoomChange, panChange, _ ->
        scale = (scale * zoomChange).coerceIn(1f, 5f)
        offset = if (scale <= 1.01f) Offset.Zero else offset + panChange
        onZoomChanged(scale > 1.01f)
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .transformable(transform)
            .pointerInput(image) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = {
                        scale = if (scale > 1.01f) 1f else 2.5f
                        offset = Offset.Zero
                        onZoomChanged(scale > 1.01f)
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        image?.let {
            Image(
                bitmap = it,
                contentDescription = null,
                modifier = Modifier.fillMaxSize().graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y,
                ),
                contentScale = ContentScale.Fit,
            )
        } ?: Text("Loading media…", color = Color.White, textAlign = TextAlign.Center)
    }
}

@Composable
private fun NormalVideoPage(uri: Uri) {
    val context = LocalContext.current
    val player = remember(uri) { ExoPlayer.Builder(context).build().apply { setMediaItem(MediaItem.fromUri(uri)); prepare(); playWhenReady = true } }
    DisposableEffect(player) { onDispose { player.release() } }
    AndroidView(factory = { PlayerView(it).apply { this.player = player; useController = true } }, modifier = Modifier.fillMaxSize())
}

@Composable
private fun ProtectedVideoPage(id: String, load: ((String, (Result<ByteArray>) -> Unit) -> Unit)?) {
    var bytes by remember(id) { mutableStateOf<ByteArray?>(null) }
    LaunchedEffect(id) { load?.invoke(id) { bytes = it.getOrNull() } }
    DisposableEffect(bytes) { onDispose { bytes?.fill(0) } }
    bytes?.let { ProtectedVideoSurface(it) } ?: Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Loading media…", color = Color.White) }
}

@Composable
@UnstableApi
private fun ProtectedVideoSurface(bytes: ByteArray) {
    val context = LocalContext.current
    val player = remember(bytes) {
        val factory = DataSource.Factory { ByteArrayDataSource(bytes) }
        ExoPlayer.Builder(context).setMediaSourceFactory(DefaultMediaSourceFactory(factory)).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.parse("memory://private-gallery/video")))
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    AndroidView(factory = { PlayerView(it).apply { this.player = player; useController = true } }, modifier = Modifier.fillMaxSize())
}
