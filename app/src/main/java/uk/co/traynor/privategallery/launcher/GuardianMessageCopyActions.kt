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

internal fun guardianSensitiveMessageClip(text: String): ClipData {
    require(text.isNotBlank())
    return ClipData.newPlainText("Guardian message", text).apply {
        description.extras = PersistableBundle().apply {
            putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
        }
    }
}

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
    showSender: Boolean,
    canCopy: () -> Boolean,
) {
    val context = LocalContext.current
    var selection by remember(row.id, message) { mutableStateOf<TextFieldValue?>(null) }
    val scope = rememberCoroutineScope()
    var sharePreview by remember(row.id, message) { mutableStateOf(false) }
    var sharing by remember(row.id, message) { mutableStateOf(false) }
    var feedback by remember(row.id, message) { mutableStateOf<String?>(null) }
    fun copy(text: String) {
        if (!enabled || !canCopy() || text.isBlank()) return
        feedback = runCatching {
            val clipboard = checkNotNull(context.getSystemService(ClipboardManager::class.java))
            clipboard.setPrimaryClip(guardianSensitiveMessageClip(text))
        }.fold({ "Copied to Android clipboard." }, { "Could not copy this message." })
    }
    val timestamp = DateFormat.getDateTimeInstance().format(Date(row.time))
    val summary = "Protected Guardian summary\nSource: $source\nTime: $timestamp\n" +
        "Assessment: ${row.assessment?.label ?: "Awaiting analysis"}"
    val sender = row.senderName?.takeIf { showSender }?.let { "Sender: $it\n" }.orEmpty()
    val reconstruction = "Nova Guardian · Archived message\n\nSource: $source\nTime: $timestamp\n$sender\n$message"

    fun export(image: Boolean, gallery: Boolean = false, clipboardImage: Boolean = false) {
        if (!enabled || !canCopy() || sharing) return
        // The system chooser cannot guarantee that the selected app is approved by Sanctuary.
        if (SanctuaryStore.get(context).refresh() != null) {
            feedback = "Image export and external sharing are unavailable during Sanctuary."
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
                    if (gallery) {
                        val prepared = checkNotNull(file)
                        var uri: android.net.Uri? = null
                        var published = false
                        try {
                            withContext(Dispatchers.IO) { uri = stageGuardianGalleryImage(context, prepared) }
                            if (canCopy() && SanctuaryStore.get(context).refresh() == null) {
                                publishGuardianGalleryImage(context, checkNotNull(uri))
                                published = true
                                feedback = "Screenshot saved to Gallery."
                            }
                        } finally {
                            if (!published) withContext(NonCancellable + Dispatchers.IO) {
                                uri?.let { pending -> runCatching { context.contentResolver.delete(pending, null, null) } }
                            }
                        }
                    } else if (clipboardImage) {
                        copyGuardianImage(context, checkNotNull(file))
                        handedOff = true
                        feedback = "Screenshot copied to Android clipboard."
                    } else {
                        shareGuardianFile(context, checkNotNull(file), image)
                        handedOff = true
                    }
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                feedback = "Could not complete this export."
            } finally {
                if (!handedOff) withContext(NonCancellable + Dispatchers.IO) { file?.delete() }
                sharing = false
                sharePreview = false
            }
        }
    }

    GuardianCard("Copy and export") {
        Text("Copied content is handed to Android. Clipboard history and other apps may retain it.", color = GuardianMuted)
        TextButton(enabled = enabled && message.isNotBlank(), onClick = { copy(message) }) { Text("Copy Message") }
        TextButton(enabled = enabled, onClick = { copy(summary) }) { Text("Copy Protected Summary") }
        TextButton(enabled = enabled && message.isNotBlank(), onClick = { if (canCopy()) selection = TextFieldValue(message) }) {
            Text("Select text")
        }
        TextButton(enabled = enabled && message.isNotBlank(), onClick = { if (canCopy()) sharePreview = true }) {
            Text("Save Screenshot")
        }
    }
    if (sharePreview) GuardianAlertDialog(
        onDismissRequest = { if (!sharing) sharePreview = false },
        title = { Text("Save or share screenshot") },
        text = {
            Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
                Text("Gallery, Android clipboard and apps you share with may retain a copy outside NovaOS encryption.")
                Text(reconstruction)
                TextButton(enabled = !sharing, onClick = { export(true) }) { Text("Share Securely") }
                TextButton(enabled = !sharing, onClick = { export(true, clipboardImage = true) }) { Text("Copy image") }
                TextButton(enabled = !sharing, onClick = { export(false) }) { Text("Share text") }
            }
        },
        confirmButton = { TextButton(enabled = !sharing, onClick = { export(true, gallery = true) }) { Text("Save to Gallery") } },
        dismissButton = { TextButton(enabled = !sharing, onClick = { sharePreview = false }) { Text("Cancel export") } },
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
