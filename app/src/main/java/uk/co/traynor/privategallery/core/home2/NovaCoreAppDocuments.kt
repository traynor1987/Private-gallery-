package uk.co.traynor.privategallery.core.home2

import android.content.Context
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.Base64
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject
import uk.co.traynor.privategallery.core.backup.PortablePreferenceSchema

internal enum class NovaCoreApp(val storageId: String, val route: String, val label: String, val symbol: String) {
    NOTES("notes", "nova-notes-draft", "Nova Notes", "edit"),
    TASKS("tasks", "nova-tasks", "Nova Tasks", "apps"),
    CALENDAR("calendar", "nova-calendar", "Nova Calendar", "widgets"),
    FILES("files", "nova-files", "Nova Files", "folder"),
    CLIPBOARD("clipboard", "nova-clipboard", "Nova Clipboard", "edit");

    val namespace: String get() = "nova-core-$storageId-v1"
}

/** Stores app documents in the originating authenticated Space preferences. */
internal class NovaCoreAppDocuments(space: SpaceContext, app: NovaCoreApp) {
    private val prefs = space.getSharedPreferences(app.namespace, Context.MODE_PRIVATE)

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

    companion object {
        private val lock = Any()
        private val ID = Regex("[a-zA-Z0-9_-]{1,80}")
        internal const val MAX_DOCUMENT_CHARS = 262_144
    }
}

internal data class NovaNoteAttachment(val id: String, val name: String, val mime: String, val content: String) {
    fun readBytes(): ByteArray {
        require(id.isNotBlank() && id.length <= 80 && name.isNotBlank() && name.length <= 160)
        require(mime.length <= 100 && mime.matches(MIME))
        require(content.length <= MAX_BYTES * 4 / 3)
        return Base64.getDecoder().decode(content).also {
            require(it.size <= MAX_BYTES)
        }
    }

    fun encode(): JSONObject = JSONObject().put("id", id).put("name", name)
        .put("mime", mime).put("content", content)

    companion object {
        const val MAX_BYTES = 24 * 1024
        const val MAX_COUNT = 3
        private val MIME = Regex("[a-zA-Z0-9.+_-]+/[a-zA-Z0-9.+_-]+")

        fun decode(value: JSONObject): NovaNoteAttachment {
            PortablePreferenceSchema.keys(value, setOf("id", "name", "mime", "content"))
            return NovaNoteAttachment(value.getString("id"), value.getString("name"),
                value.getString("mime"), value.getString("content")).also { it.readBytes().fill(0) }
        }
    }
}

/** Primary-owned app records only. Android tree permissions and provider identifiers are device-local. */
internal object NovaCoreAppBackup {
    private val portable = setOf(NovaCoreApp.NOTES, NovaCoreApp.TASKS, NovaCoreApp.CALENDAR, NovaCoreApp.CLIPBOARD)
        .associateBy { it.namespace }
    val namespaces: Set<String> get() = portable.keys

