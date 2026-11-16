package uk.co.traynor.privategallery.core.home2

import android.content.Context
import org.json.JSONObject

internal enum class NovaCoreApp(val storageId: String, val route: String, val label: String, val symbol: String) {
    NOTES("notes", "nova-notes-draft", "Nova Notes", "edit"),
    TASKS("tasks", "nova-tasks", "Nova Tasks", "apps"),
    CALENDAR("calendar", "nova-calendar", "Nova Calendar", "widgets"),
    FILES("files", "nova-files", "Nova Files", "folder"),
    CLIPBOARD("clipboard", "nova-clipboard", "Nova Clipboard", "edit"),
}

/** Stores app documents through the authenticated, encrypted Primary Space preferences. */
internal class NovaCoreAppDocuments(space: SpaceContext, app: NovaCoreApp) {
    private val prefs = space.also { require(it.spaceId == "primary") }
        .getSharedPreferences("nova-core-${app.storageId}-v1", Context.MODE_PRIVATE)

    data class Document(val revision: Long, val json: String)

    fun read(id: String): Document? = synchronized(lock) { stored(id) }

    /**
     * A missing document expects a null revision. Stale writers cannot replace a later draft.
     * Call on a background dispatcher, as a successful return requires a durable commit.
     */
    fun save(id: String, expectedRevision: Long?, json: String): Document? =
        saveTogether(mapOf(id to Change(expectedRevision, json)))?.getValue(id)

    data class Change(val expectedRevision: Long?, val json: String)

    /** One encrypted preferences commit publishes all changes, or none on a conflict. */
    fun saveTogether(changes: Map<String, Change>): Map<String, Document>? = synchronized(lock) {
        val next = linkedMapOf<String, Document>()
        for ((id, change) in changes) {
            require(change.json.length <= MAX_DOCUMENT_CHARS)
            JSONObject(change.json)
            val current = stored(id)
            if (current?.revision != change.expectedRevision) return@synchronized null
            next[id] = Document(Math.addExact(current?.revision ?: 0L, 1L), change.json)
        }
        val editor = prefs.edit()
        for ((id, document) in next) {
            editor.putString(id, JSONObject().put("revision", document.revision).put("json", document.json).toString())
        }
        check(editor.commit()) { "App documents could not be saved" }
        next
    }

    private fun stored(id: String): Document? {
        require(ID.matches(id))
        val value = prefs.getString(id, null)?.let { JSONObject(it) } ?: return null
        val revision = value.getLong("revision")
        val json = value.getString("json")
        check(revision > 0 && json.length <= MAX_DOCUMENT_CHARS)
        JSONObject(json)
        return Document(revision, json)
    }

    private companion object {
        val lock = Any()
        val ID = Regex("[a-zA-Z0-9_-]{1,80}")
        const val MAX_DOCUMENT_CHARS = 262_144
    }
}
