package uk.co.traynor.privategallery.core.editor

import java.io.FilterOutputStream
import java.io.OutputStream
import java.util.Base64
import kotlinx.coroutines.*
import org.json.JSONObject

/** Official contract: docs/acceptance/2026-09-24-ai-provider-setup.md.
 * Authenticated account/model reads never generate an image or list previous predictions.
 * Input is inline: no public object storage or separate file upload. Moderation uses only the documented, explicit owner opt-in.
 */
class ReplicateSeedreamApi(private val transport: AiHttpTransport, private val pollMillis: Long = 1500) {
    suspend fun testConnection(token: ByteArray) = withTimeout(30_000) {
        transport.consumeVerification(makeRequest("GET", "$API/account", token)) {response->
            checkResponse(response,"GET",2*1024*1024)
            val account=json(response)
            if(account.optString("type") !in setOf("user","organization")||account.optString("username").isBlank())invalid()
        }
        transport.consumeVerification(makeRequest("GET", "$API/models/$MODEL", token)) {response->
            checkResponse(response,"GET",2*1024*1024)
            val model=json(response)
            if(model.optString("owner")!="bytedance"||model.optString("name")!="seedream-4.5")invalid()
        }
    }
    suspend fun edit(token: ByteArray, jpeg: ByteArray, prompt: String, relaxModeration: Boolean = false,
        observe: (ReplicatePredictionSnapshot) -> Unit = {}): ByteArray {
        if (jpeg.isEmpty() || jpeg.size > MAX_INLINE_BYTES) throw AiEditFailure("This image is too large for remote editing.")
        if (prompt.isBlank() || prompt.length > 4000) throw AiEditFailure("Describe your change in up to 4,000 characters.")
        var predictionId: String? = null
        var terminal = false
        var polls = 0
        val start = System.nanoTime()
        val observed = mutableListOf<ReplicatePredictionState>()
        fun state(value: ReplicatePredictionState) {
            if (observed.lastOrNull() != value && observed.size < 32) observed += value
            observe(ReplicatePredictionSnapshot(MODEL, "2K", value, predictionId, polls,
                (System.nanoTime() - start) / 1_000_000, observed.toList()))
        }
        try {
            state(ReplicatePredictionState.SUBMITTING)
            var prediction = try { json(request("POST", "$API/models/$MODEL/predictions", token, imageBody(jpeg,prompt,relaxModeration))) }
            catch (_: AiNetworkFailure) {
                state(ReplicatePredictionState.SUBMISSION_TIMEOUT)
                throw ReplicatePredictionFailure(ReplicatePredictionState.SUBMISSION_TIMEOUT,
                    "Could not confirm whether Replicate accepted this edit. Check your Replicate predictions before generating again.")
            }
            while (true) {
                currentCoroutineContext().ensureActive()
                val id = prediction.optString("id")
                if (!id.matches(Regex("[a-zA-Z0-9]{1,128}")) || (predictionId != null && predictionId != id)) invalid()
                predictionId = id
                state(ReplicatePredictionState.PREDICTION_SUBMITTED)
                when (prediction.optString("status")) {
                    "succeeded" -> {
                        terminal = true
                        state(ReplicatePredictionState.PROVIDER_SUCCEEDED)
                        val output = prediction.optJSONArray("output") ?: invalid()
                        if (output.length() != 1) invalid()
                        val url = output.optString(0)
                        if (!AiRemoteUrls.output(url)) invalid()
                        val response = try { request("GET", url, token, maxBytes = 16 * 1024 * 1024) }
                        catch (_: AiNetworkFailure) {
                            state(ReplicatePredictionState.OUTPUT_DOWNLOAD_TIMEOUT)
                            throw ReplicatePredictionFailure(ReplicatePredictionState.OUTPUT_DOWNLOAD_TIMEOUT,
                                "Replicate finished the edit, but the image download was interrupted. No new prediction was requested.")
                        }
                        if (response.contentType?.substringBefore(';')?.lowercase() !in setOf("image/png","image/jpeg","image/webp") || response.bytes.isEmpty()) {
                            response.bytes.fill(0); invalid()
                        }
                        state(ReplicatePredictionState.OUTPUT_RECEIVED)
                        return response.bytes // Caller validates decoded pixels, sanitizes and wipes.
                    }
                    "failed" -> { terminal = true; state(ReplicatePredictionState.PROVIDER_FAILED); throw ReplicatePredictionFailure(ReplicatePredictionState.PROVIDER_FAILED, "Replicate could not complete this edit. Try another instruction or check your account.") }
                    "canceled" -> { terminal = true; state(ReplicatePredictionState.PROVIDER_CANCELLED); throw ReplicatePredictionFailure(ReplicatePredictionState.PROVIDER_CANCELLED, "Replicate canceled this edit. Try again.") }
                    "starting", "processing" -> { delay(pollMillis)
                        state(ReplicatePredictionState.PROVIDER_STILL_PROCESSING)
                        try { polls++; prediction = json(request("GET", "$API/predictions/$id", token)) }
                        catch (failure: AiNetworkFailure) {
                            state(if (failure.timedOut) ReplicatePredictionState.POLL_TIMEOUT else ReplicatePredictionState.POLL_NETWORK_FAILURE)
                            delay((pollMillis.coerceAtLeast(500L) * 2).coerceAtMost(10_000L))
                        }
                    }
                    "aborted" -> { terminal = true; state(ReplicatePredictionState.PROVIDER_CANCELLED); throw ReplicatePredictionFailure(ReplicatePredictionState.PROVIDER_CANCELLED, "Replicate cancelled this edit before it started.") }
                    else -> invalid()
                }
            }
        } finally {
            // Only an explicit local cancellation requests remote cancellation.
            if (!terminal && predictionId != null && !currentCoroutineContext().isActive) withContext(NonCancellable) {
                try { withTimeout(3000) { request("POST", "$API/predictions/$predictionId/cancel", token).bytes.fill(0) } } catch (_: Exception) { }
            }
        }
    }
    private fun makeRequest(method: String, url: String, token: ByteArray, body: AiRequestBody? = null, maxBytes: Int = 2 * 1024 * 1024): AiHttpRequest {
        if (token.isEmpty() || token.size > 8192 || token.any { (it.toInt() and 255) !in 33..126 }) throw AiEditFailure("Enter a valid Replicate API token.")
        val output = AiRemoteUrls.output(url)
        val headers = mutableMapOf("Accept" to if (output) "image/png,image/jpeg,image/webp" else "application/json")
        if (!output) headers["Authorization"] = "Bearer ${token.toString(Charsets.US_ASCII)}"
        if (body != null) headers["Content-Type"] = "application/json"
        return AiHttpRequest(method,url,headers,body,maxBytes)
    }
    private suspend fun request(method: String, url: String, token: ByteArray, body: AiRequestBody? = null, maxBytes: Int = 2 * 1024 * 1024): AiHttpResponse {
        val response=transport.execute(makeRequest(method,url,token,body,maxBytes))
        checkResponse(response,method,maxBytes)
        return response
    }
    private fun checkResponse(response:AiHttpResponse,method:String,maxBytes:Int) {
        if (response.status !in 200..299) {
            response.bytes.fill(0)
            if (method == "GET" && (response.status == 408 || response.status == 429 || response.status in 500..599))
                throw AiNetworkFailure(response.status == 408)
            throw AiEditFailure(when (response.status) {
                401,403 -> "Replicate did not accept this token. Check its validity and permissions."
                402 -> "Replicate needs account credit before this edit can run."
                429 -> "Replicate is busy. Wait a moment and try again."
                404 -> "Seedream 4.5 is unavailable for this account."
                else -> "Replicate is unavailable. Try again later."
            })
        }
        if (response.bytes.size > maxBytes) { response.bytes.fill(0); invalid() }
    }
    private fun json(response: AiHttpResponse): JSONObject = try {
        if (response.contentType?.substringBefore(';')?.lowercase() != "application/json") invalid()
        JSONObject(response.bytes.toString(Charsets.UTF_8))
    } catch (_: Exception) { invalid() } finally { response.bytes.fill(0) }
    private fun imageBody(jpeg: ByteArray, prompt: String, relaxModeration: Boolean) = AiRequestBody { output ->
        val moderation = if (relaxModeration) ",\"disable_safety_checker\":true" else ""
        val prefix = "{\"input\":{\"prompt\":" + JSONObject.quote(prompt) + moderation + ",\"size\":\"2K\",\"aspect_ratio\":\"match_input_image\",\"sequential_image_generation\":\"disabled\",\"max_images\":1,\"image_input\":[\"data:image/jpeg;base64,"
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
