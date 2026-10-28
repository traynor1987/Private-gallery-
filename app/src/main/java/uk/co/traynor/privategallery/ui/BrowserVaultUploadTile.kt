package uk.co.traynor.privategallery.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import uk.co.traynor.privategallery.core.vault.VaultItem
import uk.co.traynor.privategallery.core.ui.VaultPreviewPolicy
import java.util.concurrent.atomic.AtomicBoolean

/** The normal bounded, encrypted Vault preview loader supplies only an in-memory thumbnail. */
@Composable
internal fun BrowserVaultUploadTile(
    item: VaultItem,
    selected: Boolean,
    onLoadPreview: (VaultItem, (Result<Bitmap>) -> Unit) -> Unit,
    onSelect: () -> Unit,
) {
    GalleryMediaSelectionTile(item.id, item.displayName, item.mimeType.startsWith("video/"), selected,
        VaultPreviewPolicy.shouldGenerate(item.mimeType, item.plaintextSize),
        { callback -> onLoadPreview(item, callback) }, onSelect)
}

/** Shared app-owned media picker tile. Preview results are borrowed from the owning cache. */
@Composable
internal fun GalleryMediaSelectionTile(
    id: String,
    label: String,
    isVideo: Boolean,
    selected: Boolean,
    shouldLoad: Boolean,
    onLoadPreview: ((Result<Bitmap>) -> Unit) -> Unit,
    onSelect: () -> Unit,
) {
    var preview by remember(id) { mutableStateOf<Bitmap?>(null) }
    var previewFailed by remember(id) { mutableStateOf(false) }
    DisposableEffect(id) {
        val alive = AtomicBoolean(true)
        if (shouldLoad) {
            onLoadPreview { result ->
                val bitmap = result.getOrNull()
                if (alive.get()) {preview = bitmap?.takeUnless { it.isRecycled };previewFailed = preview == null}
            }
        }
        // loadPreview lends the shared memory-cache entry; only the cache owns its lifetime.
        onDispose { alive.set(false); preview = null }
    }
    Column(Modifier.fillMaxWidth().clickable(onClick = onSelect).semantics {
        contentDescription = label
        this.selected = selected
        stateDescription = if (preview != null) "Preview loaded" else if (shouldLoad && !previewFailed) "Loading preview" else "Preview unavailable"
    }) {
        Card(
            Modifier.fillMaxWidth().aspectRatio(1f),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            border = if (selected) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                preview?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                    ?: Text(if (isVideo) "Video" else "Photo", style = MaterialTheme.typography.bodySmall)
                if (isVideo) Icon(Icons.Default.PlayCircle, null, Modifier.align(Alignment.Center))
                if (selected) Icon(Icons.Default.CheckCircle, null, Modifier.align(Alignment.TopEnd).padding(5.dp), tint = MaterialTheme.colorScheme.primary)
            }
        }
        Text(label, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall)
    }
}
