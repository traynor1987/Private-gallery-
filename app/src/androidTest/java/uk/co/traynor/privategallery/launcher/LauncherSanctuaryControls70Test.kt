package uk.co.traynor.privategallery.launcher

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import uk.co.traynor.privategallery.core.sanctuary.SanctuaryMode
import uk.co.traynor.privategallery.ui.BrowserViewportRule

class LauncherSanctuaryControls70Test {
    private val compose = createAndroidComposeRule<ComponentActivity>()
    private val viewport = BrowserViewportRule { method ->
        if (method.startsWith("inner")) 820 to 920 else 375 to 850
    }
    @get:Rule val rules: TestRule = RuleChain.outerRule(viewport).around(compose)

    @Test fun coverCanStartASelectedModeThroughTheExistingPolicyCallback() {
        var requested: SanctuaryMode? = null
        compose.setContent {
            LauncherSanctuaryDialog(
                status = null,
                modeMinutes = mapOf(SanctuaryMode.GAMING to 35),
                guardianStatus = "Guardian off",
                onStart = { requested = it },
                onReview = {}, onPause = {}, onPhone = {}, onManage = {}, onDismiss = {},
            )
        }
        compose.onNodeWithTag("launcher-sanctuary-mode-gaming").performClick()
        compose.onNodeWithText("35 min", useUnmergedTree = true).assertIsDisplayed()
        compose.runOnIdle { assertEquals(null, requested) }
        compose.onNodeWithTag("launcher-sanctuary-start").performClick()
        compose.runOnIdle { assertEquals(SanctuaryMode.GAMING, requested) }
    }

    @Test fun innerShowsCountdownAndPauseStateWithoutReplacingTheMode() {
        var status by mutableStateOf(LauncherSanctuaryStatus(SanctuaryMode.WORK, 3_661_000L, paused = false))
        var opened = 0
        compose.setContent { LauncherSanctuaryStatusCard(status) { opened++ } }
        compose.onNodeWithTag("launcher-sanctuary-countdown", useUnmergedTree = true).assertTextEquals("1:01:01")
        compose.onNodeWithText("Work Mode", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("launcher-sanctuary-status").performClick()
        compose.runOnIdle {
            assertEquals(1, opened)
            status = status.copy(remainingMillis = 999L, paused = true)
        }
        compose.onNodeWithTag("launcher-sanctuary-countdown", useUnmergedTree = true).assertTextEquals("0:01")
        compose.onNodeWithText("Launcher protection paused", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test fun coverActiveControlsOfferPauseAndKeepRecoveryAvailableWhilePaused() {
        var status by mutableStateOf(LauncherSanctuaryStatus(SanctuaryMode.PANIC, 60_000L, paused = false))
        var pauses = 0
        var phone = 0
        compose.setContent {
            LauncherSanctuaryDialog(
                status, emptyMap(), "Guardian available", onStart = {},
                onReview = {}, onPause = { pauses++ }, onPhone = { phone++ }, onManage = {}, onDismiss = {},
            )
        }
        compose.onNodeWithText("Pause protection for five minutes").performClick()
        compose.runOnIdle {
            assertEquals(1, pauses)
            status = status.copy(paused = true)
        }
        compose.onNodeWithText("Pause protection for five minutes").assertDoesNotExist()
        compose.onNodeWithText("Phone and emergency access").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, phone) }
    }
}
