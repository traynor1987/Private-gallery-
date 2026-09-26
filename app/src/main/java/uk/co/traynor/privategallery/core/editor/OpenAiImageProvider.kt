package uk.co.traynor.privategallery.core.editor

import android.content.Context
import java.io.OutputStream
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.*
import org.json.JSONObject

enum class OpenAiImageModel(val id: String, val label: String) {
    FLARE("gpt-image-2.5-flare", "GPT Image 2.5 Flare"),
    SUNBURST("gpt-image-2.5-sunburst", "GPT Image 2.5 Sunburst");
    companion object { fun parse(value: String?) = entries.firstOrNull { it.name == value } ?: FLARE }
}
enum class OpenAiImageQuality(val wire: String) {
    AUTO("auto"), LOW("low"), MEDIUM("medium"), HIGH("high"), XHIGH("xhigh"), MAX("max");
    companion object { fun parse(value: String?) = entries.firstOrNull { it.name == value } ?: AUTO }
}
enum class OpenAiImageModeration(val wire: String) { STANDARD("auto"), LOWER("low");
    companion object { fun parse(value: String?) = entries.firstOrNull { it.name == value } ?: STANDARD }
}

class OpenAiImagePreferences(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("openai_image_options", Context.MODE_PRIVATE)
    var model: OpenAiImageModel
        get() = OpenAiImageModel.parse(prefs.getString("model", null))
        set(value) { prefs.edit().putString("model", value.name).apply() }
    var quality: OpenAiImageQuality
        get() = OpenAiImageQuality.parse(prefs.getString("quality", null))
        set(value) { prefs.edit().putString("quality", value.name).apply() }
    var moderation: OpenAiImageModeration
        get() = OpenAiImageModeration.parse(prefs.getString("moderation", null))
        set(value) { prefs.edit().putString("moderation", value.name).apply() }
}

data class OpenAiImageUsage(val input: Long, val output: Long, val total: Long)
data class OpenAiImageResult(val bytes: ByteArray, val usage: OpenAiImageUsage?)

class OpenAiImageApi(private val transport: AiHttpTransport) {
    suspend fun testConnection(token: ByteArray, model: OpenAiImageModel) {
        val response = request("GET", "https://api.openai.com/v1/models/${model.id}", token)
        try {
            if (response.contentType?.substringBefore(';') != "application/json" ||
                JSONObject(response.bytes.toString(Charsets.UTF_8)).optString("id") != model.id)
                throw AiEditFailure("OpenAI model access could not be verified.")
        } finally { response.bytes.fill(0) }
    }

    suspend fun edit(token: ByteArray, image: ByteArray, prompt: String, model: OpenAiImageModel,
        quality: OpenAiImageQuality, moderation: OpenAiImageModeration, mask: ByteArray? = null): OpenAiImageResult {
        if (image.isEmpty() || image.size > 16 * 1024 * 1024) throw AiEditFailure("Image is too large for OpenAI editing.")
        if (prompt.isBlank() || prompt.length > 4000) throw AiEditFailure("Describe your change in up to 4,000 characters.")
        val boundary = "PrivateGallery${java.util.UUID.randomUUID().toString().replace("-", "")}"
        val body = AiRequestBody { output ->
            fun field(name: String, value: String) {
                output.write("--$boundary\r\nContent-Disposition: form-data; name=\"$name\"\r\n\r\n$value\r\n".toByteArray(Charsets.UTF_8))
            }
            field("model", model.id); field("prompt", prompt); field("quality", quality.wire)
            field("size", "auto"); field("moderation", moderation.wire); field("output_format", "png")
            output.write("--$boundary\r\nContent-Disposition: form-data; name=\"image[]\"; filename=\"source.png\"\r\nContent-Type: image/png\r\n\r\n".toByteArray(Charsets.US_ASCII))
            output.write(image)
            if (mask != null) {
                output.write("\r\n--$boundary\r\nContent-Disposition: form-data; name=\"mask\"; filename=\"mask.png\"\r\nContent-Type: image/png\r\n\r\n".toByteArray(Charsets.US_ASCII))
                output.write(mask)
            }
            output.write("\r\n--$boundary--\r\n".toByteArray(Charsets.US_ASCII))
        }
        val response = request("POST", "https://api.openai.com/v1/images/edits", token, body, "multipart/form-data; boundary=$boundary", 48 * 1024 * 1024)
        try {
            if (response.contentType?.substringBefore(';') != "application/json") invalid()
            val json = JSONObject(response.bytes.toString(Charsets.UTF_8))
            val data = json.optJSONArray("data") ?: invalid()
            if (data.length() != 1) invalid()
            val encoded = data.getJSONObject(0).optString("b64_json")
            if (encoded.isBlank() || encoded.length > 44 * 1024 * 1024) invalid()
            val bytes = try { Base64.getDecoder().decode(encoded) } catch (_: IllegalArgumentException) { invalid() }
            if (bytes.isEmpty() || bytes.size > AiEditPipeline.MAX_BYTES) { bytes.fill(0); invalid() }
            val usage = json.optJSONObject("usage")?.let {
                val input = it.optLong("input_tokens", -1); val output = it.optLong("output_tokens", -1); val total = it.optLong("total_tokens", -1)
                if (input >= 0 && output >= 0 && total >= 0) OpenAiImageUsage(input, output, total) else null
            }
            return OpenAiImageResult(bytes, usage)
        } catch (failure: AiEditFailure) { throw failure
        } catch (_: Exception) { invalid()
        } finally { response.bytes.fill(0) }
    }