    fun validate(namespace: String, values: Map<String, Any>, owner: String) {
        require(owner == "primary")
        val app = portable.getValue(namespace)
        val allowed = if (app == NovaCoreApp.NOTES) setOf("library", "editor", "draft") else setOf("library")
        require(values.keys.all { it in allowed })
        for ((key, raw) in values) {
            require(raw is String && raw.length <= NovaCoreAppDocuments.MAX_DOCUMENT_CHARS * 6 + 128)
            val wrapper = JSONObject(raw)
            keys(wrapper, "revision", "json")
            number(wrapper, "revision", 1, Long.MAX_VALUE - 1)
            val value = JSONObject(text(wrapper, "json", NovaCoreAppDocuments.MAX_DOCUMENT_CHARS))
            if (key == "draft") {
                keys(value, "title", "body")
                text(value, "title", 200)
                text(value, "body", 32_000)
            } else if (key == "editor") {
                keys(value, "baseFingerprint", "note")
                if (value.length() != 0) {
                    note(value.getJSONObject("note"))
                    optional(value, "baseFingerprint") {
                        require(text(value, "baseFingerprint", 64).matches(Regex("[0-9a-f]{64}")))
                    }
                }
            } else when (app) {
                NovaCoreApp.NOTES -> {
                    keys(value, "notes", "protectedNotebooks")
                    records(value.getJSONArray("notes"), validate = ::note)
                    optional(value, "protectedNotebooks") {
                        val names = value.getJSONArray("protectedNotebooks")
                        require(names.length() <= 512)
                        val unique = hashSetOf<String>()
                        repeat(names.length()) { index ->
                            val name = names.get(index)
                            require(name is String && name.length in 1..120 && name.isNotBlank() &&
                                name == name.trim().lowercase(Locale.ROOT) && unique.add(name))
                        }
                    }
                }
                NovaCoreApp.TASKS -> {
                    keys(value, "tasks")
                    records(value.getJSONArray("tasks"), validate = ::task)
                }
                NovaCoreApp.CALENDAR -> {
                    keys(value, "events")
                    val events = value.getJSONArray("events")
                    records(events, validate = ::event)
                    val byId = (0 until events.length()).map(events::getJSONObject).associateBy(::id)
                    val claimed = hashSetOf<Pair<String, String>>()
                    for (record in byId.values) {
                        val original = record.optJSONObject("originalOccurrence") ?: continue
                        val seriesId = original.getString("seriesId")
                        val sourceDate = original.getString("date")
                        val parent = byId[seriesId]
                        require(parent != null && !parent.has("originalOccurrence") && claimed.add(seriesId to sourceDate))
                        val skip = parent.getJSONArray("skippedDates")
                        require((0 until skip.length()).any { skip.getString(it) == sourceDate })
                    }
                }
                NovaCoreApp.CLIPBOARD -> {
                    keys(value, "retention", "snippets", "protectAllCopies")
                    require(text(value, "retention", 16) in setOf("MANUAL", "DAY", "WEEK", "MONTH"))
                    optional(value, "protectAllCopies") { flag(value, "protectAllCopies") }
                    records(value.getJSONArray("snippets"), validate = ::snippet)
                }
                NovaCoreApp.FILES -> error("Files picker grants are not portable")
            }
        }
    }

    fun portableAt(namespace: String, values: Map<String, Any>, owner: String, now: Instant): Map<String, Any> {
        validate(namespace, values, owner)
        if (namespace != NovaCoreApp.CLIPBOARD.namespace) return values
        val raw = values["library"] as? String ?: return values
        val wrapper = JSONObject(raw)
        val data = JSONObject(wrapper.getString("json"))
        val days = when (data.getString("retention")) {
            "DAY" -> 1L
            "WEEK" -> 7L
            "MONTH" -> 30L
            else -> return values
        }
        val threshold = now.minus(Duration.ofDays(days)).toEpochMilli()
        val original = data.getJSONArray("snippets")
        val retained = JSONArray()
        repeat(original.length()) { index ->
            val snippet = original.getJSONObject(index)
            if (snippet.getBoolean("pinned") || snippet.getLong("updated") >= threshold) retained.put(snippet)
        }
        if (original.length() == retained.length()) return values
        val normalized = data.put("snippets", retained).toString()
        return mapOf("library" to wrapper.put("json", normalized).toString())
    }

    private fun note(value: JSONObject) {
        keys(value, "id", "title", "body", "pinned", "notebook", "tags", "attachments")
        id(value)
        text(value, "title", 200)
        text(value, "body", 32_000)
        flag(value, "pinned")
        optional(value, "notebook") { text(value, "notebook", 120) }
        optional(value, "tags") { text(value, "tags", 200) }
        optional(value, "attachments") {
            records(value.getJSONArray("attachments"), NovaNoteAttachment.MAX_COUNT) { NovaNoteAttachment.decode(it) }
        }
    }

