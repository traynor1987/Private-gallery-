package uk.co.traynor.privategallery.core.editor

import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream

class ReplicateImageGenerationTest {
    @Test fun modelSpecificInputsAndSingleOutputDefault() {
        for (model in GenerationModel.entries) {
            var posted: AiHttpRequest? = null
            val api = ReplicateImageGenerationApi(AiHttpTransport { request ->
                if (request.method == "POST") {
                    posted = request
                    val output = if (model == GenerationModel.SEEDREAM) "[\"https://replicate.delivery/image.png\"]" else "\"https://replicate.delivery/image.png\""
                    AiHttpResponse(201, "application/json", """{"id":"abc123","status":"succeeded","output":$output}""".toByteArray())
                } else AiHttpResponse(200, "image/png", byteArrayOf(1, 2, 3))
            })
            val aspect = if (model == GenerationModel.SEEDREAM) GenerationAspect.SQUARE else GenerationAspect.PORTRAIT
            val result = runBlocking { api.generate("test-token".toByteArray(), GenerationRequest(model, "A blue bird", aspect)) }
            assertArrayEquals(byteArrayOf(1, 2, 3), result)
            val body = ByteArrayOutputStream().also { posted!!.body!!.writeTo(it) }.toString("UTF-8")
            val input = JSONObject(body).getJSONObject("input")
            assertEquals("A blue bird", input.getString("prompt"))
            assertFalse(input.has("num_outputs"))
            assertFalse(input.has("image_input"))
            assertEquals(model.endpoint, posted!!.url)
            if (model == GenerationModel.SEEDREAM) {
                assertEquals("1:1", input.getString("aspect_ratio"))
                assertEquals(1, input.getInt("max_images"))
                assertFalse(input.has("negative_prompt"))
            }
            if (model == GenerationModel.FLUX_PRO) {
                assertEquals("2:3", input.getString("aspect_ratio"))
                assertFalse(input.has("steps"))
            }
            if (model == GenerationModel.WHISKII) {
                assertEquals(832, input.getInt("width"))
                assertEquals(1216, input.getInt("height"))
                assertEquals(30, input.getInt("steps"))
                assertFalse(input.has("aspect_ratio"))
            }
        }
    }

    @Test fun optionalControlsAreOnlySentWhenSupported() {
        val model = GenerationModel.WHISKII
        val input = model.input(GenerationRequest(model, "A fictional landscape", GenerationAspect.LANDSCAPE,
            negativePrompt = "fog", seed = 42, steps = 25))
        assertEquals("fog", input.getString("negative_prompt"))
        assertEquals(42, input.getInt("seed"))
        assertEquals(25, input.getInt("steps"))
        assertEquals(1216, input.getInt("width"))
        assertFailsRequest { GenerationRequest(GenerationModel.FLUX_PRO, "x", GenerationAspect.SQUARE, negativePrompt = "fog") }
    }

    @Test fun billingFailureDoesNotDownloadOrImportOutput() {
        var requests = 0
        val api = ReplicateImageGenerationApi(AiHttpTransport { requests++; AiHttpResponse(402, null, byteArrayOf()) })
        val error = runCatching { runBlocking { api.generate("token".toByteArray(), GenerationRequest(GenerationModel.SEEDREAM, "Blue", GenerationAspect.SQUARE)) } }.exceptionOrNull()
        assertTrue(error is AiEditFailure)
        assertTrue(error!!.message!!.contains("credit"))
        assertEquals(1, requests)
    }

    private fun assertFailsRequest(block: () -> Unit) { assertTrue(runCatching(block).isFailure) }
}
