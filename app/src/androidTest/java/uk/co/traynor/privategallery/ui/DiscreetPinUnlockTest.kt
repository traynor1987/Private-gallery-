package uk.co.traynor.privategallery.ui

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.lifecycle.Lifecycle
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import uk.co.traynor.privategallery.PinUnlock

class DiscreetPinUnlockTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun ordinaryUnlockNeverCallsHiddenAndIntentIsOneAttemptOnly() {
        var primary = 0
        var hidden = 0
        compose.setContent { MaterialTheme {
            PinUnlock({ primary++; Result.failure(SecurityException()) }, false, {}, {},
                onDirectHiddenUnlock = { hidden++; Result.failure(SecurityException()) })
        } }
        compose.onAllNodesWithText("Hidden", substring = true).assertCountEquals(0)
        compose.onNodeWithText("PIN").performTextInput("123456")
        compose.onNodeWithText("Unlock").performClick()
        compose.runOnIdle { assertEquals(1, primary); assertEquals(0, hidden) }
        compose.onNodeWithText("Private Gallery").performTouchInput { longClick() }
        compose.onAllNodesWithText("Hidden", substring = true).assertCountEquals(0)
        compose.onNodeWithText("PIN").performTextInput("728491")
        compose.onNodeWithText("Unlock").performClick()
        compose.runOnIdle { assertEquals(1, primary); assertEquals(1, hidden) }
        compose.onNodeWithText("PIN").performTextInput("123456")
        compose.onNodeWithText("Unlock").performClick()
        compose.runOnIdle { assertEquals(2, primary); assertEquals(1, hidden) }
    }
    @Test fun backgroundClearsDiscreetIntentAndPin() {
        var primary = 0
        var hidden = 0
        compose.setContent { MaterialTheme {
            PinUnlock({ primary++; Result.failure(SecurityException()) }, false, {}, {},
                onDirectHiddenUnlock = { hidden++; Result.failure(SecurityException()) })
        } }
        compose.onNodeWithText("Private Gallery").performTouchInput { longClick() }
        compose.onNodeWithText("PIN").performTextInput("728491")
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.onNodeWithText("PIN").assert(hasText("", substring = false))
        compose.onNodeWithText("PIN").performTextInput("123456")
        compose.onNodeWithText("Unlock").performClick()
        compose.runOnIdle { assertEquals(1, primary); assertEquals(0, hidden) }
        compose.onAllNodesWithText("Hidden", substring = true).assertCountEquals(0)
    }

    @Test fun restoredScreenStartsWithNeutralPrimaryIntent() {
        var primary = 0
        var hidden = 0
        val restoration = StateRestorationTester(compose)
        restoration.setContent { MaterialTheme {
            PinUnlock({ primary++; Result.failure(SecurityException()) }, false, {}, {},
                onDirectHiddenUnlock = { hidden++; Result.failure(SecurityException()) })
        } }
        compose.onNodeWithText("Private Gallery").performTouchInput { longClick() }
        restoration.emulateSavedInstanceStateRestore()
        compose.onAllNodesWithText("Hidden", substring = true).assertCountEquals(0)
        compose.onNodeWithText("PIN").performTextInput("123456")
        compose.onNodeWithText("Unlock").performClick()
        compose.runOnIdle { assertEquals(1, primary); assertEquals(0, hidden) }
    }

}
