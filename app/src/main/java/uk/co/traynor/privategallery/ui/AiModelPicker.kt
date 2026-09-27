package uk.co.traynor.privategallery.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import uk.co.traynor.privategallery.core.editor.GenerationModel
import uk.co.traynor.privategallery.core.editor.ReplicateEditModel

internal data class AiPickerItem(val key: String, val title: String, val description: String,
    val price: String, val badge: String? = null)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AiSinglePicker(label: String, selected: AiPickerItem, choices: List<AiPickerItem>,
    onSelect: (String) -> Unit, enabled: Boolean = true, summary: String = selected.price) {
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Surface(onClick = { expanded = true }, enabled = enabled, modifier = Modifier.fillMaxWidth()
            .testTag("${label.lowercase()}-selector")
            .semantics { contentDescription = "$label: ${selected.title}. ${selected.description}. $summary. Choose $label" },
            shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 1.dp) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(selected.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(selected.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (summary.isNotBlank()) Text(summary, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
                Icon(Icons.Default.KeyboardArrowDown, null)
            }
        }
    }
    if (expanded) ModalBottomSheet(onDismissRequest = { expanded = false },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().heightIn(max = 600.dp).verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, bottom = 28.dp)) {
            Text("Choose ${label.lowercase()}", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(16.dp))
            Text(if (label == "Model") "REPLICATE" else "AVAILABLE PROVIDERS",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            choices.forEach { item ->
                Surface(onClick = { onSelect(item.key); expanded = false }, modifier = Modifier.fillMaxWidth().testTag("picker-${item.key}"),
                    shape = MaterialTheme.shapes.medium,
                    color = if (item.key == selected.key) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.Top) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(item.title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f, fill = false))
                                item.badge?.let { badge ->
                                    Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.tertiaryContainer) {
                                        Text(badge, Modifier.padding(horizontal = 7.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                            Text(item.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (item.price.isNotBlank()) Text(item.price, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        }
                        if (item.key == selected.key) Icon(Icons.Default.Check, "Selected", Modifier.padding(start = 8.dp))
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .35f))
            }
        }
    }
}

internal fun ReplicateEditModel.pickerItem(adult: Boolean, resolution: String): AiPickerItem = AiPickerItem(name, label, description,
    if (this == ReplicateEditModel.SEEDREAM_5_PRO) "${resolution} · ${if (resolution == "1K") "≈$0.045" else "≈$0.09"} / image" else priceLabel,
    when { adult && supportsRelaxedModeration -> "Adult"; this == ReplicateEditModel.FILL -> "Inpainting"; else -> null })

internal fun GenerationModel.pickerItem(adult: Boolean, resolution: String): AiPickerItem = AiPickerItem(name, label, description,
    if (this == GenerationModel.SEEDREAM_5_PRO) "$resolution · ${priceFor(resolution)}" else priceLabel,
    if (this == GenerationModel.WHISKII || (adult && supportsRelaxedModeration)) "Adult" else null)
