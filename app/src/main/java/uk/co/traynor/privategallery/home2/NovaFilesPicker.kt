package uk.co.traynor.privategallery.home2

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import uk.co.traynor.privategallery.core.home2.SpaceContext

internal data class NovaFilesPicker(
    val chooseFolder: () -> Unit,
    val returned: Return?,
    val acknowledge: () -> Unit,
) {
    data class Return(val message: String? = null)
}

/** Carries only the picker result. The activity owns this across a normal auto-lock. */
internal class NovaFilesPickerTicket {
    class Selection(val tree: Uri?, val flags: Int, val cancelled: Boolean, val launchFailed: Boolean = false)

    private var inFlight = false
    var returned by mutableStateOf<Selection?>(null)
        private set

    fun launch(launchPicker: (Intent) -> Unit) {
        if (inFlight || returned != null) return
        inFlight = true
        runCatching {
            launchPicker(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).addFlags(
                FILE_GRANT_FLAGS or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PREFIX_URI_PERMISSION,
            ))
        }.onFailure {
            inFlight = false
            returned = Selection(null, 0, cancelled = false, launchFailed = true)
        }
    }

    fun accept(result: ActivityResult) {
        if (!inFlight) return
        inFlight = false
        val data = result.data
        returned = Selection(data?.data, (data?.flags ?: 0) and FILE_GRANT_FLAGS,
            cancelled = result.resultCode != Activity.RESULT_OK)
    }

    fun clear() {
        inFlight = false
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
        val message = when {
            selection.launchFailed -> "Android's folder picker could not be opened."
            selection.cancelled -> null
            else -> {
                val success = withContext(Dispatchers.IO) {
                    runCatching { saveSelectedTree(space, selection.tree, selection.flags) }.isSuccess
                }
                if (success) null else "Access to the selected folder could not be saved."
            }
        }
        if (!space.current() || ticket.returned !== selection) return@LaunchedEffect
        ticket.clear()
        returned = NovaFilesPicker.Return(message)
    }
    return NovaFilesPicker(
        chooseFolder = { if (active && space.current()) ticket.launch(launch) },
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
