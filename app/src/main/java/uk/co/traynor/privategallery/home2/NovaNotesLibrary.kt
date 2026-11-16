package uk.co.traynor.privategallery.home2

import java.security.MessageDigest
import java.util.HexFormat
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject
import uk.co.traynor.privategallery.core.home2.NovaCoreApp
import uk.co.traynor.privategallery.core.home2.NovaCoreAppDocuments
import uk.co.traynor.privategallery.core.home2.SpaceContext
import uk.co.traynor.privategallery.core.home2.NovaNoteAttachment

internal data class NovaNote(
    val id: String,
    val title: String,
    val body: String,
    val pinned: Boolean = false,
    val notebook: String = "",
    val tags: String = "",
    val attachments: List<NovaNoteAttachment> = emptyList(),
)

internal fun notebookKey(name: String) = name.trim().lowercase(Locale.ROOT)

internal class NovaNotesLibrary(space: SpaceContext) {
    private val documents = NovaCoreAppDocuments(space, NovaCoreApp.NOTES)

    data class Snapshot(
        val revision: Long?, val notes: List<NovaNote>, val protectedNotebooks: Set<String> = emptySet(),
    ) {
        private val notesById by lazy { notes.associateBy(NovaNote::id) }

        fun isProtected(note: NovaNote) = notebookKey(note.notebook) in protectedNotebooks

        fun requiredProtection(note: NovaNote): Set<String> {
            val original = notesById[note.id]?.notebook
            return listOfNotNull(note.notebook, original).map(::notebookKey)
                .filter { it in protectedNotebooks }.toSet()
        }
    }

    fun read(): Snapshot {
        val document = documents.read(LIBRARY)
        if (document == null) {
            val draft = documents.read("draft")?.let { JSONObject(it.json) }
            val notes = draft?.let {
                listOf(NovaNote("legacy-draft", it.optString("title"), it.optString("body")))
            }.orEmpty()
            return Snapshot(null, notes)
        }
        val value = JSONObject(document.json)
        val entries = value.getJSONArray("notes")
        val locked = value.optJSONArray("protectedNotebooks")?.let { array ->
            require(array.length() <= 512)
            (0 until array.length()).map { notebookKey(array.getString(it)) }.toSet()
        }.orEmpty()
        return Snapshot(document.revision, List(entries.length()) { index ->
            decode(entries.getJSONObject(index))
        }, locked)
    }

    data class Draft(val revision: Long?, val baseFingerprint: String?, val note: NovaNote?)

    fun readDraft(): Draft {
        val saved = documents.read(EDITOR) ?: return Draft(null, null, null)
        val value = JSONObject(saved.json)
        return Draft(saved.revision, value.optString("baseFingerprint").takeIf(String::isNotEmpty),
            value.optJSONObject("note")?.let(::decode))
    }

    fun writeDraft(previous: Draft, base: NovaNote?, note: NovaNote): Draft? {
        require(base == null || base.id == note.id)
        require(previous.note == null || previous.note.id == note.id)
        val fingerprint = if (previous.note != null) previous.baseFingerprint else base?.let(::fingerprint)
        val value = JSONObject().put("baseFingerprint", fingerprint).put("note", encode(note))
        val saved = documents.save(EDITOR, previous.revision, value.toString()) ?: return null
        return Draft(saved.revision, fingerprint, note)
    }

    fun attachToDraft(noteId: String, attachment: NovaNoteAttachment, allowStart: Boolean = false): Boolean {
        val draft = readDraft()
        val base = if (draft.note == null && allowStart) read().notes.find { it.id == noteId } else null
        val note = (draft.note ?: base)?.takeIf { it.id == noteId } ?: return false
        if (allowStart && read().requiredProtection(note).isNotEmpty()) return false
        if (note.attachments.any { it.id == attachment.id }) return true
        if (note.attachments.size >= NovaNoteAttachment.MAX_COUNT) return false
        return writeDraft(draft, base, note.copy(attachments = note.attachments + attachment)) != null
    }

    fun discardDraft(previous: Draft): Draft? {
        // Keep the revision monotonic across discard and recreation.
        val cleared = documents.save(EDITOR, previous.revision, "{}") ?: return null
        return Draft(cleared.revision, null, null)
    }

