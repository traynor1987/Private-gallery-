package uk.co.traynor.privategallery.core.brain

import org.junit.Assert.*
import org.junit.Test

class NovaBrainConnectionTest {
    private val config = NovaBrainConfiguration("https://brain.example", "synthetic-owner-token-".repeat(3))
    private val health = BrainResponse(200,"""{"status":"ok"}""".toByteArray())
    private fun status(model: String = "staging", enabled: Boolean = false) = BrainResponse(200,
        """{"status":"authenticated","model":"$model","analysis_enabled":$enabled,"budget":null}""".toByteArray())
    @Test fun authenticatedStagingUsesOnlyGetHealthAndStatusWithoutSensitivePayloads() {
        val paths = mutableListOf<String>()
        val service = NovaBrainConnection { uri, token ->
            paths.add(uri.path)
            if(uri.path=="/health") { assertNull(token);health }
            else { assertEquals(config.token,token);status() }
        }
        val result = service.check(config)
        assertEquals(listOf("/health","/guardian/status"),paths)
        assertEquals(BrainConnectionPhase.CONNECTED,result.phase)
        assertTrue(result.staging)
        assertFalse(result.analysisEnabledOnServer)
        assertFalse(config.toString().contains(config.token))
    }
    @Test fun authenticationRejectionAndRedirectsAreNotRetried() {
        for (code in listOf(401,403,302,429)) {
            var count=0
            val result=NovaBrainConnection { uri,_ -> count++;if(uri.path=="/health")health else BrainResponse(code,byteArrayOf()) }.check(config)
            assertEquals(2,count)
            assertEquals(if(code in listOf(401,403))BrainConnectionPhase.AUTHENTICATION_REQUIRED else BrainConnectionPhase.ERROR,result.phase)
        }
    }
    @Test fun outagesHaveBoundedReadOnlyRetryAndTlsFailureNeverRetriesOrBypassesTrust() {
        var calls=0
        val offline=NovaBrainConnection { _,_ -> calls++;throw java.io.IOException("do not leak synthetic detail") }.check(config)
        assertEquals(2,calls);assertEquals(BrainConnectionPhase.OFFLINE,offline.phase)
        assertFalse(offline.message.contains("synthetic detail"))
        calls=0
        val tls=NovaBrainConnection { _,_ -> calls++;throw javax.net.ssl.SSLHandshakeException("secret diagnostic") }.check(config)
        assertEquals(1,calls);assertEquals(BrainConnectionPhase.ERROR,tls.phase)
        assertFalse(tls.message.contains("secret diagnostic"))
    }
    @Test fun malformedAndCancelledChecksFailClosedWithoutAnalysisOrUploads() {
        val invalid=NovaBrainConnection { uri,_ -> if(uri.path=="/health")health else BrainResponse(200,"{}".toByteArray()) }.check(config)
        assertEquals(BrainConnectionPhase.ERROR,invalid.phase)
        val cancelled=NovaBrainConnection { _,_ -> error("No request allowed") }.check(config) { true }
        assertEquals(BrainConnectionPhase.DISCONNECTED,cancelled.phase)
        assertFalse(cancelled.analysisEnabledOnServer)
    }
    @Test fun rejectsInsecureAmbiguousEndpointsAndApiKeys() {
        for (origin in listOf("http://brain.example","https://owner@brain.example","https://brain.example/path","https://brain.example?token=a","https://brain.example#x"))
            assertThrows(IllegalArgumentException::class.java) { NovaBrainConfiguration(origin,config.token) }
        assertThrows(IllegalArgumentException::class.java) { NovaBrainConfiguration(config.origin,"sk-"+"a".repeat(40)) }
        assertThrows(IllegalArgumentException::class.java) { config.endpoint("/guardian/analyze") }
        assertThrows(IllegalArgumentException::class.java) { NovaBrainConfiguration(config.origin,config.token+"\r\n") }
    }
}
