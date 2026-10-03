package uk.co.traynor.privategallery.core.editor

import java.io.ByteArrayInputStream
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import uk.co.traynor.privategallery.core.security.*

class Phase3AiVerificationRouteTest {
    @Test fun ambiguousAndUnregisteredRoutesDenyBeforeConnectionOrConsumer()=runBlocking {
        val authority=testPrimaryAuthority();authority.open(ByteArray(32))
        val operation=authority.operationOrNull(setOf(PrimaryScope.REMOTE_AI_EGRESS))!!
        val invalid=listOf(
            "https://api.openai.com/v1/models/../images/edits",
            "https://api.openai.com/v1/models/%2e%2e/images/edits",
            "https://api.openai.com/v1/models/gpt-image-2.5-flare%2fother",
            "https://api.openai.com/v1/models/%67pt-image-2.5-flare",
            "https://api.openai.com/v1/models/gpt-image-2.5-flare?other=1",
            "https://api.openai.com/v1/models/gpt-image-2.5-flare/",
            "https://api.openai.com/v1/models/unregistered-model",
            "https://api.replicate.com/v1/account?other=1",
            "https://api.replicate.com/v1/models/bytedance/seedream-4.5?other=1")
        var factories=0;var consumers=0
        try {
            for(url in invalid) {
                try{withContext(PrimaryIoContext(ScopedIoGuard(operation,PrimaryScope.REMOTE_AI_EGRESS))){
                    PrivateAiHttpTransport {uri->factories++;FakeConnection(uri.toURL())}
                        .consumeVerification(AiHttpRequest("GET",url,emptyMap(),maxResponseBytes=8)){consumers++}
                };fail("Unsupported route acquired Native transport")}
                catch(_:IllegalArgumentException){}
            }
            assertEquals(0,factories);assertEquals(0,consumers);assertTrue(authority.cleanupComplete)
        }finally{operation.close();authority.revoke()}
    }
    @Test fun allFourExistingAccountAndModelRoutesRemainUsable()=runBlocking {
        val authority=testPrimaryAuthority();authority.open(ByteArray(32))
        val operation=authority.operationOrNull(setOf(PrimaryScope.REMOTE_AI_EGRESS))!!
        val supported=listOf("https://api.openai.com/v1/models/gpt-image-2.5-flare",
            "https://api.openai.com/v1/models/gpt-image-2.5-sunburst",
            "https://api.replicate.com/v1/account",
            "https://api.replicate.com/v1/models/bytedance/seedream-4.5")
        val acquired=mutableListOf<String>();var consumers=0
        try {
            for(url in supported)withContext(PrimaryIoContext(ScopedIoGuard(operation,PrimaryScope.REMOTE_AI_EGRESS))){
                PrivateAiHttpTransport {uri->acquired+=uri.toString();FakeConnection(uri.toURL())}
                    .consumeVerification(AiHttpRequest("GET",url,emptyMap(),maxResponseBytes=8)){assertArrayEquals(byteArrayOf(1),it.bytes);consumers++}
            }
            assertEquals(supported,acquired);assertEquals(4,consumers);assertTrue(authority.cleanupComplete)
        }finally{operation.close();authority.revoke()}
    }
    private class FakeConnection(url:URL):HttpsURLConnection(url) {
        override fun getInputStream()=ByteArrayInputStream(byteArrayOf(1))
        override fun getResponseCode()=200
        override fun getContentLengthLong()=-1L
        override fun getContentType()="application/json"
        override fun disconnect()=Unit
        override fun connect()=Unit
        override fun usingProxy()=false
        override fun getCipherSuite()="public-fixture"
        override fun getLocalCertificates():Array<java.security.cert.Certificate>?=null
        override fun getServerCertificates():Array<java.security.cert.Certificate> =emptyArray()
    }
}
