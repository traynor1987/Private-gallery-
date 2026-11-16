package uk.co.traynor.privategallery.home2

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uk.co.traynor.privategallery.core.home2.SpaceContext

internal data class NovaFilesPicker(
    val chooseFolder: () -> Unit,
    val returned: Return?,
    val acknowledge: () -> Unit,
) {
    data class Return(val message: String? = null)
}

/** Register above the foreground-only Home UI so DocumentsUI can pause the workspace. */
@Composable
internal fun rememberNovaFilesPicker(space: SpaceContext): NovaFilesPicker {
    val scope = rememberCoroutineScope()
    var pending by remember(space) { mutableStateOf(false) }
    var returned by remember(space) { mutableStateOf<NovaFilesPicker.Return?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) result@{ result ->
        if (!pending) return@result
        if (!space.current()) {
            pending = false
            return@result
        }
        if (result.resultCode != Activity.RESULT_OK) {
            pending = false
            returned = NovaFilesPicker.Return()
            return@result
        }
        val request = result.data
        val tree = request?.data
        val flags = (request?.flags ?: 0) and FILE_GRANT_FLAGS
        scope.launch {
            val selected = withContext(Dispatchers.IO) {
                runCatching { saveSelectedTree(space, tree, flags) }.isSuccess
            }
            pending = false
            if (space.current()) returned = NovaFilesPicker.Return(
                if (selected) null else "Access to the selected folder could not be saved.",
            )
        }
    }
    return NovaFilesPicker(
        chooseFolder = {
            if (!pending && space.current()) {
                pending = true
                runCatching {
                    launcher.launch(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).addFlags(
                        FILE_GRANT_FLAGS or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
                            Intent.FLAG_GRANT_PREFIX_URI_PERMISSION,
                    ))
                }.onFailure {
                    pending = false
                    returned = NovaFilesPicker.Return("Android's folder picker could not be opened.")
                }
            }
        },
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
