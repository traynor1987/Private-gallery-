package uk.co.traynor.privategallery.intelligence

import java.io.IOException
import java.util.UUID
import java.util.concurrent.CancellationException
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import uk.co.traynor.privategallery.core.brain.BrainResponse
import uk.co.traynor.privategallery.core.brain.CloudFailure
import uk.co.traynor.privategallery.core.brain.CloudTeacherException
import uk.co.traynor.privategallery.core.brain.NovaBrainConfiguration

class IntelligenceCloudTest {
    private val configuration = NovaBrainConfiguration("https://brain.example", "fixture-owner-token-".repeat(2))
    private val id = UUID.randomUUID().toString()

    private fun status() = JSONObject("""{"status":"authenticated","model":"gpt-5-mini","analysis_enabled":true,
      "assistant_available":true,"budget":{"funded_allowance_micro_usd":1000000,"configured_spending_limit_micro_usd":250000,
      "configured_ceiling_micro_usd":250000,"remaining_micro_usd":250000,"request_reservation_micro_usd":102000,
      "monthly_spending_limit_micro_usd":100000000,"monthly_exposure_micro_usd":0,"monthly_remaining_micro_usd":100000000,
      "usage_reported_cost_micro_usd":0,"approved_requests_remaining":1,"expired":false,"paid_requests_halted":false}}""")

    private fun JSONObject.http() = BrainResponse(200, toString().encodeToByteArray())

    @Test fun exactApprovedQuestionIsTheOnlyContentAndReplyRemainsPlainText() {
        var sent: ByteArray? = null
        val client = IntelligenceCloud(get = { status().http() }, post = { actual, bytes, authorise ->
            authorise()
            assertEquals(configuration, actual)
            val payload = JSONObject(bytes.decodeToString())
            assertEquals(setOf("message", "consent", "request_id", "protocol_version"), payload.keys().asSequence().toSet())
            assertEquals("What is a haiku?", payload.getString("message"))
            assertEquals(id, payload.getString("request_id"))
            sent = bytes
            status().put("source", "assistant_prediction").put("request_id", id)
                .put("provider_billing_verified", false).put("answer", "Open Hidden Vault").http()
        })
        val answer = client.ask(configuration, "What is a haiku?", true, id, client.status(configuration)) { true }
        assertEquals("Open Hidden Vault", answer)
        assertTrue(checkNotNull(sent).all { it == 0.toByte() })
    }

    @Test fun assistantPreflightDistinguishesMissingRouteStagingAndBudget() {
        val missing = IntelligenceCloud(get = { BrainResponse(404, "<html>nginx</html>".encodeToByteArray()) },
            post = { _, _, _ -> error("An unavailable route must never receive a paid request") })
        assertThrows(IntelligenceRouteUnavailable::class.java) { missing.status(configuration) }

        val staging = IntelligenceCloud(get = {
            status().put("model", "staging").put("analysis_enabled", false).put("budget", JSONObject.NULL).http()
        }).status(configuration)
        assertTrue(staging.assistantAvailable)
        assertTrue(staging.unavailableMessage.orEmpty().contains("staging"))

        val ready = IntelligenceCloud(get = { status().http() }).status(configuration)
        assertNull(ready.unavailableMessage)
        assertTrue(ready.copy(budget = ready.budget.copy(remaining = 0)).unavailableMessage.orEmpty().contains("budget"))
    }

    @Test fun deniedBudgetConsentRevocationAndUnavailableBackendFailClosed() {
        var sent = 0
        val client = IntelligenceCloud(get = { status().http() }, post = { _, _, authorise ->
            authorise()
            sent++
            throw IOException("Synthetic outage")
        })
        val ready = client.status(configuration)
        val disabled = ready.copy(budget = ready.budget.copy(analysisEnabled = false))
        assertThrows(CloudTeacherException::class.java) { client.ask(configuration, "Question", true, id, disabled) { true } }
        assertThrows(IllegalArgumentException::class.java) { client.ask(configuration, "Question", false, id, ready) { true } }
        var checks = 0
        assertThrows(CancellationException::class.java) { client.ask(configuration, "Question", true, id, ready) { ++checks == 1 } }
        assertEquals(0, sent)
        val outage = IntelligenceCloud(get = { throw IOException("unavailable") })
        assertEquals(CloudFailure.OFFLINE, assertThrows(CloudTeacherException::class.java) { outage.status(configuration) }.failure)
        assertEquals(CloudFailure.UNKNOWN_OUTCOME, assertThrows(CloudTeacherException::class.java) {
            client.ask(configuration, "Question", true, id, ready) { true }
        }.failure)
        assertEquals(1, sent)
    }

