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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uk.co.traynor.privategallery.core.sanctuary.SanctuaryStore
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
    val scope = rememberCoroutineScope()
    var sharePreview by remember(row.id, message) { mutableStateOf(false) }
    var sharing by remember(row.id, message) { mutableStateOf(false) }
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
    val timestamp = DateFormat.getDateTimeInstance().format(Date(row.time))
    val details = "Sender: ${row.senderName ?: "Unavailable"}\nTime: $timestamp\nSource: $source\n\n$message"
    val reconstruction = "Guardian reconstruction of captured notification\n\n$details"

    fun share(image: Boolean) {
        if (!enabled || !canCopy() || sharing) return
        // The system chooser cannot guarantee that the selected app is approved by Sanctuary.
        if (SanctuaryStore.get(context).refresh() != null) {
            feedback = "External sharing is unavailable during Sanctuary. You can still copy or preview this message."
            sharePreview = false
            return
        }
        sharing = true
        scope.launch {
            var file: java.io.File? = null
            var handedOff = false
            try {
                withContext(Dispatchers.IO) { file = createGuardianShareFile(context, reconstruction, image) }
                if (canCopy() && SanctuaryStore.get(context).refresh() == null) {
                    shareGuardianFile(context, checkNotNull(file), image)
                    handedOff = true
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                feedback = "Could not prepare this export. Try text if the image is too tall."
            } finally {
                if (!handedOff) withContext(NonCancellable + Dispatchers.IO) { file?.delete() }
                sharing = false
                sharePreview = false
            }
        }
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
                copy(details)
            })
        }
    }
    TextButton(enabled = enabled, onClick = { if (canCopy()) sharePreview = true }) { Text("Share Safely") }
    if (sharePreview) GuardianAlertDialog(
        onDismissRequest = { if (!sharing) sharePreview = false },
        title = { Text("Review Guardian export") },
        text = {
            Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
                Text("Sharing sends this content outside NovaOS.")
                Text(reconstruction)
            }
        },
        confirmButton = { TextButton(enabled = !sharing, onClick = { share(true) }) { Text("Share image") } },
        dismissButton = { TextButton(enabled = !sharing, onClick = { share(false) }) { Text("Share text") } },
        properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn),
    )
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
