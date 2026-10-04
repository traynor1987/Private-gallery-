package uk.co.traynor.privategallery.core.editor

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
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
    SEEDREAM_5_PRO("Seedream 5 Pro", "Reference-guided image creation", "https://api.replicate.com/v1/models/bytedance/seedream-5-pro/predictions",
        setOf(GenerationCapability.TEXT_TO_IMAGE, GenerationCapability.ASPECT_RATIO), GenerationAspect.entries.toSet()),
    SEEDREAM_5_LITE("Seedream 5 Lite", "Reasoning and creative image creation", "https://api.replicate.com/v1/models/bytedance/seedream-5-lite/predictions",
        setOf(GenerationCapability.TEXT_TO_IMAGE, GenerationCapability.ASPECT_RATIO), GenerationAspect.entries.toSet()),
    FLUX_PRO("FLUX 1.1 Pro", "Photorealistic and general images", "https://api.replicate.com/v1/models/black-forest-labs/flux-1.1-pro/predictions",
        setOf(GenerationCapability.TEXT_TO_IMAGE, GenerationCapability.ASPECT_RATIO, GenerationCapability.SEED), GenerationAspect.entries.toSet()),
    WHISKII("Whiskii Gen", "Artistic and synthetic character images", "https://api.replicate.com/v1/predictions",
        GenerationCapability.entries.toSet(), GenerationAspect.entries.toSet());

    val maxReferences: Int get() = when (this) { SEEDREAM_5_PRO -> 10; SEEDREAM_5_LITE -> 14; else -> 0 }
    val supportsRelaxedModeration: Boolean get() = this == SEEDREAM
    fun priceFor(resolution: String): String = when (this) {
        SEEDREAM_5_PRO -> if (resolution == "1K") "≈$0.045/image" else "≈$0.09/image"
        else -> priceLabel
    }
    val priceLabel: String get() = when (this) {
        SEEDREAM, FLUX_PRO -> "≈$0.04/image"
        SEEDREAM_5_PRO -> "≈$0.045/image · 1K"
        SEEDREAM_5_LITE -> "≈$0.035/image"
        WHISKII -> "≈$0.023/run · variable"
    }

    fun input(request: GenerationRequest): JSONObject {
        require(request.model == this)
        val value = JSONObject().put("prompt", request.prompt.trim())
        when (this) {
            SEEDREAM -> {
                value.put("size", "2K").put("aspect_ratio", "1:1")
                    .put("sequential_image_generation", "disabled").put("max_images", 1)
                if (request.relaxModeration) value.put("disable_safety_checker", true)
            }
            SEEDREAM_5_PRO -> {
                value.put("size", request.resolution).put("aspect_ratio", request.aspect.schemaRatio())
                    .put("output_format", "png")
                if (request.references.isNotEmpty()) value.put("image_input", org.json.JSONArray(request.references))
            }
            SEEDREAM_5_LITE -> {
                value.put("size", request.resolution).put("aspect_ratio", request.aspect.schemaRatio())
                    .put("output_format", "png").put("sequential_image_generation", "disabled").put("max_images", 1)
                if (request.references.isNotEmpty()) value.put("image_input", org.json.JSONArray(request.references))
            }
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
        SEEDREAM_5_PRO -> "bytedance/seedream-5-pro"
        SEEDREAM_5_LITE -> "bytedance/seedream-5-lite"
        FLUX_PRO -> "black-forest-labs/flux-1.1-pro"
        WHISKII -> "alicewuv/whiskii-gen:e90d5fa37f8c42812753afd6bc05409d67a970bc87ef57454892c0fab98a7b03"
    }
}
private fun GenerationAspect.schemaRatio() = when (this) {
    GenerationAspect.SQUARE -> "1:1"; GenerationAspect.PORTRAIT -> "3:4"; GenerationAspect.LANDSCAPE -> "4:3"
}

