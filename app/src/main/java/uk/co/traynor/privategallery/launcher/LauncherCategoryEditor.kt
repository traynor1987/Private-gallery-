package uk.co.traynor.privategallery.launcher

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import uk.co.traynor.privategallery.core.sanctuary.SanctuaryLauncherPolicy.Decision

internal fun Map<String, Decision>.forLauncherApp(app: LauncherApp): Decision? =
    get(app.component) ?: get(app.packageName).takeIf { '#' !in app.component }

@Composable
internal fun LauncherCategoryEditor(
    app: LauncherApp,
    current: LauncherCategory?,
    onSelect: (LauncherCategory?) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Category for ${app.label}") },
        text = {
            Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                TextButton(onClick = { onSelect(null) }) {
                    Text(if (current == null) "Automatic · selected" else "Automatic")
                }
                LauncherCategory.entries.filter { it.isAssignable }.forEach { category ->
                    TextButton(onClick = { onSelect(category) }) {
                        Text(if (current == category) "${category.label} · selected" else category.label)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
