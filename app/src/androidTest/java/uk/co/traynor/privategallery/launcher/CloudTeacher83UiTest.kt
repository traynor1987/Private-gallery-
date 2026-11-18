package uk.co.traynor.privategallery.launcher

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.atomic.AtomicInteger
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*
import org.json.JSONObject
import uk.co.traynor.privategallery.core.brain.*

class CloudTeacher83UiTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private val store get()=CloudTeacherStore(context)
    private val connection get()=NovaBrainConnectionStore(context)
    private val config=NovaBrainConfiguration("https://synthetic.example","synthetic-owner-token-".repeat(3))
    @Before fun prepare() { check(context.packageName.endsWith(".hiddenprototype"));store.clearHistory();store.setEnabled(false);connection.save(config) }
    @After fun cleanup() { store.clearHistory();store.setEnabled(false);connection.clear() }
    private fun waitFor(text:String)=compose.waitUntil(10_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().size==1 }
    private fun status()=JSONObject("""{"status":"authenticated","model":"gpt-5-mini","analysis_enabled":true,"budget":{
      "funded_allowance_micro_usd":1000000,"configured_spending_limit_micro_usd":250000,"configured_ceiling_micro_usd":250000,
      "remaining_micro_usd":250000,"request_reservation_micro_usd":102000,"monthly_spending_limit_micro_usd":100000000,
      "monthly_exposure_micro_usd":0,"monthly_remaining_micro_usd":100000000,"usage_reported_cost_micro_usd":0,
      "approved_requests_remaining":1,"expired":false,"paid_requests_halted":false}}""")
    @Test fun disclosureRequiresExplicitConsentAndCancellationKeepsCloudOff() {
        compose.setContent { MaterialTheme { Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) { CloudTeacherSettings(onOpenHistory={}) } } }
        waitFor("Off · no cloud analysis requests")
        compose.onNodeWithTag("cloud-teacher-enable").performScrollTo().performClick()
        compose.onNodeWithText("Enable manual requests").assertIsNotEnabled()
        compose.onNodeWithText("Cancel").performClick();assertFalse(store.enabled())
        compose.onNodeWithTag("cloud-teacher-enable").performScrollTo().performClick()
        compose.onNodeWithTag("cloud-teacher-disclosure-consent").performScrollTo().performClick()
        compose.onNodeWithText("Enable manual requests").performClick()
        waitFor("Manual requests permitted · each excerpt still needs approval");assertTrue(store.enabled())
        compose.onNodeWithTag("cloud-teacher-enable").performScrollTo().performClick()
        waitFor("Off · no cloud analysis requests");assertFalse(store.enabled())
        compose.onNodeWithText(config.token).assertDoesNotExist()
    }
    @Test fun stagingCanBeCheckedReadOnlyButNeverSendsAnAnalysisEvenWithLocalPermission() {
        store.setEnabled(true);val calls=AtomicInteger(0)
        val client=CloudTeacherClient { _,_,payload -> assertNull(payload);calls.incrementAndGet();BrainResponse(200,"""{"status":"authenticated","model":"staging","analysis_enabled":false,"budget":null}""".toByteArray()) }
        compose.setContent { MaterialTheme { GuardianCloudTeacher("Synthetic original",client=client) { true } } }
        compose.onNodeWithText("Ask Cloud Teacher").performClick();waitFor("Manual Cloud Teacher permitted · per-request approval required")
        compose.onNodeWithText("Check connection and budget").performScrollTo().performClick();waitFor("Staging · analysis disabled")
        compose.onNodeWithText("Send this message to OpenAI").assertIsNotEnabled()
        compose.onNodeWithTag("cloud-teacher-request-consent").performScrollTo().assertIsNotEnabled()
        assertEquals(1,calls.get());assertTrue(store.history().isEmpty())
    }
    @Test fun onlyEditedApprovedExcerptIsSentAndSuggestionIsNotRecordedAsOwnerFeedback() {
        store.setEnabled(true);val posts=AtomicInteger(0)
        val localBrain=uk.co.traynor.privategallery.core.guardian.GuardianCaptureStore(context)
        val trainingBefore=localBrain.learningCount()
        val validationBefore=localBrain.knowledgeValidation()
        val client=CloudTeacherClient { _,token,payload ->
            assertEquals(config.token,token)
            val body=status()
            if(payload!=null) {
                posts.incrementAndGet();val sent=JSONObject(payload.decodeToString())
                assertEquals("[redacted] synthetic approved text",sent.getString("message"));assertTrue(sent.getBoolean("consent"))
                assertEquals(setOf("message","consent","request_id","protocol_version"),sent.keys().asSequence().toSet())
                body.put("source","model_prediction").put("request_id",sent.getString("request_id"))
                    .put("provider_billing_verified",false).put("usage_cost_micro_usd",338)
                    .put("lesson",JSONObject("""{"assessment":"UNCERTAIN","patterns":[],"uncertainty":"HIGH","summary":"Synthetic suggestion needs context."}"""))
            }
            BrainResponse(200,body.toString().toByteArray())
        }
        compose.setContent { MaterialTheme { GuardianCloudTeacher("Synthetic original excluded",client=client) { true } } }
        compose.onNodeWithText("Ask Cloud Teacher").performClick();waitFor("Manual Cloud Teacher permitted · per-request approval required")
        compose.onNodeWithText("Check connection and budget").performScrollTo().performClick();waitFor("Server analysis available")
        compose.onNodeWithTag("cloud-teacher-approved-excerpt").performScrollTo().performTextReplacement("[redacted] synthetic approved text")
        compose.onNodeWithText("Send this message to OpenAI").assertIsNotEnabled()
        compose.onNodeWithTag("cloud-teacher-request-consent").performScrollTo().performClick()
        compose.onNodeWithText("Send this message to OpenAI").performClick();waitFor("AI advisory · model prediction, not verified fact")
        assertEquals(1,posts.get());assertEquals(1,store.history().size)
        assertEquals(trainingBefore,localBrain.learningCount())
        assertEquals(validationBefore,localBrain.knowledgeValidation())
        val retained=store.readResult(store.history().single().id)!!
        assertEquals("Model summary not retained for privacy.",retained.summary)
        compose.onNodeWithText(config.token).assertDoesNotExist()
        compose.onNodeWithText("Validated").assertDoesNotExist()
    }
    @Test fun backendFailureKeepsLocalReviewAndDoesNotAutomaticallyRetryPaidRequest() {
        store.setEnabled(true);val posts=AtomicInteger(0)
        val client=CloudTeacherClient { _,_,payload ->
            if(payload==null)BrainResponse(200,status().toString().toByteArray())
            else { posts.incrementAndGet();throw java.net.SocketTimeoutException("synthetic sensitive diagnostics") }
        }
        compose.setContent { MaterialTheme { GuardianCloudTeacher("Synthetic approved excerpt",client=client) { true } } }
        compose.onNodeWithText("Ask Cloud Teacher").performClick();waitFor("Manual Cloud Teacher permitted · per-request approval required")
        compose.onNodeWithText("Check connection and budget").performScrollTo().performClick();waitFor("Server analysis available")
        compose.onNodeWithTag("cloud-teacher-request-consent").performScrollTo().performClick()
        compose.onNodeWithText("Send this message to OpenAI").performClick()
        waitFor(CloudFailure.UNKNOWN_OUTCOME.userMessage)
        assertEquals(1,posts.get());assertNull(store.history().single().completedAt)
        compose.onNodeWithText("synthetic sensitive diagnostics").assertDoesNotExist()
        compose.onNodeWithText("Send this message to OpenAI").assertIsNotEnabled()
    }
}
