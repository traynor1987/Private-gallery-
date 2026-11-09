package uk.co.traynor.privategallery.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

internal enum class LauncherSmartFolderPresentation(val label: String, val detail: String) {
    COMPACT("Compact", "Small folders in the regular Home grid"),
    EXPANDED("Expanded", "Larger previews in two columns where they fit"),
    AUTOMATIC("Automatic", "Expands dedicated smart-folder pages when space allows");

    fun expands(width: Float, height: Float, items: List<LauncherItem>): Boolean =
        width >= 260 && height >= 300 && when (this) {
            COMPACT -> false
            EXPANDED -> true
            AUTOMATIC -> items.isNotEmpty() && items.all { it is LauncherItem.SmartFolder }
        }
}

/** Keep each original slot index so visual reflow doesn't change saved placement or drag targets. */
internal fun smartFolderRows(
    items: List<LauncherItem>,
    columns: Int,
    expanded: Boolean,
): List<List<IndexedValue<LauncherItem>>> = buildList {
    val indexed = items.withIndex().toList()
    if (!expanded) {
        addAll(indexed.chunked(columns))
    } else {
        var start = 0
        while (start < indexed.size) {
            val smart = indexed[start].value is LauncherItem.SmartFolder
            var end = start + 1
            while (end < indexed.size && (indexed[end].value is LauncherItem.SmartFolder) == smart) end++
            addAll(indexed.subList(start, end).chunked(if (smart) 2 else columns))
            start = end
        }
    }
}

@Composable
internal fun LauncherSmartFolderModeSetting(
    mode: LauncherSmartFolderPresentation,
    onMode: (LauncherSmartFolderPresentation) -> Unit,
) {
    Text("Home smart folders", style = MaterialTheme.typography.titleMedium)
    Column(Modifier.selectableGroup()) {
        LauncherSmartFolderPresentation.entries.forEach { option ->
            Row(
                Modifier.fillMaxWidth().heightIn(min = 52.dp)
                    .selectable(option == mode, role = Role.RadioButton) { onMode(option) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(option == mode, onClick = null, modifier = Modifier.padding(end = 12.dp))
                Column(Modifier.padding(vertical = 5.dp)) {
                    Text(option.label, style = MaterialTheme.typography.bodyLarge)
                    Text(option.detail, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
internal fun LauncherExpandedSmartFolder(folder: LauncherItem.SmartFolder, apps: List<LauncherApp>) {
    val shape = RoundedCornerShape(20.dp)
    val preview = apps.take(4)
    Column(
        Modifier.fillMaxWidth().testTag("launcher-expanded-smart-folder")
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Color(0x46C6DEFF), Color(0x222B5264))))
            .border(1.dp, Color.White.copy(alpha = .22f), shape)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().height(56.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (preview.isEmpty()) {
                Icon(Icons.Default.FolderOpen, null, Modifier.size(28.dp), tint = Color.White.copy(alpha = .78f))
                Text("No apps yet", color = Color.White.copy(alpha = .86f), style = MaterialTheme.typography.labelSmall)
            } else {
                preview.take(2).forEach { app -> LauncherAppIcon(app, Modifier.weight(1f).size(44.dp).testTag("launcher-expanded-preview-${app.component}")) }
                if (preview.size > 2) {
                    Column(Modifier.weight(.7f), horizontalAlignment = Alignment.CenterHorizontally) {
                        preview.drop(2).forEach { app -> LauncherAppIcon(app, Modifier.size(24.dp).testTag("launcher-expanded-preview-${app.component}")) }
                    }
                }
            }
        }
        Text(folder.name, color = Color.White, style = MaterialTheme.typography.titleSmall,
            maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}
