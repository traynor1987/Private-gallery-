package uk.co.traynor.privategallery.intelligence

import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CancellationException
import javax.net.ssl.SSLHandshakeException
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import uk.co.traynor.privategallery.core.brain.BrainResponse
import uk.co.traynor.privategallery.core.brain.CloudTeacherException

class OwnerPreviewClientTest {
    private val connection = OwnerPreviewConnection("a".repeat(64), UUID.randomUUID())
    private val paths = mutableListOf<String>()
    private val submitted = mutableListOf<String>()
    private var armed = false
    private var includePreviewIdentity = true
    private var limit = 250_000
    private var automatic = false
    private var askBalance = 250_000
    private var guardianMonthlyBalance = 250_000

    private val client = OwnerPreviewClient(
        assistantRequest = { configuration, payload, beforeBody ->
            assertEquals(OWNER_PREVIEW_ORIGIN, configuration.origin)
            assertEquals(connection.token, configuration.token)
            respond("/assistant/ask", payload, beforeBody)
        },
        guardianRequest = { endpoint, token, payload, beforeBody ->
            assertEquals(OWNER_PREVIEW_ORIGIN, "${endpoint.scheme}://${endpoint.authority}")
            assertEquals(connection.token, token)
            respond(endpoint.path, payload, beforeBody)
        },
    )

    private fun respond(path: String, payload: ByteArray?, beforeBody: () -> Unit): BrainResponse {
        paths += path
        val status = JSONObject("""{
            "status":"authenticated","model":"gpt-5-mini-2025-08-07",
            "assistant_available":true,"analysis_enabled":$armed,
            "cloud_teacher":{"automatic_analysis_enabled":$automatic},
            "budget":{"funded_allowance_micro_usd":250000,"configured_spending_limit_micro_usd":250000,
            "configured_ceiling_micro_usd":$limit,"remaining_micro_usd":${if (path == "/assistant/ask") askBalance else 250_000},"request_reservation_micro_usd":102000,
            "monthly_spending_limit_micro_usd":250000,"monthly_exposure_micro_usd":0,"monthly_remaining_micro_usd":${if (path == "/assistant/ask") 250_000 else guardianMonthlyBalance},
            "approved_request_limit":2,"approved_requests_remaining":2,"expired":false,"expires_at":1800000000,"paid_requests_halted":false}
        }""")
        if (includePreviewIdentity) status.put("fold_owner_preview", JSONObject().put("phone_checked", armed))
        if (payload != null) {
            beforeBody()
            val input = JSONObject(payload.decodeToString())
            assertEquals(setOf("message", "consent", "request_id", "protocol_version"), input.keys().asSequence().toSet())
            assertTrue(input.getBoolean("consent"))
            assertEquals(2, input.getInt("protocol_version"))
            submitted += input.getString("message")
            status.put("request_id", input.getString("request_id")).put("provider_billing_verified", false)
            if (path == "/assistant/ask") {
                status.put("source", "assistant_prediction").put("answer", "A short poem.")
            } else {
                status.put("source", "model_prediction").put("lesson", JSONObject().put("assessment", "UNCERTAIN")
                    .put("patterns", JSONArray()).put("uncertainty", "HIGH").put("summary", "A synthetic advisory."))
            }
        }
        return BrainResponse(200, status.toString().encodeToByteArray())
    }

    @Test fun checkOnlyAuthenticatesBothPreviewRoutesWhenDisarmed() {
        assertEquals(OwnerPreviewStatus(false, 2, false, 250_000, 250_000), client.check(connection) { true })
        assertEquals(listOf("/assistant/ask", "/guardian/status"), paths)
        assertThrows(CloudTeacherException::class.java) { client.send(connection, OwnerPreviewTest.ASK_NOVA, true) { true } }
        assertTrue(submitted.isEmpty())
    }

    @Test fun diagnosticsCheckBothFixedHttpsRoutesWithoutSendingAnyPaidRequest() {
        val result = client.diagnose(connection) { true }

        assertTrue(result.authenticated)
        assertEquals(200, result.httpStatus)
        assertEquals(OwnerPreviewTls.VERIFIED, result.tls)
        assertEquals(OwnerPreviewExpiration.ACTIVE, result.expiration)
        assertEquals(Instant.ofEpochSecond(1800000000), result.expiresAt)
        assertEquals(OwnerPreviewStatus(false, 2, false, 250_000, 250_000), result.status)
        assertEquals(listOf("/assistant/ask", "/guardian/status"), paths)
        assertTrue(submitted.isEmpty())
        assertTrue(result.asText().contains(OWNER_PREVIEW_ORIGIN))
        assertTrue(result.asText().contains("Paid analysis: Disabled"))
        assertFalse(result.asText().contains(connection.token))
    }

