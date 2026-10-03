package uk.co.traynor.privategallery.core.editor

import java.util.Base64
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject

enum class ReplicateEditCapability { IMAGE_TO_IMAGE, INPAINTING, MASK, PROMPT_EDIT, STRENGTH, NEGATIVE_PROMPT, GUIDANCE, STEPS, SEED, OUTPUT_FORMAT, ASPECT_SIZE, SAFETY_CONFIGURATION }

/** Stable official model endpoints: the model owner manages the current deployment version. */
enum class ReplicateEditModel(val label: String, val description: String, val modelId: String,
    val priceLabel: String, val features: Set<ReplicateEditCapability>) {
    SEEDREAM("Seedream 4.5", "General precision editing", "bytedance/seedream-4.5", "≈$0.04/image",
        setOf(ReplicateEditCapability.IMAGE_TO_IMAGE, ReplicateEditCapability.PROMPT_EDIT, ReplicateEditCapability.ASPECT_SIZE, ReplicateEditCapability.SAFETY_CONFIGURATION)),
    SEEDREAM_5_PRO("Seedream 5 Pro", "Reference-guided precision editing", "bytedance/seedream-5-pro", "≈$0.045/image · 1K",
        setOf(ReplicateEditCapability.IMAGE_TO_IMAGE, ReplicateEditCapability.PROMPT_EDIT, ReplicateEditCapability.ASPECT_SIZE, ReplicateEditCapability.OUTPUT_FORMAT)),
    SEEDREAM_5_LITE("Seedream 5 Lite", "Prompt and example-guided editing", "bytedance/seedream-5-lite", "≈$0.035/image",
        setOf(ReplicateEditCapability.IMAGE_TO_IMAGE, ReplicateEditCapability.PROMPT_EDIT, ReplicateEditCapability.ASPECT_SIZE, ReplicateEditCapability.OUTPUT_FORMAT)),
    KONTEXT("FLUX Kontext Pro", "Photorealistic and creative transformation", "black-forest-labs/flux-kontext-pro", "≈$0.04/image",
        setOf(ReplicateEditCapability.IMAGE_TO_IMAGE, ReplicateEditCapability.PROMPT_EDIT, ReplicateEditCapability.SEED,
            ReplicateEditCapability.OUTPUT_FORMAT, ReplicateEditCapability.ASPECT_SIZE, ReplicateEditCapability.SAFETY_CONFIGURATION)),
    FILL("FLUX Fill Pro", "Replace or remove a selected area", "black-forest-labs/flux-fill-pro", "≈$0.05/image",
        setOf(ReplicateEditCapability.IMAGE_TO_IMAGE, ReplicateEditCapability.INPAINTING, ReplicateEditCapability.MASK,
            ReplicateEditCapability.PROMPT_EDIT, ReplicateEditCapability.GUIDANCE, ReplicateEditCapability.STEPS,
            ReplicateEditCapability.SEED, ReplicateEditCapability.OUTPUT_FORMAT, ReplicateEditCapability.SAFETY_CONFIGURATION));

    val supportsRelaxedModeration: Boolean get() = this == SEEDREAM

    val tools: Set<AiCapability> get() = when (this) {
        SEEDREAM, SEEDREAM_5_PRO, SEEDREAM_5_LITE -> setOf(AiCapability.GENERATIVE_EDIT)
        KONTEXT -> setOf(AiCapability.GENERATIVE_EDIT, AiCapability.RESTYLE)
        FILL -> setOf(AiCapability.OBJECT_REMOVAL, AiCapability.GENERATIVE_FILL)
    }

    /** Only fields in the official model schema enter a paid prediction. */
    fun input(image: ByteArray, prompt: String, mask: ByteArray?, seed: Int?, resolution: String = "2K"): JSONObject {
        if (image.isEmpty() || image.size > ReplicateSeedreamApi.MAX_INLINE_BYTES || prompt.isBlank() || prompt.length > 4000)
            throw AiEditFailure("Enter a prompt and use an image suitable for remote editing.")
        if ((this == FILL) != (mask != null)) throw AiEditFailure(if (this == FILL) "Mark the area to edit first." else "This model cannot use a selection mask.")
        if (mask != null && (mask.isEmpty() || mask.size > 4 * 1024 * 1024)) throw AiEditFailure("Selection mask is too large.")
        if (seed != null && ReplicateEditCapability.SEED !in features) throw AiEditFailure("This model does not support a seed.")
        if (this == SEEDREAM_5_PRO && resolution !in setOf("1K", "2K")) throw AiEditFailure("Choose 1K or 2K for Seedream 5 Pro.")
        val uri = "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(image)
        val value = JSONObject().put("prompt", prompt.trim())
        when (this) {
            SEEDREAM -> value.put("image_input", JSONArray().put(uri)).put("size", "2K")
                .put("aspect_ratio", "match_input_image").put("sequential_image_generation", "disabled").put("max_images", 1)
            SEEDREAM_5_PRO -> value.put("image_input", JSONArray().put(uri)).put("size", resolution)
                .put("aspect_ratio", "match_input_image").put("output_format", "png")
            SEEDREAM_5_LITE -> value.put("image_input", JSONArray().put(uri)).put("size", "2K")
                .put("aspect_ratio", "match_input_image").put("sequential_image_generation", "disabled")
                .put("max_images", 1).put("output_format", "png")
            KONTEXT -> value.put("input_image", uri).put("aspect_ratio", "match_input_image").put("output_format", "png")
            FILL -> value.put("image", uri).put("mask", "data:image/png;base64," + Base64.getEncoder().encodeToString(mask!!))
                .put("output_format", "png")
        }
        if (seed != null) value.put("seed", seed)
        return value
    }
}

