package uk.co.traynor.privategallery.ui

import android.graphics.BitmapFactory
import android.graphics.Bitmap
import android.net.Uri
import android.annotation.SuppressLint
import android.view.MotionEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.awaitEachGesture
import androidx.compose.ui.input.pointer.awaitFirstDown
import androidx.compose.ui.input.pointer.awaitPointerEvent
import androidx.compose.ui.input.pointer.consume
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.util.calculatePan
import androidx.compose.ui.input.pointer.util.calculateZoom
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import uk.co.traynor.privategallery.core.ui.MediaViewerPolicy
import uk.co.traynor.privategallery.core.ui.MediaViewerSource
import uk.co.traynor.privategallery.core.ui.CropEditorGeometry
import uk.co.traynor.privategallery.core.vault.ImageEditState
import uk.co.traynor.privategallery.core.vault.NormalizedCrop

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
    onLoadImageEdit: ((id: String, onLoaded: (ImageEditState?) -> Unit) -> Unit)? = null,
    onApplyImageCrop: ((id: String, crop: NormalizedCrop, onComplete: (Result<ImageEditState>) -> Unit) -> Unit)? = null,
    onUndoImageCrop: ((id: String, onComplete: (Result<ImageEditState?>) -> Unit) -> Unit)? = null,
    onResetImageCrop: ((id: String, onComplete: (Result<Unit>) -> Unit) -> Unit)? = null,
    onCropChanged: () -> Unit = {},
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
    var editing by remember { mutableStateOf(false) }
    var imageEdits by remember { mutableStateOf<Map<String, ImageEditState>>(emptyMap()) }
    val current = entries.getOrNull(pagerState.currentPage) ?: return
    BackHandler { if (editing) editing = false else onClose(pagerState.currentPage) }

    LaunchedEffect(current.id, source) {
        if (source == MediaViewerSource.VAULT && current.mimeType.startsWith("image/")) {
            onLoadImageEdit?.invoke(current.id) { edit ->
                imageEdits = if (edit == null) imageEdits - current.id else imageEdits + (current.id to edit)
            }
        }
    }

    LaunchedEffect(controlsVisible, pagerState.currentPage) {
        if (controlsVisible) {
            delay(2800)
            controlsVisible = false
        }
    }
    LaunchedEffect(pagerState.currentPage) { zoomed = false }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (editing && source == MediaViewerSource.VAULT && current.mimeType.startsWith("image/")) {
            VaultCropEditor(
                id = current.id,
                load = onLoadProtectedBytes,
                existing = imageEdits[current.id],
                onCancel = { editing = false },
                onApply = { crop ->
                    if (crop.isOriginal) {
                        onResetImageCrop?.invoke(current.id) { result -> result.onSuccess {
                            imageEdits = imageEdits - current.id
                            onCropChanged()
                            editing = false
                        } }
                    } else onApplyImageCrop?.invoke(current.id, crop) { result ->
                        result.onSuccess { edit -> imageEdits = imageEdits + (current.id to edit); onCropChanged(); editing = false }
                    }
                },
                onUndo = { onUndoImageCrop?.invoke(current.id) { result -> result.onSuccess { edit ->
                    imageEdits = if (edit == null) imageEdits - current.id else imageEdits + (current.id to edit)
                    onCropChanged()
                } } },
            )
        } else HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = MediaViewerPolicy.canSwipePager(zoomed),
            beyondViewportPageCount = 0,
        ) { page ->
            // The pager composes only the visible item. Vault bytes are never prefetched for neighbours.
            if (page == pagerState.currentPage) {
                val entry = entries[page]
                if (entry.mimeType.startsWith("video/")) {
                    if (source == MediaViewerSource.GALLERY) NormalVideoPage(checkNotNull(entry.uri))
                    else ProtectedVideoPage(entry.id, entry.mimeType, onLoadProtectedBytes)
                } else {
                    key(entry.id) {
                        if (source == MediaViewerSource.GALLERY) NormalImagePage(checkNotNull(entry.uri), onTap = { controlsVisible = MediaViewerPolicy.toggleControls(controlsVisible) }) { zoomed = it }
                        else ProtectedImagePage(entry.id, onLoadProtectedBytes, imageEdits[entry.id]?.crop, onTap = { controlsVisible = MediaViewerPolicy.toggleControls(controlsVisible) }) { zoomed = it }
                    }
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
                        if (current.mimeType.startsWith("image/")) TextButton(onClick = { editing = true; controlsVisible = false }) { Text("Edit crop") }
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
    crop: NormalizedCrop?,
    onTap: () -> Unit,
    onZoomChanged: (Boolean) -> Unit,
) {
    var bytes by remember(id) { mutableStateOf<ByteArray?>(null) }
    var image by remember(id) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    LaunchedEffect(id) { load?.invoke(id) { bytes = it.getOrNull() } }
    LaunchedEffect(bytes) {
        bytes?.let { clearable ->
            image = withContext(Dispatchers.IO) {
                BitmapFactory.decodeByteArray(clearable, 0, clearable.size)?.let { bitmap ->
                    VaultImageEdits.visuallyOrient(clearable, bitmap).asImageBitmap()
                }
            }
            clearable.fill(0)
            bytes = null
        }
    }
    // The edit can change while the same decrypted bytes remain in memory.
    val displayed = remember(image, crop) {
        val sourceImage = image
        if (sourceImage == null || crop == null || crop.isOriginal) sourceImage else {
            val original = sourceImage.asAndroidBitmap()
            VaultImageEdits.crop(original, crop).asImageBitmap()
        }
    }
    ViewerImage(displayed, onTap, onZoomChanged)
}

@Composable
private fun ViewerImage(image: androidx.compose.ui.graphics.ImageBitmap?, onTap: () -> Unit, onZoomChanged: (Boolean) -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    // Consume only two-finger transforms or one-finger panning after zoom. At fitted scale a
    // normal single-finger horizontal drag remains unconsumed for HorizontalPager navigation.
    val imageGestureModifier = Modifier.pointerInput(image) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            var keepGoing: Boolean
            do {
                val event = awaitPointerEvent()
                val activePointers = event.changes.count { it.pressed }
                if (activePointers >= 2) {
                    scale = MediaViewerPolicy.clampedScale(scale * event.calculateZoom())
                    offset = MediaViewerPolicy.boundedPan(offset + event.calculatePan(), scale, viewport)
                    onZoomChanged(scale > 1.01f)
                    event.changes.forEach { it.consume() }
                } else if (scale > 1.01f && event.calculatePan() != Offset.Zero) {
                    offset = MediaViewerPolicy.boundedPan(offset + event.calculatePan(), scale, viewport)
                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                }
                keepGoing = event.changes.any { it.pressed }
            } while (keepGoing)
        }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { viewport = it; offset = MediaViewerPolicy.boundedPan(offset, scale, it) }
            .then(imageGestureModifier)
            .pointerInput(image) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = {
                        scale = MediaViewerPolicy.doubleTapScale(scale)
                        offset = MediaViewerPolicy.boundedPan(Offset.Zero, scale, viewport)
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

private enum class CropDragTarget { MOVE, LEFT, TOP, RIGHT, BOTTOM, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }

/** Focused, in-memory crop surface. It writes only normalized edit metadata on Apply. */
@Composable
private fun VaultCropEditor(
    id: String,
    load: ((String, (Result<ByteArray>) -> Unit) -> Unit)?,
    existing: ImageEditState?,
    onCancel: () -> Unit,
    onApply: (NormalizedCrop) -> Unit,
    onUndo: () -> Unit,
) {
    var bytes by remember(id) { mutableStateOf<ByteArray?>(null) }
    var bitmap by remember(id) { mutableStateOf<Bitmap?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var analysing by remember { mutableStateOf(false) }
    var draft by remember(id, existing) { mutableStateOf(existing?.crop ?: NormalizedCrop.ORIGINAL) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(id) { load?.invoke(id) { bytes = it.getOrNull(); if (it.isFailure) message = "Image could not be opened." } }
    LaunchedEffect(bytes) {
        bytes?.let { data ->
            bitmap = withContext(Dispatchers.IO) { BitmapFactory.decodeByteArray(data, 0, data.size)?.let { VaultImageEdits.visuallyOrient(data, it) } }
            data.fill(0); bytes = null
        }
    }
    val canApply = draft != (existing?.crop ?: NormalizedCrop.ORIGINAL)
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        // Crop mode deliberately reserves workspace around the image. The normal viewer is
        // edge-to-edge; editing is not, because handles must be reachable.
        bitmap?.let { image ->
            CropCanvas(
                bitmap = image,
                crop = draft,
                onCropChanged = { draft = it; message = null },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 28.dp, vertical = 72.dp),
            )
        }
            ?: Text(message ?: "Loading image…", color = Color.White, modifier = Modifier.align(Alignment.Center))
        Surface(modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth(), color = Color.Black.copy(alpha = .76f)) {
            Row(Modifier.padding(8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onCancel) { Text("Cancel") }
                Text("Crop", color = Color.White, style = MaterialTheme.typography.titleMedium)
                TextButton(enabled = bitmap != null && !analysing, onClick = {
                    val image = bitmap ?: return@TextButton
                    analysing = true
                    message = "Analysing…"
                    scope.launch {
                        val candidate = withContext(Dispatchers.Default) { VaultAutoCrop.detect(image) }
                        analysing = false
                        if (candidate == null) {
                            message = "No obvious borders detected"
                        } else {
                            draft = candidate
                            message = "Auto crop preview — adjust or apply"
                        }
                    }
                }) { Text(if (analysing) "Analysing…" else "Auto crop") }
            }
        }
        Surface(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(), color = Color.Black.copy(alpha = .78f)) {
            Column(Modifier.padding(8.dp)) {
                message?.let { Text(it, color = Color.White, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    TextButton(onClick = onUndo, enabled = existing != null) { Text("Undo") }
                    TextButton(onClick = { draft = NormalizedCrop.ORIGINAL; message = "Original preview" }, enabled = !draft.isOriginal) { Text("Original") }
                    TextButton(onClick = { onApply(draft) }, enabled = canApply) { Text("Apply") }
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalComposeUiApi::class)
internal fun CropCanvas(bitmap: Bitmap, crop: NormalizedCrop, onCropChanged: (NormalizedCrop) -> Unit, modifier: Modifier = Modifier) {
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var target by remember { mutableStateOf<CropDragTarget?>(null) }
    val imageRect = remember(canvasSize, bitmap) { fitImageRect(canvasSize, bitmap.width.toFloat() / bitmap.height.coerceAtLeast(1)) }
    val latestCrop by rememberUpdatedState(crop)
    val density = LocalDensity.current
    val hitTarget = with(density) { 32.dp.toPx() }
    val cropRect = imageRect.cropRect(crop)
    var previousPointer by remember { mutableStateOf<Offset?>(null) }
    Box(modifier.fillMaxSize().onSizeChanged { canvasSize = it }) {
        Image(bitmap.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        androidx.compose.foundation.Canvas(
            // The crop surface owns the Android touch stream while editing. This avoids
            // gesture competition with image/pager machinery and keeps a selected handle
            // stable until ACTION_UP.
            modifier = Modifier.fillMaxSize().pointerInteropFilter { event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        val point = Offset(event.x, event.y)
                        target = imageRect.cropRect(latestCrop).targetFor(point, hitTarget)
                        previousPointer = point
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val current = Offset(event.x, event.y)
                        val previous = previousPointer
                        val active = target
                        if (previous != null && active != null) {
                            onCropChanged(latestCrop.adjust(active, current - previous, imageRect))
                            previousPointer = current
                        }
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        target = null
                        previousPointer = null
                        true
                    }
                    else -> true
                }
            },
        ) {
            val shade = Color.Black.copy(alpha = .52f)
            drawRect(shade, topLeft = Offset.Zero, size = androidx.compose.ui.geometry.Size(size.width, cropRect.top))
            drawRect(shade, topLeft = Offset(0f, cropRect.bottom), size = androidx.compose.ui.geometry.Size(size.width, size.height - cropRect.bottom))
            drawRect(shade, topLeft = Offset(0f, cropRect.top), size = androidx.compose.ui.geometry.Size(cropRect.left, cropRect.height))
            drawRect(shade, topLeft = Offset(cropRect.right, cropRect.top), size = androidx.compose.ui.geometry.Size(size.width - cropRect.right, cropRect.height))
            drawRect(Color.White, topLeft = cropRect.topLeft, size = cropRect.size, style = Stroke(3.dp.toPx()))
            listOf(cropRect.topLeft, Offset(cropRect.right, cropRect.top), Offset(cropRect.left, cropRect.bottom), Offset(cropRect.right, cropRect.bottom)).forEach {
                drawCircle(Color.White, 10.dp.toPx(), it)
            }
        }
    }
}

private fun fitImageRect(size: IntSize, aspect: Float): Rect {
    if (size.width == 0 || size.height == 0) return Rect.Zero
    val fitted = CropEditorGeometry.fitImage(size.width.toFloat(), size.height.toFloat(), aspect)
    return Rect(fitted.left, fitted.top, fitted.right, fitted.bottom)
}

private fun Rect.cropRect(crop: NormalizedCrop) = Rect(
    left + width * crop.left, top + height * crop.top, left + width * crop.right, top + height * crop.bottom,
)

private fun Rect.targetFor(point: Offset, hit: Float): CropDragTarget {
    fun near(x: Float, y: Float) = kotlin.math.hypot(point.x - x, point.y - y) <= hit
    return when {
        near(left, top) -> CropDragTarget.TOP_LEFT
        near(right, top) -> CropDragTarget.TOP_RIGHT
        near(left, bottom) -> CropDragTarget.BOTTOM_LEFT
        near(right, bottom) -> CropDragTarget.BOTTOM_RIGHT
        kotlin.math.abs(point.x - left) <= hit -> CropDragTarget.LEFT
        kotlin.math.abs(point.x - right) <= hit -> CropDragTarget.RIGHT
        kotlin.math.abs(point.y - top) <= hit -> CropDragTarget.TOP
        kotlin.math.abs(point.y - bottom) <= hit -> CropDragTarget.BOTTOM
        contains(point) -> CropDragTarget.MOVE
        else -> CropDragTarget.MOVE
    }
}

private fun NormalizedCrop.adjust(target: CropDragTarget, delta: Offset, bounds: Rect): NormalizedCrop {
    if (bounds.width <= 0f || bounds.height <= 0f) return this
    val dx = delta.x / bounds.width
    val dy = delta.y / bounds.height
    var l = left; var t = top; var r = right; var b = bottom
    when (target) {
        CropDragTarget.MOVE -> { val width = r - l; val height = b - t; l = (l + dx).coerceIn(0f, 1f - width); t = (t + dy).coerceIn(0f, 1f - height); r = l + width; b = t + height }
        CropDragTarget.LEFT -> l = (l + dx).coerceIn(0f, r - NormalizedCrop.MINIMUM_SIDE)
        CropDragTarget.RIGHT -> r = (r + dx).coerceIn(l + NormalizedCrop.MINIMUM_SIDE, 1f)
        CropDragTarget.TOP -> t = (t + dy).coerceIn(0f, b - NormalizedCrop.MINIMUM_SIDE)
        CropDragTarget.BOTTOM -> b = (b + dy).coerceIn(t + NormalizedCrop.MINIMUM_SIDE, 1f)
        CropDragTarget.TOP_LEFT -> { l = (l + dx).coerceIn(0f, r - NormalizedCrop.MINIMUM_SIDE); t = (t + dy).coerceIn(0f, b - NormalizedCrop.MINIMUM_SIDE) }
        CropDragTarget.TOP_RIGHT -> { r = (r + dx).coerceIn(l + NormalizedCrop.MINIMUM_SIDE, 1f); t = (t + dy).coerceIn(0f, b - NormalizedCrop.MINIMUM_SIDE) }
        CropDragTarget.BOTTOM_LEFT -> { l = (l + dx).coerceIn(0f, r - NormalizedCrop.MINIMUM_SIDE); b = (b + dy).coerceIn(t + NormalizedCrop.MINIMUM_SIDE, 1f) }
        CropDragTarget.BOTTOM_RIGHT -> { r = (r + dx).coerceIn(l + NormalizedCrop.MINIMUM_SIDE, 1f); b = (b + dy).coerceIn(t + NormalizedCrop.MINIMUM_SIDE, 1f) }
    }
    return NormalizedCrop(l, t, r, b)
}

@Composable
private fun NormalVideoPage(uri: Uri) {
    val context = LocalContext.current
    val player = remember(uri) { ExoPlayer.Builder(context).build().apply { setMediaItem(MediaItem.fromUri(uri)); prepare(); playWhenReady = true } }
    DisposableEffect(player) { onDispose { player.release() } }
    AndroidView(factory = { PlayerView(it).apply { this.player = player; useController = true } }, modifier = Modifier.fillMaxSize())
}

@Composable
private fun ProtectedVideoPage(id: String, mimeType: String, load: ((String, (Result<ByteArray>) -> Unit) -> Unit)?) {
    var bytes by remember(id) { mutableStateOf<ByteArray?>(null) }
    var error by remember(id) { mutableStateOf<String?>(null) }
    LaunchedEffect(id) {
        load?.invoke(id) { result ->
            bytes = result.getOrNull()
            error = result.exceptionOrNull()?.let { VaultVideoDiagnostics.userMessageForReadFailure() }
        }
    }
    // Capture this composition's buffer. Reading the mutable state in onDispose
    // can otherwise wipe a newly-loaded buffer while disposing the previous one.
    val bufferForDisposal = bytes
    DisposableEffect(bufferForDisposal) { onDispose { VaultVideoBufferPolicy.clear(bufferForDisposal) } }
    bytes?.let { ProtectedVideoSurface(it, mimeType, onPlaybackError = { error = VaultVideoDiagnostics.userMessageForPlayerError() }) }
        ?: Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(error ?: "Loading media…", color = Color.White, textAlign = TextAlign.Center)
        }
}

@Composable
@SuppressLint("UnsafeOptInUsageError")
private fun ProtectedVideoSurface(bytes: ByteArray, mimeType: String, onPlaybackError: () -> Unit) {
    val context = LocalContext.current
    val player = remember(bytes, mimeType) {
        val factory = DataSource.Factory { ByteArrayDataSource(bytes) }
        val mediaSource = ProgressiveMediaSource.Factory(factory)
            .createMediaSource(VaultVideoPlaybackSpec.mediaItem(mimeType))
        ExoPlayer.Builder(context).build().apply {
            setMediaSource(mediaSource)
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(player) {
        val listener = object : androidx.media3.common.Player.Listener {
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) = onPlaybackError()
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }
    AndroidView(factory = { PlayerView(it).apply { this.player = player; useController = true } }, modifier = Modifier.fillMaxSize())
}

/** Preserve the stored MIME and provide a matching non-sensitive synthetic extension for Media3 extractors. */
object VaultVideoPlaybackSpec {
    fun mediaItem(mimeType: String): MediaItem = MediaItem.Builder()
        .setUri(uriFor(mimeType))
        .setMimeType(mimeType(mimeType))
        .build()

    fun uriFor(mimeType: String): Uri = Uri.parse(uriStringFor(mimeType))

    fun uriStringFor(mimeType: String): String = "memory://private-gallery/video.${extensionFor(mimeType)}"

    fun mimeType(value: String): String = value.takeIf { it.startsWith("video/") } ?: "video/*"

    private fun extensionFor(value: String): String = when (mimeType(value)) {
        "video/mp4" -> "mp4"
        "video/webm" -> "webm"
        "video/3gpp" -> "3gp"
        "video/quicktime" -> "mov"
        "video/x-matroska" -> "mkv"
        else -> "video"
    }
}

/** Deliberately avoids filenames, media bytes, keys and decrypted metadata in UI diagnostics. */
object VaultVideoDiagnostics {
    fun userMessageForReadFailure(): String = "Protected video could not be opened."
    fun userMessageForPlayerError(): String = "Protected video could not be played on this device."
}

object VaultVideoBufferPolicy {
    fun clear(bytes: ByteArray?) {
        bytes?.fill(0)
    }
}
