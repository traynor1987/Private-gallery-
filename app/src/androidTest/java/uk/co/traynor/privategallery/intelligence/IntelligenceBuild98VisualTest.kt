package uk.co.traynor.privategallery.intelligence

import android.graphics.Bitmap
import android.view.WindowInsets
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.time.ZonedDateTime
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import uk.co.traynor.privategallery.core.sanctuary.SanctuaryLauncherPolicy.Decision
import uk.co.traynor.privategallery.launcher.GuardianTheme
import uk.co.traynor.privategallery.launcher.LauncherApp
import uk.co.traynor.privategallery.launcher.LauncherIntelligenceActions
import uk.co.traynor.privategallery.launcher.LauncherItem
import uk.co.traynor.privategallery.launcher.LauncherLayout
import uk.co.traynor.privategallery.launcher.LauncherUiActions
import uk.co.traynor.privategallery.launcher.NovaLauncherScreen
import uk.co.traynor.privategallery.launcher.NovaLauncherState
import uk.co.traynor.privategallery.ui.BrowserViewportRule

/** Widget and keyboard screenshots from an API 37 emulator using synthetic UI state only. */
class IntelligenceBuild98VisualTest {
    private val compose = createAndroidComposeRule<ComponentActivity>()

    @get:Rule
    val rule = RuleChain.outerRule(BrowserViewportRule {
        if (it.startsWith("inner")) 820 to 920 else 375 to 850
    }).around(compose)

    private val now = ZonedDateTime.parse("2026-11-23T09:30:00Z[Europe/London]")
    private val localExchange = IntelligenceExchange(
        "What’s on tomorrow?", "Nova Calendar has one event tomorrow in this demo: design review at 10:30.",
    )

    @Test fun coverDashboardAndChat() {
        show()
        compose.onNodeWithTag("intelligence-cover").assertIsDisplayed()
        compose.onNodeWithText("Daily briefing").assertIsDisplayed()
        capture("cover-dashboard")

        compose.onNodeWithText("Ask Nova").performClick()
        compose.onNodeWithTag("intelligence-input").assertIsDisplayed()
        compose.onNodeWithText(localExchange.answer).assertIsDisplayed()
        capture("cover-chat")
    }

    @Test fun innerDashboard() {
        show(exchanges = emptyList())
        compose.onNodeWithTag("intelligence-inner").assertIsDisplayed()
        compose.onNodeWithText("Daily briefing").assertIsDisplayed()
        compose.onNodeWithTag("intelligence-input").assertIsDisplayed()
        capture("inner-dashboard")
    }

    @Test fun innerChat() {
        show()
        compose.onNodeWithTag("intelligence-inner").assertIsDisplayed()
        compose.onNodeWithText(localExchange.answer).assertIsDisplayed()
        capture("inner-chat")
    }

