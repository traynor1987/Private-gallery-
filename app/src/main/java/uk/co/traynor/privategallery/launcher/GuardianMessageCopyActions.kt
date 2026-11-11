package uk.co.traynor.privategallery.launcher

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.os.PersistableBundle
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import java.text.DateFormat
import java.util.Date
import uk.co.traynor.privategallery.core.guardian.GuardianInboxRow

// Keep the selection handles while routing copying through the sensitive-clip action.
private object GuardianSelectionToolbar : TextToolbar {
    override val status: TextToolbarStatus get() = TextToolbarStatus.Hidden
    override fun showMenu(
        rect: Rect, onCopyRequested: (() -> Unit)?, onPasteRequested: (() -> Unit)?,
        onCutRequested: (() -> Unit)?, onSelectAllRequested: (() -> Unit)?,
    ) = Unit
    override fun hide() = Unit
}

@Composable
internal fun GuardianMessageCopyActions(
    row: GuardianInboxRow,
    message: String,
    source: String,
    enabled: Boolean,
    canCopy: () -> Boolean,
) {
    val context = LocalContext.current
    var expanded by remember(row.id, message) { mutableStateOf(false) }
    var selection by remember(row.id, message) { mutableStateOf<TextFieldValue?>(null) }
    var feedback by remember(row.id, message) { mutableStateOf<String?>(null) }
    fun copy(text: String) {
        if (!enabled || !canCopy() || text.isEmpty()) return
        val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
        feedback = runCatching {
            val clip = ClipData.newPlainText("Guardian message", text)
            clip.description.extras = PersistableBundle().apply {
                putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
            }
            clipboard.setPrimaryClip(clip)
        }.fold({ "Copied to Android clipboard." }, { "Could not copy this message." })
    }
    Box {
        TextButton(enabled = enabled, onClick = { expanded = true }) { Text("Copy message") }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("Copy Text") }, onClick = {
                expanded = false
                copy(message)
            })
            DropdownMenuItem(text = { Text("Select Text") }, onClick = {
                expanded = false
                if (canCopy()) selection = TextFieldValue(message)
            })
            DropdownMenuItem(text = { Text("Copy With Details") }, onClick = {
                expanded = false
                val timestamp = DateFormat.getDateTimeInstance().format(Date(row.time))
                copy("Sender: ${row.senderName ?: "Unavailable"}\nTime: $timestamp\nSource: $source\n\n$message")
            })
        }
    }
    feedback?.let { Text(it, color = GuardianMuted) }
    selection?.let { selected ->
        GuardianAlertDialog(
            onDismissRequest = { selection = null },
            title = { Text("Select message text") },
            text = {
                CompositionLocalProvider(LocalTextToolbar provides GuardianSelectionToolbar) {
                    OutlinedTextField(selected, { selection = selected.copy(selection = it.selection) },
                        modifier = Modifier.fillMaxWidth(), readOnly = true, maxLines = 12)
                }
            },
            confirmButton = {
                TextButton(enabled = enabled && !selected.selection.collapsed, onClick = {
                    copy(message.substring(selected.selection.min, selected.selection.max))
                    selection = null
                }) { Text("Copy selection") }
            },
            dismissButton = { TextButton(onClick = { selection = null }) { Text("Cancel") } },
            properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn),
        )
    }
}
