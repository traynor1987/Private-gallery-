package uk.co.traynor.privategallery.launcher

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*
import uk.co.traynor.privategallery.core.brain.*
import uk.co.traynor.privategallery.core.ui.AppTheme
import uk.co.traynor.privategallery.ui.SettingsFixture

class NovaBrain87UiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val store get() = NovaBrainConnectionStore(context)
    private var previous: BrainConnectionRecord? = null
    private val config = NovaBrainConfiguration("https://synthetic.example", "synthetic-owner-token-".repeat(3))
    @Before fun prepare() { assertTrue(context.packageName.endsWith(".hiddenprototype")); previous=store.readRecord();store.clear() }
    @After fun restore() {
        val before=previous
        if(before==null)store.clear() else {
            store.save(before.configuration)
            before.lastAuthenticated?.let { store.rememberAuthenticated(before.configuration,BrainConnectionStatus(BrainConnectionPhase.CONNECTED,staging=it.staging,analysisEnabledOnServer=it.analysisEnabled),it.at) }
        }
    }
    private fun waitFor(text: String) = compose.waitUntil(10_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().size==1 }
    private fun content(connection: NovaBrainConnection, clock: () -> Instant = Instant::now) {
        compose.setContent { MaterialTheme { Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            NovaBrainConnectionSettings(connection=connection,clock=clock)
        } } }
    }

    @Test fun actualSettingsHasDedicatedNovaBrainRouteAndKeepsKnowledgeSeparateFromConnectivity() {
        compose.setContent { MaterialTheme { SettingsFixture(AppTheme.DARK,onTimeout={}) } }
        compose.onNodeWithText("Nova Brain").performScrollTo().performClick()
        compose.onNodeWithText("Nova Brain connection").assertIsDisplayed()
        compose.onNodeWithTag("nova-brain-status").assertTextEquals("Disconnected · no backend configured")
        compose.onNodeWithTag("nova-brain-environment").assertTextEquals("Backend environment: Not yet verified")
        compose.onNodeWithTag("nova-brain-last-authenticated").assertTextEquals("Last successful authenticated connection: Never")
        compose.onNodeWithText("Test connection").assertIsNotEnabled()
        compose.onNodeWithTag("nova-brain-offline").performScrollTo().assertTextContains("Offline mode: Active",substring=true)
        compose.onNodeWithTag("nova-brain-privacy").performScrollTo().assertTextContains("No automatic messages",substring=true)
        compose.onNodeWithText("Connection tests send only your access token to the status endpoint. Local Knowledge evaluation is separate from backend connectivity.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Back to Settings").performScrollTo().performClick()
        compose.onNodeWithText("Nova Brain").performScrollTo().assertIsDisplayed()
    }

    @Test fun actualAuthenticatedResponseIsRecordedAndRejectionDoesNotOverwriteLastSuccess() {
        store.save(config)
        val mode=AtomicInteger(0);val paths=CopyOnWriteArrayList<String>()
        val client=NovaBrainConnection { uri, token ->
            paths.add(uri.path)
            if(uri.path=="/health") { assertNull(token);BrainResponse(200,"""{"status":"ok"}""".toByteArray()) }
            else { assertEquals(config.token,token);if(mode.get()==1) BrainResponse(401,byteArrayOf()) else BrainResponse(200,"""{"status":"authenticated","model":"staging","analysis_enabled":false,"budget":null}""".toByteArray()) }
        }
        val at=Instant.parse("2026-11-18T11:00:00Z")
        content(client){at}
        waitFor("HTTPS backend configured")
        assertTrue(paths.isEmpty())
        compose.onNodeWithText("Test connection").performScrollTo().performClick()
        waitFor("Connected · authenticated test passed")
        assertEquals(BrainAuthenticatedCheck(at,true,false),store.readRecord()?.lastAuthenticated)
        compose.onNodeWithTag("nova-brain-environment").performScrollTo().assertTextEquals("Backend environment: Staging")
        compose.onNodeWithTag("nova-brain-analysis").performScrollTo().assertTextEquals("Cloud analysis: Disabled by server")
        mode.set(1)
        compose.onNodeWithText("Test connection").performScrollTo().performClick()
        waitFor("Authentication required")
        assertEquals(at,store.readRecord()?.lastAuthenticated?.at)
        compose.onNodeWithTag("nova-brain-offline").performScrollTo().assertTextContains("Offline mode: Active",substring=true)
        assertEquals(listOf("/health","/guardian/status","/health","/guardian/status"),paths.toList())
        compose.onNodeWithText(config.origin).assertDoesNotExist()
        compose.onNodeWithText(config.token).assertDoesNotExist()
    }

    @Test fun serverOutageUsesLocalModeAndNeverInventsAuthenticatedEnvironmentOrTimestamp() {
        store.save(config)
        val calls=AtomicInteger(0)
        content(NovaBrainConnection { _, token -> assertNull(token);calls.incrementAndGet();throw java.io.IOException("synthetic failure must not be displayed") })
        waitFor("HTTPS backend configured")
        compose.onNodeWithText("Test connection").performScrollTo().performClick()
        waitFor("Server unavailable")
        assertEquals(2,calls.get())
        assertNull(store.readRecord()?.lastAuthenticated)
        compose.onNodeWithTag("nova-brain-environment").performScrollTo().assertTextEquals("Backend environment: Not yet verified")
        compose.onNodeWithTag("nova-brain-last-authenticated").performScrollTo().assertTextEquals("Last successful authenticated connection: Never")
        compose.onNodeWithTag("nova-brain-offline").performScrollTo().assertTextContains("Offline mode: Active",substring=true)
        compose.onNodeWithText("synthetic failure must not be displayed").assertDoesNotExist()
    }

    @Test fun recreatedScreenKeepsHistoricalReceiptWithoutPretendingItHasACurrentConnection() {
        store.save(config)
        val at=Instant.parse("2026-11-18T12:00:00Z")
        store.rememberAuthenticated(config,BrainConnectionStatus(BrainConnectionPhase.CONNECTED,staging=false),at)
        var visible by mutableStateOf(true)
        val calls=AtomicInteger(0)
        val client=NovaBrainConnection { _,_ -> calls.incrementAndGet();error("Opening status must not send owner data or enable analysis") }
        compose.setContent { MaterialTheme { Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            if(visible)NovaBrainConnectionSettings(connection=client)
        } } }
        waitFor("HTTPS backend configured")
        compose.runOnIdle { visible=false }
        compose.onNodeWithText("Nova Brain connection").assertDoesNotExist()
        compose.runOnIdle { visible=true }
        waitFor("HTTPS backend configured")
        compose.onNodeWithTag("nova-brain-status").assertTextEquals("Disconnected · test the saved connection")
        compose.onNodeWithTag("nova-brain-environment").performScrollTo().assertTextEquals("Backend environment: Production configuration")
        compose.onNodeWithTag("nova-brain-last-authenticated").performScrollTo().assertTextContains("Last successful authenticated connection:",substring=true)
        assertEquals(at,store.readRecord()?.lastAuthenticated?.at)
        compose.onNodeWithText("Automatic Cloud Teacher: Off · connection tests do not activate paid analysis").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Send this message to OpenAI").assertDoesNotExist()
        assertEquals(0,calls.get())
    }
}