    fun finishDraft(
        draft: Draft, delete: Boolean, copyId: String? = null, unlockedNotebooks: Set<String> = emptySet(),
    ): Snapshot? {
        val note = requireNotNull(draft.note)
        val current = read()
        if (!unlockedNotebooks.containsAll(current.requiredProtection(note))) return null
        require(copyId == null || (copyId.isNotBlank() && !delete))
        if (copyId != null && current.notes.any { it.id == copyId }) return null
        if (copyId == null && current.notes.find { it.id == note.id }?.let(::fingerprint) != draft.baseFingerprint) return null
        val notes = when {
            copyId != null -> listOf(note.copy(id = copyId)) + current.notes
            delete -> current.notes.filterNot { it.id == note.id }
            draft.baseFingerprint == null -> listOf(note) + current.notes
            else -> current.notes.map { if (it.id == note.id) note else it }
        }
        val saved = documents.saveTogether(mapOf(
            LIBRARY to NovaCoreAppDocuments.Change(current.revision, encodeLibrary(notes, current.protectedNotebooks)),
            EDITOR to NovaCoreAppDocuments.Change(draft.revision, "{}"),
        )) ?: return null
        return Snapshot(saved.getValue(LIBRARY).revision, notes, current.protectedNotebooks)
    }

    fun save(previous: Snapshot, notes: List<NovaNote>): Snapshot? {
        val document = documents.save(LIBRARY, previous.revision,
            encodeLibrary(notes, previous.protectedNotebooks)) ?: return null
        return Snapshot(document.revision, notes.toList(), previous.protectedNotebooks)
    }

    fun setNotebookProtected(previous: Snapshot, notebook: String, protect: Boolean): Snapshot? {
        val key = notebookKey(notebook)
        require(key.isNotBlank() && key.length <= 120)
        val names = if (protect) previous.protectedNotebooks + key else previous.protectedNotebooks - key
        require(names.size <= 512)
        val document = documents.save(LIBRARY, previous.revision, encodeLibrary(previous.notes, names)) ?: return null
        return Snapshot(document.revision, previous.notes, names)
    }

    private fun encodeLibrary(notes: List<NovaNote>, protectedNotebooks: Set<String>): String {
        val entries = JSONArray()
        notes.forEach { entries.put(encode(it)) }
        val library = JSONObject().put("notes", entries)
        if (protectedNotebooks.isNotEmpty()) library.put("protectedNotebooks", JSONArray(protectedNotebooks.sorted()))
        return library.toString()
    }

    private fun fingerprint(note: NovaNote): String {
        val fields = JSONArray(listOf(note.id, note.title, note.body, note.pinned))
        if (note.notebook.isNotEmpty() || note.tags.isNotEmpty()) fields.put(note.notebook).put(note.tags)
        if (note.attachments.isNotEmpty()) fields.put(JSONArray(note.attachments.map { it.encode() }))
        val digest = MessageDigest.getInstance("SHA-256")
        for (unit in fields.toString()) {
            digest.update((unit.code ushr 8).toByte())
            digest.update(unit.code.toByte())
        }
        return HexFormat.of().formatHex(digest.digest())
    }

    private fun encode(note: NovaNote) =
        JSONObject().put("id", note.id).put("title", note.title).put("body", note.body).put("pinned", note.pinned)
            .apply {
                if (note.notebook.isNotEmpty()) put("notebook", note.notebook)
                if (note.tags.isNotEmpty()) put("tags", note.tags)
                if (note.attachments.isNotEmpty()) {
                    require(note.attachments.size <= NovaNoteAttachment.MAX_COUNT)
                    put("attachments", JSONArray(note.attachments.map { it.encode() }))
                }
            }

    private fun decode(entry: JSONObject) = NovaNote(
        entry.getString("id"), entry.getString("title"), entry.getString("body"), entry.getBoolean("pinned"),
        entry.optString("notebook"), entry.optString("tags"),
        entry.optJSONArray("attachments")?.let { attachments ->
            require(attachments.length() <= NovaNoteAttachment.MAX_COUNT)
            List(attachments.length()) { NovaNoteAttachment.decode(attachments.getJSONObject(it)) }
                .also { require(it.map(NovaNoteAttachment::id).toSet().size == it.size) }
        }.orEmpty(),
    )

    private companion object {
        const val LIBRARY = "library"
        const val EDITOR = "editor"
    }
}
