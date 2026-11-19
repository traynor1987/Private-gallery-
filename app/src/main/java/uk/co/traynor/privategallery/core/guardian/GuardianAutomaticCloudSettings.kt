package uk.co.traynor.privategallery.core.guardian

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import uk.co.traynor.privategallery.core.editor.AiCredentialStore
import java.util.UUID

internal data class GuardianCloudConsent(val origin: String, val revision: String, val scopes: Set<GuardianConversationKey>)

/** Consent and contact scopes stay in private, Keystore-encrypted storage. */
internal class GuardianAutomaticCloudSettings(context: Context) {
    private val credentials = AiCredentialStore(context.applicationContext)

    fun read(): GuardianCloudConsent? = synchronized(lock) {
        val bytes = credentials.read(ID) ?: return@synchronized null
        try {
            val json = JSONObject(bytes.decodeToString())
            if (json.getInt("policy") != POLICY) return@synchronized null
            val array = json.getJSONArray("scopes")
            require(array.length() <= MAX_SCOPES)
            val scopes = List(array.length()) { index ->
                val item = array.getJSONObject(index)
                GuardianConversationKey(item.getString("app"), item.getLong("profile"), item.getString("conversation"))
            }.toSet()
            GuardianCloudConsent(json.getString("origin"), UUID.fromString(json.getString("revision")).toString(), scopes)
        } finally { bytes.fill(0) }
    }

    fun enable(origin: String) = synchronized(lock) {
        save(GuardianCloudConsent(origin, UUID.randomUUID().toString(), emptySet()))
    }

    fun setScope(origin: String, key: GuardianConversationKey, allowed: Boolean): Boolean = synchronized(lock) {
        val current = read()?.takeIf { it.origin == origin } ?: return@synchronized false
        if (key.app != GUARDIAN_WHATSAPP || !key.conversation.startsWith("person-key:") && !key.conversation.startsWith("person-uri:") &&
            !key.conversation.startsWith("shortcut:")) return@synchronized false
        val scopes = if (allowed) current.scopes + key else current.scopes - key
        if (scopes.size > MAX_SCOPES) return@synchronized false
        if (scopes != current.scopes) save(current.copy(revision = UUID.randomUUID().toString(), scopes = scopes))
        true
    }

    fun disable() = synchronized(lock) { credentials.clear(ID) }

    private fun save(consent: GuardianCloudConsent) {
        val scopes = consent.scopes.map { key -> JSONObject().put("app", key.app).put("profile", key.profile)
            .put("conversation", key.conversation) }
        val bytes = JSONObject().put("policy", POLICY).put("origin", consent.origin).put("revision", consent.revision)
            .put("scopes", JSONArray(scopes)).toString().toByteArray()
        try { credentials.save(ID, bytes) } finally { bytes.fill(0) }
    }

    private companion object {
        const val ID = "guardian-automatic-cloud"
        const val POLICY = 1
        const val MAX_SCOPES = 64
        val lock = Any()
    }
}
