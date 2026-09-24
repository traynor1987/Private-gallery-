package uk.co.traynor.privategallery.core.editor

import android.content.Context

/** Application-scoped configuration, never stores a decrypted key in a field. */
internal fun androidAiConfiguration(context: Context): AiProviderConfiguration {
    val store = AiCredentialStore(context.applicationContext)
    val preferences = AiConsentStore(context.applicationContext)
    val api = ReplicateSeedreamApi(PrivateAiHttpTransport())
    val credentials = object : AiCredentials {
        override fun isConfigured() = store.isConfigured(ReplicateSeedreamProvider.ID)
        override fun read() = store.read(ReplicateSeedreamProvider.ID)
        override fun save(credential: ByteArray) = store.save(ReplicateSeedreamProvider.ID,credential)
        override fun clear() = store.clear(ReplicateSeedreamProvider.ID)
    }
    return AiProviderConfiguration(credentials, api::testConnection) { read -> ReplicateSeedreamProvider(read,api,ReplicateImagePreparation::prepare,preferences::relaxSeedreamModeration) }
}
