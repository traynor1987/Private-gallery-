package uk.co.traynor.privategallery.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.platform.testTag
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.resume
import android.graphics.BitmapFactory
import android.graphics.Bitmap
import android.net.Uri
import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.GestureDetector
import android.view.ScaleGestureDetector
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import kotlin.math.abs
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
    onSaveEditedCopy: ((String, ByteArray, () -> Boolean, (Result<Unit>) -> Unit) -> Unit)? = null,
    onAddToCollection: ((ViewerMediaEntry) -> Unit)? = null,
    restrictedIds: Set<String> = emptySet(),
    onSaveRemoteCopy: ((String, ByteArray, () -> Boolean, (Result<Unit>) -> Unit) -> Unit)? = null,
    onLoadEditorBytes: ((String, () -> Boolean, (Result<ByteArray>) -> Unit) -> Unit)? = null,
    onLoadVideoBytes: ((String, () -> Boolean, (Int) -> Unit, (Result<ByteArray>) -> Unit) -> Unit)? = null,
    onSaveAiCopy: ((String, ByteArray, uk.co.traynor.privategallery.core.editor.AiEditProvenance, () -> Boolean, (Result<Unit>) -> Unit) -> Unit)? = null,
) {
    if (entries.isEmpty()) return
    val pagerState = rememberPagerState(
        initialPage = MediaViewerPolicy.initialPage(initialIndex, entries.size),
        pageCount = { entries.size },
    )
    val pagerScope = rememberCoroutineScope()
    var controlsVisible by remember { mutableStateOf(true) }
    var zoomed by remember { mutableStateOf(false) }
    var showMore by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var editing by remember(entries) { mutableStateOf(false) }
    var imageEdits by remember { mutableStateOf<Map<String, ImageEditState>>(emptyMap()) }
    var editsLoaded by remember { mutableStateOf<Set<String>>(emptySet()) }
    LaunchedEffect(entries) { pagerState.scrollToPage(MediaViewerPolicy.initialPage(initialIndex, entries.size)) }
    val current = entries.getOrNull(pagerState.currentPage) ?: entries.first()
    BackHandler(enabled = !editing) { onClose(pagerState.currentPage) }

    LaunchedEffect(current.id, source) {
        if (source == MediaViewerSource.VAULT && current.mimeType.startsWith("image/")) {
            if (onLoadImageEdit == null) editsLoaded = editsLoaded + current.id
            onLoadImageEdit?.invoke(current.id) { edit ->
                editsLoaded = editsLoaded + current.id
                imageEdits = if (edit == null) imageEdits - current.id else imageEdits + (current.id to edit)
            }
        }
    }

    LaunchedEffect(pagerState.currentPage) { zoomed = false }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (editing && source == MediaViewerSource.VAULT && current.mimeType.startsWith("image/")) {
            PhotoEditor(
                id = current.id,
                load = onLoadProtectedBytes,
                loadForEditing = onLoadEditorBytes,
                initialCrop = imageEdits[current.id]?.crop,
                onCancel = { editing = false; controlsVisible = true },
                onSaveAi = onSaveAiCopy?.let { save -> { bytes, provenance, cancelled, completed -> save(current.id, bytes, provenance, cancelled, completed) } },
                onSaveRemote = onSaveRemoteCopy?.let { save -> { bytes, cancelled, completed -> save(current.id, bytes, cancelled, completed) } },
                onSave = { bytes, cancelled, completed ->
                    onSaveEditedCopy?.invoke(current.id, bytes, cancelled, completed)
                        ?: completed(Result.failure(IllegalStateException("Save unavailable")))
                },
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
                    // Reserve chrome space so native playback/seek controls stay reachable.
                    Box(Modifier.fillMaxSize()) {
                        if (source == MediaViewerSource.GALLERY) NormalVideoPage(checkNotNull(entry.uri), { onClose(pagerState.currentPage) }, { showMore = true })
                        else ProtectedVideoPage(entry.id, entry.mimeType, onLoadProtectedBytes, { onClose(pagerState.currentPage) }, { showMore = true }, onLoadVideoBytes)
                    }
                } else {
                    key(entry.id) {
                        val onFitSwipe: (Int) -> Unit = { direction ->
                            val target = (pagerState.currentPage + direction).coerceIn(0, entries.lastIndex)
                            if (target != pagerState.currentPage) pagerScope.launch { pagerState.animateScrollToPage(target) }
                        }
                        if (source == MediaViewerSource.GALLERY) NormalImagePage(checkNotNull(entry.uri), onTap = { controlsVisible = MediaViewerPolicy.toggleControls(controlsVisible) }, onFitSwipe = onFitSwipe) { zoomed = it }
                        else ProtectedImagePage(entry.id, onLoadProtectedBytes, imageEdits[entry.id]?.crop, onLoadEditorBytes, onTap = { controlsVisible = MediaViewerPolicy.toggleControls(controlsVisible) }, onFitSwipe = onFitSwipe) { zoomed = it }
                    }
                }
            }
        }
        if (controlsVisible && !editing && !current.mimeType.startsWith("video/")) {
            ViewerTopBar("${pagerState.currentPage + 1} / ${entries.size}", { onClose(pagerState.currentPage) }, { showMore = true }, Modifier.align(Alignment.TopCenter))
            ViewerBottomBar(
                onEdit = if (source == MediaViewerSource.VAULT && current.mimeType.startsWith("image/") && onSaveEditedCopy != null && current.id in editsLoaded) ({ editing = true }) else null,
                onRestore = onRestore?.takeIf { current.id !in restrictedIds }?.let { action -> { action(current) } },
                onCollection = onAddToCollection?.let { action -> { action(current) } },
                onCopy = onCopyToVault?.let { action -> { action(current) } },
                onMove = onMoveToVault?.let { action -> { action(current) } },
                onMore = { showMore = true }, modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
    if (showMore && !editing) {
        GalleryMenuSheet(if (source == MediaViewerSource.VAULT) "Media actions" else "Gallery actions", { showMore = false }) {
            onAddToCollection?.let { action -> SheetAction("Add to collection", Icons.Default.CreateNewFolder) { showMore = false; action(current) } }
            onRestore?.takeIf { current.id !in restrictedIds }?.let { action -> SheetAction("Restore a copy", Icons.Default.FileDownload) { showMore = false; action(current) } }
            onRestoreAndRemove?.takeIf { current.id !in restrictedIds }?.let { action -> SheetAction("Restore and remove from Vault", Icons.Default.MoveToInbox) { showMore = false; action(current) } }
            onCopyToVault?.let { action -> SheetAction("Copy to Vault", Icons.Default.ContentCopy) { showMore = false; action(current) } }
            onMoveToVault?.let { action -> SheetAction("Move to Vault", Icons.Default.Lock) { showMore = false; action(current) } }
            onDeleteFromVault?.let { SheetAction("Delete from Vault", Icons.Default.DeleteOutline, destructive = true) { showMore = false; confirmDelete = true } }
        }
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
private fun NormalImagePage(uri: Uri, onTap: () -> Unit, onFitSwipe: (Int) -> Unit, onZoomChanged: (Boolean) -> Unit) {
    val context = LocalContext.current
    var image by remember(uri) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    LaunchedEffect(uri) {
        image = withContext(Dispatchers.IO) {
            runCatching { context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it)?.asImageBitmap() } }.getOrNull()
        }
    }
    ViewerImage(image, onTap, onFitSwipe, onZoomChanged)
}

@Composable
private fun ProtectedImagePage(
    id: String,
    load: ((String, (Result<ByteArray>) -> Unit) -> Unit)?,
    crop: NormalizedCrop?,
    loadCancellable: ((String, () -> Boolean, (Result<ByteArray>) -> Unit) -> Unit)?,
    onTap: () -> Unit,
    onFitSwipe: (Int) -> Unit,
    onZoomChanged: (Boolean) -> Unit,
) {
    var image by remember(id) { mutableStateOf<Bitmap?>(null) }
    var error by remember(id) { mutableStateOf(false) }
    DisposableEffect(image) { val bitmap = image; onDispose { bitmap?.recycle() } }
    LaunchedEffect(id, crop) {
        var bytes: ByteArray? = null
        var rendered: Bitmap? = null
        try {
            bytes = suspendCancellableCoroutine { continuation ->
                val callback: (Result<ByteArray>) -> Unit = { result ->
                    val buffer = result.getOrNull()
                    if (!continuation.isActive) buffer?.fill(0)
                    else result.fold({ continuation.resume(it) { buffer?.fill(0) } }, { continuation.resumeWith(Result.failure(it)) })
                }
                if (loadCancellable != null) loadCancellable(id, { !continuation.isActive }, callback)
                else if (load != null) load(id, callback)
                else continuation.resumeWith(Result.failure(IllegalStateException()))
            }
            withContext(Dispatchers.Default) {
                rendered = uk.co.traynor.privategallery.core.editor.PhotoRenderer.render(bytes!!, uk.co.traynor.privategallery.core.editor.PhotoEdit(crop = crop ?: NormalizedCrop.ORIGINAL), false)
            }
            ensureActive()
            image = rendered; rendered = null
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: OutOfMemoryError) { error = true }
        catch (_: Exception) { error = true }
        finally { bytes?.fill(0); rendered?.recycle() }
    }
    if (error) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("This image could not be displayed safely.", color = Color.White) }
    else ViewerImage(image?.asImageBitmap(), onTap, onFitSwipe, onZoomChanged)
}

