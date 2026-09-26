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
