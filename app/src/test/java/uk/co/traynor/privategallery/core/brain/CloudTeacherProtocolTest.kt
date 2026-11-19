package uk.co.traynor.privategallery.core.brain

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class CloudTeacherProtocolTest {
    private fun status() = JSONObject("""{"status":"authenticated","model":"gpt-5-mini","analysis_enabled":true,
      "budget":{"funded_allowance_micro_usd":1000000,"configured_spending_limit_micro_usd":250000,
      "configured_ceiling_micro_usd":250000,"remaining_micro_usd":250000,"request_reservation_micro_usd":102000,
      "monthly_spending_limit_micro_usd":100000000,"monthly_exposure_micro_usd":0,
      "monthly_remaining_micro_usd":100000000,"usage_reported_cost_micro_usd":0,
      "approved_requests_remaining":1,"paid_requests_halted":false,"expired":false}}""")
    private fun lesson() = JSONObject("""{"assessment":"UNCERTAIN","patterns":["BOUNDARY_PRESSURE"],"uncertainty":"HIGH","summary":"Context is missing."}""")

    @Test fun authenticatedBudgetUsesExactServerMicrosAndNeverClaimsProviderBalance() {
        val parsed = CloudTeacherStatus.parse(status())
        assertTrue(parsed.canAnalyze)
        assertTrue(parsed.lines().contains("Monthly maximum (UTC): \$100.000000"))
        assertTrue(parsed.lines().contains("Remaining local allowance: \$0.250000"))
        assertTrue(parsed.lines().any { it.contains("not verified OpenAI billing") })
    }
    @Test fun stagingAndLegacyIncompleteBudgetNeverPermitPaidDispatch() {
        val staged = JSONObject("""{"status":"authenticated","model":"staging","analysis_enabled":true,"budget":null}""")
        val parsed = CloudTeacherStatus.parse(staged)
        assertFalse(parsed.analysisEnabled)
        assertFalse(parsed.canAnalyze)
        assertTrue(parsed.lines().any { it.contains("Unavailable") })
        val legacy = status().apply { getJSONObject("budget").remove("monthly_spending_limit_micro_usd") }
        assertFalse(CloudTeacherStatus.parse(legacy).canAnalyze)
    }
    @Test fun exhaustionHaltExpiryAndSingleRequestLimitAllDisablePaidRequests() {
        for ((key, value) in listOf("expired" to true, "paid_requests_halted" to true,
            "approved_requests_remaining" to 0, "monthly_remaining_micro_usd" to 100,
            "monthly_spending_limit_micro_usd" to 100000001)) {
            val body = status().apply { getJSONObject("budget").put(key,value) }
            assertFalse(key,CloudTeacherStatus.parse(body).canAnalyze)
        }
    }
    @Test fun malformedMoneyAndInventedRemainingAllowanceCannotAuthorizeSpending() {
        for (value in listOf(-1, 1.5, "250000", true)) {
            val body = status().apply { getJSONObject("budget").put("remaining_micro_usd",value) }
            assertThrows(IllegalArgumentException::class.java) { CloudTeacherStatus.parse(body) }
        }
        val body = status().apply { getJSONObject("budget").put("remaining_micro_usd",999999) }
        assertFalse(CloudTeacherStatus.parse(body).canAnalyze)
    }
    @Test fun perRequestPayloadContainsOnlyTheApprovedExcerptAndIdempotencyFields() {
        val identity=UUID.randomUUID().toString()
        assertThrows(IllegalArgumentException::class.java) { approvedCloudPayload("private original",false,identity) }
        val payload=JSONObject(approvedCloudPayload("[redacted] approved excerpt",true,identity).decodeToString())
        assertEquals(setOf("message","consent","request_id","protocol_version"),payload.keys().asSequence().toSet())
        assertEquals("[redacted] approved excerpt",payload.getString("message"))
        assertEquals(identity,payload.getString("request_id"))
        assertTrue(payload.getBoolean("consent"))
        assertEquals(2,payload.getInt("protocol_version"))
    }
    @Test fun structuredAdviceIsAPredictionAndMustMatchItsExactRequest() {
        val identity=UUID.randomUUID().toString()
        val body=JSONObject().put("source","model_prediction").put("request_id",identity)
            .put("provider_billing_verified",false).put("lesson",lesson()).put("usage_cost_micro_usd",338)
        val parsed=CloudLesson.parseResponse(body,identity)
        assertEquals("UNCERTAIN",parsed.assessment)
        assertEquals(338L,parsed.usageCost)
        assertEquals(parsed,CloudLesson.fromStored(parsed.json()))
        assertFalse(parsed.toString().contains("Context"))
        assertThrows(IllegalArgumentException::class.java) { CloudLesson.parseResponse(body,UUID.randomUUID().toString()) }
        body.put("source","verified_fact")
        assertThrows(IllegalArgumentException::class.java) { CloudLesson.parseResponse(body,identity) }
    }
    @Test fun unstructuredClaimsExtraFieldsAndUnknownPatternsAreRejected() {
        val diagnosis=lesson().put("diagnosis","unsupported")
        assertThrows(IllegalArgumentException::class.java) { CloudLesson.parseLesson(diagnosis,null) }
        val unknown=lesson().put("patterns",org.json.JSONArray(listOf("invented")))
        assertThrows(IllegalArgumentException::class.java) { CloudLesson.parseLesson(unknown,null) }
        val duplicate=lesson().put("patterns",org.json.JSONArray(listOf("INSULT","INSULT")))
        assertThrows(IllegalArgumentException::class.java) { CloudLesson.parseLesson(duplicate,null) }
    }
}
