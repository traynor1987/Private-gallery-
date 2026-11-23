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

internal class IntelligenceRouteUnavailable : IOException("The assistant route is unavailable on the configured Nova Brain server.")

/** Manual Ask Nova uses the same authenticated status, consent format and server ledger as Nova Brain. */
internal class IntelligenceCloud(
    private val get: (NovaBrainConfiguration) -> BrainResponse = { requestIntelligence(it) },
    private val post: (NovaBrainConfiguration, ByteArray, () -> Unit) -> BrainResponse = ::requestIntelligence,
) {
    data class Status(
        val assistantAvailable: Boolean,
        val budget: CloudTeacherStatus,
        val contextMaxChars: Int = IntelligenceCloudRequest.QUESTION_LIMIT,
    ) {
        val unavailableMessage: String?
            get() = when {
                !assistantAvailable -> "The Nova Brain VPS does not advertise cloud chat."
                budget.staging -> "Nova Brain is in staging. Paid cloud chat is disabled."
                !budget.analysisEnabled -> "Paid cloud analysis is disabled on the Nova Brain VPS."
                !budget.canAnalyze -> "The Nova Brain budget currently blocks paid requests."
                else -> null
            }
    }

    fun status(configuration: NovaBrainConfiguration): Status = withResponse(paid = false, { get(configuration) }) {
        val contextLimit = when (val advertised = it.opt("assistant_context_max_chars")) {
            null -> IntelligenceCloudRequest.QUESTION_LIMIT
            is Int -> advertised.also { limit ->
                require(limit in IntelligenceCloudRequest.QUESTION_LIMIT..IntelligenceCloudRequest.PAYLOAD_LIMIT)
            }
            else -> error("Invalid assistant context limit")
        }
        Status(it.opt("assistant_available") == true, CloudTeacherStatus.parse(it), contextLimit)
    }

    fun ask(
        configuration: NovaBrainConfiguration,
        question: String,
        approved: Boolean,
        requestId: String,
        status: Status,
        maySend: () -> Boolean,
    ): String {
        require(question.length in 1..status.contextMaxChars)
        if (status.unavailableMessage != null || !maySend()) {
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
            if (response.code == 404 && !paid) throw IntelligenceRouteUnavailable()
            if (response.code != 200) {
                val reason = runCatching { JSONObject(response.body.decodeToString()).optString("error") }.getOrNull()
                val failure = when {
                    response.code in setOf(401, 403) -> CloudFailure.AUTHENTICATION
                    response.code == 402 && reason == "monthly_limit_exhausted" -> CloudFailure.MONTHLY_LIMIT
                    response.code == 402 && reason == "approved_request_limit_exhausted" -> CloudFailure.APPROVED_LIMIT
                    response.code == 402 && reason == "expired" -> CloudFailure.EXPIRED
                    reason in setOf("halted", "pricing_verification_required", "budget_reconciliation_required") -> CloudFailure.PRICING_REVIEW
                    response.code == 402 -> CloudFailure.BUDGET
                    response.code == 404 && reason == "not_found" -> CloudFailure.DISABLED
                    response.code == 409 -> CloudFailure.ALREADY_RESERVED
                    response.code == 429 -> CloudFailure.RATE_LIMIT
                    reason == "analysis_disabled" -> CloudFailure.DISABLED
                    else -> if (paid) CloudFailure.UNKNOWN_OUTCOME else CloudFailure.OFFLINE
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

internal fun requestIntelligence(
    configuration: NovaBrainConfiguration,
    payload: ByteArray? = null,
    assertMaySend: () -> Unit = {},
): BrainResponse {
    val connection = URI(configuration.origin).resolve("/assistant/ask").toURL().openConnection() as HttpsURLConnection
    try {
        connection.instanceFollowRedirects = false
        connection.connectTimeout = 8000
        connection.readTimeout = 20000
        connection.useCaches = false
        connection.setRequestProperty("Authorization", "Bearer ${configuration.token}")
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("Cache-Control", "no-store")
        if (payload == null) {
            connection.readTimeout = 8000
            assertMaySend()
        } else {
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.setFixedLengthStreamingMode(payload.size)
            assertMaySend()
            connection.outputStream.use {
                assertMaySend()
                it.write(payload)
            }
        }
        assertMaySend()
        val code = connection.responseCode
        if (connection.contentLengthLong > 32768) throw IOException("Oversized assistant response")
        val body = (if (code == 200) connection.inputStream else connection.errorStream)?.use {
            it.readNBytes(32769)
        } ?: byteArrayOf()
        if (body.size > 32768) throw IOException("Oversized assistant response")
        return BrainResponse(code, body)
    } finally { connection.disconnect() }
}
