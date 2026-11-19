package uk.co.traynor.privategallery.core.brain

import java.io.IOException
import java.net.URI
import java.util.concurrent.CancellationException
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLException
import org.json.JSONObject

internal enum class CloudFailure(val userMessage: String, val mayHaveCharged: Boolean) {
    AUTHENTICATION("Authentication rejected · check the saved Nova Brain connection", false),
    DISABLED("Cloud analysis is disabled · local Guardian remains available", false),
    BUDGET("The server budget or approved request limit does not permit another request", false),
    MONTHLY_LIMIT("The server monthly spending limit has been reached · local protection continues", false),
    APPROVED_LIMIT("The approved cloud-request count is exhausted · owner budget review is required", false),
    EXPIRED("The server-funded allowance has expired · local protection continues", false),
    ALREADY_RESERVED("This request was already reserved or processed. No second paid call was made; its earlier outcome may have been charged", true),
    RATE_LIMIT("Server is busy · no automatic paid retry will occur", false),
    OFFLINE("Server unavailable · local protection continues", false),
    UNKNOWN_OUTCOME("The paid outcome is uncertain. Its reservation remains held; retrying the same request cannot charge again", true),
    INVALID_RESPONSE("The backend response could not be verified. Local protection continues; the request may have been charged", true),
    INVALID_STATUS("Authenticated backend status could not be verified · no analysis request was sent", false),
    PRICING_REVIEW("Paid requests are halted pending server budget or pricing review", true),
    SECURE_CONNECTION("Secure connection failed · certificate validation was not bypassed", true),
    SECURE_STATUS("Secure status connection failed · certificate validation was not bypassed; no analysis was sent", false),
}
internal class CloudTeacherException(val failure: CloudFailure) : IOException(failure.userMessage)
internal data class CloudReply(val lesson: CloudLesson, val status: CloudTeacherStatus) {
    override fun toString() = "CloudReply(redacted)"
}
internal fun interface CloudTeacherTransport {
    fun execute(endpoint: URI, token: String, payload: ByteArray?): BrainResponse
}

/** Approved requests use the same server ledger. The transport never retries a paid POST. */
internal class CloudTeacherClient(private val transport: CloudTeacherTransport = CloudTeacherTransport { endpoint, token, payload ->
    cloudRequest(endpoint, token, payload)
}) {
    fun status(configuration: NovaBrainConfiguration): CloudTeacherStatus {
        val response = execute(configuration, null, false)
        return try { CloudTeacherStatus.parse(response) }
        catch (_: Exception) { throw CloudTeacherException(CloudFailure.INVALID_STATUS) }
    }
    fun analyze(configuration: NovaBrainConfiguration, message: String, approved: Boolean, requestId: String,
        status: CloudTeacherStatus, permission: () -> Boolean, cancelled: () -> Boolean = { false },
        automatic: Boolean = false): CloudReply {
        if (cancelled()) throw CancellationException()
        if (!status.canAnalyze || automatic && !status.automaticAllowed || !permission()) throw CloudTeacherException(CloudFailure.DISABLED)
        val payload = approvedCloudPayload(message, approved, requestId, automatic)
        try {
            if (cancelled()) throw CancellationException()
            if (!permission()) throw CloudTeacherException(CloudFailure.DISABLED)
            val response = execute(configuration, payload, true)
            if (cancelled()) throw CancellationException()
            return try { CloudReply(CloudLesson.parseResponse(response, requestId), CloudTeacherStatus.parse(response)) }
            catch (_: Exception) { throw CloudTeacherException(CloudFailure.INVALID_RESPONSE) }
        } finally { payload.fill(0) }
    }
    private fun execute(configuration: NovaBrainConfiguration, payload: ByteArray?, paid: Boolean): JSONObject {
        val endpoint = URI(configuration.origin).resolve(if (paid) "/guardian/analyze" else "/guardian/status")
        val response = try { transport.execute(endpoint, configuration.token, payload) }
        catch (_: SSLException) { throw CloudTeacherException(if (paid) CloudFailure.SECURE_CONNECTION else CloudFailure.SECURE_STATUS) }
        catch (error: CancellationException) { throw error }
        catch (_: IOException) { throw CloudTeacherException(if (paid) CloudFailure.UNKNOWN_OUTCOME else CloudFailure.OFFLINE) }
        catch (_: Exception) { throw CloudTeacherException(if (paid) CloudFailure.INVALID_RESPONSE else CloudFailure.INVALID_STATUS) }
        try {
            if (response.body.size > 32768) throw CloudTeacherException(if (paid) CloudFailure.INVALID_RESPONSE else CloudFailure.INVALID_STATUS)
            if (response.code != 200) {
                val reason = runCatching { JSONObject(response.body.decodeToString()).optString("error") }.getOrDefault("")
                val failure = when {
                    response.code in setOf(401,403) -> CloudFailure.AUTHENTICATION
                    response.code == 402 && reason == "monthly_limit_exhausted" -> CloudFailure.MONTHLY_LIMIT
                    response.code == 402 && reason == "approved_request_limit_exhausted" -> CloudFailure.APPROVED_LIMIT
                    response.code == 402 && reason == "expired" -> CloudFailure.EXPIRED
                    reason == "halted" -> CloudFailure.PRICING_REVIEW
                    response.code == 402 -> CloudFailure.BUDGET
                    response.code == 409 -> CloudFailure.ALREADY_RESERVED
                    response.code == 429 -> CloudFailure.RATE_LIMIT
                    reason in setOf("analysis_disabled", "automatic_analysis_disabled") -> CloudFailure.DISABLED
                    reason in setOf("pricing_verification_required","budget_reconciliation_required") -> CloudFailure.PRICING_REVIEW
                    paid -> CloudFailure.UNKNOWN_OUTCOME
                    else -> CloudFailure.OFFLINE
                }
                throw CloudTeacherException(failure)
            }
            return try { JSONObject(response.body.decodeToString()) }
            catch (_: Exception) { throw CloudTeacherException(if (paid) CloudFailure.INVALID_RESPONSE else CloudFailure.INVALID_STATUS) }
        } finally { response.body.fill(0) }
    }
}

internal fun cloudRequest(endpoint: URI, token: String, payload: ByteArray?,
    open: (java.net.URL) -> java.net.URLConnection = { it.openConnection() },
    beforeBody: () -> Unit = {},
): BrainResponse {
    require(endpoint.scheme == "https" && endpoint.host != null && endpoint.rawUserInfo == null &&
        endpoint.rawQuery == null && endpoint.rawFragment == null &&
        endpoint.rawPath == (if (payload == null) "/guardian/status" else "/guardian/analyze"))
    require(payload == null || payload.size in 1..32768)
    val connection = open(endpoint.toURL()) as HttpsURLConnection
    try {
        connection.instanceFollowRedirects = false
        connection.connectTimeout = 8000
        connection.readTimeout = 20000
        connection.useCaches = false
        connection.setRequestProperty("Authorization", "Bearer $token")
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("Cache-Control", "no-store")
        if (payload != null) {
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.setFixedLengthStreamingMode(payload.size)
            connection.outputStream.use {
                beforeBody()
                it.write(payload)
            }
        }
        val code = connection.responseCode
        require(connection.contentLengthLong <= 32768)
        val bytes = (if (code == 200) connection.inputStream else connection.errorStream)?.use { it.readNBytes(32769) } ?: byteArrayOf()
        require(bytes.size <= 32768)
        return BrainResponse(code, bytes)
    } finally { connection.disconnect() }
}