    private fun task(value: JSONObject) {
        keys(value, "id", "title", "description", "project", "due", "priority", "completed", "repeat", "repeatDay",
            "nextCreated", "tags", "subtasks", "noteId", "remindAt")
        id(value)
        text(value, "title", 200)
        text(value, "description", 4_096)
        text(value, "project", 120)
        val due = text(value, "due", 10)
        if (due.isNotEmpty()) date(due)
        optional(value, "remindAt") {
            require(due.isNotEmpty())
            require(runCatching { LocalTime.parse(text(value, "remindAt", 20)) }.isSuccess)
        }
        require(text(value, "priority", 12) in setOf("NONE", "LOW", "NORMAL", "HIGH"))
        flag(value, "completed")
        optional(value, "repeat") { require(text(value, "repeat", 12) in setOf("NONE", "DAILY", "WEEKLY", "MONTHLY")) }
        optional(value, "repeatDay") { number(value, "repeatDay", 1, 31) }
        optional(value, "nextCreated") { flag(value, "nextCreated") }
        optional(value, "tags") { text(value, "tags", 200) }
        optional(value, "noteId") { require(text(value, "noteId", 80).isNotBlank()) }
        optional(value, "subtasks") {
            records(value.getJSONArray("subtasks"), 100) { child ->
                keys(child, "id", "title", "completed")
                require(text(child, "title", 200).isNotBlank())
                flag(child, "completed")
            }
        }
    }

    private fun event(value: JSONObject) {
        keys(value, "id", "title", "description", "firstDate", "time", "zone", "minutes", "repeat", "skippedDates", "noteId", "reminderMinutes", "originalOccurrence")
        optional(value, "reminderMinutes") { number(value, "reminderMinutes", 0, 1440) }
        optional(value, "originalOccurrence") {
            require(value.getString("repeat") == "NONE" && !value.has("skippedDates"))
            val source = value.getJSONObject("originalOccurrence")
            keys(source, "seriesId", "date")
            val series = text(source, "seriesId", 80)
            require(series.isNotBlank() && series != value.getString("id"))
            date(text(source, "date", 10))
        }
        id(value)
        text(value, "title", 200)
        text(value, "description", 4_096)
        date(text(value, "firstDate", 10))
        val time = text(value, "time", 18)
        if (time.isNotEmpty()) require(runCatching { LocalTime.parse(time) }.isSuccess)
        require(runCatching { ZoneId.of(text(value, "zone", 120)) }.isSuccess)
        number(value, "minutes", 1, 1440)
        require(text(value, "repeat", 12) in setOf("NONE", "DAILY", "WEEKLY", "MONTHLY", "YEARLY"))
        optional(value, "noteId") { require(text(value, "noteId", 80).isNotBlank()) }
        optional(value, "skippedDates") {
            val dates = value.getJSONArray("skippedDates")
            require(dates.length() <= 4096)
            val unique = hashSetOf<String>()
            repeat(dates.length()) { index ->
                val skipped = dates.get(index)
                require(skipped is String && unique.add(skipped))
                date(skipped)
            }
        }
    }

    private fun snippet(value: JSONObject) {
        keys(value, "id", "title", "text", "category", "pinned", "sensitive", "updated")
        id(value)
        text(value, "title", 200)
        text(value, "text", 16_000)
        text(value, "category", 120)
        flag(value, "pinned")
        flag(value, "sensitive")
        number(value, "updated", Long.MIN_VALUE, Long.MAX_VALUE)
    }

    private fun records(entries: JSONArray, limit: Int = 4096, validate: (JSONObject) -> Unit) {
        require(entries.length() <= limit)
        val ids = hashSetOf<String>()
        repeat(entries.length()) { index ->
            val record = entries.getJSONObject(index)
            require(ids.add(id(record)))
            validate(record)
        }
    }

    private fun id(value: JSONObject) = text(value, "id", 80).also { require(it.isNotBlank()) }
    private fun date(value: String) {
        require(value.length == 10 && runCatching { LocalDate.parse(value).year in 0..9999 }.getOrDefault(false))
    }
    private fun keys(value: JSONObject, vararg fields: String) = PortablePreferenceSchema.keys(value, fields.toSet())
    private fun text(value: JSONObject, key: String, max: Int) = PortablePreferenceSchema.string(value, key, 0, max)
    private fun number(value: JSONObject, key: String, min: Long, max: Long) =
        PortablePreferenceSchema.integer(value, key, min, max)
    private fun flag(value: JSONObject, key: String) = require(value.get(key) is Boolean)
    private inline fun optional(value: JSONObject, key: String, validate: () -> Unit) {
        if (value.has(key)) validate()
    }
}