/** Shared registry: text creation and source-image editing have independent selections. */
object ReplicateModelCapabilities {
    val creationModels: List<GenerationModel> get() = GenerationModel.entries
    val editModels: List<ReplicateEditModel> get() = ReplicateEditModel.entries
    fun editModelsFor(operation: AiCapability): List<ReplicateEditModel> = editModels.filter { operation in it.tools }
}

class ReplicateEditModelStore(context: android.content.Context) {
    private val prefs = context.getSharedPreferences("ai_replicate_edit_model", android.content.Context.MODE_PRIVATE)
    fun selected() = ReplicateEditModel.entries.firstOrNull { it.name == prefs.getString("model", null) } ?: ReplicateEditModel.SEEDREAM
    fun select(model: ReplicateEditModel) { prefs.edit().putString("model", model.name).apply() }
    fun proResolution(): String = prefs.getString("pro_resolution", "1K").takeIf { it in setOf("1K", "2K") } ?: "1K"
    fun setProResolution(value: String) { require(value in setOf("1K", "2K")); prefs.edit().putString("pro_resolution", value).apply() }
}

enum class ReplicatePredictionState { SUBMITTING, SUBMISSION_TIMEOUT, PREDICTION_SUBMITTED, PROVIDER_STILL_PROCESSING,
    POLL_NETWORK_FAILURE, POLL_TIMEOUT, PROVIDER_FAILED, PROVIDER_CANCELLED, PROVIDER_SUCCEEDED, OUTPUT_DOWNLOAD_TIMEOUT, OUTPUT_RECEIVED }
data class ReplicatePredictionSnapshot(val model: String, val resolution: String, val state: ReplicatePredictionState,
    val predictionId: String? = null, val pollingAttempts: Int = 0, val elapsedMillis: Long = 0,
    val observedStates: List<ReplicatePredictionState> = emptyList())
class ReplicatePredictionFailure(val state: ReplicatePredictionState, message: String) : AiEditFailure(message)

