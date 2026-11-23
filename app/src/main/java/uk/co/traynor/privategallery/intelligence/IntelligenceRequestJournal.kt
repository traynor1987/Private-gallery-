package uk.co.traynor.privategallery.intelligence

import android.content.Context
import java.net.URI
import java.security.MessageDigest
import java.util.HexFormat
import java.util.Locale
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject
import uk.co.traynor.privategallery.core.brain.NovaBrainConfiguration
import uk.co.traynor.privategallery.core.editor.AiCredentialStore

/** Retains paid request identities across process death without retaining chat text. */
internal class IntelligenceRequestJournal(private val read: () -> ByteArray?, private val write: (ByteArray) -> Unit) {
    data class Entry(val scope: String, val question: String, val payload: String, val requestId: String)
    sealed interface Decision {
        data class Ready(val entry: Entry, val newEntry: Boolean) : Decision
        data object PayloadChanged : Decision
        data object Full : Decision
    }

    fun reserve(configuration: NovaBrainConfiguration, request: IntelligenceCloudRequest): Decision = synchronized(LOCK) {
        val origin = URI(configuration.origin)
        val endpoint = "${origin.host.lowercase(Locale.ROOT)}:${origin.port.takeIf { it != -1 } ?: 443}"
        val scope = digest("$endpoint\u0000${configuration.token}")
        val question = digest(request.question)
        val payload = digest(request.payload)
        val entries = load()
        val existing = entries.firstOrNull { it.scope == scope && it.question == question }
        if (existing != null) {
            return@synchronized if (existing.payload == payload) Decision.Ready(existing, newEntry = false)
                else Decision.PayloadChanged
        }
        if (entries.size >= LIMIT) return@synchronized Decision.Full
        val entry = Entry(scope, question, payload, request.requestId)
        save(entries + entry)
        Decision.Ready(entry, newEntry = true)
    }

    fun resolve(entry: Entry) = synchronized(LOCK) {
        val entries = load()
        if (entry in entries) save(entries - entry)
    }

    private fun load(): List<Entry> {
        val bytes = read() ?: return emptyList()
        try {
            val rows = JSONArray(bytes.decodeToString())
            require(rows.length() <= LIMIT)
            return List(rows.length()) { index ->
                val row = rows.getJSONObject(index)
                fun hash(key: String) = row.getString(key).also { require(HASH.matches(it)) }
                val id = row.getString("id").also { require(UUID.fromString(it).toString() == it) }
                Entry(hash("scope"), hash("question"), hash("payload"), id)
            }.also { entries ->
                require(entries.distinctBy { it.scope to it.question }.size == entries.size)
            }
        } finally { bytes.fill(0) }
    }

    private fun save(entries: List<Entry>) {
        val rows = JSONArray(entries.map {
            JSONObject().put("scope", it.scope).put("question", it.question).put("payload", it.payload).put("id", it.requestId)
        })
        val bytes = rows.toString().encodeToByteArray()
        try { write(bytes) } finally { bytes.fill(0) }
    }

    private fun digest(value: String): String {
        val bytes = value.encodeToByteArray()
        return try { HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)) }
        finally { bytes.fill(0) }
    }

    companion object {
        private const val LIMIT = 24
        private const val ID = "nova-intelligence-pending-requests"
        private val LOCK = Any()
        private val HASH = Regex("[a-f0-9]{64}")

        fun encrypted(context: Context): IntelligenceRequestJournal {
            val credentials = AiCredentialStore(context.applicationContext)
            return IntelligenceRequestJournal({ credentials.read(ID) }) { credentials.save(ID, it) }
        }
    }
}
