package uk.co.traynor.privategallery.home2

import org.json.JSONArray
import org.json.JSONObject
import uk.co.traynor.privategallery.core.home2.NovaCoreApp
import uk.co.traynor.privategallery.core.home2.NovaCoreAppDocuments
import uk.co.traynor.privategallery.core.home2.SpaceContext

internal data class NovaNote(
    val id: String,
    val title: String,
    val body: String,
    val pinned: Boolean = false,
)

internal class NovaNotesLibrary(space: SpaceContext) {
    private val documents = NovaCoreAppDocuments(space, NovaCoreApp.NOTES)

    data class Snapshot(val revision: Long?, val notes: List<NovaNote>)

    fun read(): Snapshot {
        val document = documents.read("library")
        if (document == null) {
            val draft = documents.read("draft")?.let { JSONObject(it.json) }
            val notes = draft?.let {
                listOf(NovaNote("legacy-draft", it.optString("title"), it.optString("body")))
            }.orEmpty()
            return Snapshot(null, notes)
        }
        val entries = JSONObject(document.json).getJSONArray("notes")
        return Snapshot(document.revision, List(entries.length()) { index ->
            val entry = entries.getJSONObject(index)
            NovaNote(entry.getString("id"), entry.getString("title"), entry.getString("body"),
                entry.getBoolean("pinned"))
        })
    }

    fun save(previous: Snapshot, notes: List<NovaNote>): Snapshot? {
        val entries = JSONArray()
        for (note in notes) {
            entries.put(JSONObject().put("id", note.id).put("title", note.title)
                .put("body", note.body).put("pinned", note.pinned))
        }
        val document = documents.save("library", previous.revision,
            JSONObject().put("notes", entries).toString()) ?: return null
        return Snapshot(document.revision, notes.toList())
    }
}
