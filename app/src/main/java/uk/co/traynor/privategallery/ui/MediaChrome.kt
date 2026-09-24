package uk.co.traynor.privategallery.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
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
) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to collections") }
        Column(Modifier.weight(1f).padding(start = 6.dp)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (onAdd != null) IconButton(onClick = onAdd) { Icon(Icons.Default.Add, "Add media") }
        if (onLock != null) IconButton(onClick = onLock) { Icon(Icons.Default.Lock, "Lock Vault") }
        if (onMenu != null) IconButton(onClick = onMenu) { Icon(Icons.Default.MoreVert, "$title menu") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryMenuSheet(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().heightIn(max = 600.dp).verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
            Text(title, Modifier.padding(horizontal = 24.dp, vertical = 8.dp), style = MaterialTheme.typography.titleLarge)
            content()
        }
    }
}

@Composable
fun SheetAction(label: String, icon: ImageVector, enabled: Boolean = true, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick).heightIn(min = 52.dp).padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        val tint = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.38f)
        Icon(icon, contentDescription = null, tint = tint)
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
