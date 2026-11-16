package uk.co.traynor.privategallery.home2

import android.net.Uri
import android.provider.DocumentsContract.Document
import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uk.co.traynor.privategallery.core.home2.SpaceContext

private enum class FileSort(val label: String) {
    NAME("Name"), NEWEST("Newest"), SIZE("Size"),
}
private enum class FileNameAction(val label: String) {
    CREATE("Create folder"), RENAME("Rename"),
}
private data class FileNameRequest(val action: FileNameAction, val parent: NovaFile, val file: NovaFile? = null)
private data class FilesListing(val provider: NovaFilesAccess, val root: NovaFile,
    val children: List<NovaFile>, val writable: Boolean)
private data class FileTransfer(val file: NovaFile, val source: NovaFile, val move: Boolean)

@Composable
internal fun NovaFilesScreen(
    space: SpaceContext,
    onBack: () -> Unit,
    onChooseFolder: (() -> Unit)?,
    pickerMessage: String?,
) {
    val scope = rememberCoroutineScope()
    var selection by remember(space) { mutableStateOf<NovaFilesSelection.Selection?>(null) }
    var access by remember(space) { mutableStateOf<NovaFilesAccess?>(null) }
    var hasWriteGrant by remember(space) { mutableStateOf(false) }
    var root by remember(space) { mutableStateOf<NovaFile?>(null) }
    var path by remember(space) { mutableStateOf<List<NovaFile>>(emptyList()) }
    var files by remember(space) { mutableStateOf<List<NovaFile>>(emptyList()) }
    var sort by remember(space) { mutableStateOf(FileSort.NAME) }
    var search by remember(space) { mutableStateOf("") }
    var status by remember(space) { mutableStateOf(pickerMessage) }
    var working by remember(space) { mutableStateOf(false) }
    var loading by remember(space) { mutableStateOf(true) }
    var reload by remember(space) { mutableIntStateOf(0) }
    var detail by remember(space) { mutableStateOf<NovaFile?>(null) }
    var nameRequest by remember(space) { mutableStateOf<FileNameRequest?>(null) }
    var nameText by remember(space) { mutableStateOf("") }
    var toDelete by remember(space) { mutableStateOf<NovaFile?>(null) }
    var transfer by remember(space) { mutableStateOf<FileTransfer?>(null) }

    LaunchedEffect(space) {
        val result = withContext(Dispatchers.IO) { runCatching { NovaFilesSelection(space).read() } }
        if (!space.current()) return@LaunchedEffect
        result.onSuccess { selection = it }.onFailure {
            loading = false
            status = "The encrypted folder selection could not be loaded."
        }
    }
    LaunchedEffect(space, selection, path, reload) {
        val selected = selection ?: return@LaunchedEffect
        val tree: Uri? = selected.tree
        loading = true
        files = emptyList()
        if (tree == null) {
            loading = false
            return@LaunchedEffect
        }
        val folder = path.lastOrNull()
        val result = withContext(Dispatchers.IO) {
            runCatching {
                val provider = NovaFilesAccess(space.contentResolver, tree, space::current)
                val rootNode = provider.root()
                FilesListing(provider, rootNode, provider.children(folder ?: rootNode), provider.canWrite)
            }
        }
        if (!space.current()) return@LaunchedEffect
        result.onSuccess { (provider, rootNode, children, writable) ->
            hasWriteGrant = writable
            access = provider
            root = rootNode
            files = children
        }.onFailure {
            access = null
            root = null
            status = "This folder could not be read. Reopen the root or choose the folder again."
        }
        loading = false
    }
    fun change(success: String, failure: String, operation: suspend () -> Unit) {
        working = true
        status = null
        scope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { operation() } }
            if (!space.current()) return@launch
            status = when {
                result.isSuccess -> success
                result.exceptionOrNull() is NovaFileCleanupException ->
                    "The provider left a NovaOS-incomplete temporary file in the destination. The source was retained."
                else -> failure
            }
            if (result.isSuccess) transfer = null
            reload++
            working = false
        }
    }

    fun navigateUp() {
        when {
            path.isNotEmpty() -> { path = path.dropLast(1); search = ""; status = null }
            transfer != null -> transfer = null
            else -> onBack()
        }
    }

    BackHandler(enabled = working || path.isNotEmpty() || transfer != null) {
        if (!working) navigateUp()
    }

    val provider = access
    val folder = path.lastOrNull() ?: root
    val active = !working && !loading
    val writable = active && provider != null && hasWriteGrant
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(16.dp).testTag("nova-files"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Nova Files", style = MaterialTheme.typography.headlineSmall)
        if (working || loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        status?.let { Text(it) }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Button(enabled = active && selection != null && onChooseFolder != null,
                onClick = { onChooseFolder?.invoke() }) { Text("Choose folder") }
            if (path.isNotEmpty()) {
                TextButton(enabled = !working, onClick = { navigateUp() }) { Text("Up") }
            }
            TextButton(enabled = !working, onClick = onBack) { Text("Done") }
        }
        if (selection?.tree == null && !loading) Text("Choose a folder with Android to browse its files.")
        if (selection?.tree != null && folder == null && active) {
            TextButton(onClick = { path = emptyList(); status = null; reload++ }) { Text("Reopen root") }
        }
        if (folder != null && provider != null) {
            Text(folder.name, style = MaterialTheme.typography.titleMedium)
            val pending = transfer
            if (pending == null) {
                OutlinedTextField(search, { search = it }, label = { Text("Search this folder") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(), enabled = active)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FileSort.entries.forEach { order ->
                        FilterChip(sort == order, { sort = order }, { Text(order.label) }, enabled = active)
                    }
                    if (folder.supports(Document.FLAG_DIR_SUPPORTS_CREATE)) {
                        TextButton(enabled = writable, onClick = {
                            nameText = ""
                            nameRequest = FileNameRequest(FileNameAction.CREATE, folder)
                        }) { Text("New folder") }
                    }
                }
            } else {
                Text(if (pending.move) "Choose the destination folder for the move." else "Choose the destination folder for the copy.")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(
                        enabled = writable && folder.id != pending.source.id && folder.supports(Document.FLAG_DIR_SUPPORTS_CREATE),
                        onClick = {
                            if (pending.move) change("File moved", "The provider did not complete the move.") {
                                provider.move(pending.file, pending.source, folder)
                            } else change("Verified copy saved", "Copy failed. The source file was retained.") {
                                val job = currentCoroutineContext()
                                provider.copy(pending.file, folder) { job.isActive }
                            }
                        },
                    ) { Text(if (pending.move) "Move here" else "Copy here") }
                    TextButton(enabled = active, onClick = { transfer = null }) { Text("Cancel") }
                }
            }
            val ordering = when (sort) {
                FileSort.NAME -> compareBy<NovaFile> { it.name.lowercase() }
                FileSort.NEWEST -> compareByDescending<NovaFile> { it.modified ?: 0L }
                FileSort.SIZE -> compareByDescending<NovaFile> { it.bytes ?: 0L }
            }
            val shown = files.filter {
                if (pending != null) it.directory else it.name.contains(search.trim(), true)
            }.sortedWith(compareByDescending<NovaFile> { it.directory }.then(ordering))
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (active && shown.isEmpty()) item { Text("No matching items") }
                items(shown, key = { it.id }) { file ->
                    OutlinedCard(onClick = {
                        if (file.directory) {
                            path = path + file
                            search = ""
                            status = null
                        } else detail = file
                    }, enabled = active, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f).padding(vertical = 10.dp)) {
                                Text(file.name)
                                Text(if (file.directory) "Folder" else file.bytes?.let { Formatter.formatFileSize(space, it) }.orEmpty(),
                                    style = MaterialTheme.typography.labelMedium)
                            }
                            if (pending == null && file.directory) {
                                TextButton(enabled = active, onClick = { detail = file }) { Text("Details") }
                            }
                        }
                    }
                }
            }
        }
    }
    detail?.let { file ->
        AlertDialog(
            onDismissRequest = { detail = null },
            title = { Text(file.name) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(if (file.directory) "Folder" else file.mimeType.ifBlank { "File" })
                    file.bytes?.let { Text(Formatter.formatFileSize(space, it)) }
                    file.modified?.takeIf { it > 0L }?.let {
                        Text(java.text.DateFormat.getDateTimeInstance().format(java.util.Date(it)))
                    }
                    if (writable && folder != null) {
                        if (file.supports(Document.FLAG_SUPPORTS_RENAME)) TextButton(onClick = {
                            detail = null
                            nameText = file.name
                            nameRequest = FileNameRequest(FileNameAction.RENAME, folder, file)
                        }) { Text("Rename") }
                        if (!file.directory) TextButton(onClick = {
                            detail = null
                            transfer = FileTransfer(file, folder, move = false)
                        }) { Text("Copy to folder") }
                        if (!file.directory && file.supports(Document.FLAG_SUPPORTS_MOVE)) TextButton(onClick = {
                            detail = null
                            transfer = FileTransfer(file, folder, move = true)
                        }) { Text("Move to folder") }
                        if (file.supports(Document.FLAG_SUPPORTS_DELETE)) TextButton(onClick = {
                            detail = null
                            toDelete = file
                        }) { Text("Delete") }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { detail = null }) { Text("Close") } },
        )
    }
    nameRequest?.let { request ->
        AlertDialog(
            onDismissRequest = { nameRequest = null },
            title = { Text(request.action.label) },
            text = { OutlinedTextField(nameText, { nameText = it.take(160) }, label = { Text("Name") }, singleLine = true) },
            confirmButton = {
                val valid = runCatching { NovaFilesAccess.checkedName(nameText) }.isSuccess
                TextButton(enabled = valid && writable && provider != null, onClick = {
                    val requested = nameText
                    nameRequest = null
                    if (provider != null) change("Saved", "The name may already be in use, or the folder is unavailable.") {
                        if (request.file == null) provider.createFolder(request.parent, requested)
                        else provider.rename(request.parent, request.file, requested)
                    }
                }) { Text(request.action.label) }
            },
            dismissButton = { TextButton(onClick = { nameRequest = null }) { Text("Cancel") } },
        )
    }
    toDelete?.let { file ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Delete " + file.name + "?") },
            text = { if (file.directory) Text("The provider may also delete the contents of this folder.") },
            confirmButton = {
                TextButton(enabled = writable && provider != null, onClick = {
                    toDelete = null
                    if (provider != null) change("Item deleted", "The provider did not confirm deletion.") { provider.delete(file) }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Cancel") } },
        )
    }
}