    private suspend fun request(method: String, url: String, token: ByteArray, body: AiRequestBody? = null,
        contentType: String? = null, maxBytes: Int = 1024 * 1024): AiHttpResponse {
        if (token.isEmpty() || token.size > 8192 || token.any { (it.toInt() and 255) !in 33..126 }) throw AiEditFailure("Enter a valid OpenAI API key.")
        val headers = mutableMapOf("Authorization" to "Bearer ${token.toString(Charsets.US_ASCII)}", "Accept" to "application/json")
        contentType?.let { headers["Content-Type"] = it }
        val response = transport.execute(AiHttpRequest(method, url, headers, body, maxBytes))
        if (response.status !in 200..299) {
            response.bytes.fill(0)
            throw AiEditFailure(when (response.status) {
                401, 403 -> "OpenAI did not accept this API key or model access."
                402 -> "OpenAI account billing is required for this edit."
                429 -> "OpenAI rate limit reached. Try again later."
                400, 413, 415, 422 -> "OpenAI rejected this input or request."
                else -> "OpenAI is unavailable. Try again later."
            })
        }
        return response
    }
    private fun invalid(): Nothing = throw AiEditFailure("OpenAI returned an invalid image response.")
}

class OpenAiImageProvider(private val readCredential: () -> ByteArray?, private val api: OpenAiImageApi,
    private val options: OpenAiImagePreferences) : AiImageEditProvider {
    override val id = ID
    override val modelId get() = options.model.id
    override val displayName get() = "OpenAI · ${options.model.label}"
    override val capabilities = setOf(AiCapability.GENERATIVE_EDIT, AiCapability.OBJECT_REMOVAL, AiCapability.GENERATIVE_FILL)
    private val active = AtomicBoolean(true)
    private val jobs = ConcurrentHashMap.newKeySet<Job>()
    @Volatile var lastUsage: OpenAiImageUsage? = null
        private set
    override fun invalidate() { active.set(false); jobs.forEach { it.cancel() }; lastUsage = null }
    override suspend fun edit(request: AiEditRequest): ByteArray = coroutineScope {
        if (!active.get()) throw AiEditFailure("OpenAI configuration was removed.")
        if (request.parameters.capability !in capabilities || request.parameters.aspect != null)
            throw AiEditFailure("This provider does not support this edit.")
        if (request.parameters.capability != AiCapability.GENERATIVE_EDIT && request.parameters.strokes.isEmpty())
            throw AiEditFailure("Mark the area to edit first.")
        val job = currentCoroutineContext().job
        jobs.add(job)
        var token: ByteArray? = null
        var image: ByteArray? = null
        var mask: ByteArray? = null
        try {
            token = withContext(Dispatchers.IO) { readCredential() } ?: throw AiEditFailure("Set up OpenAI in AI editing settings.")
            if (request.parameters.strokes.isNotEmpty()) mask = withContext(Dispatchers.Default) { OpenAiMaskRenderer.render(request.image, request.parameters.strokes) }
            val result = withTimeout(90_000) { api.edit(token!!, request.image, request.parameters.prompt, options.model, options.quality, options.moderation, mask) }
            image = result.bytes
            ensureActive()
            lastUsage = result.usage
            result.bytes.also { image = null }
        } finally { jobs.remove(job); token?.fill(0); mask?.fill(0); image?.fill(0) }
    }
    companion object { const val ID = "openai-gpt-image-25" }
}

internal fun androidOpenAiConfiguration(context: Context): AiProviderConfiguration {
    val store = AiCredentialStore(context.applicationContext)
    val options = OpenAiImagePreferences(context)
    val api = OpenAiImageApi(PrivateAiHttpTransport())
    val credentials = object : AiCredentials {
        override fun isConfigured() = store.isConfigured(OpenAiImageProvider.ID)
        override fun read() = store.read(OpenAiImageProvider.ID)
        override fun save(credential: ByteArray) = store.save(OpenAiImageProvider.ID, credential)
        override fun clear() = store.clear(OpenAiImageProvider.ID)
    }
    return AiProviderConfiguration(credentials, { api.testConnection(it, options.model) }, "OpenAI") { read -> OpenAiImageProvider(read, api, options) }
}