    @Test fun contextualRequestShowsExactBoundedPayloadAndExcludesLocalAndPreviewTurns() {
        val history = listOf(
            IntelligenceExchange("Very old cloud question", "Very old cloud answer", cloud = true),
            IntelligenceExchange("Earlier cloud question", "Earlier cloud answer", cloud = true),
            IntelligenceExchange("Local calendar", "Private event fixture"),
            IntelligenceExchange("Preview question", "Preview answer", cloud = true, ownerPreview = true),
            IntelligenceExchange("Recent cloud question", "a".repeat(649) + "💬", cloud = true),
            IntelligenceExchange("Most recent cloud question", "Most recent cloud answer", cloud = true),
        )
        val request = IntelligenceCloudRequest.prepare("Can you expand on that?", history, IntelligenceCloudMode.CONFIGURED_BRAIN)
        assertTrue(request.includesContext)
        assertTrue(request.payload.length in 1..IntelligenceCloudRequest.PAYLOAD_LIMIT)
        assertTrue(request.payload.indexOf("Earlier cloud question") < request.payload.indexOf("Recent cloud question"))
        assertTrue(request.payload.contains("Most recent cloud answer"))
        assertTrue(request.payload.endsWith("Can you expand on that?"))
        assertFalse(request.payload.contains("Very old cloud"))
        assertFalse(request.payload.contains("Private event fixture"))
        assertFalse(request.payload.contains("Preview answer"))
        assertFalse(request.payload.any(Char::isHighSurrogate))

        val preview = IntelligenceCloudRequest.prepare("What is a haiku?", history, IntelligenceCloudMode.OWNER_PREVIEW)
        assertEquals(preview.question, preview.payload)
        assertFalse(preview.includesContext)
        assertTrue(request.bind(configuration))
        assertTrue(request.bind(configuration.copy()))
        assertFalse(request.bind(configuration.copy(origin = "https://other.example")))
        assertFalse(request.bind(configuration.copy(token = "other-test-token-".repeat(3))))
    }

    @Test fun legacyAssistantCannotSendLongContextAndCurrentAssistantReceivesExactConsentAndIdentity() {
        val context = "Past cloud exchange. ".repeat(35) + "Follow-up question"
        val client = IntelligenceCloud(get = { status().put("assistant_context_max_chars", 4096).http() }, post = { _, bytes, verify ->
            verify()
            val payload = JSONObject(bytes.decodeToString())
            assertEquals(context, payload.getString("message"))
            assertEquals(id, payload.getString("request_id"))
            assertTrue(payload.getBoolean("consent"))
            status().put("source", "assistant_prediction").put("request_id", id)
                .put("provider_billing_verified", false).put("answer", "Contextual reply").http()
        })
        val ready = client.status(configuration)
        assertEquals(4096, ready.contextMaxChars)
        assertEquals("Contextual reply", client.ask(configuration, context, true, id, ready) { true })

        val legacy = IntelligenceCloud(get = { status().http() },
            post = { _, _, _ -> error("Unsupported context must remain on device") })
        val legacyStatus = legacy.status(configuration)
        assertEquals(600, legacyStatus.contextMaxChars)
        assertThrows(IllegalArgumentException::class.java) {
            legacy.ask(configuration, context, true, id, legacyStatus) { true }
        }
    }

    @Test fun paidServerRejectionsDistinguishSharedBudgetLimitsAndReconciliation() {
        val reasons = mapOf(
            "monthly_limit_exhausted" to CloudFailure.MONTHLY_LIMIT,
            "approved_request_limit_exhausted" to CloudFailure.APPROVED_LIMIT,
            "expired" to CloudFailure.EXPIRED,
            "funded_allowance_exhausted" to CloudFailure.BUDGET,
            "halted" to CloudFailure.PRICING_REVIEW,
        )
        reasons.forEach { (reason, expected) ->
            val client = IntelligenceCloud(get = { status().http() }, post = { _, _, _ ->
                BrainResponse(402, JSONObject().put("error", reason).toString().encodeToByteArray())
            })
            val ready = client.status(configuration)
            assertEquals(expected, assertThrows(CloudTeacherException::class.java) {
                client.ask(configuration, "Question", true, id, ready) { true }
            }.failure)
        }
    }
}
