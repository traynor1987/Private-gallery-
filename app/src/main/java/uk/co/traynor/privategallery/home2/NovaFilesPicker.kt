package uk.co.traynor.privategallery.home2

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import java.util.Base64
import java.util.UUID
import uk.co.traynor.privategallery.core.home2.NovaNoteAttachment
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import uk.co.traynor.privategallery.core.home2.SpaceContext

internal data class NovaFilesPicker(
    val chooseFolder: () -> Unit,
    val chooseAttachment: (String) -> Unit,
    val returned: Return?,
    val acknowledge: () -> Unit,
) {
    data class Return(val message: String? = null, val noteId: String? = null)
}

/** Carries only the picker result. The activity owns this across a normal auto-lock. */
internal class NovaFilesPickerTicket {
    data class AttachmentRequest(val noteId: String, val id: String = UUID.randomUUID().toString())
    class Selection(val tree: Uri?, val flags: Int, val cancelled: Boolean, val launchFailed: Boolean = false,
        val attachment: AttachmentRequest? = null)

    private var inFlight = false
    private var attachmentRequest: AttachmentRequest? = null
    var returned by mutableStateOf<Selection?>(null)
        private set

    fun launch(launchPicker: (Intent) -> Unit, noteId: String? = null) {
        if (inFlight || returned != null) return
        inFlight = true
        val request = noteId?.let { AttachmentRequest(it) }
        attachmentRequest = request
        runCatching {
            val intent = if (request == null) Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).addFlags(
                FILE_GRANT_FLAGS or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PREFIX_URI_PERMISSION,
            ) else Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                .setType("*/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            launchPicker(intent)
        }.onFailure {
            inFlight = false
            returned = Selection(null, 0, cancelled = false, launchFailed = true, attachment = request)
        }
    }

    fun accept(result: ActivityResult) {
        if (!inFlight) return
        inFlight = false
        val data = result.data
        returned = Selection(data?.data, (data?.flags ?: 0) and FILE_GRANT_FLAGS,
            cancelled = result.resultCode != Activity.RESULT_OK, attachment = attachmentRequest)
    }

    fun clear() {
        inFlight = false
        attachmentRequest = null
        returned = null
    }
}

/** Fallback for in-process previews. The real activity supplies a ticket above the lock UI. */
@Composable
internal fun rememberNovaFilesPicker(
    space: SpaceContext,
    active: Boolean,
    activityTicket: NovaFilesPickerTicket?,
    activityLaunch: ((Intent) -> Unit)?,
): NovaFilesPicker {
    val ticket = activityTicket ?: remember(space) { NovaFilesPickerTicket() }
    val launch: (Intent) -> Unit = if (activityLaunch != null) activityLaunch else {
        val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult(), ticket::accept)
        val localLaunch: (Intent) -> Unit = { intent -> launcher.launch(intent) }
        localLaunch
    }
    var returned by remember(space) { mutableStateOf<NovaFilesPicker.Return?>(null) }
    val selection = ticket.returned
    LaunchedEffect(space, active, selection) {
        if (!active || !space.current() || selection == null) return@LaunchedEffect
        val attachment = selection.attachment
        val message = when {
            selection.launchFailed -> "Android's document picker could not be opened."
            selection.cancelled -> null
            attachment != null -> withContext(Dispatchers.IO) {
                runCatching { saveSelectedAttachment(space, selection, attachment) }
                    .fold({ "Attachment saved in the encrypted draft" }, {
                        when (it) {
                            is AttachmentSizeExceeded -> "Choose an attachment of 24 KiB or less."
                            is SecurityException -> "Android no longer grants access to that file. Select it again."
                            else -> "Could not attach the file. Your existing draft is still available."
                        }
                    })
            }
            else -> {
                val success = withContext(Dispatchers.IO) {
                    runCatching { saveSelectedTree(space, selection.tree, selection.flags) }.isSuccess
                }
                if (success) null else "Access to the selected folder could not be saved."
            }
        }
        if (!space.current() || ticket.returned !== selection) return@LaunchedEffect
        ticket.clear()
        returned = NovaFilesPicker.Return(message, attachment?.noteId)
    }
    return NovaFilesPicker(
        chooseFolder = { if (active && space.current()) ticket.launch(launch) },
        chooseAttachment = { id -> if (active && space.current()) ticket.launch(launch, id) },
        returned = returned,
        acknowledge = { returned = null },
    )
}

private const val FILE_GRANT_FLAGS =
    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION

private fun saveSelectedTree(space: SpaceContext, tree: Uri?, flags: Int) {
    check(space.current())
    requireNotNull(tree)
    require(DocumentsContract.isTreeUri(tree) && flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
    val selection = NovaFilesSelection(space)
    val previous = selection.read()
    val resolver = space.contentResolver
    var existing = 0
    resolver.persistedUriPermissions.filter { it.uri == tree }.forEach {
        if (it.isReadPermission) existing = existing or Intent.FLAG_GRANT_READ_URI_PERMISSION
        if (it.isWritePermission) existing = existing or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
    }
    var saved = false
    resolver.takePersistableUriPermission(tree, flags)
    try {
        saved = selection.save(previous, tree) != null
        check(saved)
    } finally {
        val added = flags and existing.inv()
        if (!saved && added != 0) runCatching { resolver.releasePersistableUriPermission(tree, added) }
    }
}


private class AttachmentSizeExceeded : IllegalArgumentException()

private fun saveSelectedAttachment(
    space: SpaceContext, selection: NovaFilesPickerTicket.Selection,
    request: NovaFilesPickerTicket.AttachmentRequest,
) {
    check(space.current())
    val uri = requireNotNull(selection.tree)
    require(uri.scheme == "content" && selection.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
    val resolver = space.contentResolver
    val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
        if (it.moveToFirst()) it.getString(0) else null
    }?.filterNot { it.isISOControl() }?.take(160)?.takeIf(String::isNotBlank) ?: "Attachment"
    val mime = resolver.getType(uri)?.substringBefore(';')?.take(100) ?: "application/octet-stream"
    val bytes = requireNotNull(resolver.openInputStream(uri)).use {
        it.readNBytes(NovaNoteAttachment.MAX_BYTES + 1)
    }
    try {
        if (bytes.size > NovaNoteAttachment.MAX_BYTES) throw AttachmentSizeExceeded()
        val attachment = NovaNoteAttachment(request.id, name, mime, Base64.getEncoder().encodeToString(bytes))
        attachment.readBytes().fill(0)
        check(NovaNotesLibrary(space).attachToDraft(request.noteId, attachment))
    } finally {
        bytes.fill(0)
    }
}
