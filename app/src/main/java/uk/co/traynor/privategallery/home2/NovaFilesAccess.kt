package uk.co.traynor.privategallery.home2

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject
import uk.co.traynor.privategallery.core.home2.NovaCoreApp
import uk.co.traynor.privategallery.core.home2.NovaCoreAppDocuments
import uk.co.traynor.privategallery.core.home2.SpaceContext

internal data class NovaFile(
    val id: String,
    val name: String,
    val mimeType: String,
    val bytes: Long?,
    val modified: Long?,
    val flags: Int,
) {
    val directory get() = mimeType == Document.MIME_TYPE_DIR
    fun supports(flag: Int) = flags and flag != 0
}

internal fun acceptsPrimaryVaultFile(mime: String): Boolean =
    mime.length in 1..120 &&
        (mime.startsWith("image/") || mime.startsWith("video/") ||
            uk.co.traynor.privategallery.core.vault.VaultDocumentPolicy.isDocument(mime))

internal class NovaFileCleanupException : IOException("The provider retained an incomplete temporary file")

internal class NovaFilesAccess(
    private val resolver: ContentResolver,
    private val tree: Uri,
    private val stillAuthorized: () -> Boolean,
) {
    init {
        require(tree.scheme == ContentResolver.SCHEME_CONTENT && DocumentsContract.isTreeUri(tree))
    }

    val canWrite: Boolean
        get() = resolver.persistedUriPermissions.any { it.uri == tree && it.isWritePermission }

    private val rootId = DocumentsContract.getTreeDocumentId(tree)

    private fun requireAccess(write: Boolean = false) {
        check(stillAuthorized())
        check(resolver.persistedUriPermissions.any {
            it.uri == tree && it.isReadPermission && (!write || it.isWritePermission)
        }) { "Folder permission is unavailable" }
    }

    private fun uri(file: NovaFile): Uri = DocumentsContract.buildDocumentUriUsingTree(tree, file.id)

    fun authorizedFileUri(file: NovaFile): Uri {
        requireAccess()
        require(!file.directory && !file.supports(Document.FLAG_VIRTUAL_DOCUMENT))
        return uri(file)
    }

    data class Preview(val text: String, val truncated: Boolean)

    fun preview(file: NovaFile): Preview {
        require(file.mimeType.startsWith("text/") || file.mimeType == "application/json")
        val buffer = ByteArray(PREVIEW_LIMIT + 1)
        try {
            val fileUri = authorizedFileUri(file)
            val count = checkNotNull(resolver.openInputStream(fileUri)).use { input ->
                var size = 0
                while (size < buffer.size) {
                    check(stillAuthorized())
                    val read = input.read(buffer, size, buffer.size - size)
                    if (read < 0) break
                    if (read == 0) throw IOException("Provider made no progress")
                    size += read
                }
                size
            }
            requireAccess()
            val text = String(buffer, 0, minOf(count, PREVIEW_LIMIT), Charsets.UTF_8)
            require(text.none { it == '\u0000' }) { "Provider returned binary data" }
            return Preview(text, count > PREVIEW_LIMIT)
        } finally {
            buffer.fill(0)
        }
    }

    fun root(): NovaFile {
        requireAccess()
        return query(DocumentsContract.buildDocumentUriUsingTree(tree, rootId)).single()
    }

    fun children(folder: NovaFile): List<NovaFile> {
        requireAccess()
        require(folder.directory)
        return query(DocumentsContract.buildChildDocumentsUriUsingTree(tree, folder.id))
    }

    private fun query(target: Uri): List<NovaFile> {
        val cursor = resolver.query(target, PROJECTION, null, null, null)
            ?: throw IOException("The provider did not return a folder listing")
        return cursor.use {
            val id = it.getColumnIndexOrThrow(Document.COLUMN_DOCUMENT_ID)
            val name = it.getColumnIndexOrThrow(Document.COLUMN_DISPLAY_NAME)
            val mime = it.getColumnIndexOrThrow(Document.COLUMN_MIME_TYPE)
            val size = it.getColumnIndex(Document.COLUMN_SIZE)
            val modified = it.getColumnIndex(Document.COLUMN_LAST_MODIFIED)
            val flags = it.getColumnIndex(Document.COLUMN_FLAGS)
            buildList {
                while (it.moveToNext()) {
                    check(stillAuthorized())
                    add(NovaFile(it.getString(id), it.getString(name).orEmpty(), it.getString(mime).orEmpty(),
                        if (size < 0 || it.isNull(size)) null else it.getLong(size),
                        if (modified < 0 || it.isNull(modified)) null else it.getLong(modified),
                        if (flags < 0 || it.isNull(flags)) 0 else it.getInt(flags)))
                }
            }
        }
    }

    fun createFolder(parent: NovaFile, input: String) {
        requireAccess(write = true)
        require(parent.directory && parent.supports(Document.FLAG_DIR_SUPPORTS_CREATE))
        val name = checkedName(input)
        checkAvailable(parent, name)
        requireAccess(write = true)
        checkNotNull(DocumentsContract.createDocument(resolver, uri(parent), Document.MIME_TYPE_DIR, name))
    }

    fun rename(parent: NovaFile, file: NovaFile, input: String) {
        requireAccess(write = true)
        require(file.supports(Document.FLAG_SUPPORTS_RENAME))
        val name = checkedName(input)
        if (name == file.name) return
        checkAvailable(parent, name, exceptId = file.id)
        requireAccess(write = true)
        checkNotNull(DocumentsContract.renameDocument(resolver, uri(file), name))
    }

    fun delete(file: NovaFile) {
        requireAccess(write = true)
        require(file.id != rootId && file.supports(Document.FLAG_SUPPORTS_DELETE))
        check(DocumentsContract.deleteDocument(resolver, uri(file)))
    }

    fun move(file: NovaFile, parent: NovaFile, destination: NovaFile) {
        requireAccess(write = true)
        require(file.id != rootId && !file.directory && parent.id != destination.id)
        require(file.supports(Document.FLAG_SUPPORTS_MOVE) && destination.supports(Document.FLAG_DIR_SUPPORTS_CREATE))
        checkAvailable(destination, file.name)
        requireAccess(write = true)
        checkNotNull(DocumentsContract.moveDocument(resolver, uri(file), uri(parent), uri(destination)))
    }

    fun copy(file: NovaFile, destination: NovaFile, active: () -> Boolean) {
        requireAccess(write = true)
        require(!file.directory && destination.directory && destination.supports(Document.FLAG_DIR_SUPPORTS_CREATE))
        checkAvailable(destination, file.name)
        check(active())
        requireAccess(write = true)
        val created = checkNotNull(DocumentsContract.createDocument(resolver, uri(destination),
            file.mimeType.ifBlank { "application/octet-stream" }, "NovaOS-incomplete-" + UUID.randomUUID()))
        var verified = false
        try {
            val staged = query(created).single()
            check(staged.supports(Document.FLAG_SUPPORTS_RENAME) && staged.supports(Document.FLAG_SUPPORTS_DELETE))
            val digest = MessageDigest.getInstance("SHA-256")
            val byteCount = checkNotNull(resolver.openInputStream(uri(file))).use { input ->
                checkNotNull(resolver.openOutputStream(created, "w")).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var count = 0L
                    try {
                        while (true) {
                            check(active() && stillAuthorized())
                            val size = input.read(buffer)
                            if (size < 0) break
                            output.write(buffer, 0, size)
                            digest.update(buffer, 0, size)
                            count += size
                        }
                    } finally {
                        buffer.fill(0)
                    }
                    count
                }
            }
            check(file.bytes == null || byteCount == file.bytes)
            val expected = digest.digest()
            val actual = checkNotNull(resolver.openInputStream(created)).use { input ->
                val buffer = ByteArray(64 * 1024)
                try {
                    while (true) {
                        check(active() && stillAuthorized())
                        val size = input.read(buffer)
                        if (size < 0) break
                        digest.update(buffer, 0, size)
                    }
                } finally {
                    buffer.fill(0)
                }
                digest.digest()
            }
            check(MessageDigest.isEqual(expected, actual))
            checkAvailable(destination, file.name)
            check(active())
            requireAccess(write = true)
            checkNotNull(DocumentsContract.renameDocument(resolver, created, file.name))
            verified = true
        } finally {
            if (!verified && !runCatching { DocumentsContract.deleteDocument(resolver, created) }.getOrDefault(false)) {
                throw NovaFileCleanupException()
            }
        }
    }

    private fun checkAvailable(folder: NovaFile, name: String, exceptId: String? = null) {
        check(children(folder).none { it.id != exceptId && it.name.equals(name, ignoreCase = true) }) {
            "An item with this name already exists"
        }
    }

    companion object {
        private const val PREVIEW_LIMIT = 32 * 1024
        private val PROJECTION = arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_MIME_TYPE, Document.COLUMN_SIZE, Document.COLUMN_LAST_MODIFIED, Document.COLUMN_FLAGS)

        fun checkedName(input: String): String {
            val name = input.trim()
            require(name.length in 1..160 && name != "." && name != ".." &&
                name.none { it == '/' || it == '\\' || it.isISOControl() })
            return name
        }
    }
}

