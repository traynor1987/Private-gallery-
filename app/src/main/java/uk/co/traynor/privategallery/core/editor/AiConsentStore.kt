package uk.co.traynor.privategallery.core.editor
import android.content.Context
/** Only provider-scoped disclosure consent is persisted. No image, prompt or credential. */
class AiConsentStore(context: Context) {
    private val preferences = context.getSharedPreferences("ai_editing_privacy", Context.MODE_PRIVATE)
    fun hasConsent(providerId: String) = preferences.getString("consented_provider_v1", null) == providerId
    fun remember(providerId: String) { preferences.edit().putString("consented_provider_v1", providerId).apply() }
    fun clear() { preferences.edit().clear().apply() }
}
