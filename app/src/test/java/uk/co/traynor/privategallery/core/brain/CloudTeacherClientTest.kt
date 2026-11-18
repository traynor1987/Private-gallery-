package uk.co.traynor.privategallery.core.brain

import java.io.IOException
import java.util.UUID
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class CloudTeacherClientTest {
    private val config = NovaBrainConfiguration("https://gateway.example", "synthetic-owner-token-".repeat(3))
    private fun readyBody() = JSONObject("""{"status":"authenticated","model":"gpt-5-mini","analysis_enabled":true,
      "budget":{"funded_allowance_micro_usd":1000000,"configured_spending_limit_micro_usd":250000,
      "configured_ceiling_micro_usd":250000,"remaining_micro_usd":250000,"request_reservation_micro_usd":102000,
      "monthly_spending_limit_micro_usd":100000000,"monthly_exposure_micro_usd":0,
      "monthly_remaining_micro_usd":100000000,"usage_reported_cost_micro_usd":0,
      "approved_requests_remaining":1,"expired":false,"paid_requests_halted":false}}""")
    @Test fun disabledPermissionAndMissingRequestConsentSendNothing() {
        var calls=0
        val client=CloudTeacherClient { _,_,_ -> calls++;error("must not send") }
        val status=CloudTeacherStatus.parse(readyBody())
        assertThrows(CloudTeacherException::class.java) { client.analyze(config,"approved excerpt",true,UUID.randomUUID().toString(),status,{false}) }
        assertThrows(IllegalArgumentException::class.java) { client.analyze(config,"private original",false,UUID.randomUUID().toString(),status,{true}) }
        assertEquals(0,calls)
    }
    @Test fun successfulPostUsesExactEditedBytesAndClearsTransientPayload() {
        val identity=UUID.randomUUID().toString()
        var transmitted:ByteArray?=null
        var copy:ByteArray?=null
        var calls=0
        val client=CloudTeacherClient { endpoint,token,payload ->
            calls++
            assertEquals("https://gateway.example/guardian/analyze",endpoint.toString())
            assertEquals(config.token,token)
            transmitted=payload;copy=payload!!.copyOf()
            val body=readyBody().put("source","model_prediction").put("request_id",identity)
                .put("provider_billing_verified",false).put("usage_cost_micro_usd",338)
                .put("lesson",JSONObject("""{"assessment":"UNCERTAIN","patterns":[],"uncertainty":"HIGH","summary":"Synthetic uncertainty."}"""))
            BrainResponse(200,body.toString().toByteArray())
        }
        val reply=client.analyze(config,"[redacted] approved excerpt",true,identity,CloudTeacherStatus.parse(readyBody()),{true})
        assertEquals("[redacted] approved excerpt",JSONObject(copy!!.decodeToString()).getString("message"))
        assertTrue(transmitted!!.all { it==0.toByte() })
        assertEquals(338L,reply.lesson.usageCost)
        assertEquals(1,calls)
    }
    @Test fun timeoutNeverAutomaticallyRetriesOrLeaksProviderDiagnostics() {
        var calls=0
        val client=CloudTeacherClient { _,_,_ -> calls++;throw IOException("private text "+config.token) }
        val error=assertThrows(CloudTeacherException::class.java) {
            client.analyze(config,"approved excerpt",true,UUID.randomUUID().toString(),CloudTeacherStatus.parse(readyBody()),{true})
        }
        assertEquals(CloudFailure.UNKNOWN_OUTCOME,error.failure)
        assertEquals(1,calls)
        assertFalse(error.toString().contains(config.token))
        assertFalse(error.toString().contains("private text"))
    }
    @Test fun statusFailureCannotPretendAnAnalysisWasCharged() {
        val client=CloudTeacherClient { _,_,payload -> assertNull(payload);BrainResponse(200,"invalid private body".toByteArray()) }
        val error=assertThrows(CloudTeacherException::class.java) { client.status(config) }
        assertEquals(CloudFailure.INVALID_STATUS,error.failure)
        assertFalse(error.failure.mayHaveCharged)
    }
    @Test fun knownBudgetAndDuplicateErrorsAreActionableWithoutEchoingBodies() {
        for ((code,reason,expected) in listOf(Triple(402,"monthly_limit_exhausted",CloudFailure.MONTHLY_LIMIT),
            Triple(402,"approved_request_limit_exhausted",CloudFailure.APPROVED_LIMIT),
            Triple(409,"private arbitrary text",CloudFailure.ALREADY_RESERVED))) {
            val client=CloudTeacherClient { _,_,_ -> BrainResponse(code,JSONObject().put("error",reason).toString().toByteArray()) }
            val error=assertThrows(CloudTeacherException::class.java) { client.analyze(config,"approved excerpt",true,UUID.randomUUID().toString(),CloudTeacherStatus.parse(readyBody()),{true}) }
            assertEquals(expected,error.failure)
            assertFalse(error.message.orEmpty().contains("private arbitrary"))
        }
    }
    @Test fun permissionRevokedImmediatelyBeforeDispatchSendsNoPayload() {
        var calls=0;var checks=0
        val client=CloudTeacherClient { _,_,_ -> calls++;error("revoked authority must not dispatch") }
        assertThrows(CloudTeacherException::class.java) {
            client.analyze(config,"approved excerpt",true,UUID.randomUUID().toString(),CloudTeacherStatus.parse(readyBody()),{ ++checks == 1 })
        }
        assertEquals(2,checks);assertEquals(0,calls)
    }
    @Test fun cancelledRequestNeverDispatchesAndCertificateFailuresAreNotBypassed() {
        var calls=0
        val client=CloudTeacherClient { _,_,_ -> calls++;throw javax.net.ssl.SSLHandshakeException("private certificate diagnostics") }
        assertThrows(java.util.concurrent.CancellationException::class.java) {
            client.analyze(config,"approved excerpt",true,UUID.randomUUID().toString(),CloudTeacherStatus.parse(readyBody()),{true},{true})
        }
        assertEquals(0,calls)
        val error=assertThrows(CloudTeacherException::class.java) {
            client.analyze(config,"approved excerpt",true,UUID.randomUUID().toString(),CloudTeacherStatus.parse(readyBody()),{true})
        }
        assertEquals(CloudFailure.SECURE_CONNECTION,error.failure);assertEquals(1,calls)
        assertFalse(error.toString().contains("private certificate"))
    }

}