/** Same bounded transport, output allowlist, polling, cancellation and token as Seedream. */
class ReplicateModelEditApi(private val transport: AiHttpTransport, private val pollMillis: Long = 1500L) {
    suspend fun edit(token: ByteArray, model: ReplicateEditModel, image: ByteArray, prompt: String, mask: ByteArray?, seed: Int? = null, resolution: String = "2K",
        observe: (ReplicatePredictionSnapshot) -> Unit = {}): ByteArray {
        val input = model.input(image, prompt, mask, seed, resolution)
        if (token.isEmpty() || token.size > 8192 || token.any { (it.toInt() and 255) !in 33..126 }) throw AiEditFailure("Enter a valid Replicate API token.")
        var id: String? = null
        var terminal = false
        var polls = 0
        val start = System.nanoTime()
        val observed = mutableListOf<ReplicatePredictionState>()
        fun state(value: ReplicatePredictionState) {
            if (observed.lastOrNull() != value && observed.size < 32) observed += value
            observe(ReplicatePredictionSnapshot(model.modelId, resolution, value, id, polls,
                (System.nanoTime() - start) / 1_000_000, observed.toList()))
        }
        try {
                state(ReplicatePredictionState.SUBMITTING)
                val submitted = try { send("POST", "https://api.replicate.com/v1/models/${model.modelId}/predictions", token,
                    AiRequestBody { it.write(JSONObject().put("input", input).toString().toByteArray(Charsets.UTF_8)) }) }
                catch (failure: AiNetworkFailure) { state(ReplicatePredictionState.SUBMISSION_TIMEOUT)
                    throw ReplicatePredictionFailure(ReplicatePredictionState.SUBMISSION_TIMEOUT,
                        "Could not confirm whether Replicate accepted this edit. Check your Replicate predictions before generating again.") }
                var prediction = parse(submitted)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val nextId = prediction.optString("id")
                    if (!nextId.matches(Regex("[a-zA-Z0-9]{1,128}")) || (id != null && nextId != id)) invalid()
                    id = nextId
                    state(ReplicatePredictionState.PREDICTION_SUBMITTED)
                    when (prediction.optString("status")) {
                        "succeeded" -> {
                            terminal = true
                            state(ReplicatePredictionState.PROVIDER_SUCCEEDED)
                            val output = prediction.opt("output")
                            val url = when (output) {
                                is String -> output
                                is JSONArray -> if (output.length() == 1) output.optString(0) else ""
                                is JSONObject -> if (model == ReplicateEditModel.SEEDREAM_5_PRO && output.optJSONArray("layers")?.length() == 0 && output.optJSONArray("images")?.length() == 1)
                                    output.getJSONArray("images").optString(0) else ""
                                else -> ""
                            }
                            if (!AiRemoteUrls.output(url)) invalid()
                            val response = try { send("GET", url, token, maxBytes = 16 * 1024 * 1024) }
                            catch (failure: AiNetworkFailure) { state(ReplicatePredictionState.OUTPUT_DOWNLOAD_TIMEOUT)
                                throw ReplicatePredictionFailure(ReplicatePredictionState.OUTPUT_DOWNLOAD_TIMEOUT,
                                    "Replicate finished the edit, but the image download was interrupted. No new prediction was requested.") }
                            if (response.contentType?.substringBefore(';')?.lowercase() !in setOf("image/png", "image/jpeg", "image/webp") || response.bytes.isEmpty()) {
                                response.bytes.fill(0); invalid()
                            }
                            state(ReplicatePredictionState.OUTPUT_RECEIVED)
                            return response.bytes
                        }
                        "failed", "canceled", "aborted" -> { terminal = true
                            val cancelled = prediction.optString("status") != "failed"
                            state(if (cancelled) ReplicatePredictionState.PROVIDER_CANCELLED else ReplicatePredictionState.PROVIDER_FAILED)
                            throw ReplicatePredictionFailure(if (cancelled) ReplicatePredictionState.PROVIDER_CANCELLED else ReplicatePredictionState.PROVIDER_FAILED,
                                if (cancelled) "Replicate cancelled this edit." else "Replicate failed to complete this edit.") }
                        "starting", "processing" -> {
                            state(ReplicatePredictionState.PROVIDER_STILL_PROCESSING)
                            delay(pollMillis)
                            try { polls++; prediction = predictionStatus(nextId,token) }
                            catch (failure: AiNetworkFailure) {
                                state(if (failure.timedOut) ReplicatePredictionState.POLL_TIMEOUT else ReplicatePredictionState.POLL_NETWORK_FAILURE)
                                // A failed GET says nothing about provider execution. Continue the same prediction.
                                delay((pollMillis.coerceAtLeast(500L) * 2).coerceAtMost(10_000L))
                            }
                        }
                        else -> invalid()
                    }
                }
        } finally {
            // Only an explicit cancellation requests remote cancellation. A transport failure must
            // never turn into another paid POST or silently cancel an accepted prediction.
            if (!terminal && id != null && !currentCoroutineContext().isActive) withContext(NonCancellable) {
                try { withTimeout(3000) { send("POST", "https://api.replicate.com/v1/predictions/$id/cancel", token).bytes.fill(0) } }
                catch (_: Exception) { /* Best effort, one prediction only. */ }
            }
        }
    }
    private fun makeRequest(method:String,url:String,token:ByteArray,body:AiRequestBody?=null,maxBytes:Int=2*1024*1024):AiHttpRequest {
        val output = AiRemoteUrls.output(url)
        val headers = mutableMapOf("Accept" to if (output) "image/png,image/jpeg,image/webp" else "application/json")
        if (!output) headers["Authorization"] = "Bearer ${token.toString(Charsets.US_ASCII)}"
        if (body != null) headers["Content-Type"] = "application/json"
        return AiHttpRequest(method,url,headers,body,maxBytes)
    }
    private suspend fun send(method:String,url:String,token:ByteArray,body:AiRequestBody?=null,maxBytes:Int=2*1024*1024):AiHttpResponse {
        val response=transport.execute(makeRequest(method,url,token,body,maxBytes))
        checkResponse(response,method,url,maxBytes);return response
    }
    private fun checkResponse(response:AiHttpResponse,method:String,url:String,maxBytes:Int) {
        val output=AiRemoteUrls.output(url)

        if (response.status !in 200..299 || response.bytes.size > maxBytes) {
            response.bytes.fill(0)
            if (method == "GET" && !output && (response.status == 408 || response.status == 429 || response.status in 500..599))
                throw AiNetworkFailure(response.status == 408)
            throw AiEditFailure(when (response.status) {
                401, 403 -> "Replicate did not accept this token."
                402 -> "Replicate needs account credit before this edit."
                404 -> "This Replicate model is unavailable."
                429 -> "Replicate is busy. Try again shortly."
                else -> "Replicate could not complete this edit."
            })
        }
    }
    private suspend fun predictionStatus(id:String,token:ByteArray):JSONObject {
        val url="https://api.replicate.com/v1/predictions/$id"
        var parsed:JSONObject?=null
        transport.consumePredictionStatus(id,makeRequest("GET",url,token)) {response->
            checkResponse(response,"GET",url,2*1024*1024)
            parsed=parse(response)
        }
        return checkNotNull(parsed)
    }
    private fun parse(response: AiHttpResponse): JSONObject = try {
        if (response.contentType?.substringBefore(';')?.lowercase() != "application/json") invalid()
        JSONObject(response.bytes.toString(Charsets.UTF_8))
    } catch (_: Exception) { invalid() } finally { response.bytes.fill(0) }
    private fun invalid(): Nothing = throw AiEditFailure("Replicate returned an invalid result.")
}
