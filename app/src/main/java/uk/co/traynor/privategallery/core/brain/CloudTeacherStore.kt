package uk.co.traynor.privategallery.core.brain

import android.content.Context
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray
import org.json.JSONObject
import uk.co.traynor.privategallery.core.editor.AiCredentialStore

internal enum class CloudExampleSource { GUARDIAN, EVIDENCE }
internal data class CloudHistoryItem(val id: String, val source: CloudExampleSource,
    val startedAt: Instant, val completedAt: Instant?, val deleting: Boolean)
internal data class CloudRequestPlan(val id: String, val cached: CloudLesson?) {
    override fun toString() = "CloudRequestPlan(redacted)"
}

/** Separate advisory records, not a competing learning engine. Existing key/AAD/credentials stay intact. */
internal class CloudTeacherStore(context: Context, private val clock: () -> Instant = Instant::now) {
    private val context = context.applicationContext
    private val credentials = AiCredentialStore(this.context)
    private fun read(id: String): JSONObject? = credentials.read(id)?.let { bytes ->
        try { JSONObject(bytes.decodeToString()) } finally { bytes.fill(0) }
    }
    private fun write(id: String, value: JSONObject) {
        val bytes = value.toString().toByteArray(Charsets.UTF_8)
        try { credentials.save(id, bytes) } finally { bytes.fill(0) }
    }
    fun enabled(): Boolean = synchronized(LOCK) {
        val state = read(SETTINGS) ?: return@synchronized false
        state.optBoolean("enabled", false) && state.optInt("consentVersion", 0) == CONSENT_VERSION
    }
    fun setEnabled(enabled: Boolean) = synchronized(LOCK) {
        write(SETTINGS, JSONObject().put("enabled", enabled).put("consentVersion", CONSENT_VERSION))
        changes.value++
    }
    private fun index(): JSONObject = read(INDEX) ?: JSONObject().put("clearing", false).put("entries", JSONArray())
    private fun entries(index: JSONObject): List<JSONObject> {
        val rows = index.getJSONArray("entries")
        require(rows.length() <= MAX_HISTORY)
        return List(rows.length()) { rows.getJSONObject(it).also { row -> UUID.fromString(row.getString("id")) } }
    }
    private fun resultId(id: String) = "nova-cloud-lesson-${UUID.fromString(id)}"
    private fun fingerprint(configuration: NovaBrainConfiguration, text: String): String {
        val bytes = (configuration.origin + "\u0000" + configuration.token + "\u0000" + text).toByteArray(Charsets.UTF_8)
        return try { java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)) }
        finally { bytes.fill(0) }
    }
    fun prepare(configuration: NovaBrainConfiguration, text: String, source: CloudExampleSource,
        forceNew: Boolean = false): CloudRequestPlan = synchronized(LOCK) {
        check(enabled()) { "Cloud Teacher is disabled" }
        check(NovaBrainConnectionStore(context).read() == configuration) { "Connection configuration changed" }
        require(text.length in 1..4096 && text.isNotBlank())
        val index = index()
        check(!index.optBoolean("clearing", false)) { "History deletion must finish first" }
        val rows = entries(index)
        val digest = fingerprint(configuration, text)
        if (!forceNew) rows.firstOrNull { it.optString("fingerprint") == digest && it.optString("state") != "deleting" }?.let {
            val id = it.getString("id")
            return@synchronized CloudRequestPlan(id, readResult(id))
        }
        check(rows.size < MAX_HISTORY) { "Review or delete older cloud history before another request" }
        val id = UUID.randomUUID().toString()
        val entry = JSONObject().put("id", id).put("fingerprint", digest).put("source", source.name)
            .put("startedAt", clock().toString()).put("state", "pending")
        write(INDEX, index.put("entries", JSONArray(listOf(entry) + rows)))
        changes.value++
        CloudRequestPlan(id, null)
    }
    fun recordSuccess(id: String, configuration: NovaBrainConfiguration, lesson: CloudLesson): Boolean = synchronized(LOCK) {
        if (!enabled() || NovaBrainConnectionStore(context).read() != configuration) return@synchronized false
        val index = index()
        if (index.optBoolean("clearing", false)) return@synchronized false
        val rows = entries(index)
        val entry = rows.firstOrNull { it.getString("id") == id && it.optString("state") != "deleting" }
            ?: return@synchronized false
        val at = clock().toString()
        // Advice can echo private Guardian/Evidence context. Retain only bounded
        // classification metadata; never duplicate that text outside its source.
        val retained = lesson.copy(summary = "Model summary not retained for privacy.")
        write(resultId(id), JSONObject().put("receivedAt", at).put("lesson", retained.json()))
        entry.put("state", "completed").put("completedAt", at)
        write(INDEX, index.put("entries", JSONArray(rows)))
        changes.value++
        true
    }
    fun history(): List<CloudHistoryItem> = synchronized(LOCK) {
        val index = index()
        if (index.optBoolean("clearing", false)) return@synchronized emptyList()
        entries(index).map { row -> CloudHistoryItem(row.getString("id"), CloudExampleSource.valueOf(row.getString("source")),
            Instant.parse(row.getString("startedAt")), row.optString("completedAt").takeIf(String::isNotBlank)?.let(Instant::parse),
            row.optString("state") == "deleting") }
    }
    fun readResult(id: String): CloudLesson? = synchronized(LOCK) {
        val index = index()
        if (index.optBoolean("clearing", false) || entries(index).none { it.getString("id") == id && it.optString("state") != "deleting" }) return@synchronized null
        read(resultId(id))?.getJSONObject("lesson")?.let { CloudLesson.fromStored(it) }
    }
    fun delete(id: String) = synchronized(LOCK) {
        val index = index()
        val rows = entries(index)
        rows.firstOrNull { it.getString("id") == id }?.put("state", "deleting") ?: return@synchronized
        write(INDEX, index.put("entries", JSONArray(rows))) // Durable deletion intent blocks late replies.
        credentials.clear(resultId(id))
        write(INDEX, index.put("entries", JSONArray(rows.filterNot { it.getString("id") == id })))
        changes.value++
    }
    fun clearHistory() = synchronized(LOCK) {
        // A corrupt index must not prevent deletion of this feature's own ciphertext.
        write(INDEX, JSONObject().put("clearing", true).put("entries", JSONArray()))
        val pattern = Regex("ai-provider-nova-cloud-lesson-([a-f0-9-]{36})\\.enc(?:\\.bak|\\.new)?")
        val ids = context.noBackupFilesDir.listFiles().orEmpty().mapNotNull { pattern.matchEntire(it.name)?.groupValues?.get(1) }
        ids.distinct().forEach { credentials.clear(resultId(it)) }
        credentials.clear(INDEX)
        changes.value++
    }
    companion object {
        const val CONSENT_VERSION = 1
        const val MAX_HISTORY = 20
        private const val SETTINGS = "nova-cloud-teacher-permission"
        private const val INDEX = "nova-cloud-teacher-history"
        private val LOCK = Any()
        val changes = MutableStateFlow(0L)
    }
}
