package uk.co.traynor.privategallery.intelligence

import java.io.IOException
import java.net.URI
import java.util.UUID
import java.util.concurrent.CancellationException
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLException
import org.json.JSONObject
import uk.co.traynor.privategallery.core.brain.BrainResponse
import uk.co.traynor.privategallery.core.brain.CloudFailure
import uk.co.traynor.privategallery.core.brain.CloudTeacherException
import uk.co.traynor.privategallery.core.brain.CloudTeacherStatus
import uk.co.traynor.privategallery.core.brain.NovaBrainConfiguration
import uk.co.traynor.privategallery.core.brain.approvedCloudPayload
import uk.co.traynor.privategallery.core.brain.cloudRequest

/** Manual Ask Nova uses the same authenticated status, consent format and server ledger as Nova Brain. */
internal class IntelligenceCloud(
    private val get: (NovaBrainConfiguration) -> BrainResponse = { configuration ->
        cloudRequest(configuration.endpoint("/guardian/status"), configuration.token, null)
    },
    private val post: (NovaBrainConfiguration, ByteArray, () -> Unit) -> BrainResponse = ::postIntelligenceQuestion,
) {
    data class Status(val assistantAvailable: Boolean, val budget: CloudTeacherStatus)

    fun status(configuration: NovaBrainConfiguration): Status = withResponse(paid = false, { get(configuration) }) {
        Status(it.opt("assistant_available") == true, CloudTeacherStatus.parse(it))
    }

    fun ask(
        configuration: NovaBrainConfiguration,
        question: String,
        approved: Boolean,
        requestId: String,
        status: Status,
        maySend: () -> Boolean,
    ): String {
        require(question.length in 1..600)
        if (!status.assistantAvailable || !status.budget.canAnalyze || !maySend()) {
            throw CloudTeacherException(CloudFailure.DISABLED)
        }
        val payload = approvedCloudPayload(question, approved, requestId)
        try {
            return withResponse(paid = true, {
                post(configuration, payload) { if (!maySend()) throw CancellationException() }
            }) { response ->
                if (!maySend()) throw CancellationException()
                require(response.getString("source") == "assistant_prediction" &&
                    response.getString("request_id") == UUID.fromString(requestId).toString() &&
                    response.get("provider_billing_verified") == false)
                CloudTeacherStatus.parse(response)
                response.getString("answer").trim().also { answer ->
                    require(answer.length in 1..4000 && answer.none { it.code < 32 && it !in "\n\t" })
                }
            }
        } finally { payload.fill(0) }
    }

    private fun <T> withResponse(paid: Boolean, fetch: () -> BrainResponse, parse: (JSONObject) -> T): T {
        val response = try { fetch() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: SSLException) {
            throw CloudTeacherException(if (paid) CloudFailure.SECURE_CONNECTION else CloudFailure.SECURE_STATUS)
        } catch (_: IOException) {
            throw CloudTeacherException(if (paid) CloudFailure.UNKNOWN_OUTCOME else CloudFailure.OFFLINE)
        }
        try {
            if (response.code != 200) {
                val reason = runCatching { JSONObject(response.body.decodeToString()).optString("error") }.getOrNull()
                val failure = when (response.code) {
                    401, 403 -> CloudFailure.AUTHENTICATION
                    402 -> CloudFailure.BUDGET
                    404 -> if (paid) CloudFailure.DISABLED else CloudFailure.INVALID_STATUS
                    409 -> CloudFailure.ALREADY_RESERVED
                    429 -> CloudFailure.RATE_LIMIT
                    else -> when (reason) {
                        "analysis_disabled" -> CloudFailure.DISABLED
                        "halted", "pricing_verification_required", "budget_reconciliation_required" -> CloudFailure.PRICING_REVIEW
                        else -> if (paid) CloudFailure.UNKNOWN_OUTCOME else CloudFailure.OFFLINE
                    }
                }
                throw CloudTeacherException(failure)
            }
            return try {
                require(response.body.size <= 32768)
                parse(JSONObject(response.body.decodeToString()))
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { throw CloudTeacherException(if (paid) CloudFailure.INVALID_RESPONSE else CloudFailure.INVALID_STATUS) }
        } finally { response.body.fill(0) }
    }
}

private fun postIntelligenceQuestion(
    configuration: NovaBrainConfiguration,
    payload: ByteArray,
    assertMaySend: () -> Unit,
): BrainResponse {
    val connection = URI(configuration.origin).resolve("/assistant/ask").toURL().openConnection() as HttpsURLConnection
    try {
        connection.instanceFollowRedirects = false
        connection.connectTimeout = 8000
        connection.readTimeout = 20000
        connection.useCaches = false
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.setRequestProperty("Authorization", "Bearer ${configuration.token}")
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        connection.setRequestProperty("Cache-Control", "no-store")
        connection.setFixedLengthStreamingMode(payload.size)
        assertMaySend()
        connection.outputStream.use {
            assertMaySend()
            it.write(payload)
        }
        val code = connection.responseCode
        if (connection.contentLengthLong > 32768) throw IOException("Oversized assistant response")
        val body = (if (code == 200) connection.inputStream else connection.errorStream)?.use {
            it.readNBytes(32769)
        } ?: byteArrayOf()
        if (body.size > 32768) throw IOException("Oversized assistant response")
        return BrainResponse(code, body)
    } finally { connection.disconnect() }
}
