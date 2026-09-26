package uk.co.traynor.privategallery.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

enum class ThumbnailDensity(val label: String, val minSize: Int) {
    COMPACT("Compact", 87), COMFORTABLE("Comfortable", 112), LARGE("Large", 156),
}

@Composable
fun MediaHeader(
    title: String,
    subtitle: String,
    onMenu: (() -> Unit)? = null,
    onAdd: (() -> Unit)? = null,
    onLock: (() -> Unit)? = null,
    onBack: (() -> Unit)? = null,
    onGenerate: (() -> Unit)? = null,
) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to collections") }
        Column(Modifier.weight(1f).padding(start = 6.dp)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (onAdd != null) IconButton(onClick = onAdd) { Icon(Icons.Default.Add, "Add media") }
        if (onGenerate != null) IconButton(onClick = onGenerate) { Icon(Icons.Default.AutoAwesome, "Create image") }
        if (onLock != null) IconButton(onClick = onLock) { Icon(Icons.Default.Lock, "Lock Vault") }
        if (onMenu != null) IconButton(onClick = onMenu) { Icon(Icons.Default.MoreVert, "$title menu") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryMenuSheet(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainerLow, tonalElevation = 0.dp, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().heightIn(max = 600.dp).verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
            Text(title, Modifier.padding(horizontal = 24.dp, vertical = 8.dp).semantics { heading() }, style = MaterialTheme.typography.titleLarge)
            content()
        }
    }
}

@Composable
fun SheetAction(label: String, icon: ImageVector, enabled: Boolean = true, destructive: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(enabled = enabled, role = Role.Button, onClick = onClick).heightIn(min = 52.dp).padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        val tint = (if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface).copy(alpha = if (enabled) 1f else 0.38f)
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
        Text(label, color = tint, style = MaterialTheme.typography.bodyLarge)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ThumbnailSizeChoices(selected: ThumbnailDensity, onSelect: (ThumbnailDensity) -> Unit) {
    Column(Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
        Text("Thumbnail size", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ThumbnailDensity.entries.forEach { density ->
                FilterChip(selected = density == selected, onClick = { onSelect(density) }, label = { Text(density.label) })
            }
        }
    }
}

/** Search text stays in memory and is discarded when the Vault leaves composition. */
@Composable
fun VaultBrowseControls(
    query: String,
    onQuery: (String) -> Unit,
    kind: uk.co.traynor.privategallery.core.ui.MediaKindFilter,
    onKind: (uk.co.traynor.privategallery.core.ui.MediaKindFilter) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp)) {
        OutlinedTextField(
            value = query, onValueChange = onQuery, singleLine = true,
            modifier = Modifier.fillMaxWidth(), shape = GalleryTokens.RowShape,
            label = { Text("Search filenames") },
            leadingIcon = { Icon(androidx.compose.material.icons.Icons.Default.Search, null) },
            trailingIcon = { if (query.isNotEmpty()) TextButton(onClick = { onQuery("") }) { Text("Clear") } },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            uk.co.traynor.privategallery.core.ui.MediaKindFilter.entries.forEach { value ->
                FilterChip(selected = kind == value, onClick = { onKind(value) }, label = { Text(value.label) })
            }
        }
    }
}

@Composable
fun MediaSortChoices(selected: uk.co.traynor.privategallery.core.ui.MediaSort, onSelect: (uk.co.traynor.privategallery.core.ui.MediaSort) -> Unit) {
    Text("Sort media", Modifier.padding(horizontal = 24.dp, vertical = 8.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Column(Modifier.selectableGroup()) {
        uk.co.traynor.privategallery.core.ui.MediaSort.entries.forEach { value ->
            GalleryChoiceRow(value.label, selected == value) { onSelect(value) }
        }
    }
}

@Composable
fun GalleryChoiceRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
        .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
        .padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = null)
        Text(label, Modifier.padding(start = 12.dp), style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun SheetSection(title: String) {
    HorizontalDivider(Modifier.padding(horizontal = 24.dp, vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
    Text(title, Modifier.padding(horizontal = 24.dp, vertical = 8.dp).semantics { heading() },
        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
fun GalleryLoadingState(label: String) {
    Row(Modifier.fillMaxWidth().padding(24.dp).semantics { liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
