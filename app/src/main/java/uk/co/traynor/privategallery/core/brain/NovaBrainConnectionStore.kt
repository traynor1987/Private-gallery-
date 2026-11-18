package uk.co.traynor.privategallery.core.brain

import android.content.Context
import org.json.JSONObject
import uk.co.traynor.privategallery.core.editor.AiCredentialStore

/** Existing AES256-GCM Android Keystore credential mechanism; excluded from backup. */
internal class NovaBrainConnectionStore(context: Context) {
    private val credentials = AiCredentialStore(context.applicationContext)
    fun read(): NovaBrainConfiguration? {
        val bytes = credentials.read(ID) ?: return null
        try {
            val json = JSONObject(bytes.decodeToString())
            return NovaBrainConfiguration(json.getString("origin"), json.getString("token"))
        } finally { bytes.fill(0) }
    }
    fun save(configuration: NovaBrainConfiguration) {
        val bytes = JSONObject().put("origin", configuration.origin).put("token", configuration.token).toString().toByteArray()
        try { credentials.save(ID, bytes) } finally { bytes.fill(0) }
    }
    fun clear() = credentials.clear(ID)
    private companion object { const val ID = "nova-brain-connection" }
}
