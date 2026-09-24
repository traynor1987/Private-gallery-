package uk.co.traynor.privategallery.ui

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import uk.co.traynor.privategallery.core.editor.*

class AiPrivacySettingsTest {
    @Test fun consentIsProviderScopedAndClearable() {
        val store = AiConsentStore(InstrumentationRegistry.getInstrumentation().targetContext)
        try { store.clear(); assertFalse(store.hasConsent("test")); store.remember("test"); assertTrue(store.hasConsent("test")); assertFalse(store.hasConsent("other")); store.clear(); assertFalse(store.hasConsent("test")) }
        finally { store.clear() }
    }
    @Test fun seedreamModerationDefaultsOffAndPersistsOnlyExplicitOwnerChoice() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preferences = context.getSharedPreferences("ai_editing_privacy", android.content.Context.MODE_PRIVATE)
        preferences.edit().remove("relax_seedream_moderation").commit()
        val store = AiConsentStore(context)
        try {
            assertFalse(store.relaxSeedreamModeration())
            store.setRelaxSeedreamModeration(true)
            assertTrue(AiConsentStore(context).relaxSeedreamModeration())
            store.clear()
            assertTrue(store.relaxSeedreamModeration())
            store.setRelaxSeedreamModeration(false)
            assertFalse(AiConsentStore(context).relaxSeedreamModeration())
        } finally { preferences.edit().remove("relax_seedream_moderation").commit() }
    }
    @Test fun credentialsAreKeystoreEncryptedAndClearable() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = AiCredentialStore(context)
        val credential = "synthetic-test-credential".toByteArray()
        try {
            store.save("test-provider", credential)
            assertTrue(store.isConfigured("test-provider"))
            assertArrayEquals(credential, store.read("test-provider"))
            val stored = java.io.File(context.noBackupFilesDir, "ai-provider-test-provider.enc").readBytes()
            assertFalse(stored.toString(Charsets.UTF_8).contains("synthetic-test-credential"))
            store.clear("test-provider"); assertNull(store.read("test-provider"))
        } finally { credential.fill(0); store.clear("test-provider") }
    }
}
