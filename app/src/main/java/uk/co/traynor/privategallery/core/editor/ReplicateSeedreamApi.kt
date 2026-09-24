package uk.co.traynor.privategallery.core.editor

import java.io.FilterOutputStream
import java.io.OutputStream
import java.util.Base64
import kotlinx.coroutines.*
import org.json.JSONObject

/** Official contract: docs/acceptance/2026-09-24-ai-provider-setup.md.
 * Authenticated account/model reads never generate an image or list previous predictions.
 * Input is inline: no public object storage or separate file upload. No safety overrides.
 */
class ReplicateSeedreamApi(private val transport: AiHttpTransport, private val pollMillis: Long = 1500) {
    suspend fun testConnection(token: ByteArray) = withTimeout(30_000) {
        val account = json(request("GET", "$API/account", token))
        if (account.optString("type") !in setOf("user","organization") || account.optString("username").isBlank()) invalid()
        val model = json(request("GET", "$API/models/$MODEL", token))
        if (model.optString("owner") != "bytedance" || model.optString("name") != "seedream-4.5") invalid()
    }
    suspend fun edit(token: ByteArray, jpeg: ByteArray, prompt: String): ByteArray {
        if (jpeg.isEmpty() || jpeg.size > MAX_INLINE_BYTES) throw AiEditFailure("This image is too large for remote editing.")
        if (prompt.isBlank() || prompt.length > 4000) throw AiEditFailure("Describe your change in up to 4,000 characters.")
        var predictionId: String? = null
        var terminal = false
        try {
            var prediction = json(request("POST", "$API/models/$MODEL/predictions", token, imageBody(jpeg,prompt)))
            while (true) {
                currentCoroutineContext().ensureActive()
                val id = prediction.optString("id")
                if (!id.matches(Regex("[a-zA-Z0-9]{1,128}")) || (predictionId != null && predictionId != id)) invalid()
                predictionId = id
                when (prediction.optString("status")) {
                    "succeeded" -> {
                        terminal = true
                        val output = prediction.optJSONArray("output") ?: invalid()
                        if (output.length() != 1) invalid()
                        val url = output.optString(0)
                        if (!AiRemoteUrls.output(url)) invalid()
                        val response = request("GET", url, token, maxBytes = 16 * 1024 * 1024)
                        if (response.contentType?.substringBefore(';')?.lowercase() !in setOf("image/png","image/jpeg","image/webp") || response.bytes.isEmpty()) {
                            response.bytes.fill(0); invalid()
                        }
                        return response.bytes // Caller validates decoded pixels, sanitizes and wipes.
                    }
                    "failed" -> { terminal = true; throw AiEditFailure("Replicate could not complete this edit. Try another instruction or check your account.") }
                    "canceled" -> { terminal = true; throw AiEditFailure("Replicate canceled this edit. Try again.") }
                    "starting", "processing" -> { delay(pollMillis); prediction = json(request("GET", "$API/predictions/$id", token)) }
                    else -> invalid()
                }
            }
        } finally {
            // Best effort only. Server deadline also bounds jobs if the process/network disappears.
            if (!terminal && predictionId != null) withContext(NonCancellable) {
                try { withTimeout(3000) { request("POST", "$API/predictions/$predictionId/cancel", token).bytes.fill(0) } } catch (_: Exception) { }
            }
        }
    }
    private suspend fun request(method: String, url: String, token: ByteArray, body: AiRequestBody? = null, maxBytes: Int = 2 * 1024 * 1024): AiHttpResponse {
        if (token.isEmpty() || token.size > 8192 || token.any { (it.toInt() and 255) !in 33..126 }) throw AiEditFailure("Enter a valid Replicate API token.")
        val headers = mutableMapOf("Authorization" to "Bearer ${token.toString(Charsets.US_ASCII)}", "Accept" to if (AiRemoteUrls.output(url)) "image/png,image/jpeg,image/webp" else "application/json")
        if (body != null) { headers["Content-Type"] = "application/json"; headers["Cancel-After"] = "90s" }
        val response = transport.execute(AiHttpRequest(method,url,headers,body,maxBytes))
        if (response.status !in 200..299) {
            response.bytes.fill(0)
            throw AiEditFailure(when (response.status) {
                401,403 -> "Replicate did not accept this token. Check its validity and permissions."
                402 -> "Replicate needs account credit before this edit can run."
                429 -> "Replicate is busy. Wait a moment and try again."
                404 -> "Seedream 4.5 is unavailable for this account."
                else -> "Replicate is unavailable. Try again later."
            })
        }
        if (response.bytes.size > maxBytes) { response.bytes.fill(0); invalid() }
        return response
    }
    private fun json(response: AiHttpResponse): JSONObject = try {
        if (response.contentType?.substringBefore(';')?.lowercase() != "application/json") invalid()
        JSONObject(response.bytes.toString(Charsets.UTF_8))
    } catch (_: Exception) { invalid() } finally { response.bytes.fill(0) }
    private fun imageBody(jpeg: ByteArray, prompt: String) = AiRequestBody { output ->
        val prefix = "{\"input\":{\"prompt\":" + JSONObject.quote(prompt) + ",\"size\":\"2K\",\"aspect_ratio\":\"match_input_image\",\"sequential_image_generation\":\"disabled\",\"max_images\":1,\"image_input\":[\"data:image/jpeg;base64,"
        output.write(prefix.toByteArray(Charsets.UTF_8))
        // Closing the encoder emits padding without closing the HTTP body before the JSON suffix.
        Base64.getEncoder().wrap(object : FilterOutputStream(output) { override fun close() { flush() } }).use { it.write(jpeg) }
        output.write("\"]}}".toByteArray(Charsets.US_ASCII))
    }
    private fun invalid(): Nothing = throw AiEditFailure("Replicate returned an invalid response. Try again.")
    companion object {
        const val MODEL = "bytedance/seedream-4.5"
        const val API = "https://api.replicate.com/v1"
        const val MAX_INLINE_BYTES = 900_000
    }
}
