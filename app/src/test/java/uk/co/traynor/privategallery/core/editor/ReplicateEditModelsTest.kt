package uk.co.traynor.privategallery.core.editor

import java.io.ByteArrayOutputStream
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ReplicateEditModelsTest {
    @Test fun operationFiltersOnlyCapableModels() {
        assertEquals(listOf(ReplicateEditModel.FILL), ReplicateModelCapabilities.editModelsFor(AiCapability.OBJECT_REMOVAL))
        assertTrue(ReplicateEditModel.FILL !in ReplicateModelCapabilities.editModelsFor(AiCapability.GENERATIVE_EDIT))
        assertEquals(5, ReplicateModelCapabilities.editModels.size)
    }
    @Test fun transientPollFailureContinuesSamePaidPrediction() = runBlocking {
        var posts = 0; var polls = 0
        val transport = AiHttpTransport { request -> when {
            request.method == "POST" -> { posts++; AiHttpResponse(201, "application/json", """{"id":"abc","status":"processing"}""".toByteArray()) }
            request.url.endsWith("/abc") -> { polls++; if (polls == 1) throw AiNetworkFailure(true)
                AiHttpResponse(200, "application/json", """{"id":"abc","status":"succeeded","output":"https://replicate.delivery/a.png"}""".toByteArray()) }
            else -> AiHttpResponse(200, "image/png", byteArrayOf(9))
        } }
        var last: ReplicatePredictionSnapshot? = null
        assertArrayEquals(byteArrayOf(9), ReplicateModelEditApi(transport, 1).edit("token".toByteArray(), ReplicateEditModel.KONTEXT, byteArrayOf(1), "edit", null,
            observe = { last = it }))
        assertEquals(1, posts); assertEquals(2, polls)
        assertEquals("abc", last?.predictionId)
        assertTrue(ReplicatePredictionState.POLL_TIMEOUT in last!!.observedStates)
        assertEquals(ReplicatePredictionState.OUTPUT_RECEIVED, last!!.state)
    }

    @Test fun submittedPredictionHasNoPrematureProviderDeadline() = runBlocking {
        val transport = AiHttpTransport { request ->
            if (request.method == "POST") {
                assertNull(request.headers["Cancel-After"])
                AiHttpResponse(201, "application/json", """{"id":"abc","status":"processing"}""".toByteArray())
            } else if (request.url.endsWith("/abc")) AiHttpResponse(200, "application/json", """{"id":"abc","status":"succeeded","output":"https://replicate.delivery/a.png"}""".toByteArray())
            else AiHttpResponse(200, "image/png", byteArrayOf(1))
        }
        assertArrayEquals(byteArrayOf(1), ReplicateModelEditApi(transport, 1).edit("token".toByteArray(),
            ReplicateEditModel.SEEDREAM_5_PRO, byteArrayOf(1), "edit", null, resolution = "2K"))
    }
    @Test fun transientStatusHttpFailureRetriesGetOnly() = runBlocking {
        var posts = 0; var polls = 0
        val transport = AiHttpTransport { request -> when {
            request.method == "POST" -> { posts++; AiHttpResponse(201, "application/json", """{"id":"abc","status":"processing"}""".toByteArray()) }
            request.url.endsWith("/abc") -> { polls++; if (polls == 1) AiHttpResponse(503, null, byteArrayOf())
                else AiHttpResponse(200, "application/json", """{"id":"abc","status":"succeeded","output":"https://replicate.delivery/a.png"}""".toByteArray()) }
            else -> AiHttpResponse(200, "image/png", byteArrayOf(1))
        } }
        ReplicateModelEditApi(transport, 1).edit("token".toByteArray(), ReplicateEditModel.KONTEXT, byteArrayOf(1), "edit", null)
        assertEquals(1, posts); assertEquals(2, polls)
    }

    @Test fun outputTimeoutDoesNotResubmitPrediction() = runBlocking {
        var posts = 0
        val transport = AiHttpTransport { request -> when {
            request.method == "POST" -> { posts++; AiHttpResponse(201, "application/json", """{"id":"abc","status":"succeeded","output":"https://replicate.delivery/a.png"}""".toByteArray()) }
            else -> throw AiNetworkFailure(true)
        } }
        try { ReplicateModelEditApi(transport).edit("token".toByteArray(), ReplicateEditModel.KONTEXT, byteArrayOf(1), "edit", null); fail() }
        catch (failure: ReplicatePredictionFailure) { assertEquals(ReplicatePredictionState.OUTPUT_DOWNLOAD_TIMEOUT, failure.state) }
        assertEquals(1, posts)
    }
    @Test fun seedreamFiveAdaptersSendSourceAndRespectDistinctSchemas() {
        for (model in listOf(ReplicateEditModel.SEEDREAM_5_PRO, ReplicateEditModel.SEEDREAM_5_LITE)) {
            val input = model.input(byteArrayOf(1, 2, 3), "Preserve face", null, null)
            assertEquals("Preserve face", input.getString("prompt"))
            assertEquals("data:image/jpeg;base64,AQID", input.getJSONArray("image_input").getString(0))
            assertFalse(input.has("mask")); assertFalse(input.has("disable_safety_checker"))
            assertFalse(model.supportsRelaxedModeration)
            assertEquals("match_input_image", input.getString("aspect_ratio"))
            assertEquals("png", input.getString("output_format"))
            if (model == ReplicateEditModel.SEEDREAM_5_PRO) {
                assertEquals("2K", input.getString("size")); assertFalse(input.has("max_images"))
                assertEquals("1K", model.input(byteArrayOf(1), "edit", null, null, "1K").getString("size"))
            } else {
                assertEquals("disabled", input.getString("sequential_image_generation")); assertEquals(1, input.getInt("max_images"))
            }
            assertTrue(runCatching { model.input(byteArrayOf(1), "edit", byteArrayOf(2), null) }.isFailure)
        }
    }
    @Test fun onlySeedreamFourFiveCanRequestRelaxedModeration() {
        assertTrue(ReplicateEditModel.SEEDREAM.supportsRelaxedModeration)
        assertEquals("≈$0.045/image · 1K", ReplicateEditModel.SEEDREAM_5_PRO.priceLabel)
    }
    @Test fun eachAdapterUsesSourceAndOnlyItsDocumentedFields() {
        val image = byteArrayOf(1, 2, 3)
        val kontext = ReplicateEditModel.KONTEXT.input(image, "Restyle", null, null)
        assertEquals("data:image/jpeg;base64,AQID", kontext.getString("input_image"))
        assertEquals("Restyle", kontext.getString("prompt"))
        assertEquals("match_input_image", kontext.getString("aspect_ratio"))
        assertFalse(kontext.has("mask"))
        val fill = ReplicateEditModel.FILL.input(image, "Replace", byteArrayOf(4, 5), null)
        assertEquals("data:image/jpeg;base64,AQID", fill.getString("image"))
        assertEquals("data:image/png;base64,BAU=", fill.getString("mask"))
        assertEquals("Replace", fill.getString("prompt"))
        assertEquals("png", fill.getString("output_format"))
    }

    @Test fun unsupportedOperationsAreRejectedBeforeNetwork() {
        try { ReplicateEditModel.KONTEXT.input(byteArrayOf(1), "edit", byteArrayOf(2), null); fail() } catch (_: AiEditFailure) {}
        try { ReplicateEditModel.FILL.input(byteArrayOf(1), "edit", null, null); fail() } catch (_: AiEditFailure) {}
        try { ReplicateEditModel.KONTEXT.input(byteArrayOf(1), " ", null, null); fail() } catch (_: AiEditFailure) {}
    }

    @Test fun apiAcceptsSingleOutputAndDoesNotSendTokenToDeliveryHost() = runBlocking {
        val requests = mutableListOf<AiHttpRequest>()
        val transport = object : AiHttpTransport {
            override suspend fun execute(request: AiHttpRequest): AiHttpResponse {
                requests += request
                return if (request.method == "POST") {
                    val out = ByteArrayOutputStream(); request.body!!.writeTo(out)
                    assertEquals("Restyle", JSONObject(out.toString("UTF-8")).getJSONObject("input").getString("prompt"))
                    AiHttpResponse(200, "application/json", """{"id":"abc","status":"succeeded","output":"https://replicate.delivery/a.png"}""".toByteArray())
                } else AiHttpResponse(200, "image/png", byteArrayOf(9))
            }
        }
        assertArrayEquals(byteArrayOf(9), ReplicateModelEditApi(transport).edit("token".toByteArray(), ReplicateEditModel.KONTEXT, byteArrayOf(1), "Restyle", null))
        assertEquals("https://api.replicate.com/v1/models/black-forest-labs/flux-kontext-pro/predictions", requests.first().url)
        assertFalse(requests.last().headers.containsKey("Authorization"))
    }
    @Test fun proParsesObjectOutputAndLiteArrayWithoutExtraPredictions() = runBlocking {
        for (model in listOf(ReplicateEditModel.SEEDREAM_5_PRO, ReplicateEditModel.SEEDREAM_5_LITE)) {
            val requests = mutableListOf<AiHttpRequest>()
            val output = if (model == ReplicateEditModel.SEEDREAM_5_PRO)
                "{\"images\":[\"https://replicate.delivery/a.png\"],\"layers\":[]}"
                else "[\"https://replicate.delivery/a.png\"]"
            val transport = AiHttpTransport { request ->
                requests += request
                if (request.method == "POST") AiHttpResponse(201, "application/json", """{"id":"abc","status":"succeeded","output":$output}""".toByteArray())
                else AiHttpResponse(200, "image/png", byteArrayOf(7))
            }
            assertArrayEquals(byteArrayOf(7), ReplicateModelEditApi(transport).edit("token".toByteArray(), model, byteArrayOf(1), "edit", null))
            assertEquals("https://api.replicate.com/v1/models/${model.modelId}/predictions", requests.first().url)
            assertEquals(1, requests.count { it.method == "POST" })
        }
    }

    @Test fun providerWipesPreparedSourceMaskAndCredentialOnFailure() = runBlocking {
        val token = "token".toByteArray()
        val prepared = byteArrayOf(1, 2)
        val mask = byteArrayOf(3, 4)
        val seedream = ReplicateSeedreamProvider({ null }, ReplicateSeedreamApi(object : AiHttpTransport {
            override suspend fun execute(request: AiHttpRequest): AiHttpResponse = error("unused")
        }), { it })
        val transport = object : AiHttpTransport {
            override suspend fun execute(request: AiHttpRequest): AiHttpResponse = AiHttpResponse(500, "application/json", byteArrayOf(1))
        }
        val provider = ReplicateMultiEditProvider({ token }, { ReplicateEditModel.FILL }, seedream,
            ReplicateModelEditApi(transport), { prepared }, { _, _ -> mask })
        try { provider.edit(AiEditRequest(byteArrayOf(7), AiParameters(AiCapability.GENERATIVE_FILL, "Replace", listOf(MaskStroke(listOf(MaskPoint(.5f, .5f)), .04f))))); fail() }
        catch (_: AiEditFailure) {}
        assertTrue(token.all { it == 0.toByte() })
        assertTrue(prepared.all { it == 0.toByte() })
        assertTrue(mask.all { it == 0.toByte() })
    }

    @Test fun cancellationRequestsRemoteCancelWithoutSecondPrediction() = runBlocking {
        val polling = CompletableDeferred<Unit>()
        val requests = mutableListOf<AiHttpRequest>()
        val transport = object : AiHttpTransport {
            override suspend fun execute(request: AiHttpRequest): AiHttpResponse {
                requests += request
                return when {
                    request.url.endsWith("/cancel") -> AiHttpResponse(200, "application/json", "{}".toByteArray())
                    request.method == "POST" -> AiHttpResponse(200, "application/json", """{"id":"abc","status":"processing"}""".toByteArray())
                    else -> { polling.complete(Unit); awaitCancellation() }
                }
            }
        }
        val job = launch { ReplicateModelEditApi(transport, 1).edit("token".toByteArray(), ReplicateEditModel.KONTEXT, byteArrayOf(1), "edit", null) }
        polling.await(); job.cancelAndJoin()
        assertEquals(1, requests.count { it.url.endsWith("/predictions") })
        assertTrue(requests.any { it.url.endsWith("/abc/cancel") })
    }

    @Test fun unsafeOrMultipleOutputsNeverDownload() = runBlocking {
        for (output in listOf("\"https://evil.example/a.png\"", "[\"https://replicate.delivery/a.png\",\"https://replicate.delivery/b.png\"]")) {
            val requests = mutableListOf<AiHttpRequest>()
            val transport = object : AiHttpTransport {
                override suspend fun execute(request: AiHttpRequest): AiHttpResponse {
                    requests += request
                    return AiHttpResponse(200, "application/json", """{"id":"abc","status":"succeeded","output":$output}""".toByteArray())
                }
            }
            try { ReplicateModelEditApi(transport).edit("token".toByteArray(), ReplicateEditModel.KONTEXT, byteArrayOf(1), "edit", null); fail() }
            catch (_: AiEditFailure) {}
            assertEquals(1, requests.size)
        }
    }
}
