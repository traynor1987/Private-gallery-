package uk.co.traynor.privategallery.core.editor

import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream

class ReplicateImageGenerationTest {
    @Test fun seedreamFiveCreationHasResolutionPricingAndReferenceLimit() {
        val pro = GenerationModel.SEEDREAM_5_PRO
        val input = pro.input(GenerationRequest(pro, "A portrait", GenerationAspect.PORTRAIT, resolution = "1K"))
        assertEquals("1K", input.getString("size")); assertEquals("3:4", input.getString("aspect_ratio"))
        assertFalse(input.has("sequential_image_generation")); assertFalse(input.has("disable_safety_checker"))
        assertEquals("≈$0.045/image", pro.priceFor("1K")); assertEquals("≈$0.09/image", pro.priceFor("2K"))
        assertEquals("≈$0.035/image", GenerationModel.SEEDREAM_5_LITE.priceLabel)
        assertEquals(10, pro.maxReferences)
        val refs = List(10) { "data:image/jpeg;base64,AQ==" }
        assertEquals(10, pro.input(GenerationRequest(pro, "Reference portrait", GenerationAspect.SQUARE, references = refs)).getJSONArray("image_input").length())
        assertTrue(runCatching { GenerationRequest(pro, "x", GenerationAspect.SQUARE, references = refs + refs[0]) }.isFailure)
        assertTrue(runCatching { GenerationRequest(pro, "x", GenerationAspect.SQUARE, resolution = "3K") }.isFailure)
        assertTrue(runCatching { GenerationRequest(GenerationModel.SEEDREAM_5_LITE, "x", GenerationAspect.SQUARE, resolution = "1K") }.isFailure)
    }
    @Test fun seedreamExplicitOwnerModerationChoiceDoesNotAffectOtherModels() {
        val enabled = GenerationRequest(GenerationModel.SEEDREAM, "fictional scene", GenerationAspect.SQUARE, relaxModeration = true)
        assertTrue(GenerationModel.SEEDREAM.input(enabled).getBoolean("disable_safety_checker"))
        val default = GenerationRequest(GenerationModel.SEEDREAM, "fictional scene", GenerationAspect.SQUARE)
        assertFalse(GenerationModel.SEEDREAM.input(default).has("disable_safety_checker"))
        try {
            GenerationRequest(GenerationModel.WHISKII, "fictional scene", GenerationAspect.SQUARE, relaxModeration = true)
            fail("Only Seedream has this documented field")
        } catch (_: IllegalArgumentException) { }
    }
    @Test fun modelSpecificInputsAndSingleOutputDefault() {
        for (model in GenerationModel.entries) {
            var posted: AiHttpRequest? = null
            val api = ReplicateImageGenerationApi(AiHttpTransport { request ->
                if (request.method == "POST") {
                    posted = request
                    val output = when (model) {
                        GenerationModel.SEEDREAM, GenerationModel.SEEDREAM_5_LITE -> "[\"https://replicate.delivery/image.png\"]"
                        GenerationModel.SEEDREAM_5_PRO -> "{\"images\":[\"https://replicate.delivery/image.png\"],\"layers\":[]}"
                        else -> "\"https://replicate.delivery/image.png\""
                    }
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
            if (model == GenerationModel.SEEDREAM_5_PRO) {
                assertEquals("2K", input.getString("size"))
                assertFalse(input.has("max_images"))
            }
            if (model == GenerationModel.SEEDREAM_5_LITE) {
                assertEquals(1, input.getInt("max_images"))
                assertEquals("disabled", input.getString("sequential_image_generation"))
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
