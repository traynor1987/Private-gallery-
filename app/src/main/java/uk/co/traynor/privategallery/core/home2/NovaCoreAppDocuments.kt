package uk.co.traynor.privategallery.core.home2

import android.content.Context
import org.json.JSONObject

internal enum class NovaCoreApp(val storageId: String) {
    NOTES("notes"), TASKS("tasks"), CALENDAR("calendar"), FILES("files"), CLIPBOARD("clipboard"),
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
    fun save(id: String, expectedRevision: Long?, json: String): Document? = synchronized(lock) {
        require(json.length <= MAX_DOCUMENT_CHARS)
        JSONObject(json)
        val current = stored(id)
        if (current?.revision != expectedRevision) return@synchronized null
        val next = Document(Math.addExact(current?.revision ?: 0L, 1L), json)
        val value = JSONObject().put("revision", next.revision).put("json", next.json)
        check(prefs.edit().putString(id, value.toString()).commit()) { "App document could not be saved" }
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
