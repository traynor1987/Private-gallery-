package uk.co.traynor.privategallery.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import uk.co.traynor.privategallery.core.editor.PhotoEdit
import uk.co.traynor.privategallery.core.editor.PhotoRenderer
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

/** All source reads use the editor's authenticated callback. Never retain disk or saved-state caches. */
internal suspend fun loadEditorImage(
    id: String,
    active: AtomicBoolean,
    load: ((String, (Result<ByteArray>) -> Unit) -> Unit)?,
    loadForEditing: ((String, () -> Boolean, (Result<ByteArray>) -> Unit) -> Unit)?,
): ByteArray = suspendCancellableCoroutine { continuation ->
    val cancelled = { !continuation.isActive || !active.get() }
    val callback: (Result<ByteArray>) -> Unit = { result ->
        val bytes = result.getOrNull()
        if (cancelled()) bytes?.fill(0)
        else result.fold({ continuation.resume(it) { bytes?.fill(0) } }, { continuation.resumeWith(Result.failure(it)) })
    }
    if (cancelled()) continuation.cancel()
    else if (loadForEditing != null) loadForEditing(id, cancelled, callback)
    else if (load != null) load(id, callback)
    else continuation.resumeWith(Result.failure(IllegalStateException("Image access unavailable")))
}

@Composable
internal fun AiSourceCard(
    number: Int,
    bitmap: Bitmap?,
    selected: Boolean,
    loading: Boolean,
    enabled: Boolean,
    onReplace: () -> Unit,
    onPreview: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedCard(onClick = onReplace, enabled = enabled, modifier = modifier.testTag("ai-source-$number")) {
        Box(Modifier.fillMaxWidth().height(104.dp), contentAlignment = Alignment.Center) {
            if (bitmap != null) Image(bitmap.asImageBitmap(), "Photo $number thumbnail",
                Modifier.fillMaxSize().testTag("ai-source-thumbnail-$number"), contentScale = ContentScale.Fit)
            else if (loading) CircularProgressIndicator(Modifier.size(24.dp))
            else if (selected) Text("Preview unavailable", style = MaterialTheme.typography.labelSmall)
            else Icon(Icons.Default.AddPhotoAlternate, null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(Modifier.fillMaxWidth().padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Photo $number", style = MaterialTheme.typography.titleSmall)
                Text(if (selected) "Tap to replace" else "Tap to choose", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onPreview, enabled = bitmap != null && enabled) {
                Icon(Icons.Default.ZoomIn, "Preview Photo $number")
            }
        }
    }
}

/** Lazy rows and at most two decodes bound plaintext and bitmap memory even in large vaults. */
@Composable
internal fun AiSourcePicker(
    number: Int,
    vaultLabel: String,
    candidates: List<String>,
    active: AtomicBoolean,
    load: ((String, (Result<ByteArray>) -> Unit) -> Unit)?,
    loadForEditing: ((String, () -> Boolean, (Result<ByteArray>) -> Unit) -> Unit)?,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val decodeSlots = remember { Semaphore(2) }
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text("Choose Photo $number") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Photos from $vaultLabel", style = MaterialTheme.typography.bodyMedium)
                if (candidates.isEmpty()) Text("No other photos are available in this vault.")
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(candidates, key = { _, id -> id }) { index, candidate ->
                        AiCandidateRow(candidate, index, active, load, loadForEditing, decodeSlots) { onSelect(candidate) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
private fun AiCandidateRow(
    id: String,
    index: Int,
    active: AtomicBoolean,
    load: ((String, (Result<ByteArray>) -> Unit) -> Unit)?,
    loadForEditing: ((String, () -> Boolean, (Result<ByteArray>) -> Unit) -> Unit)?,
    decodeSlots: Semaphore,
    onSelect: () -> Unit,
) {
    var bitmap by remember(id) { mutableStateOf<Bitmap?>(null) }
    var failed by remember(id) { mutableStateOf(false) }
    val currentLoad by rememberUpdatedState(load)
    val currentAuthenticatedLoad by rememberUpdatedState(loadForEditing)
    DisposableEffect(bitmap) { val owned = bitmap; onDispose { owned?.recycle() } }
    LaunchedEffect(id) {
        decodeSlots.withPermit {
            var bytes: ByteArray? = null
            var rendered: Bitmap? = null
            try {
                bytes = loadEditorImage(id, active, currentLoad, currentAuthenticatedLoad)
                withContext(Dispatchers.Default) { rendered = PhotoRenderer.render(bytes!!, PhotoEdit(), true, 65_536) }
                ensureActive()
                if (active.get()) { bitmap = rendered; rendered = null }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { failed = true }
            catch (_: OutOfMemoryError) { failed = true }
            finally { bytes?.fill(0); rendered?.recycle() }
        }
    }
    OutlinedCard(onClick = onSelect, enabled = bitmap != null && active.get(), modifier = Modifier.fillMaxWidth().testTag("ai-candidate-$id")) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                bitmap?.let { Image(it.asImageBitmap(), "Vault photo ${index + 1}", Modifier.fillMaxSize().testTag("ai-candidate-thumbnail-$id"), contentScale = ContentScale.Fit) }
                    ?: if (failed) Text("Unavailable", style = MaterialTheme.typography.labelSmall) else CircularProgressIndicator(Modifier.size(24.dp))
            }
            Text("Photo ${index + 1}", style = MaterialTheme.typography.bodyLarge)
        }
    }
}
