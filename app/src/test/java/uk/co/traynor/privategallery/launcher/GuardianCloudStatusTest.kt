package uk.co.traynor.privategallery.launcher

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class GuardianCloudStatusTest {
    @Test
    fun statusUsesSameHttpsOriginAndRejectsUserInfo() {
        val endpoint = cloudTeacherEndpoint("https://gateway.example:8443/guardian/analyze")
        assertEquals("https://gateway.example:8443/guardian/status", cloudTeacherStatusEndpoint(endpoint).toString())
        assertThrows(IllegalArgumentException::class.java) {
            cloudTeacherEndpoint("https://owner@gateway.example/guardian/analyze")
        }
    }

    @Test
    fun payloadRequiresApprovalAndContainsOnlyTheReviewedText() {
        assertThrows(IllegalArgumentException::class.java) {
            cloudTeacherApprovedPayload("private original", false)
        }
        val payload = JSONObject(cloudTeacherApprovedPayload("[redacted] asked me to reply", true).decodeToString())
        assertEquals(setOf("message", "consent"), payload.keySet())
        assertEquals("[redacted] asked me to reply", payload.getString("message"))
        assertTrue(payload.getBoolean("consent"))
    }

    @Test
    fun stagingNeverDisplaysAnEnabledAnalysisState() {
        val status = JSONObject("""{"status":"authenticated","model":"staging","analysis_enabled":true,"budget":null}""")
        assertTrue(cloudTeacherStatusLines(status).contains("Staging · analysis disabled"))
    }

    @Test
    fun exhaustedBudgetReportsReservationsAndDeniesAnotherRequest() {
        val status = JSONObject("""{
          "status":"authenticated","model":"gpt-5-mini","analysis_enabled":true,
          "budget":{"configured_ceiling_micro_usd":37000000,"remaining_micro_usd":1000,
            "reserved_upper_bound_micro_usd":36999000,"request_reservation_micro_usd":102000,"expired":false,
            "reservation_activity_utc":{"daily_reserved_micro_usd":102000,"daily_requests":1,
              "monthly_reserved_micro_usd":204000,"monthly_requests":2}}
        }""")
        val lines = cloudTeacherStatusLines(status)
        assertTrue(lines.contains("Budget permits another request: No"))
        assertTrue(lines.any { it.startsWith("Today (UTC):") && it.endsWith("1 requests") })
        assertTrue(lines.any { it.startsWith("Month to date (UTC):") && it.endsWith("2 requests") })
        assertTrue(lines.any { it.contains("Confirmed OpenAI charges are unavailable") })
    }
}
