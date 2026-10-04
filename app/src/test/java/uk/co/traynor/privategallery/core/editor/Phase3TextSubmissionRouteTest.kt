package uk.co.traynor.privategallery.core.editor

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** Actual generation caller, fixed model entrypoints, entirely fake downstream. */
class Phase3TextSubmissionRouteTest {
    private val models=listOf(GenerationModel.SEEDREAM,GenerationModel.WHISKII)
    private fun request(model:GenerationModel)=GenerationRequest(model,"public blue bird",GenerationAspect.SQUARE)
    private abstract class Typed(private val expected:GenerationModel):AiHttpTransport {
        var typed=0;var legacy=0
        override suspend fun execute(request:AiHttpRequest):AiHttpResponse {legacy++;return AiHttpResponse(201,"application/json","""{"id":"Public42","status":"failed"}""".toByteArray())}
        override suspend fun consumeFluxProSubmission(generation:GenerationRequest,request:AiHttpRequest,consume:(AiHttpResponse)->Unit)=error("Cross-entry FLUX")
        override suspend fun consumeSeedreamTextSubmission(generation:GenerationRequest,request:AiHttpRequest,consume:(AiHttpResponse)->Unit) {
            assertEquals(GenerationModel.SEEDREAM,expected);submit(generation,request,consume)
        }
        override suspend fun consumeWhiskiiTextSubmission(generation:GenerationRequest,request:AiHttpRequest,consume:(AiHttpResponse)->Unit) {
            assertEquals(GenerationModel.WHISKII,expected);submit(generation,request,consume)
        }
        private suspend fun submit(generation:GenerationRequest,request:AiHttpRequest,consume:(AiHttpResponse)->Unit) {
            typed++;assertEquals(expected,generation.model);assertEquals("POST",request.method);assertEquals(expected.endpoint,request.url)
            assertNull(request.body);assertEquals("application/json",request.headers["Content-Type"]);assertEquals(2*1024*1024,request.maxResponseBytes)
            response(consume)
        }
        abstract suspend fun response(consume:(AiHttpResponse)->Unit)
    }
    @Test fun eachActualCallerUsesOnlyItsTypedEntryOnceAndWipesLocalResponse()=runBlocking {
        for(model in models) {
            val bytes="""{"id":"Public42","status":"failed"}""".toByteArray()
            val t=object:Typed(model){override suspend fun response(consume:(AiHttpResponse)->Unit){try{consume(AiHttpResponse(201,"application/json",bytes))}finally{bytes.fill(0)}}}
            val failure=runCatching{ReplicateImageGenerationApi(t,0).generate("public-token".toByteArray(),request(model))}.exceptionOrNull()
            assertEquals(GenerationFailureCategory.PREDICTION_FAILED,(failure as GenerationFailure).category)
            assertEquals(1,t.typed);assertEquals(0,t.legacy);assertTrue(bytes.all{it==0.toByte()})
        }
    }
    @Test fun ambiguousPaidSubmissionDoesNotRetryPollDownloadOrCancel()=runBlocking {
        for(model in models) {
            val t=object:Typed(model){override suspend fun response(consume:(AiHttpResponse)->Unit){throw AiNetworkFailure(true)}}
            val failure=runCatching{ReplicateImageGenerationApi(t,0).generate("public-token".toByteArray(),request(model))}.exceptionOrNull()
            assertEquals(GenerationFailureCategory.TIMEOUT,(failure as GenerationFailure).category)
            assertTrue(failure.message!!.contains("Check your Replicate predictions"));assertEquals(1,t.typed);assertEquals(0,t.legacy)
        }
    }
    @Test fun invalidTypedIdNeverOpensAnotherRequest()=runBlocking {
        for(model in models) {
            val bytes="""{"id":"../bad","status":"starting"}""".toByteArray()
            val t=object:Typed(model){override suspend fun response(consume:(AiHttpResponse)->Unit){try{consume(AiHttpResponse(201,"application/json",bytes))}finally{bytes.fill(0)}}}
            val failure=runCatching{ReplicateImageGenerationApi(t,0).generate("public-token".toByteArray(),request(model))}.exceptionOrNull()
            assertEquals(GenerationFailureCategory.OUTPUT_INVALID,(failure as GenerationFailure).category)
            assertEquals(1,t.typed);assertEquals(0,t.legacy);assertTrue(bytes.all{it==0.toByte()})
        }
    }
    @Test fun genuineTypedErrorIdentitySurvivesWithoutFallback()=runBlocking {
        for(model in models) {
            val expected=object:AssertionError("public encoder fault"){}
            val t=object:Typed(model){override suspend fun response(consume:(AiHttpResponse)->Unit){throw expected}}
            assertSame(expected,runCatching{ReplicateImageGenerationApi(t,0).generate("public-token".toByteArray(),request(model))}.exceptionOrNull())
            assertEquals(1,t.typed);assertEquals(0,t.legacy)
        }
    }
}
