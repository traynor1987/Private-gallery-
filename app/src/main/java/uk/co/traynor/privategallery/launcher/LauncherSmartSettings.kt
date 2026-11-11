package uk.co.traynor.privategallery.launcher

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

private enum class SmartSettingsDialog { USAGE_ACCESS, CLEAR_HISTORY, RESET_SUGGESTIONS, PINS, EXCLUSIONS }

@Composable
@OptIn(ExperimentalLayoutApi::class)
internal fun LauncherSmartSettings(
    tracking: LauncherSmartTracking,
    eligibleApps: List<LauncherApp>,
    onClearLegacyHistory: () -> Unit = {},
    onRequestUsageAccess: (Intent) -> Unit,
) {
    val state by tracking.state.collectAsState()
    var dialog by rememberSaveable { mutableStateOf<SmartSettingsDialog?>(null) }

    Column {
        Text("Smart App Tracking", style = MaterialTheme.typography.titleMedium)
        Text("App history and preferences stay on this device.", style = MaterialTheme.typography.bodySmall)
        Column(Modifier.selectableGroup()) {
            LauncherTrackingMode.entries.forEach { mode ->
                val selected = mode == state.mode
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp)
                        .testTag("launcher-tracking-${mode.name.lowercase()}")
                        .selectable(selected = selected, role = Role.RadioButton) {
                            if (mode == LauncherTrackingMode.EXTENDED) {
                                if (!selected) dialog = SmartSettingsDialog.USAGE_ACCESS
                            } else {
                                tracking.setMode(mode)
                            }
                        },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    RadioButton(selected = selected, onClick = null)
                    Column {
                        Text(mode.title, style = MaterialTheme.typography.titleSmall)
                        Text(mode.description, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        Text("Recent app limit", style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LAUNCHER_RECENT_LIMITS.forEach { limit ->
                FilterChip(
                    selected = limit == state.recentLimit,
                    onClick = { tracking.setRecentLimit(limit) },
                    label = { Text(limit.toString()) },
                )
            }
        }
        LauncherToggle("Show recents on Home", state.showHomeRecents, tracking::setShowHomeRecents)
        LauncherToggle("Show suggestions", state.showSuggestions, tracking::setShowSuggestions)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { dialog = SmartSettingsDialog.PINS }) { Text("Pinned suggestions") }
            OutlinedButton(onClick = { dialog = SmartSettingsDialog.EXCLUSIONS }) { Text("Excluded apps") }
            TextButton(onClick = { dialog = SmartSettingsDialog.CLEAR_HISTORY }) { Text("Clear app history") }
            TextButton(onClick = { dialog = SmartSettingsDialog.RESET_SUGGESTIONS }) { Text("Reset suggestions") }
        }
    }

    when (dialog) {
        SmartSettingsDialog.USAGE_ACCESS -> GuardianAlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("Enable Extended tracking?") },
            text = {
                Text("Extended uses Android Usage Access to recognise eligible apps you open outside Nova Launcher. " +
                    "Nova Launcher stores the app and last-used time on this device. " +
                    "If Android asks, enable Usage Access for NovaOS and then return here.")
            },
            confirmButton = {
                TextButton(onClick = {
                    dialog = null
                    tracking.beginExtendedConsent()?.let(onRequestUsageAccess)
                }) { Text("Continue") }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text("Cancel") } },
        )
        SmartSettingsDialog.CLEAR_HISTORY -> GuardianAlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("Clear app history?") },
            text = { Text("This removes your remembered app launches. Your pins and exclusions stay saved.") },
            confirmButton = {
                TextButton(onClick = {
                    dialog = null
                    tracking.clearHistory()
                    onClearLegacyHistory()
                }) { Text("Clear history") }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text("Cancel") } },
        )
        SmartSettingsDialog.RESET_SUGGESTIONS -> GuardianAlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("Reset suggestions?") },
            text = { Text("Automatic suggestions will start fresh. Your recent history, pins and exclusions stay saved.") },
            confirmButton = {
                TextButton(onClick = {
                    dialog = null
                    tracking.resetSuggestions()
                }) { Text("Reset suggestions") }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text("Cancel") } },
        )
        SmartSettingsDialog.PINS -> SmartTrackingAppPicker(
            title = "Pinned suggestions",
            emptyMessage = "No apps are available to pin.",
            apps = eligibleApps.filterNot { it.component in state.excluded },
            selected = state.pinned,
            checkedLabel = "Unpin",
            uncheckedLabel = "Pin",
            onToggle = tracking::togglePin,
            onDismiss = { dialog = null },
        )
        SmartSettingsDialog.EXCLUSIONS -> SmartTrackingAppPicker(
            title = "Excluded apps",
            emptyMessage = "No apps are available.",
            apps = eligibleApps,
            selected = state.excluded,
            checkedLabel = "Include",
            uncheckedLabel = "Exclude",
            onToggle = tracking::toggleExclusion,
            onDismiss = { dialog = null },
        )
        null -> Unit
    }
}

@Composable
private fun SmartTrackingAppPicker(
    title: String,
    emptyMessage: String,
    apps: List<LauncherApp>,
    selected: Set<String>,
    checkedLabel: String,
    uncheckedLabel: String,
    onToggle: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val matching = apps.filter { it.label.contains(query.trim(), ignoreCase = true) }
    GuardianAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Find an app") },
                    singleLine = true,
                )
                if (matching.isEmpty()) {
                    Text(if (query.isBlank()) emptyMessage else "No matching apps.")
                } else {
                    LazyColumn(Modifier.heightIn(max = 280.dp)) {
                        items(matching, key = LauncherApp::component) { app ->
                            ListItem(
                                headlineContent = { Text(app.label) },
                                supportingContent = app.profileLabel?.let { label -> { Text(label) } },
                                leadingContent = { LauncherAppIcon(app, Modifier.size(32.dp)) },
                                trailingContent = {
                                    TextButton(onClick = { onToggle(app.component) }) {
                                        Text(if (app.component in selected) checkedLabel else uncheckedLabel)
                                    }
                                },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}