data class GenerationRequest(val model: GenerationModel, val prompt: String, val aspect: GenerationAspect,
    val negativePrompt: String? = null, val seed: Int? = null, val steps: Int? = null, val relaxModeration: Boolean = false,
    val resolution: String = "2K", val references: List<String> = emptyList(), val enhancePrompt: Boolean = true,
    val referenceHandles: List<uk.co.traynor.privategallery.core.security.ScopedItemHandle> = emptyList()) {
    init {
        require(prompt.isNotBlank() && prompt.length <= 4000)
        require(aspect in model.aspects)
        require(negativePrompt == null || (GenerationCapability.NEGATIVE_PROMPT in model.capabilities && negativePrompt.length <= 2000))
        require(seed == null || GenerationCapability.SEED in model.capabilities)
        require(steps == null || (GenerationCapability.STEPS in model.capabilities && steps in 1..100))
        require(!relaxModeration || model.supportsRelaxedModeration)
        require(resolution in when (model) {
            GenerationModel.SEEDREAM_5_PRO -> setOf("1K", "2K")
            GenerationModel.SEEDREAM_5_LITE -> setOf("2K", "3K")
            else -> setOf("2K")
        })
        require(references.size <= model.maxReferences && references.all { it.startsWith("data:image/jpeg;base64,") || it.startsWith("data:image/png;base64,") })
        require(referenceHandles.size <= model.maxReferences && referenceHandles.distinct().size == referenceHandles.size)
    }
}

