package uk.co.traynor.privategallery.launcher

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.util.Locale
import uk.co.traynor.privategallery.core.sanctuary.SanctuaryMode

/** Display-only snapshot of the real Sanctuary session. Launcher state never defines policy. */
internal data class LauncherSanctuaryStatus(
    val mode: SanctuaryMode,
    val remainingMillis: Long,
    val paused: Boolean,
)

@Composable
internal fun LauncherSanctuaryStatusCard(
    status: LauncherSanctuaryStatus?,
    expanded: Boolean = false,
    onOpen: () -> Unit,
) {
    Surface(
        onClick = onOpen,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("launcher-sanctuary-status"),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = if (expanded) 14.dp else 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Shield, contentDescription = null)
            Column(Modifier.weight(1f)) {
                Text(status?.mode?.label ?: "Sanctuary", style = MaterialTheme.typography.titleSmall,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    when {
                        status == null -> "Protection inactive"
                        status.paused -> "Launcher protection paused"
                        else -> "Launcher rules active"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                if (expanded) Text("Open Sanctuary controls", style = MaterialTheme.typography.labelMedium)
            }
            status?.let { SanctuaryCountdown(it.remainingMillis) }
        }
    }
}

@Composable
internal fun LauncherSanctuaryDialog(
    status: LauncherSanctuaryStatus?,
    modeMinutes: Map<SanctuaryMode, Int>,
    guardianStatus: String,
    onStart: (SanctuaryMode) -> Unit,
    onReview: () -> Unit,
    onPause: () -> Unit,
    onPhone: () -> Unit,
    onManage: () -> Unit,
    onDismiss: () -> Unit,
) {
    var selected by remember { mutableStateOf(SanctuaryMode.SELF_CARE) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nova Sanctuary") },
        text = {
            LazyColumn(
                Modifier.heightIn(max = 340.dp).testTag("launcher-sanctuary-modes"),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (status == null) {
                    item { Text("Choose a mode using your saved Sanctuary rules.") }
                    item {
                        Column(Modifier.selectableGroup()) {
                            SanctuaryMode.entries.forEach { mode ->
                                Row(
                                    Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                        .selectable(
                                            selected = selected == mode, role = Role.RadioButton,
                                            onClick = { selected = mode },
                                        ).testTag("launcher-sanctuary-mode-" + mode.name.lowercase(Locale.ROOT)),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    RadioButton(selected = selected == mode, onClick = null)
                                    Text(mode.label, Modifier.weight(1f))
                                    Text("${modeMinutes[mode] ?: mode.defaultMinutes} min")
                                }
                            }
                        }
                    }
                } else {
                    item {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(status.mode.label, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                            SanctuaryCountdown(status.remainingMillis)
                        }
                        Text(if (status.paused) "Launcher protection paused" else "Launcher rules active")
                        Text(guardianStatus, style = MaterialTheme.typography.bodySmall)
                    }
                    if (!status.paused) item {
                        TextButton(onClick = onPause) { Text("Pause protection for five minutes") }
                    }
                }
                item { TextButton(onClick = onReview) { Text("Review approved apps") } }
                item { TextButton(onClick = onManage) { Text("Manage in NovaOS") } }
                item { TextButton(onClick = onPhone) { Text("Phone and emergency access") } }
            }
        },
        confirmButton = {
            if (status == null) {
                Button(onClick = { onStart(selected) }, modifier = Modifier.testTag("launcher-sanctuary-start")) {
                    Text("Start ${selected.label}")
                }
            } else {
                TextButton(onClick = onDismiss) { Text("Done") }
            }
        },
        dismissButton = if (status == null) ({ TextButton(onClick = onDismiss) { Text("Cancel") } }) else null,
    )
}

@Composable
private fun SanctuaryCountdown(remainingMillis: Long) {
    val remaining = remainingMillis.coerceAtLeast(0L)
    val seconds = remaining / 1000L + if (remaining % 1000L > 0) 1L else 0L
    val hours = seconds / 3600L
    val minutes = seconds / 60L % 60L
    val remainder = seconds % 60L
    val formatted = if (hours == 0L) String.format(Locale.ROOT, "%d:%02d", minutes, remainder)
        else String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, remainder)
    Text(
        formatted,
        Modifier.testTag("launcher-sanctuary-countdown").semantics { contentDescription = "Time remaining: $formatted" },
        style = MaterialTheme.typography.titleMedium,
    )
}
