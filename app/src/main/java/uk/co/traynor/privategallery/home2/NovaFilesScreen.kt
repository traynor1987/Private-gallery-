package uk.co.traynor.privategallery.home2

import android.app.DownloadManager
import android.content.Intent
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
private enum class FilesSection(val label: String) { BROWSE("Browse"), RECENT("Recent"), INFO("Info") }

private enum class FileType(val label: String) {
    ALL("All types"), PDF("PDF"), IMAGE("Image"), TEXT("Text"), AUDIO("Audio"), OTHER("Other");

    companion object {
        fun of(mime: String): FileType = when {
            mime == "application/pdf" -> PDF
            mime.startsWith("image/") -> IMAGE
            mime.startsWith("audio/") -> AUDIO
            mime.startsWith("text/") || mime == "application/json" -> TEXT
            else -> OTHER
        }
    }
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
    onImportPrimaryFile: ((Uri, (String) -> Unit) -> Unit)? = null,
    onOpenNote: ((String) -> Unit)? = null,
    openExternal: ((Intent) -> Boolean)? = null,
) {
    val scope = rememberCoroutineScope()
    var selection by remember(space) { mutableStateOf<NovaFilesSelection.Selection?>(null) }
    var access by remember(space) { mutableStateOf<NovaFilesAccess?>(null) }
    var hasWriteGrant by remember(space) { mutableStateOf(false) }
    var root by remember(space) { mutableStateOf<NovaFile?>(null) }
    var path by remember(space) { mutableStateOf<List<NovaFile>>(emptyList()) }
    var files by remember(space) { mutableStateOf<List<NovaFile>>(emptyList()) }
    var sort by rememberSaveable { mutableStateOf(FileSort.NEWEST) }
    var section by rememberSaveable { mutableStateOf(FilesSection.BROWSE) }
    var grid by rememberSaveable { mutableStateOf(true) }
    var favouritesOnly by rememberSaveable { mutableStateOf(false) }
    var type by rememberSaveable { mutableStateOf(FileType.ALL) }
    var savingMarks by remember(space) { mutableStateOf(false) }
    var search by remember(space) { mutableStateOf("") }
    var status by remember(space) { mutableStateOf(pickerMessage) }
    var working by remember(space) { mutableStateOf(false) }
    var loading by remember(space) { mutableStateOf(true) }
    var reload by remember(space) { mutableIntStateOf(0) }
    var detail by remember(space) { mutableStateOf<NovaFile?>(null) }
    var preview by remember(space, detail?.id) { mutableStateOf<NovaFilesAccess.Preview?>(null) }
    var toAttach by remember(space) { mutableStateOf<NovaFile?>(null) }
    var toImport by remember(space) { mutableStateOf<NovaFile?>(null) }
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
    LaunchedEffect(space, selection != null, selection?.tree, path, reload) {
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

    fun mark(file: NovaFile, favourite: Boolean) {
        val previous = selection ?: return
        if (savingMarks) return
        savingMarks = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val store = NovaFilesSelection(space)
                    if (favourite) store.toggleFavourite(previous, file.id) else store.markViewed(previous, file.id)
                }
            }
            if (!space.current()) return@launch
            val updated = result.getOrNull()
            if (updated != null) selection = updated
            else status = "Could not update this folder’s file history."
            savingMarks = false
        }
    }

    fun navigateUp() {
        when {
            transfer != null -> transfer = null
            section != FilesSection.BROWSE -> section = FilesSection.BROWSE
            path.isNotEmpty() -> { path = path.dropLast(1); search = ""; status = null }
            else -> onBack()
        }
    }

    BackHandler(enabled = working || path.isNotEmpty() || transfer != null || section != FilesSection.BROWSE) {
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
        CoreWorkspaceHeader("Nova Files", enabled = !working, onBack = ::navigateUp)
        if (transfer == null) Row(Modifier.horizontalScroll(rememberScrollState())) {
            FilesSection.entries.forEach { tab ->
                FilterChip(section == tab, { section = tab }, { Text(tab.label) },
                    modifier = Modifier.padding(end = 8.dp))
            }
        }
        if (working || loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        status?.let { Text(it) }
        NovaCoreAppActions {
            Button(enabled = active && selection != null && onChooseFolder != null,
                onClick = { onChooseFolder?.invoke() }) { Text("Choose folder") }
            if (openExternal != null) TextButton(enabled = !working, onClick = {
                if (!openExternal(Intent(DownloadManager.ACTION_VIEW_DOWNLOADS))) {
                    status = "Android Downloads could not be opened."
                }
            }) { Text("Android Downloads") }
            if (path.isNotEmpty()) {
                TextButton(enabled = !working, onClick = { navigateUp() }) { Text("Up") }
            }
        }
        if (selection?.tree == null && !loading) Text("Choose a folder with Android to browse its files.")
        if (selection?.tree != null && folder == null && active) {
            TextButton(onClick = { path = emptyList(); status = null; reload++ }) { Text("Reopen root") }
        }
        if (folder != null && provider != null) {
            WorkspaceLabel(folder.name, if (loading) "" else "${files.size} items")
            val pending = transfer
            if (pending == null && section != FilesSection.INFO) {
                WorkspaceSearch(search, { search = it }, "Search this folder", Modifier.fillMaxWidth(), enabled = active)
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
            } else if (pending != null) {
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
            if (pending == null && section != FilesSection.INFO) {
                var typeMenu by remember { mutableStateOf(false) }
                Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                    Box {
                        TextButton(onClick = { typeMenu = true }, enabled = active) {
                            Icon(Icons.Default.FilterList, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(type.label)
                        }
                        DropdownMenu(typeMenu, { typeMenu = false }) {
                            FileType.entries.forEach { option ->
                                DropdownMenuItem({ Text(option.label) }, { type = option; typeMenu = false })
                            }
                        }
                    }
                    FilterChip(favouritesOnly, { favouritesOnly = !favouritesOnly }, { Text("Favourites") })
                    if (section == FilesSection.BROWSE) IconButton({ grid = !grid }, enabled = active) {
                        Icon(if (grid) Icons.AutoMirrored.Filled.ViewList else Icons.Default.GridView,
                            if (grid) "List folders" else "Grid folders")
                    }
                }
            }
            val ordering = when (sort) {
                FileSort.NAME -> compareBy<NovaFile> { it.name.lowercase() }
                FileSort.NEWEST -> compareByDescending<NovaFile> { it.modified ?: 0L }
                FileSort.SIZE -> compareByDescending<NovaFile> { it.bytes ?: 0L }
            }
            val recent = selection?.recent.orEmpty().withIndex().associate { it.value to it.index }
            val favourites = selection?.favourites.orEmpty()
            val shown = files.filter { file ->
                if (pending != null) file.directory else file.name.contains(search.trim(), true) &&
                    (!favouritesOnly || file.id in favourites) &&
                    (section != FilesSection.RECENT || file.id in recent) &&
                    (file.directory || type == FileType.ALL || FileType.of(file.mimeType) == type)
            }.let { entries ->
                if (section == FilesSection.RECENT && sort == FileSort.NEWEST) entries.sortedBy { recent[it.id] }
                else entries.sortedWith(compareByDescending<NovaFile> { it.directory }.then(ordering))
            }
            val openFile: (NovaFile) -> Unit = { file ->
                if (file.directory) {
                    path = path + file
                    section = FilesSection.BROWSE
                    favouritesOnly = false
                    search = ""
                    status = null
                } else {
                    detail = file
                    mark(file, favourite = false)
                }
            }
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val columns = if (maxWidth >= 640.dp) 3 else 2
                val gridFolders = grid && section == FilesSection.BROWSE
                val folders = if (gridFolders) shown.filter { it.directory } else emptyList()
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (active && pending == null && (section == FilesSection.INFO || (section == FilesSection.BROWSE && path.isEmpty()))) {
                        item { AuthorisedStorageCard(space, files) }
                    }
                    if (section == FilesSection.INFO) return@LazyColumn
                    if (active && shown.isEmpty()) item {
                        Text(if (section == FilesSection.RECENT) "No recently opened files in this folder" else "No matching items")
                    }
                    if (folders.isNotEmpty()) item { WorkspaceLabel("Folders", folders.size.toString()) }
                    items(folders.chunked(columns)) { group ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            group.forEach { file ->
                                Column(Modifier.weight(1f)) {
                                    WorkspaceFolderCard(file.name, "", Modifier.fillMaxWidth(), enabled = active) { openFile(file) }
                                    if (pending == null) TextButton(
                                        onClick = { detail = file }, enabled = active,
                                        modifier = Modifier.semantics { contentDescription = "Details for ${file.name}" },
                                    ) { Text("Details") }
                                }
                            }
                            repeat(columns - group.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                    items(shown.filterNot { gridFolders && it.directory }, key = { it.id }) { file ->
                        val fileType = FileType.of(file.mimeType)
                        val subtitle = if (file.directory) "Folder" else listOfNotNull(
                            fileType.takeUnless { it == FileType.OTHER }?.label,
                            file.bytes?.takeIf { it >= 0 }?.let { Formatter.formatFileSize(space, it) },
                        ).joinToString(" · ").ifBlank { "File" }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            WorkspaceRow(file.name, subtitle, if (file.directory) Icons.Default.Folder else fileIcon(fileType.label),
                                Modifier.weight(1f), enabled = active) { openFile(file) }
                            if (pending == null && file.directory) {
                                IconButton({ detail = file }, enabled = active) { Icon(Icons.Default.Info, "Details for ${file.name}") }
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
                    if (!file.directory) {
                        TextButton(enabled = !savingMarks && active, onClick = { mark(file, favourite = true) }) {
                            Text(if (file.id in selection?.favourites.orEmpty()) "Remove favourite" else "Add to favourites")
                        }
                    }
                    if (provider != null && !file.directory) {
                        if (openExternal != null && !file.supports(Document.FLAG_VIRTUAL_DOCUMENT)) {
                            TextButton(enabled = active, onClick = {
                                working = true
                                scope.launch {
                                    val share = withContext(Dispatchers.IO) {
                                        runCatching { novaFileShareIntent(provider.authorizedFileUri(file), file) }.getOrNull()
                                    }
                                    if (!space.current()) return@launch
                                    working = false
                                    if (detail?.id == file.id) {
                                        detail = null
                                        if (share == null || !openExternal(Intent.createChooser(share, "Share file"))) {
                                            status = "The selected document could not be shared."
                                        }
                                    }
                                }
                            }) { Text("Share file…") }
                        }
                        if (file.mimeType.startsWith("text/") || file.mimeType == "application/json") {
                            TextButton(enabled = active, onClick = {
                                working = true
                                scope.launch {
                                    val opened = withContext(Dispatchers.IO) { runCatching { provider.preview(file) } }
                                    if (!space.current()) return@launch
                                    if (detail?.id == file.id) preview = opened.getOrNull()
                                    if (opened.isFailure) status = "The file preview could not be opened."
                                    working = false
                                }
                            }) { Text("Preview text") }
                        }
                        if (onOpenNote != null && !file.supports(Document.FLAG_VIRTUAL_DOCUMENT) &&
                            (file.bytes ?: 0L) <= uk.co.traynor.privategallery.core.home2.NovaNoteAttachment.MAX_BYTES) {
                            TextButton(enabled = active, onClick = { detail = null; toAttach = file }) {
                                Text("Attach to a Nova Note")
                            }
                        }
                        if (onImportPrimaryFile != null && !file.supports(Document.FLAG_VIRTUAL_DOCUMENT) &&
                            acceptsPrimaryVaultFile(file.mimeType) &&
                            (file.bytes ?: 0L) <= uk.co.traynor.privategallery.core.backup.PortableArchive.MAX_ITEM) {
                            TextButton(enabled = active, onClick = { detail = null; toImport = file }) {
                                Text("Import into Primary Vault")
                            }
                        }
                    }
                    file.bytes?.let { Text(Formatter.formatFileSize(space, it)) }
                    preview?.let {
                        if (it.truncated) Text("Showing the first 32 KiB")
                        Text(it.text.ifEmpty { "Empty file" })
                    }
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
    toAttach?.let { file ->
        NovaNotePicker(space, title = "Attach to a note", includeDraft = true, onDismiss = { toAttach = null }, onChoose = { id ->
            toAttach = null
            working = true
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        val source = checkNotNull(provider).authorizedFileUri(file)
                        saveNovaNoteAttachment(space, source, id, allowStart = true)
                    }
                }
                if (!space.current()) return@launch
                working = false
                if (result.isSuccess) onOpenNote?.invoke(id)
                else status = "Could not attach the file. Check that it is 24 KiB or less and that no other note draft is open."
            }
        })
    }
    toImport?.let { file ->
        AlertDialog(
            onDismissRequest = { toImport = null },
            title = { Text("Import into Primary Vault?") },
            text = { Text("Create a verified encrypted copy of " + file.name + ". The original will stay in its folder.") },
            confirmButton = {
                TextButton(enabled = active && provider != null && onImportPrimaryFile != null, onClick = {
                    toImport = null
                    working = true
                    scope.launch {
                        val resolved = withContext(Dispatchers.IO) { runCatching { provider?.authorizedFileUri(file) } }
                        if (!space.current()) return@launch
                        val source = resolved.getOrNull()
                        if (source == null || onImportPrimaryFile == null) {
                            status = "Folder access is unavailable."
                            working = false
                        } else onImportPrimaryFile(source) { result ->
                            if (space.current()) {
                                status = result
                                working = false
                            }
                        }
                    }
                }) { Text("Import copy") }
            },
            dismissButton = { TextButton(onClick = { toImport = null }) { Text("Cancel") } },
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


@Composable
private fun AuthorisedStorageCard(space: SpaceContext, files: List<NovaFile>) {
    val colors = MaterialTheme.colorScheme
    val documents = files.filterNot { it.directory }
    val sizes = documents.mapNotNull { file ->
        file.bytes?.takeIf { it >= 0 }?.let { FileType.of(file.mimeType) to it }
    }
    val totals = sizes.groupBy({ it.first }, { it.second }).mapValues { (_, values) ->
        values.fold(0L) { total, bytes -> total + bytes.coerceAtMost(Long.MAX_VALUE - total) }
    }
    val reported = totals.values.fold(0L) { total, bytes -> total + bytes.coerceAtMost(Long.MAX_VALUE - total) }
    val palette = mapOf(
        FileType.PDF to colors.primary, FileType.IMAGE to colors.tertiary,
        FileType.AUDIO to colors.secondary, FileType.TEXT to colors.onSurfaceVariant,
        FileType.OTHER to colors.outline,
    )
    WorkspaceStorageCard(
        "Storage overview",
        if (sizes.isEmpty() && documents.isNotEmpty()) "Unavailable" else Formatter.formatFileSize(space, reported),
        if (documents.size == sizes.size) "Reported file sizes in this folder. Drive capacity unavailable."
        else "Sizes reported for ${sizes.size} of ${documents.size} files. Drive capacity unavailable.",
        totals.map { (type, bytes) -> bytes.toFloat() to palette.getValue(type) },
        Modifier.testTag("nova-files-storage"),
    )
    totals.forEach { (type, bytes) ->
        Text("${type.label} · ${Formatter.formatFileSize(space, bytes)}", style = MaterialTheme.typography.labelMedium)
    }
}
