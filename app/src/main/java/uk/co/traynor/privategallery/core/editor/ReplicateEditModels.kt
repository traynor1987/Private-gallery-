package uk.co.traynor.privategallery.core.editor

import java.util.Base64
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
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
    KONTEXT("FLUX Kontext Pro", "Photorealistic and creative transformation", "black-forest-labs/flux-kontext-pro", "≈$0.04/image",
        setOf(ReplicateEditCapability.IMAGE_TO_IMAGE, ReplicateEditCapability.PROMPT_EDIT, ReplicateEditCapability.SEED,
            ReplicateEditCapability.OUTPUT_FORMAT, ReplicateEditCapability.ASPECT_SIZE, ReplicateEditCapability.SAFETY_CONFIGURATION)),
    FILL("FLUX Fill Pro", "Replace or remove a selected area", "black-forest-labs/flux-fill-pro", "≈$0.05/image",
        setOf(ReplicateEditCapability.IMAGE_TO_IMAGE, ReplicateEditCapability.INPAINTING, ReplicateEditCapability.MASK,
            ReplicateEditCapability.PROMPT_EDIT, ReplicateEditCapability.GUIDANCE, ReplicateEditCapability.STEPS,
            ReplicateEditCapability.SEED, ReplicateEditCapability.OUTPUT_FORMAT, ReplicateEditCapability.SAFETY_CONFIGURATION));

    val tools: Set<AiCapability> get() = when (this) {
        SEEDREAM -> setOf(AiCapability.GENERATIVE_EDIT)
        KONTEXT -> setOf(AiCapability.GENERATIVE_EDIT, AiCapability.RESTYLE)
        FILL -> setOf(AiCapability.OBJECT_REMOVAL, AiCapability.GENERATIVE_FILL)
    }

    /** Only fields in the official model schema enter a paid prediction. */
    fun input(image: ByteArray, prompt: String, mask: ByteArray?, seed: Int?): JSONObject {
        if (image.isEmpty() || image.size > ReplicateSeedreamApi.MAX_INLINE_BYTES || prompt.isBlank() || prompt.length > 4000)
            throw AiEditFailure("Enter a prompt and use an image suitable for remote editing.")
        if ((this == FILL) != (mask != null)) throw AiEditFailure(if (this == FILL) "Mark the area to edit first." else "This model cannot use a selection mask.")
        if (mask != null && (mask.isEmpty() || mask.size > 4 * 1024 * 1024)) throw AiEditFailure("Selection mask is too large.")
        if (seed != null && ReplicateEditCapability.SEED !in features) throw AiEditFailure("This model does not support a seed.")
        val uri = "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(image)
        val value = JSONObject().put("prompt", prompt.trim())
        when (this) {
            SEEDREAM -> value.put("image_input", JSONArray().put(uri)).put("size", "2K")
                .put("aspect_ratio", "match_input_image").put("sequential_image_generation", "disabled").put("max_images", 1)
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
}

class ReplicateEditModelStore(context: android.content.Context) {
    private val prefs = context.getSharedPreferences("ai_replicate_edit_model", android.content.Context.MODE_PRIVATE)
    fun selected() = ReplicateEditModel.entries.firstOrNull { it.name == prefs.getString("model", null) } ?: ReplicateEditModel.SEEDREAM
    fun select(model: ReplicateEditModel) { prefs.edit().putString("model", model.name).apply() }
}

/** Same bounded transport, output allowlist, polling, cancellation and token as Seedream. */
class ReplicateModelEditApi(private val transport: AiHttpTransport, private val pollMillis: Long = 1500L) {
    suspend fun edit(token: ByteArray, model: ReplicateEditModel, image: ByteArray, prompt: String, mask: ByteArray?, seed: Int? = null): ByteArray {
        val input = model.input(image, prompt, mask, seed)
        if (token.isEmpty() || token.size > 8192 || token.any { (it.toInt() and 255) !in 33..126 }) throw AiEditFailure("Enter a valid Replicate API token.")
        var id: String? = null
        var terminal = false
        try {
            return withTimeout(90_000) {
                var prediction = parse(send("POST", "https://api.replicate.com/v1/models/${model.modelId}/predictions", token,
                    AiRequestBody { it.write(JSONObject().put("input", input).toString().toByteArray(Charsets.UTF_8)) }))
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val nextId = prediction.optString("id")
                    if (!nextId.matches(Regex("[a-zA-Z0-9]{1,128}")) || (id != null && nextId != id)) invalid()
                    id = nextId
                    when (prediction.optString("status")) {
                        "succeeded" -> {
                            terminal = true
                            val output = prediction.opt("output")
                            val url = when (output) {
                                is String -> output
                                is JSONArray -> if (output.length() == 1) output.optString(0) else ""
                                else -> ""
                            }
                            if (!AiRemoteUrls.output(url)) invalid()
                            val response = send("GET", url, token, maxBytes = 16 * 1024 * 1024)
                            if (response.contentType?.substringBefore(';')?.lowercase() !in setOf("image/png", "image/jpeg", "image/webp") || response.bytes.isEmpty()) {
                                response.bytes.fill(0); invalid()
                            }
                            return@withTimeout response.bytes
                        }
                        "failed", "canceled" -> { terminal = true; throw AiEditFailure("Replicate could not complete this edit.") }
                        "starting", "processing" -> {
                            delay(pollMillis)
                            prediction = parse(send("GET", "https://api.replicate.com/v1/predictions/$nextId", token))
                        }
                        else -> invalid()
                    }
                }
                @Suppress("UNREACHABLE_CODE") byteArrayOf()
            }
        } finally {
            if (!terminal && id != null) withContext(NonCancellable) {
                try { withTimeout(3000) { send("POST", "https://api.replicate.com/v1/predictions/$id/cancel", token).bytes.fill(0) } }
                catch (_: Exception) { /* Best effort, one prediction only. */ }
            }
        }
    }
    private suspend fun send(method: String, url: String, token: ByteArray, body: AiRequestBody? = null, maxBytes: Int = 2 * 1024 * 1024): AiHttpResponse {
        val output = AiRemoteUrls.output(url)
        val headers = mutableMapOf("Accept" to if (output) "image/png,image/jpeg,image/webp" else "application/json")
        if (!output) headers["Authorization"] = "Bearer ${token.toString(Charsets.US_ASCII)}"
        if (body != null) { headers["Content-Type"] = "application/json"; headers["Cancel-After"] = "90s" }
        val response = transport.execute(AiHttpRequest(method, url, headers, body, maxBytes))
        if (response.status !in 200..299 || response.bytes.size > maxBytes) {
            response.bytes.fill(0)
            throw AiEditFailure(when (response.status) {
                401, 403 -> "Replicate did not accept this token."
                402 -> "Replicate needs account credit before this edit."
                404 -> "This Replicate model is unavailable."
                429 -> "Replicate is busy. Try again shortly."
                else -> "Replicate could not complete this edit."
            })
        }
        return response
    }
    private fun parse(response: AiHttpResponse): JSONObject = try {
        if (response.contentType?.substringBefore(';')?.lowercase() != "application/json") invalid()
        JSONObject(response.bytes.toString(Charsets.UTF_8))
    } catch (_: Exception) { invalid() } finally { response.bytes.fill(0) }
    private fun invalid(): Nothing = throw AiEditFailure("Replicate returned an invalid result.")
}
