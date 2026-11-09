package uk.co.traynor.privategallery.launcher

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import uk.co.traynor.privategallery.core.sanctuary.SanctuaryLauncherPolicy.Decision

@Composable
internal fun LauncherSmartFolderEditor(
    folder: LauncherItem.SmartFolder,
    apps: List<LauncherApp>,
    decisions: Map<String, Decision>,
    onSave: (LauncherItem.SmartFolder) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember(folder) { mutableStateOf(folder.customName.orEmpty()) }
    var category by remember(folder) { mutableStateOf(folder.category) }
    var selectingCategory by remember(folder) { mutableStateOf(false) }
    var query by remember(folder) { mutableStateOf("") }
    var included by remember(folder) { mutableStateOf(folder.included.toSet()) }
    val appsByKey = remember(apps) { apps.associateBy(LauncherApp::component) }
    val unavailable = included.filterNot(appsByKey::containsKey)
    val matching = remember(apps, query) { launcherLibraryMatches(apps, query) }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("launcher-smart-folder-editor"),
        title = { Text("Edit smart folder") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    name, { name = it.filterNot(Char::isISOControl).take(64) },
                    label = { Text("Folder name") },
                    placeholder = { Text(category.label) },
                    singleLine = true,
                )
                Box {
                    OutlinedButton(onClick = { selectingCategory = true }) { Text("Category: ${category.label}") }
                    DropdownMenu(expanded = selectingCategory, onDismissRequest = { selectingCategory = false }) {
                        LauncherCategory.entries.forEach { option ->
                            DropdownMenuItem(text = { Text(option.label) }, onClick = {
                                category = option
                                selectingCategory = false
                            })
                        }
                    }
                }
                Text("Always include", style = MaterialTheme.typography.titleSmall)
                Text("These apps are included alongside the category’s automatic matches.",
                    style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(query, { query = it.take(100) }, label = { Text("Find an app") }, singleLine = true)
                LazyColumn(Modifier.heightIn(max = 192.dp)) {
                    if (query.isBlank()) {
                        items(unavailable, key = { it }) { key ->
                            LauncherToggle("Unavailable app", true) { included = included - key }
                        }
                    }
                    items(matching, key = LauncherApp::component) { app ->
                        val label = buildString {
                            append(app.label)
                            app.profileLabel?.let { append(" · ").append(it) }
                            if (decisions[app.packageName]?.canLaunch == false) append(" · Blocked")
                        }
                        LauncherToggle(label, app.component in included) { checked ->
                            if (!checked) included = included - app.component
                            else if (included.size < MAX_LAUNCHER_FOLDER_ITEMS) included = included + app.component
                        }
                    }
                }
                Text("${included.size} included", style = MaterialTheme.typography.bodySmall)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        confirmButton = {
            Button(onClick = {
                onSave(LauncherItem.SmartFolder(category, name.trim().ifEmpty { null }, included.toList()))
            }) { Text("Save folder") }
        },
    )
}
