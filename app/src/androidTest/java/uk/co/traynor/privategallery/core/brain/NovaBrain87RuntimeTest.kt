package uk.co.traynor.privategallery.core.brain

import androidx.test.platform.app.InstrumentationRegistry
import java.time.Instant
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import uk.co.traynor.privategallery.core.editor.AiCredentialStore

class NovaBrain87RuntimeTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val config = NovaBrainConfiguration("https://synthetic.example", "synthetic-owner-token-".repeat(3))
    private val connected = BrainConnectionStatus(BrainConnectionPhase.CONNECTED, staging=true)

    @Test fun originalBuild86ConfigurationLoadsWithoutInventedAuthenticationAndVerifiedHistoryIsEncrypted() {
        assertTrue(context.packageName.endsWith(".hiddenprototype"))
        val store = NovaBrainConnectionStore(context)
        val before = store.read()
        val bytes = JSONObject().put("origin", config.origin).put("token", config.token).toString().toByteArray()
        try {
            AiCredentialStore(context).save("nova-brain-connection", bytes)
            assertEquals(config, store.read())
            assertNull(store.readRecord()?.lastAuthenticated)
            val at = Instant.parse("2026-11-18T10:00:00Z")
            val receipt = store.rememberAuthenticated(config, connected, at)
            assertEquals(BrainAuthenticatedCheck(at,true,false), receipt)
            assertEquals(receipt, NovaBrainConnectionStore(context).readRecord()?.lastAuthenticated)
            val raw = java.io.File(context.noBackupFilesDir,"ai-provider-nova-brain-connection.enc").readBytes().decodeToString()
            for (secret in listOf(config.origin,config.token,at.toString(),"lastAuthenticated")) assertFalse(raw.contains(secret))
        } finally { bytes.fill(0); if (before == null) store.clear() else store.save(before) }
    }

    @Test fun rejectedOrStaleAuthenticationCannotRecordSuccessOrResurrectClearedCredentials() {
        assertTrue(context.packageName.endsWith(".hiddenprototype"))
        val store = NovaBrainConnectionStore(context)
        val before = store.read()
        try {
            store.save(config)
            assertThrows(IllegalArgumentException::class.java) { store.rememberAuthenticated(config,BrainConnectionStatus(BrainConnectionPhase.AUTHENTICATION_REQUIRED)) }
            assertNull(store.readRecord()?.lastAuthenticated)
            store.rememberAuthenticated(config,connected)
            val replacement = config.copy(token="different-synthetic-owner-token-".repeat(2))
            store.save(replacement)
            assertNull(store.readRecord()?.lastAuthenticated)
            assertNull(store.rememberAuthenticated(config,connected))
            assertEquals(replacement,store.read())
            store.clear()
            assertNull(store.rememberAuthenticated(config,connected))
            assertNull(store.readRecord())
        } finally { if (before == null) store.clear() else store.save(before) }
    }
}
