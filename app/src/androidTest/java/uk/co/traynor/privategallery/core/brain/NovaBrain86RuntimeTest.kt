package uk.co.traynor.privategallery.core.brain

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

class NovaBrain86RuntimeTest {
    @Test fun sharedConfigurationIsKeystoreEncryptedSurvivesRecreationAndCanBeRemoved() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val store=NovaBrainConnectionStore(context)
        val before=store.read()
        val synthetic=NovaBrainConfiguration("https://synthetic.example","synthetic-isolated-owner-token-".repeat(2))
        try {
            store.save(synthetic)
            assertEquals(synthetic,NovaBrainConnectionStore(context).read())
            val file=java.io.File(context.noBackupFilesDir,"ai-provider-nova-brain-connection.enc")
            assertTrue(file.isFile)
            val stored=file.readBytes().decodeToString()
            assertFalse(stored.contains(synthetic.token))
            assertFalse(stored.contains(synthetic.origin))
            store.clear()
            assertNull(NovaBrainConnectionStore(context).read())
        } finally { if(before==null)store.clear() else store.save(before) }
    }
    @Test fun authenticatedDisabledStatusNeverActivatesAnalysisAndOfflineUsesNoOwnerContent() {
        val config=NovaBrainConfiguration("https://synthetic.example","synthetic-isolated-owner-token-".repeat(2))
        val status=NovaBrainConnection { uri,_ -> BrainResponse(200,if(uri.path=="/health")"""{"status":"ok"}""".toByteArray() else
            """{"status":"authenticated","model":"staging","analysis_enabled":false,"budget":null}""".toByteArray()) }.check(config)
        assertEquals(BrainConnectionPhase.CONNECTED,status.phase)
        assertFalse(status.analysisEnabledOnServer)
        val offline=NovaBrainConnection { _,_ -> throw java.net.SocketTimeoutException() }.check(config)
        assertEquals(BrainConnectionPhase.OFFLINE,offline.phase)
        assertFalse(offline.analysisEnabledOnServer)
    }
}
