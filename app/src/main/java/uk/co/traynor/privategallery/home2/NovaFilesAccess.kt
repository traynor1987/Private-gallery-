package uk.co.traynor.privategallery.home2

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID
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
            if (!verified) runCatching { DocumentsContract.deleteDocument(resolver, created) }
        }
    }

    private fun checkAvailable(folder: NovaFile, name: String, exceptId: String? = null) {
        check(children(folder).none { it.id != exceptId && it.name.equals(name, ignoreCase = true) }) {
            "An item with this name already exists"
        }
    }

    companion object {
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

    data class Selection(val revision: Long?, val tree: Uri?)

    fun read(): Selection {
        val document = documents.read("authed-tree") ?: return Selection(null, null)
        val tree = JSONObject(document.json).getString("tree").takeIf(String::isNotBlank)?.let { Uri.parse(it) }
        return Selection(document.revision, tree)
    }

    fun save(previous: Selection, tree: Uri?): Selection? {
        val document = documents.save("authed-tree", previous.revision,
            JSONObject().put("tree", tree?.toString().orEmpty()).toString()) ?: return null
        return Selection(document.revision, tree)
    }
}
