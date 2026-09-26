package uk.co.traynor.privategallery.core.editor

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject

enum class GenerationAspect(val label: String) { SQUARE("Square"), PORTRAIT("Portrait"), LANDSCAPE("Landscape") }
enum class GenerationCapability { TEXT_TO_IMAGE, ASPECT_RATIO, NEGATIVE_PROMPT, SEED, STEPS }

/** Field names, values and version come from the selected model's Replicate schema. */
enum class GenerationModel(val label: String, val description: String, val endpoint: String,
    val capabilities: Set<GenerationCapability>, val aspects: Set<GenerationAspect>) {
    SEEDREAM("Seedream 4.5", "Creative image generation", "https://api.replicate.com/v1/models/bytedance/seedream-4.5/predictions",
        setOf(GenerationCapability.TEXT_TO_IMAGE), setOf(GenerationAspect.SQUARE)),
    FLUX_PRO("FLUX 1.1 Pro", "Photorealistic and general images", "https://api.replicate.com/v1/models/black-forest-labs/flux-1.1-pro/predictions",
        setOf(GenerationCapability.TEXT_TO_IMAGE, GenerationCapability.ASPECT_RATIO, GenerationCapability.SEED), GenerationAspect.entries.toSet()),
    WHISKII("Whiskii Gen", "Artistic and synthetic character images", "https://api.replicate.com/v1/predictions",
        GenerationCapability.entries.toSet(), GenerationAspect.entries.toSet());

    fun input(request: GenerationRequest): JSONObject {
        require(request.model == this)
        val value = JSONObject().put("prompt", request.prompt.trim())
        when (this) {
            SEEDREAM -> value.put("size", "2K").put("aspect_ratio", "1:1")
                .put("sequential_image_generation", "disabled").put("max_images", 1)
            FLUX_PRO -> value.put("aspect_ratio", when (request.aspect) {
                GenerationAspect.SQUARE -> "1:1"; GenerationAspect.PORTRAIT -> "2:3"; GenerationAspect.LANDSCAPE -> "3:2"
            }).put("output_format", "png")
            WHISKII -> {
                val dimensions = when (request.aspect) {
                    GenerationAspect.SQUARE -> 1024 to 1024
                    GenerationAspect.PORTRAIT -> 832 to 1216
                    GenerationAspect.LANDSCAPE -> 1216 to 832
                }
                value.put("width", dimensions.first).put("height", dimensions.second)
                    .put("steps", request.steps ?: 30).put("guidance", 7).put("scheduler", "dpmpp_2m")
            }
        }
        request.negativePrompt?.takeIf(String::isNotBlank)?.let { value.put("negative_prompt", it) }
        request.seed?.let { value.put("seed", it) }
        return value
    }

    val modelId: String get() = when (this) {
        SEEDREAM -> "bytedance/seedream-4.5"
        FLUX_PRO -> "black-forest-labs/flux-1.1-pro"
        WHISKII -> "alicewuv/whiskii-gen:e90d5fa37f8c42812753afd6bc05409d67a970bc87ef57454892c0fab98a7b03"
    }
}

data class GenerationRequest(val model: GenerationModel, val prompt: String, val aspect: GenerationAspect,
    val negativePrompt: String? = null, val seed: Int? = null, val steps: Int? = null) {
    init {
        require(prompt.isNotBlank() && prompt.length <= 4000)
        require(aspect in model.aspects)
        require(negativePrompt == null || (GenerationCapability.NEGATIVE_PROMPT in model.capabilities && negativePrompt.length <= 2000))
        require(seed == null || GenerationCapability.SEED in model.capabilities)
        require(steps == null || (GenerationCapability.STEPS in model.capabilities && steps in 1..100))
    }
}

class GenerationModelStore(context: Context) {
    private val preferences = context.getSharedPreferences("ai_image_generation", Context.MODE_PRIVATE)
    fun selected(): GenerationModel = GenerationModel.entries.firstOrNull { it.name == preferences.getString("model", null) }
        ?: GenerationModel.SEEDREAM
    fun select(model: GenerationModel) { preferences.edit().putString("model", model.name).apply() }
}

enum class GenerationFailureCategory { AUTHENTICATION, BILLING, MODEL_UNAVAILABLE, PROVIDER_REJECTION, INVALID_INPUT,
    RATE_LIMIT, TIMEOUT, PREDICTION_FAILED, OUTPUT_DOWNLOAD_FAILED, OUTPUT_INVALID }
class GenerationFailure(val category: GenerationFailureCategory, message: String) : AiEditFailure(message)