    @Test fun coverComposerRemainsVisibleWithKeyboard() {
        val app = LauncherApp("example.maps/.Main", "example.maps", "Maps")
        val layout = LauncherLayout(pages = listOf(listOf(LauncherItem.Workspace), emptyList()),
            dock = listOf(LauncherItem.App(app.component)))
        compose.runOnUiThread {
            compose.activity.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
        compose.setContent {
            NovaLauncherScreen(
                NovaLauncherState(configured = true, cover = layout, inner = layout, separateDisplays = true),
                listOf(app), emptyList(), mapOf(app.component to Decision.APPROVED), true, false,
                LauncherUiActions(save = { true }, launchApp = {}, launchShortcut = {}, openWorkspace = {},
                    selectHome = {}, continueWithCurrentHome = {}, emergencyPhone = {}, reviewSanctuary = {},
                    setOutsideProtection = {}, chooseWallpaper = {}, addWidget = {}, removeWidget = {},
                    exportLayout = {}, importLayout = {}, renderWidget = { _, _ -> }),
                intelligence = LauncherIntelligenceActions(openPrivate = {}, openDestination = { false }, openEvidence = { false }),
            )
        }
        compose.onNodeWithContentDescription("Nova Intelligence page").performClick()
        compose.onNodeWithText("Ask Nova").performClick()
        compose.onNodeWithTag("intelligence-input").performClick().performTextInput("Show my tasks")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        compose.waitUntil(10_000) {
            var imeVisible = false
            instrumentation.runOnMainSync {
                imeVisible = compose.activity.window.decorView.rootWindowInsets
                    ?.isVisible(WindowInsets.Type.ime()) == true
            }
            imeVisible
        }
        compose.onNodeWithTag("intelligence-input").assertIsDisplayed()
        compose.onNodeWithContentDescription("Send message").assertIsDisplayed()
        capture("cover-keyboard-open")
    }

    @Test fun coverCloudLoadingAndSyntheticReply() {
        val reply = CompletableDeferred<IntelligenceCloudAnswer>()
        val approved = AtomicReference<IntelligenceCloudRequest?>()
        showAssistant { request ->
            check(approved.compareAndSet(null, request))
            reply.await()
        }
        send("What is a haiku?")
        compose.onNodeWithText("Send one question to Nova Brain?").assertIsDisplayed()
        assertNull(approved.get())

        compose.onNodeWithText("Send question").performClick()
        compose.onNodeWithText("Waiting for Nova Brain…").assertIsDisplayed()
        compose.onNodeWithContentDescription("Cancel Nova Brain request").assertIsDisplayed()
        assertEquals("What is a haiku?", approved.get()?.question)
        capture("cover-cloud-loading", composed = true)

        val sample = "Synthetic reply: a haiku is a short form of Japanese poetry."
        reply.complete(IntelligenceCloudAnswer(sample, cloud = true))
        compose.waitUntil(10_000) { compose.onNodeWithText(sample).isDisplayed() }
        compose.onNodeWithText("Nova Brain · cloud").assertIsDisplayed()
        capture("cover-synthetic-cloud-reply", composed = true)
    }

    @Test fun coverOfflineFallback() {
        val offline = "Nova Brain is offline. Local commands are available."
        val opened = AtomicReference<IntelligenceDestination?>()
        showAssistant(openDestination = { destination -> opened.set(destination); true }) {
            IntelligenceCloudAnswer(offline, retryable = true)
        }
        send("Open Nova Notes")
        assertEquals(IntelligenceDestination.NOTES, opened.get())
        send("What is a haiku?")
        compose.onNodeWithText("Send question").performClick()
        compose.onNodeWithText(offline).assertIsDisplayed()
        compose.onNodeWithText("Retry cloud question").assertIsDisplayed()
        capture("cover-offline-fallback")
    }

    @Test fun guardianOwnerPreviewDisconnected() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".hiddenprototype"))
        val preview = OwnerPreviewStore(context)
        check(!preview.isEnabled())
        // This token stays in the debug fixture. The disabled action must never transmit it.
        preview.enable("0".repeat(64))
        try {
            compose.setContent {
                GuardianTheme {
                    Surface(Modifier.fillMaxSize()) {
                        Box(Modifier.safeDrawingPadding().padding(12.dp)) { OwnerPreviewSettings() }
                    }
                }
            }
            val secondOpinion = compose.onNodeWithText("Test Guardian second opinion")
            compose.waitUntil(10_000) { secondOpinion.isDisplayed() }
            secondOpinion.assertIsNotEnabled()
            capture("guardian-owner-preview-disconnected", composed = true)
        } finally {
            preview.disable()
        }
    }

    private fun show(exchanges: List<IntelligenceExchange> = listOf(localExchange)) {
        compose.setContent {
            var showChat by remember { mutableStateOf(false) }
            var input by remember { mutableStateOf("") }
            GuardianTheme {
                IntelligenceExperience(
                    onBack = {}, showChat = showChat, onShowChat = { showChat = it }, protected = false,
                    dashboard = { modifier ->
                        IntelligenceDashboard(
                            modifier = modifier, now = now, briefingName = "Demo", readyBriefing = null,
                            guardian = IntelligenceGuardian(7, 5, 2, 0, 2, 3, 1, false),
                            guardianState = "Connected · sample totals", sanctuaryStatus = "Protection inactive",
                            protected = false, guardianVisible = true, connection = "Authenticated · demo data", email = null,
                            agenda = Result.success(IntelligenceAgenda(
                                tasks = listOf("Review notes · This afternoon"),
                                events = listOf("Design review · Tomorrow at 10:30"),
                                reminders = listOf("Check project notes · Today at 15:00"),
                            )),
                            primaryUnlocked = true, busy = false, onUnlock = null,
                            onCloudCheck = {}, onShortcut = {}, onInbox = {}, onSchedule = {}, onName = {},
                        )
                    },
                    conversation = { modifier, expanded ->
                        IntelligenceChat(
                            modifier = modifier, expanded = expanded, exchanges = exchanges, zone = now.zone,
                            input = input, onInput = { input = it }, onSend = {}, busy = false,
                            pendingQuestion = null, retryAvailable = false, onCancel = {}, onRetry = {},
                            privatePrompt = false, onUnlock = null, onMemory = {}, onClear = {},
                        )
                    },
                )
            }
        }
    }

    private fun showAssistant(
        openDestination: (IntelligenceDestination) -> Boolean = { false },
        answer: suspend (IntelligenceCloudRequest) -> IntelligenceCloudAnswer,
    ) {
        val assistant = object : IntelligenceAssistant {
            override fun previewEnabled() = false
            override suspend fun check(mayContinue: () -> Boolean) = "Demo connection"
            override suspend fun ask(request: IntelligenceCloudRequest, maySend: () -> Boolean): IntelligenceCloudAnswer {
                check(maySend())
                return answer(request)
            }
        }
        compose.setContent {
            IntelligenceScreen(
                active = true, guardianVisible = false, protectedBySanctuary = false, openPrivate = {},
                openDestination = openDestination, onBack = {}, assistant = assistant,
            )
        }
        compose.onNodeWithText("Ask Nova").performClick()
    }

    private fun send(message: String) {
        compose.onNodeWithTag("intelligence-input").performTextInput(message)
        compose.onNodeWithContentDescription("Send message").performClick()
    }

    private fun capture(name: String, composed: Boolean = false) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val directory = File(instrumentation.targetContext.cacheDir, "nova-intelligence-build98").apply { mkdirs() }
        // Compose captures synchronize with the displayed root. Use the full display when
        // the system IME or launcher chrome needs to be visible in the evidence.
        val screenshot = if (composed) compose.onRoot().captureToImage().asAndroidBitmap()
        else checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        try {
            File(directory, "$name.png").outputStream().use {
                check(screenshot.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
        } finally {
            screenshot.recycle()
        }
    }
}
