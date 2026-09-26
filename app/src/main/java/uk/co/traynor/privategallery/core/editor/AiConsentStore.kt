package uk.co.traynor.privategallery.core.editor
import android.content.Context
/** Stores owner AI preferences and provider-scoped disclosure consent. No image, prompt or credential. */
class AiConsentStore(context: Context) {
    private val preferences = context.getSharedPreferences("ai_editing_privacy", Context.MODE_PRIVATE)
    fun hasConsent(providerId: String) = preferences.getString("consented_provider_v1", null) == providerId
    fun remember(providerId: String) { preferences.edit().putString("consented_provider_v1", providerId).apply() }
    fun clear() { preferences.edit().remove("consented_provider_v1").apply() }
    fun clearFor(providerId: String) { if (hasConsent(providerId)) clear() }
    fun keepEditsInVault() = preferences.getBoolean("keep_ai_edits_in_vault", true)
    fun setKeepEditsInVault(value: Boolean) { preferences.edit().putBoolean("keep_ai_edits_in_vault", value).apply() }
    fun relaxSeedreamModeration() = preferences.getBoolean("relax_seedream_moderation", false)
    fun setRelaxSeedreamModeration(value: Boolean) { preferences.edit().putBoolean("relax_seedream_moderation", value).apply() }
}
