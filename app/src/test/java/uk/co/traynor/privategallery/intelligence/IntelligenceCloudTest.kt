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
}