    @Test fun diagnosticsDistinguishNoNetworkServerStoppedDnsTimeoutAndTlsWithoutLeakingExceptions() {
        val offline = OwnerPreviewClient(
            assistantRequest = { _, _, _ -> error("No request without an active network") },
            networkAvailable = { false },
        ).diagnose(connection) { true }
        assertEquals(OwnerPreviewFailure.OFFLINE, offline.failure)
        assertNull(offline.httpStatus)
        assertFalse(offline.authenticated)

        val sensitive = "credential ${connection.token} and example private message"
        val failures = listOf(
            ConnectException(sensitive) to OwnerPreviewFailure.SERVER_UNAVAILABLE,
            UnknownHostException(sensitive) to OwnerPreviewFailure.HOST_LOOKUP,
            SocketTimeoutException(sensitive) to OwnerPreviewFailure.TIMEOUT,
            SSLHandshakeException(sensitive) to OwnerPreviewFailure.TLS,
        )
        for ((error, expected) in failures) {
            val client = OwnerPreviewClient(assistantRequest = { _, payload, _ ->
                assertNull(payload)
                throw error
            })
            val result = client.diagnose(connection) { true }
            assertEquals(expected, result.failure)
            assertEquals(if (expected == OwnerPreviewFailure.TLS) OwnerPreviewTls.FAILED else OwnerPreviewTls.NOT_VERIFIED,
                result.tls)
            assertNull(result.httpStatus)
            assertEquals(OwnerPreviewExpiration.UNKNOWN, result.expiration)
            assertFalse(result.authenticated)
            assertFalse(result.asText().contains(connection.token))
            assertFalse(result.asText().contains("private message"))
            assertFalse(result.toString().contains(connection.token))
        }
    }

    @Test fun diagnosticsNeverTreatARejectedOrMismatchedSecondRouteAsAuthenticated() {
        for ((code, reason, expected) in listOf(
            Triple(401, "authentication_required", OwnerPreviewFailure.AUTHENTICATION),
            Triple(404, "not_found", OwnerPreviewFailure.ROUTE_UNAVAILABLE),
            Triple(503, "preview_expired", OwnerPreviewFailure.EXPIRED),
            Triple(200, "not_a_preview", OwnerPreviewFailure.INVALID_RESPONSE),
        )) {
            val rejected = """{"error":"$reason","private":"${connection.token}"}""".encodeToByteArray()
            val client = OwnerPreviewClient(
                assistantRequest = { _, payload, beforeBody -> respond("/assistant/ask", payload, beforeBody) },
                guardianRequest = { endpoint, _, payload, _ ->
                    assertEquals("$OWNER_PREVIEW_ORIGIN/guardian/status", endpoint.toString())
                    assertNull(payload)
                    BrainResponse(code, rejected)
                },
            )
            val result = client.diagnose(connection) { true }
            assertFalse(result.authenticated)
            assertEquals(code, result.httpStatus)
            assertEquals(expected, result.failure)
            assertEquals(OwnerPreviewTls.VERIFIED, result.tls)
            assertEquals(if (expected == OwnerPreviewFailure.EXPIRED) OwnerPreviewExpiration.EXPIRED
                else OwnerPreviewExpiration.UNKNOWN, result.expiration)
            assertFalse(result.asText().contains(connection.token))
            assertTrue(rejected.all { it == 0.toByte() })
        }
        assertTrue(submitted.isEmpty())
    }

    @Test fun availableBalanceUsesTheTightestAuthenticatedLedgerValue() {
        armed = true
        askBalance = 190_000
        guardianMonthlyBalance = 80_000

        assertEquals(OwnerPreviewStatus(true, 2, false, 250_000, 80_000), client.check(connection) { true })
        assertTrue(submitted.isEmpty())
    }

    @Test fun eachExplicitApprovalUsesItsExactSyntheticTextAndNoPrivatePayload() {
        armed = true
        for (test in OwnerPreviewTest.entries) {
            assertThrows(IllegalArgumentException::class.java) { client.send(connection, test, false) { true } }
        }
        assertTrue(paths.isEmpty())
        assertEquals("A short poem.", client.send(connection, OwnerPreviewTest.ASK_NOVA, true) { true }.text)
        assertEquals("UNCERTAIN", client.send(connection, OwnerPreviewTest.GUARDIAN, true) { true }.assessment)
        assertEquals(listOf("What is a haiku?", "Please reply right away or I will be upset."), submitted)
        assertEquals(listOf("/assistant/ask", "/assistant/ask", "/guardian/status", "/guardian/analyze"), paths)
    }

    @Test fun mismatchedIdentityCapAutomaticModeAndRevocationPreventProviderDispatch() {
        armed = true
        includePreviewIdentity = false
        assertThrows(IllegalStateException::class.java) { client.send(connection, OwnerPreviewTest.ASK_NOVA, true) { true } }
        includePreviewIdentity = true
        limit = 250_001
        assertThrows(IllegalStateException::class.java) { client.send(connection, OwnerPreviewTest.ASK_NOVA, true) { true } }
        limit = 250_000
        automatic = true
        assertThrows(CloudTeacherException::class.java) { client.send(connection, OwnerPreviewTest.GUARDIAN, true) { true } }
        automatic = false
        assertThrows(CancellationException::class.java) { client.send(connection, OwnerPreviewTest.ASK_NOVA, true) { paths.isEmpty() } }
        var checks = 0
        assertThrows(CancellationException::class.java) { client.send(connection, OwnerPreviewTest.ASK_NOVA, true) { ++checks < 4 } }
        assertEquals(4, checks)
        assertTrue(submitted.isEmpty())
    }
}