@Composable
@OptIn(ExperimentalComposeUiApi::class)
private fun ViewerImage(
    image: androidx.compose.ui.graphics.ImageBitmap?,
    onTap: () -> Unit,
    onFitSwipe: (Int) -> Unit,
    onZoomChanged: (Boolean) -> Unit,
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    val context = LocalContext.current
    var lastPanPoint by remember { mutableStateOf<Offset?>(null) }
    var downPoint by remember { mutableStateOf<Offset?>(null) }
    val scaleDetector = remember(context) {
        ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                scale = MediaViewerPolicy.clampedScale(scale * detector.scaleFactor)
                offset = MediaViewerPolicy.boundedPan(offset, scale, viewport)
                onZoomChanged(scale > 1.01f)
                return true
            }
        })
    }
    val tapDetector = remember(context, viewport) {
        GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(event: MotionEvent): Boolean {
                onTap()
                return true
            }

            override fun onDoubleTap(event: MotionEvent): Boolean {
                scale = MediaViewerPolicy.doubleTapScale(scale)
                offset = MediaViewerPolicy.boundedPan(Offset.Zero, scale, viewport)
                onZoomChanged(scale > 1.01f)
                return true
            }
        })
    }
    // This owns the Android pointer stream from ACTION_DOWN. Returning false at DOWN makes
    // Compose's pager retain the gesture and the ScaleGestureDetector never sees the second
    // pointer on several production WebView/Compose combinations. At fitted scale we explicitly
    // route a deliberate horizontal swipe to the pager; when zoomed every drag pans instead.
    val imageGestureModifier = Modifier.pointerInteropFilter { event ->
        scaleDetector.onTouchEvent(event)
        tapDetector.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastPanPoint = Offset(event.x, event.y)
                downPoint = lastPanPoint
                true
            }
            MotionEvent.ACTION_MOVE -> {
                val previous = lastPanPoint
                lastPanPoint = Offset(event.x, event.y)
                if (scaleDetector.isInProgress || scale > 1.01f) {
                    previous?.let { offset = MediaViewerPolicy.boundedPan(offset + (Offset(event.x, event.y) - it), scale, viewport) }
                }
                true
            }
            MotionEvent.ACTION_UP -> {
                val start = downPoint
                val end = Offset(event.x, event.y)
                if (!scaleDetector.isInProgress && scale <= 1.01f && start != null) {
                    val horizontal = end.x - start.x
                    val vertical = end.y - start.y
                    if (abs(horizontal) > 72f && abs(horizontal) > abs(vertical) * 1.4f) onFitSwipe(if (horizontal < 0) 1 else -1)
                }
                lastPanPoint = null
                downPoint = null
                true
            }
            MotionEvent.ACTION_CANCEL -> { lastPanPoint = null; downPoint = null; true }
            else -> true
        }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { viewport = it; offset = MediaViewerPolicy.boundedPan(offset, scale, it) }
            .then(imageGestureModifier).testTag("viewer-image"),
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
internal fun CropCanvas(bitmap: Bitmap, crop: NormalizedCrop, onCropChanged: (NormalizedCrop) -> Unit, modifier: Modifier = Modifier, onGestureFinished: () -> Unit = {}) {
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var target by remember { mutableStateOf<CropDragTarget?>(null) }
    val imageRect = remember(canvasSize, bitmap) { fitImageRect(canvasSize, bitmap.width.toFloat() / bitmap.height.coerceAtLeast(1)) }
    val latestCrop by rememberUpdatedState(crop)
    val finishGesture by rememberUpdatedState(onGestureFinished)
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
                        finishGesture()
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
private fun NormalVideoPage(uri: Uri, close: () -> Unit, more: () -> Unit) {
    val item = remember(uri) { MediaItem.fromUri(uri) }
    PrivateVideoPlayer(item, onClose = close, onMore = more)

}

@Composable
private fun ProtectedVideoPage(id: String, mimeType: String, load: ((String, (Result<ByteArray>) -> Unit) -> Unit)?, close: () -> Unit, more: () -> Unit,
    loadCancellable: ((String, () -> Boolean, (Int) -> Unit, (Result<ByteArray>) -> Unit) -> Unit)?) {
    var bytes by remember(id) { mutableStateOf<ByteArray?>(null) }
    var error by remember(id) { mutableStateOf<String?>(null) }
    var percent by remember(id) { androidx.compose.runtime.mutableIntStateOf(0) }
    var attempt by remember(id) { androidx.compose.runtime.mutableIntStateOf(0) }
    val currentLoad by rememberUpdatedState(load)
    val currentCancellable by rememberUpdatedState(loadCancellable)
    DisposableEffect(id, attempt) {
        val active = java.util.concurrent.atomic.AtomicBoolean(true)
        var owned: ByteArray? = null
        bytes = null; error = null; percent = 0
        VaultPlaybackDiagnostics.begin()
        val completed: (Result<ByteArray>) -> Unit = { result ->
            if (!active.get()) result.getOrNull()?.fill(0)
            else {
                owned = result.getOrNull()
                bytes = owned
                VaultPlaybackDiagnostics.record(result.exceptionOrNull()?.let {
                    when (VaultVideoDiagnostics.readFailureCode(it)) {
                        "MEMORY_LIMIT" -> VaultPlaybackEvent.READ_MEMORY_LIMIT
                        "AUTHENTICATION_FAILED" -> VaultPlaybackEvent.READ_AUTHENTICATION_FAILED
                        "FILE_UNAVAILABLE" -> VaultPlaybackEvent.READ_FILE_UNAVAILABLE
                        else -> VaultPlaybackEvent.READ_FAILED
                    }
                } ?: VaultPlaybackEvent.AUTHENTICATED)
                error = result.exceptionOrNull()?.let { VaultVideoDiagnostics.userMessageForReadFailure() + " (" + VaultVideoDiagnostics.readFailureCode(it) + ")" }
            }
        }
        if (currentCancellable != null) currentCancellable!!.invoke(id, { !active.get() }, { if (active.get()) {
            percent = it
            // Ten-percent buckets avoid retaining every progress callback.
            VaultPlaybackDiagnostics.record(VaultPlaybackEvent.READ_PROGRESS, it.coerceIn(0, 100) / 10 * 10)
        } }, completed)
        else if (currentLoad != null) currentLoad!!.invoke(id, completed)
        else completed(Result.failure(IllegalStateException("Media reader unavailable")))
        onDispose { active.set(false); owned?.fill(0); owned = null; bytes = null; VaultPlaybackDiagnostics.record(VaultPlaybackEvent.VIEWER_CLOSED) }
    }
    bytes?.let { ProtectedVideoSurface(it, mimeType, close, more) }
        ?: Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (error != null) AlertDialog(onDismissRequest = close,
                title = { Text("Video could not be opened") },
                text = { Text(error!!) },
                confirmButton = { TextButton(onClick = { attempt++ }) { Text("Retry") } },
                dismissButton = { TextButton(onClick = close) { Text("Close") } })
            else VideoLoadingDialog(if (percent < 99) "Decrypting video" else "Verifying video", percent, close)
        }
}

@Composable
@SuppressLint("UnsafeOptInUsageError")
private fun ProtectedVideoSurface(bytes: ByteArray, mimeType: String, close: () -> Unit, more: () -> Unit) {
    val factory = remember(bytes) { DataSource.Factory { ByteArrayDataSource(bytes) } }
    val item = remember(mimeType) { VaultVideoPlaybackSpec.mediaItem(mimeType) }
    PrivateVideoPlayer(item, factory, onClose = close, onMore = more, recordVaultDiagnostics = true)
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
    fun readFailureCode(failure: Throwable): String = when (failure) {
        is OutOfMemoryError -> "MEMORY_LIMIT"
        is javax.crypto.AEADBadTagException -> "AUTHENTICATION_FAILED"
        is java.io.FileNotFoundException -> "FILE_UNAVAILABLE"
        else -> "READ_FAILED"
    }
    fun userMessageForReadFailure(): String = "Protected video could not be opened."
    fun userMessageForPlayerError(): String = "Protected video could not be played on this device."
}

object VaultVideoBufferPolicy {
    fun clear(bytes: ByteArray?) {
        bytes?.fill(0)
    }
}