internal class NovaFilesSelection(space: SpaceContext) {
    private val documents = NovaCoreAppDocuments(space, NovaCoreApp.FILES)

    data class Selection(
        val revision: Long?,
        val tree: Uri?,
        val favourites: Set<String> = emptySet(),
        val recent: List<String> = emptyList(),
    )

    fun read(): Selection {
        val document = documents.read("authed-tree") ?: return Selection(null, null)
        val value = JSONObject(document.json)
        val tree = value.getString("tree").takeIf(String::isNotBlank)?.let { Uri.parse(it) }
        return Selection(document.revision, tree, readIds(value, "favourites").toSet(), readIds(value, "recent"))
    }

    fun save(previous: Selection, tree: Uri?): Selection? =
        update(previous.revision, if (previous.tree == tree) previous else Selection(previous.revision, tree))

    fun markViewed(previous: Selection, id: String): Selection? {
        require(previous.tree != null && id.length in 1..MAX_ID_LENGTH)
        return update(previous.revision, previous.copy(recent = (listOf(id) + previous.recent).distinct().take(MAX_IDS)))
    }

    fun toggleFavourite(previous: Selection, id: String): Selection? {
        require(previous.tree != null && id.length in 1..MAX_ID_LENGTH)
        val next = if (id in previous.favourites) previous.favourites - id else previous.favourites + id
        require(next.size <= MAX_IDS) { "Favourites list is full" }
        return update(previous.revision, previous.copy(favourites = next))
    }

    private fun update(revision: Long?, selection: Selection): Selection? {
        val value = JSONObject().put("tree", selection.tree?.toString().orEmpty())
            .put("favourites", JSONArray(selection.favourites.toList()))
            .put("recent", JSONArray(selection.recent))
        val document = documents.save("authed-tree", revision, value.toString()) ?: return null
        return selection.copy(revision = document.revision)
    }

    private fun readIds(value: JSONObject, key: String): List<String> {
        val entries = value.optJSONArray(key) ?: return emptyList()
        require(entries.length() <= MAX_IDS)
        return (0 until entries.length()).map { entries.getString(it).also { id ->
            require(id.length in 1..MAX_ID_LENGTH)
        } }.distinct()
    }

    private companion object {
        const val MAX_IDS = 80
        const val MAX_ID_LENGTH = 1_024
    }
}