class GenerationModelStore(context: Context) {
    private val preferences = context.getSharedPreferences("ai_image_generation", Context.MODE_PRIVATE)
    fun selected(): GenerationModel = GenerationModel.entries.firstOrNull { it.name == preferences.getString("model", null) }
        ?: GenerationModel.SEEDREAM
    fun select(model: GenerationModel) { preferences.edit().putString("model", model.name).apply() }
    fun resolution(model: GenerationModel): String = preferences.getString("resolution_${model.name}", null)
        .takeIf { it in if (model == GenerationModel.SEEDREAM_5_PRO) setOf("1K", "2K") else setOf("2K", "3K") }
        ?: if (model == GenerationModel.SEEDREAM_5_PRO) "1K" else "2K"
    fun setResolution(model: GenerationModel, value: String) { require(value in if (model == GenerationModel.SEEDREAM_5_PRO) setOf("1K", "2K") else setOf("2K", "3K"))
        preferences.edit().putString("resolution_${model.name}", value).apply() }
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
        val started = System.nanoTime()
        fun elapsed(): String = "${(System.nanoTime() - started) / 1_000_000_000}s"
        try {
                stage("Preparing request…")
                var prediction = try {
                    if(request.model==GenerationModel.FLUX_PRO) fluxProSubmission(request,token)
                    else {
                        val body=JSONObject().put("input",request.model.input(request))
                        if(request.model==GenerationModel.WHISKII)body.put("version",request.model.modelId)
                        json(send("POST",request.model.endpoint,token,AiRequestBody {
                            it.write(body.toString().toByteArray(Charsets.UTF_8))
                        }))
                    }
                } catch (_: AiNetworkFailure) {
                    throw GenerationFailure(GenerationFailureCategory.TIMEOUT,
                        "Could not confirm whether Replicate accepted this image. Check your Replicate predictions before generating again.")
                }
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
                                is JSONObject -> if (request.model == GenerationModel.SEEDREAM_5_PRO && output.optJSONArray("layers")?.length() == 0 && output.optJSONArray("images")?.length() == 1)
                                    output.getJSONArray("images").optString(0) else ""
                                else -> ""
                            }
                            if (!AiRemoteUrls.output(url)) throw GenerationFailure(GenerationFailureCategory.OUTPUT_INVALID, "Replicate returned an invalid image.")
                            stage("Image ready · downloading…")
                            val response = try { send("GET", url, token, null, 16 * 1024 * 1024) }
                            catch (_: AiNetworkFailure) {
                                throw GenerationFailure(GenerationFailureCategory.OUTPUT_DOWNLOAD_FAILED,
                                    "Replicate finished the image, but the download was interrupted. No new prediction was requested.")
                            }
                            if (response.contentType?.substringBefore(';')?.lowercase() !in setOf("image/png", "image/jpeg", "image/webp") || response.bytes.isEmpty()) {
                                response.bytes.fill(0)
                                throw GenerationFailure(GenerationFailureCategory.OUTPUT_INVALID, "Replicate returned an invalid image.")
                            }
                            return response.bytes
                        }
                        "failed" -> { terminal = true; throw GenerationFailure(GenerationFailureCategory.PREDICTION_FAILED, "Replicate could not create the image.") }
                        "canceled", "aborted" -> { terminal = true; throw GenerationFailure(GenerationFailureCategory.PREDICTION_FAILED, "Replicate cancelled the image.") }
                        "starting", "processing" -> {
                            stage("${prediction.optString("status").replaceFirstChar(Char::uppercase)} · ${elapsed()} elapsed")
                            delay(pollMillis)
                            try { prediction = predictionStatus(id,token) }
                            catch (failure: AiNetworkFailure) {
                                stage("Connection interrupted · retrying status for $id · ${elapsed()} elapsed")
                                delay((pollMillis.coerceAtLeast(500L) * 2).coerceAtMost(10_000L))
                            }
                        }
                        else -> throw GenerationFailure(GenerationFailureCategory.OUTPUT_INVALID, "Replicate returned an invalid result.")
                    }
                }
                @Suppress("UNREACHABLE_CODE") throw IllegalStateException("Prediction loop exited")
        } finally {
            if (!terminal && predictionId != null && !currentCoroutineContext().isActive) withContext(NonCancellable) {
                try { withTimeout(3000) { predictionCancellation(predictionId,token) } }
                catch (_: Exception) { /* Best effort. Remote cancellation may not refund credit. */ }
            }
        }
    }

    private suspend fun fluxProSubmission(generation:GenerationRequest,token:ByteArray):JSONObject {
        val request=makeRequest("POST",GenerationModel.FLUX_PRO.endpoint,token)
        var parsed:JSONObject?=null
        transport.consumeFluxProSubmission(generation,request) {response->
            checkResponse(response,request.method,request.url,request.maxResponseBytes)
            if(response.bytes.size>request.maxResponseBytes) {
                response.bytes.fill(0)
                throw GenerationFailure(GenerationFailureCategory.OUTPUT_INVALID,"Replicate returned an invalid response.")
            }
            parsed=json(response)
        }
        return checkNotNull(parsed)
    }

    private suspend fun predictionCancellation(id:String,token:ByteArray) {
        val request=makeRequest("POST","https://api.replicate.com/v1/predictions/$id/cancel",token)
        transport.consumePredictionCancellation(id,request) {response->checkResponse(response,request.method,request.url,request.maxResponseBytes)}
    }
    private fun makeRequest(method:String,url:String,token:ByteArray,body:AiRequestBody?=null,maxBytes:Int=2*1024*1024):AiHttpRequest {
        val headers = mutableMapOf("Accept" to if (AiRemoteUrls.output(url)) "image/png,image/jpeg,image/webp" else "application/json")
        if (!AiRemoteUrls.output(url)) headers["Authorization"] = "Bearer ${token.toString(Charsets.US_ASCII)}"
        if (body != null || (method=="POST"&&url==GenerationModel.FLUX_PRO.endpoint)) headers["Content-Type"] = "application/json"
        return AiHttpRequest(method,url,headers,body,maxBytes)
    }
    private suspend fun send(method:String,url:String,token:ByteArray,body:AiRequestBody?=null,maxBytes:Int=2*1024*1024):AiHttpResponse {
        val response=transport.execute(makeRequest(method,url,token,body,maxBytes))
        checkResponse(response,method,url,maxBytes);return response
    }
    private fun checkResponse(response:AiHttpResponse,method:String,url:String,maxBytes:Int) {

        if (response.status !in 200..299) {
            response.bytes.fill(0)
            if (method == "GET" && !AiRemoteUrls.output(url) &&
                (response.status == 408 || response.status == 429 || response.status in 500..599))
                throw AiNetworkFailure(response.status == 408)
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
    }
    private suspend fun predictionStatus(id:String,token:ByteArray):JSONObject {
        val url="https://api.replicate.com/v1/predictions/$id"
        var parsed:JSONObject?=null
        transport.consumePredictionStatus(id,makeRequest("GET",url,token)) {response->
            checkResponse(response,"GET",url,2*1024*1024)
            if(response.bytes.size>2*1024*1024) {
                response.bytes.fill(0)
                throw GenerationFailure(GenerationFailureCategory.OUTPUT_INVALID,"Replicate returned an invalid response.")
            }
            parsed=json(response)
        }
        return checkNotNull(parsed)
    }
    private fun json(response: AiHttpResponse): JSONObject = try {
        if (response.contentType?.substringBefore(';')?.lowercase() != "application/json")
            throw GenerationFailure(GenerationFailureCategory.OUTPUT_INVALID, "Replicate returned an invalid response.")
        JSONObject(response.bytes.toString(Charsets.UTF_8))
    } finally { response.bytes.fill(0) }
}
