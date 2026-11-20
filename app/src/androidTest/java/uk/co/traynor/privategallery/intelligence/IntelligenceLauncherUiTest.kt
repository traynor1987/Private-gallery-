package uk.co.traynor.privategallery.intelligence

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import uk.co.traynor.privategallery.core.sanctuary.SanctuaryLauncherPolicy.Decision
import uk.co.traynor.privategallery.launcher.*
import uk.co.traynor.privategallery.ui.BrowserViewportRule

class IntelligenceLauncherUiTest {
    private val compose = createAndroidComposeRule<ComponentActivity>()
    @get:Rule val rule = RuleChain.outerRule(BrowserViewportRule(fontScale = 1.15f) {
        if (it.startsWith("inner")) 820 to 920 else 375 to 850
    }).around(compose)

    private val app = LauncherApp("example.maps/.Main", "example.maps", "Maps")
    private val layout = LauncherLayout(
        pages = listOf(listOf(LauncherItem.Workspace), emptyList()),
        dock = listOf(LauncherItem.App(app.component)),
    )
    private val destinations = mutableListOf<IntelligenceDestination>()
    private var privateOpens = 0

    private fun show(protected: Boolean = false) {
        compose.setContent {
            NovaLauncherScreen(
                NovaLauncherState(configured = true, cover = layout, inner = layout, separateDisplays = true),
                listOf(app), emptyList(), mapOf(app.component to Decision.APPROVED), true, false,
                LauncherUiActions(save = { true }, launchApp = {}, launchShortcut = {}, openWorkspace = {},
                    selectHome = {}, continueWithCurrentHome = {}, emergencyPhone = {}, reviewSanctuary = {},
                    setOutsideProtection = {}, chooseWallpaper = {}, addWidget = {}, removeWidget = {},
                    exportLayout = {}, importLayout = {}, renderWidget = { _, _ -> }),
                launcherProtected = protected,
                intelligence = LauncherIntelligenceActions(
                    openPrivate = { privateOpens++ },
                    openDestination = { destinations += it; true },
                    openEvidence = { false },
                ),
            )
        }
    }

    private fun openWithSwipes() {
        val pager = compose.onNodeWithTag("launcher-home-pages")
        pager.performTouchInput { swipeRight() }
        compose.onNodeWithTag("launcher-guardian-home").assertIsDisplayed()
        pager.performTouchInput { swipeRight() }
        compose.onNodeWithTag("nova-home-intelligence").assertIsDisplayed()
    }

    @Test fun coverKeepsGuardianNextToHomeAndOpensIntelligenceWithDock() {
        show()
        openWithSwipes()
        compose.onNodeWithTag("intelligence-cover").assertIsDisplayed()
        compose.onNodeWithTag("launcher-dock-slot-0").assertIsDisplayed()
        compose.onNodeWithText("No email provider connected").assertExists()
        compose.onNodeWithText("Unlock private briefing").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, privateOpens) }
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithContentDescription("App Library page").performClick()
        compose.onNodeWithTag("launcher-app-library").assertIsDisplayed()
    }

    @Test fun innerShowsBriefingAndChatAndUsesLocalNavigation() {
        show()
        compose.onNodeWithContentDescription("Nova Intelligence page").performClick()
        compose.onNodeWithTag("intelligence-inner").assertIsDisplayed()
        compose.onNodeWithText("Daily briefing").assertIsDisplayed()
        send("Open Nova Notes")
        compose.runOnIdle { assertEquals(listOf(IntelligenceDestination.NOTES), destinations) }
        send("Open Hidden Vault")
        compose.onNodeWithText("That app is outside the assistant's approved destinations.").assertExists()
        send("Show my held messages")
        compose.onNodeWithText("Open held messages?").assertIsDisplayed()
        compose.onNodeWithText("Open Guardian").performClick()
        compose.runOnIdle { assertEquals(IntelligenceDestination.HELD, destinations.last()) }
    }

    @Test fun coverSanctuaryPausesPrivateAndCloudQuestions() {
        show(protected = true)
        compose.onNodeWithContentDescription("Nova Intelligence page").performClick()
        compose.onNodeWithText("Private briefing paused during Sanctuary").assertExists()
        compose.onAllNodesWithText("Check cloud chat").assertCountEquals(0)
        compose.onNodeWithText("Ask Nova").performClick()
        send("What is a haiku?")
        compose.onNodeWithText("Cloud questions are paused during Sanctuary.", substring = true).assertExists()
        compose.onAllNodesWithText("Send question").assertCountEquals(0)
    }

    @Test fun coverCloudDisconnectedLeavesOfflineCommandsAvailable() {
        show()
        compose.onNodeWithContentDescription("Nova Intelligence page").performClick()
        compose.onNodeWithText("Check cloud chat").performScrollTo().performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("Cloud is disconnected. Configure Nova Brain in Settings.")
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Ask Nova").performClick()
        send("What is a haiku?")
        compose.onNodeWithText("Send one question to Nova Brain?").assertIsDisplayed()
        compose.onNodeWithText("Send question").performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("Cloud is disconnected.", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        send("Open Nova Notes")
        compose.runOnIdle { assertEquals(listOf(IntelligenceDestination.NOTES), destinations) }
    }

    private fun send(message: String) {
        compose.onNodeWithTag("intelligence-input").performTextInput(message)
        compose.onNodeWithContentDescription("Send message").performClick()
    }
}
