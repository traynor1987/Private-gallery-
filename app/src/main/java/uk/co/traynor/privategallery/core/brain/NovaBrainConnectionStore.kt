package uk.co.traynor.privategallery.core.brain

import android.content.Context
import org.json.JSONObject
import uk.co.traynor.privategallery.core.editor.AiCredentialStore

/** Existing AES256-GCM Android Keystore credential mechanism; excluded from backup. */
internal class NovaBrainConnectionStore(context: Context) {
    private val credentials = AiCredentialStore(context.applicationContext)
    fun read(): NovaBrainConfiguration? = readRecord()?.configuration
    fun readRecord(): BrainConnectionRecord? = synchronized(LOCK) {
        val bytes = credentials.read(ID) ?: return@synchronized null
        try {
            val json = JSONObject(bytes.decodeToString())
            val configuration = NovaBrainConfiguration(json.getString("origin"), json.getString("token"))
            // Build86 has only origin/token. Invalid optional history cannot invalidate its credential.
            val last = json.optJSONObject("lastAuthenticated")?.let { saved -> runCatching {
                val at = java.time.Instant.parse(saved.getString("at"))
                require(at.toEpochMilli() > 0)
                val staging = saved.getBoolean("staging")
                BrainAuthenticatedCheck(at, staging, saved.getBoolean("analysisEnabled") && !staging)
            }.getOrNull() }
            BrainConnectionRecord(configuration, last)
        } finally { bytes.fill(0) }
    }
    fun save(configuration: NovaBrainConfiguration) = synchronized(LOCK) { write(configuration, null) }
    fun rememberAuthenticated(configuration: NovaBrainConfiguration, status: BrainConnectionStatus,
        at: java.time.Instant = java.time.Instant.now()): BrainAuthenticatedCheck? = synchronized(LOCK) {
        require(status.phase == BrainConnectionPhase.CONNECTED && at.toEpochMilli() > 0)
        // A late response for a changed/removed token must not resurrect a connection or its history.
        if (readRecord()?.configuration != configuration) return@synchronized null
        val last = BrainAuthenticatedCheck(at, status.staging, status.analysisEnabledOnServer && !status.staging)
        write(configuration, last)
        last
    }
    private fun write(configuration: NovaBrainConfiguration, last: BrainAuthenticatedCheck?) {
        val json = JSONObject().put("origin", configuration.origin).put("token", configuration.token)
        if (last != null) json.put("lastAuthenticated", JSONObject().put("at", last.at.toString())
            .put("staging", last.staging).put("analysisEnabled", last.analysisEnabled))
        val bytes = json.toString().toByteArray()
        try { credentials.save(ID, bytes) } finally { bytes.fill(0) }
    }
    fun clear() = synchronized(LOCK) { credentials.clear(ID) }
    private companion object { const val ID = "nova-brain-connection"; val LOCK = Any() }
}
