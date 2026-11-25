package uk.co.traynor.privategallery

import android.view.WindowManager
import android.view.inspector.WindowInspector
import androidx.compose.runtime.MutableState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import uk.co.traynor.privategallery.core.guardian.GuardianInboxRow
import uk.co.traynor.privategallery.core.guardian.GuardianInboxSection
import uk.co.traynor.privategallery.launcher.GuardianInboxActivity

class CapturePolicy98Test {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun primaryBrainStatusAllowsCaptureWithoutAllowingCaptureOfHidden() {
        check(BuildConfig.DEBUG)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var activity: MainActivity
            lateinit var session: ProtectedSessionState
            val key = ByteArray(32) { 17 }
            try {
                scenario.onActivity { owner ->
                    activity = owner
                    session = ViewModelProvider(owner)[ProtectedSessionState::class.java]
                    owner.fixtureState<Boolean>("allowScreenshots").value = false
                    session.key = key
                    session.session.unlock()
                    session.homePrimaryAuthentication = session.session.captureAuthenticationEpoch()
                    session.homeVisible = false
                    owner.openNovaSettings()
                }
                compose.onNodeWithText("Nova Brain").performScrollTo().assertIsDisplayed()
                compose.waitUntil(8_000) { activity.window.attributes.flags and secureFlag == 0 }
                compose.onNodeWithText("Nova Brain").performClick()
                compose.onNodeWithText("Nova Brain connection").assertIsDisplayed()
                compose.runOnIdle { assertTrue(activity.window.attributes.flags and secureFlag == 0) }

                compose.onNodeWithContentDescription("Back to Settings").performClick()
                compose.onNodeWithText("Privacy & Security").performScrollTo().performClick()
                compose.waitUntil(8_000) { activity.window.attributes.flags and secureFlag != 0 }

                scenario.onActivity { openHiddenSpaceFixtureDashboard(it, session) }
                compose.onNodeWithTag("hidden-workspace-pager").assertIsDisplayed()
                compose.runOnIdle { assertTrue(activity.window.attributes.flags and secureFlag != 0) }
            } finally {
                scenario.onActivity { it.panicExit() }
                key.fill(0)
            }
        }
    }

    @Test fun guardianSettingsAndDiagnosticsLeavePrivateInboxWindowSecure() {
        check(BuildConfig.DEBUG)
        ActivityScenario.launch(GuardianInboxActivity::class.java).use { scenario ->
            lateinit var activity: GuardianInboxActivity
            scenario.onActivity {
                activity = it
                it.fixtureState<Boolean>("authenticated").value = true
                it.fixtureState<List<GuardianInboxRow>>("rows").value = listOf(
                    GuardianInboxRow("synthetic-fixture", 7_123, GuardianInboxSection.READY, 0,
                        senderName = "Private fixture sender"),
                )
            }
            compose.onNodeWithText("Private fixture sender").assertIsDisplayed()
            compose.runOnIdle { assertTrue(activity.window.attributes.flags and secureFlag != 0) }

            scenario.onActivity { it.fixtureState<Boolean>("settingsOpen").value = true }
            compose.onNodeWithText("Inbox privacy").assertIsDisplayed()
            assertCapturableGuardianDialog(activity)

            // Display the same redacted preview opened by Share safe diagnostic view.
            scenario.onActivity {
                it.fixtureState<Boolean>("settingsOpen").value = false
                it.fixtureState<Boolean>("diagnostic").value = true
            }
            compose.onNodeWithText("Guardian diagnostics").assertIsDisplayed()
            compose.onNodeWithText("Sender redacted").assertIsDisplayed()
            compose.onNodeWithText("Private fixture sender").assertDoesNotExist()
            assertCapturableGuardianDialog(activity)
        }
    }

    private fun assertCapturableGuardianDialog(activity: GuardianInboxActivity) {
        compose.runOnIdle {
            val dialog = WindowInspector.getGlobalWindowViews().single {
                it !== activity.window.decorView && it.hasWindowFocus()
            }
            assertTrue((dialog.layoutParams as WindowManager.LayoutParams).flags and secureFlag == 0)
            assertTrue(activity.window.attributes.flags and secureFlag != 0)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> Any.fixtureState(name: String): MutableState<T> =
        javaClass.getDeclaredField("$name\$delegate").apply { isAccessible = true }.get(this) as MutableState<T>

    private val secureFlag = WindowManager.LayoutParams.FLAG_SECURE
}
