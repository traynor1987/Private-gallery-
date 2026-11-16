package uk.co.traynor.privategallery.home2

import java.time.Duration
import java.time.Instant
import org.json.JSONArray
import org.json.JSONObject
import uk.co.traynor.privategallery.core.home2.NovaCoreApp
import uk.co.traynor.privategallery.core.home2.NovaCoreAppDocuments
import uk.co.traynor.privategallery.core.home2.SpaceContext

internal enum class SnippetRetention(val label: String, val days: Long?) {
    MANUAL("Until deleted", null), WEEK("7 days", 7), MONTH("30 days", 30);

    fun retains(snippet: NovaSnippet, at: Instant): Boolean =
        days == null || snippet.pinned || snippet.updated >= at.minus(Duration.ofDays(days))
}

internal data class NovaSnippet(
    val id: String,
    val title: String,
    val text: String,
    val category: String,
    val pinned: Boolean,
    val sensitive: Boolean,
    val updated: Instant,
)

internal class NovaClipboardLibrary(space: SpaceContext) {
    private val documents = NovaCoreAppDocuments(space, NovaCoreApp.CLIPBOARD)

    data class Snapshot(val revision: Long?, val retention: SnippetRetention, val snippets: List<NovaSnippet>)

    fun read(at: Instant): Snapshot {
        val current = load()
        val retained = current.snippets.filter { current.retention.retains(it, at) }
        if (retained.size == current.snippets.size) return current
        return save(current, current.retention, retained, at)
            ?: throw IllegalStateException("Snippets changed while applying retention")
    }

    private fun load(): Snapshot {
        val document = documents.read("library") ?: return Snapshot(null, SnippetRetention.MANUAL, emptyList())
        val data = JSONObject(document.json)
        val entries = data.getJSONArray("snippets")
        return Snapshot(document.revision, SnippetRetention.valueOf(data.getString("retention")),
            List(entries.length()) { index ->
                val entry = entries.getJSONObject(index)
                NovaSnippet(entry.getString("id"), entry.getString("title"), entry.getString("text"),
                    entry.getString("category"), entry.getBoolean("pinned"), entry.getBoolean("sensitive"),
                    Instant.ofEpochMilli(entry.getLong("updated")))
            })
    }

    fun save(previous: Snapshot, retention: SnippetRetention, snippets: List<NovaSnippet>, at: Instant): Snapshot? {
        val retained = snippets.filter { retention.retains(it, at) }
        val entries = JSONArray()
        for (snippet in retained) {
            entries.put(JSONObject()
                .put("id", snippet.id)
                .put("title", snippet.title)
                .put("text", snippet.text)
                .put("category", snippet.category)
                .put("pinned", snippet.pinned)
                .put("sensitive", snippet.sensitive)
                .put("updated", snippet.updated.toEpochMilli()))
        }
        val saved = documents.save("library", previous.revision,
            JSONObject().put("retention", retention.name).put("snippets", entries).toString()) ?: return null
        return Snapshot(saved.revision, retention, retained)
    }
}
