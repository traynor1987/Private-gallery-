package uk.co.traynor.privategallery.core.editor

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

/** Actual generation entrypoint; fake provider cannot initiate paid requests. */
@RunWith(AndroidJUnit4::class)
class Phase3FluxSubmissionRouteAndroidTest {
    private fun request(model:GenerationModel=GenerationModel.FLUX_PRO)=GenerationRequest(model,"public blue bird",GenerationAspect.SQUARE)
    @Test fun actualFluxCallerUsesTypedNoBodyAdmissionAndLocalResponseConsumption()=runBlocking {
        var typed=0;var legacy=0;var response:ByteArray?=null
        val transport=object:AiHttpTransport {
            override suspend fun execute(request:AiHttpRequest):AiHttpResponse {legacy++;return AiHttpResponse(201,"application/json","""{"id":"Public42","status":"failed"}""".toByteArray())}
            override suspend fun consumeFluxProSubmission(generation:GenerationRequest,request:AiHttpRequest,consume:(AiHttpResponse)->Unit) {
                typed++;assertEquals(GenerationModel.FLUX_PRO,generation.model);assertEquals("POST",request.method)
                assertEquals(GenerationModel.FLUX_PRO.endpoint,request.url);assertNull(request.body)
                assertEquals("application/json",request.headers["Content-Type"]);assertEquals(2*1024*1024,request.maxResponseBytes)
                val bytes="""{"id":"Public42","status":"failed"}""".toByteArray();response=bytes
                try{consume(AiHttpResponse(201,"application/json",bytes))}finally{bytes.fill(0)}
            }
        }
        val failure=runCatching{ReplicateImageGenerationApi(transport,0).generate("public-token".toByteArray(),request())}.exceptionOrNull()
        assertTrue(failure is GenerationFailure);assertEquals(GenerationFailureCategory.PREDICTION_FAILED,(failure as GenerationFailure).category)
        assertEquals(1,typed);assertEquals(0,legacy);assertTrue(checkNotNull(response).all{it==0.toByte()})
    }
    @Test fun otherFourModelSubmissionsKeepLegacyRoute()=runBlocking {
        for(model in GenerationModel.entries.filter{it!=GenerationModel.FLUX_PRO}) {
            var typed=0;var legacy=0
            val transport=object:AiHttpTransport {
                override suspend fun execute(request:AiHttpRequest):AiHttpResponse {legacy++;assertNotNull(request.body);return AiHttpResponse(201,"application/json","""{"id":"Public42","status":"failed"}""".toByteArray())}
                override suspend fun consumeFluxProSubmission(generation:GenerationRequest,request:AiHttpRequest,consume:(AiHttpResponse)->Unit){typed++;error("Wrong typed model")}
            }
            assertTrue(runCatching{ReplicateImageGenerationApi(transport,0).generate("public-token".toByteArray(),request(model))}.exceptionOrNull() is GenerationFailure)
            assertEquals(0,typed);assertEquals(1,legacy)
        }
    }
    @Test fun ambiguousTypedSubmissionNeverRetriesPollsDownloadsOrCancels()=runBlocking {
        var typed=0;var legacy=0
        val transport=object:AiHttpTransport {
            override suspend fun execute(request:AiHttpRequest):AiHttpResponse{legacy++;throw AssertionError("No fallback")}
            override suspend fun consumeFluxProSubmission(generation:GenerationRequest,request:AiHttpRequest,consume:(AiHttpResponse)->Unit){typed++;throw AiNetworkFailure(true)}
        }
        val failure=runCatching{ReplicateImageGenerationApi(transport,0).generate("public-token".toByteArray(),request())}.exceptionOrNull()
        assertTrue(failure is GenerationFailure);assertEquals(GenerationFailureCategory.TIMEOUT,(failure as GenerationFailure).category)
        assertTrue(failure.message!!.contains("Check your Replicate predictions"));assertEquals(1,typed);assertEquals(0,legacy)
    }
    @Test fun typedConsumerRejectsInvalidIdAndNeverPollsOrDownloads()=runBlocking {
        var typed=0;var legacy=0;val bytes="""{"id":"../bad","status":"starting"}""".toByteArray()
        val transport=object:AiHttpTransport {
            override suspend fun execute(request:AiHttpRequest):AiHttpResponse{legacy++;throw AssertionError("No fallback")}
            override suspend fun consumeFluxProSubmission(generation:GenerationRequest,request:AiHttpRequest,consume:(AiHttpResponse)->Unit){typed++;try{consume(AiHttpResponse(201,"application/json",bytes))}finally{bytes.fill(0)}}
        }
        val failure=runCatching{ReplicateImageGenerationApi(transport,0).generate("public-token".toByteArray(),request())}.exceptionOrNull()
        assertTrue(failure is GenerationFailure);assertEquals(GenerationFailureCategory.OUTPUT_INVALID,(failure as GenerationFailure).category)
        assertEquals(1,typed);assertEquals(0,legacy);assertTrue(bytes.all{it==0.toByte()})
    }
    @Test fun genuineTypedErrorPreservesIdentityWithoutLegacyFallback()=runBlocking {
        val expected=object:AssertionError("public encoder failure"){};var typed=0;var legacy=0
        val transport=object:AiHttpTransport {
            override suspend fun execute(request:AiHttpRequest):AiHttpResponse{legacy++;throw AssertionError("No fallback")}
            override suspend fun consumeFluxProSubmission(generation:GenerationRequest,request:AiHttpRequest,consume:(AiHttpResponse)->Unit){typed++;throw expected}
        }
        val actual=runCatching{ReplicateImageGenerationApi(transport,0).generate("public-token".toByteArray(),request())}.exceptionOrNull()
        assertSame(expected,actual);assertEquals(1,typed);assertEquals(0,legacy)
    }
}