/** Uses the same token and cancellation-capable transport as AI Edit. Never retains prompts. */
class ReplicateImageGenerationApi(private val transport: AiHttpTransport, private val pollMillis: Long = 1500L) {
    suspend fun generate(token: ByteArray, request: GenerationRequest, stage: (String) -> Unit = {}): ByteArray {
        if (token.isEmpty() || token.size > 8192) throw GenerationFailure(GenerationFailureCategory.AUTHENTICATION, "Set up Replicate in AI editing settings.")
        var predictionId: String? = null
        var terminal = false
        try {
            return withTimeout(200_000) {
                stage("Preparing request…")
                val body = JSONObject().put("input", request.model.input(request))
                if (request.model == GenerationModel.WHISKII) body.put("version", request.model.modelId)
                var prediction = json(send("POST", request.model.endpoint, token, AiRequestBody {
                    it.write(body.toString().toByteArray(Charsets.UTF_8))
                }))
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val id = prediction.optString("id")
                    if (!id.matches(Regex("[a-zA-Z0-9]{1,128}")) || (predictionId != null && predictionId != id))
                        throw GenerationFailure(GenerationFailureCategory.OUTPUT_INVALID, "Replicate returned an invalid result.")
                    predictionId = id
                    when (prediction.optString("status")) {
                        "succeeded" -> {
                            terminal = true
                            val output = prediction.opt("output")
                            val url = when (output) {
                                is String -> output
                                is JSONArray -> if (output.length() == 1) output.optString(0) else ""
                                else -> ""
                            }
                            if (!AiRemoteUrls.output(url)) throw GenerationFailure(GenerationFailureCategory.OUTPUT_INVALID, "Replicate returned an invalid image.")
                            stage("Receiving image…")
                            val response = send("GET", url, token, null, 16 * 1024 * 1024)
                            if (response.contentType?.substringBefore(';')?.lowercase() !in setOf("image/png", "image/jpeg", "image/webp") || response.bytes.isEmpty()) {
                                response.bytes.fill(0)
                                throw GenerationFailure(GenerationFailureCategory.OUTPUT_INVALID, "Replicate returned an invalid image.")
                            }
                            return@withTimeout response.bytes
                        }
                        "failed" -> { terminal = true; throw GenerationFailure(GenerationFailureCategory.PREDICTION_FAILED, "Replicate could not create the image.") }
                        "canceled" -> { terminal = true; throw GenerationFailure(GenerationFailureCategory.PREDICTION_FAILED, "Replicate cancelled the image.") }
                        "starting", "processing" -> {
                            stage("Generating…")
                            delay(pollMillis)
                            prediction = json(send("GET", "https://api.replicate.com/v1/predictions/$id", token))
                        }
                        else -> throw GenerationFailure(GenerationFailureCategory.OUTPUT_INVALID, "Replicate returned an invalid result.")
                    }
                }
                @Suppress("UNREACHABLE_CODE") byteArrayOf()
            }
        } catch (failure: kotlinx.coroutines.TimeoutCancellationException) {
            throw GenerationFailure(GenerationFailureCategory.TIMEOUT, "Replicate took too long. Try again.")
        } finally {
            if (!terminal && predictionId != null) withContext(NonCancellable) {
                try { withTimeout(3000) { send("POST", "https://api.replicate.com/v1/predictions/$predictionId/cancel", token).bytes.fill(0) } }
                catch (_: Exception) { /* Best effort. Remote cancellation may not refund credit. */ }
            }
        }
    }

    private suspend fun send(method: String, url: String, token: ByteArray, body: AiRequestBody? = null,
        maxBytes: Int = 2 * 1024 * 1024): AiHttpResponse {
        val headers = mutableMapOf("Accept" to if (AiRemoteUrls.output(url)) "image/png,image/jpeg,image/webp" else "application/json")
        if (!AiRemoteUrls.output(url)) headers["Authorization"] = "Bearer ${token.toString(Charsets.US_ASCII)}"
        if (body != null) { headers["Content-Type"] = "application/json"; headers["Cancel-After"] = "180s" }
        val response = transport.execute(AiHttpRequest(method, url, headers, body, maxBytes))
        if (response.status !in 200..299) {
            response.bytes.fill(0)
            val category = when (response.status) {
                400, 422 -> GenerationFailureCategory.INVALID_INPUT
                401, 403 -> GenerationFailureCategory.AUTHENTICATION
                402 -> GenerationFailureCategory.BILLING
                404 -> GenerationFailureCategory.MODEL_UNAVAILABLE
                429 -> GenerationFailureCategory.RATE_LIMIT
                else -> GenerationFailureCategory.PROVIDER_REJECTION
            }
            throw GenerationFailure(category, when (category) {
                GenerationFailureCategory.BILLING -> "Replicate needs account credit before generation."
                GenerationFailureCategory.AUTHENTICATION -> "Replicate did not accept this token."
                GenerationFailureCategory.RATE_LIMIT -> "Replicate is busy. Try again shortly."
                else -> "Replicate could not create this image."
            })
        }
        return response
    }
    private fun json(response: AiHttpResponse): JSONObject = try {
        if (response.contentType?.substringBefore(';')?.lowercase() != "application/json")
            throw GenerationFailure(GenerationFailureCategory.OUTPUT_INVALID, "Replicate returned an invalid response.")
        JSONObject(response.bytes.toString(Charsets.UTF_8))
    } finally { response.bytes.fill(0) }
}
