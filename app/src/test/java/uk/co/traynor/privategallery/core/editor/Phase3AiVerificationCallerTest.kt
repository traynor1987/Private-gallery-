package uk.co.traynor.privategallery.core.editor

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** The real API validators must consume locally; a raw execute result is rejected. */
class Phase3AiVerificationCallerTest {
    @Test fun openAiModelVerificationUsesLocalConsumption()=runBlocking {
        val seen=mutableListOf<AiHttpRequest>()
        val transport=transport(seen) {request->
            check(request.url=="https://api.openai.com/v1/models/gpt-image-2.5-flare")
            """{"id":"gpt-image-2.5-flare","object":"model"}"""
        }
        OpenAiImageApi(transport).testConnection("public-fixture-token".toByteArray(),OpenAiImageModel.FLARE)
        assertEquals(1,seen.size);assertNull(seen.single().body);assertEquals("GET",seen.single().method)
    }
    @Test fun replicateAccountAndModelAreConsumedSeparately()=runBlocking {
        val seen=mutableListOf<AiHttpRequest>()
        val transport=transport(seen) {request->when(request.url){
            "https://api.replicate.com/v1/account"->"""{"type":"user","username":"public-fixture"}"""
            "https://api.replicate.com/v1/models/bytedance/seedream-4.5"->"""{"owner":"bytedance","name":"seedream-4.5"}"""
            else->error("unexpected endpoint")
        }}
        ReplicateSeedreamApi(transport).testConnection("public-fixture-token".toByteArray())
        assertEquals(2,seen.size);assertTrue(seen.all{it.method=="GET"&&it.body==null})
    }
    private fun transport(seen:MutableList<AiHttpRequest>,json:(AiHttpRequest)->String)=object:AiHttpTransport {
        override suspend fun execute(request:AiHttpRequest):AiHttpResponse=throw AssertionError("raw result escaped local verification transport")
        override suspend fun consumeVerification(request:AiHttpRequest,consume:(AiHttpResponse)->Unit) {
            seen+=request;val bytes=json(request).toByteArray()
            try{consume(AiHttpResponse(200,"application/json",bytes))}finally{bytes.fill(0)}
        }
    }
}
