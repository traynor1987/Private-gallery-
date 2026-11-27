package uk.co.traynor.privategallery.novaos2.working

import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.view.inspector.WindowInspector
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import uk.co.traynor.privategallery.core.ui.AppTheme
import uk.co.traynor.privategallery.ui.PrivateGalleryTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.window.DialogWindowProvider
import java.io.File
import java.time.ZonedDateTime
import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import uk.co.traynor.privategallery.launcher.LauncherUiActions
import uk.co.traynor.privategallery.launcher.NovaLauncherState
import uk.co.traynor.privategallery.novaos2.LauncherNovaSystemsActions
import uk.co.traynor.privategallery.novaos2.NovaSystemConfig
import uk.co.traynor.privategallery.novaos2.NovaSystemHost
import uk.co.traynor.privategallery.novaos2.NovaSystemId
import uk.co.traynor.privategallery.novaos2.NovaSystemsPanel
import uk.co.traynor.privategallery.novaos2.novaSystemFeatures
import uk.co.traynor.privategallery.novaos2.captureNovaSystem
import uk.co.traynor.privategallery.ui.BrowserViewportRule

class WorkingModeUiTest {
    private val compose = createAndroidComposeRule<ComponentActivity>()

    @get:Rule val rule = RuleChain.outerRule(BrowserViewportRule {
        if (it.startsWith("inner")) 820 to 920 else 375 to 850
    }).around(compose)

    @Test fun coverDrivingHidesEditingAndFinishingPersistsTheShift() {
        val persisted = NovaSystemConfig(compose.activity, "working-mode")
        persisted.clear()
        try {
            show()
            val start = compose.onNodeWithTag("start-working-shift")
            compose.waitUntil(10_000) { start.isDisplayed() }
            start.performClick()
            val enter = compose.onNodeWithTag("enter-driving-mode")
            compose.waitUntil(10_000) { enter.isDisplayed() }
            enter.performClick()

            val speed = compose.onNodeWithTag("driving-speed")
            compose.waitUntil(10_000) { speed.isDisplayed() }
            speed.assertIsDisplayed()
            compose.onNodeWithText("Record shift totals").assertDoesNotExist()
            compose.onNodeWithText("Work apps").assertDoesNotExist()
            compose.onNodeWithText("Shift Tracker summary").assertDoesNotExist()
            captureNovaSystem("working-cover-driving", compose)
            compose.onNodeWithText("Emergency phone").assertIsDisplayed()

            compose.onNodeWithText("Leave Driving Mode").performScrollTo().performClick()
            compose.waitUntil(10_000) { enter.isDisplayed() }
            compose.onNodeWithText("End shift").performScrollTo().performClick()
            compose.waitUntil(10_000) { start.isDisplayed() }
            val state = WorkingState.decode(persisted.read()?.let(::JSONObject))
            assertNotNull(state.previous)
            assertFalse(state.active?.driving ?: false)
        } finally { persisted.clear() }
    }

    @Test fun coverMissingTrackerExplainsUnavailableSourceAndOffersNoUnboundWidget() {
        val persisted = NovaSystemConfig(compose.activity, "working-mode")
        persisted.clear()
        try {
            show()
            compose.waitUntil(10_000) { compose.onNodeWithTag("start-working-shift").isDisplayed() }
            compose.onNodeWithText("The stable Shift Tracker app is unavailable in this profile.")
                .performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Show shift summary").assertDoesNotExist()
            compose.onNodeWithText("Choose a Home widget").assertDoesNotExist()
        } finally { persisted.clear() }
    }

    @Test fun innerScheduledWorkingShiftStartsOnceWithoutDriving() {
        val persisted = NovaSystemConfig(compose.activity, "working-mode")
        persisted.clear()
        val start = ZonedDateTime.now().minusMinutes(10)
        val end = start.plusMinutes(20)
        try {
            persisted.write(WorkingState(schedule = WorkingSchedule(start.hour * 60 + start.minute,
                end.hour * 60 + end.minute, setOf(start.dayOfWeek))).encode().toString())
            show(wide = true)
            val enter = compose.onNodeWithTag("enter-driving-mode")
            compose.waitUntil(10_000) { enter.isDisplayed() }
            compose.onNodeWithTag("driving-speed").assertDoesNotExist()
            captureNovaSystem("working-inner-scheduled", compose)
            compose.onNodeWithText("End shift").performClick()
            compose.waitUntil(10_000) { compose.onNodeWithTag("start-working-shift").isDisplayed() }
            val state = WorkingState.decode(persisted.read()?.let(::JSONObject))
            assertNotNull(state.lastScheduledDay)
            assertFalse(state.active != null)
        } finally { persisted.clear() }
    }

    @Test fun coverVehicleSettingsMaskIdentifierAndUseAProtectedChooser() {
        val persisted = NovaSystemConfig(compose.activity, "working-mode")
        val syntheticIdentifier = "02:01:02:03:04:05"
        persisted.clear()
        try {
            persisted.write(WorkingState().setVehicle(syntheticIdentifier, automatic = false).encode().toString())
            show()
            compose.waitUntil(10_000) { compose.onNodeWithTag("start-working-shift").isDisplayed() }
            val change = compose.onNodeWithText("Change vehicle activation")
            change.performScrollTo().assertIsDisplayed()
            compose.onNodeWithText(syntheticIdentifier, substring = true).assertDoesNotExist()
            val stored = File(compose.activity.noBackupFilesDir, "ai-provider-novaos2-working-mode.enc")
            assertTrue(stored.isFile)
            assertFalse(stored.readBytes().decodeToString().contains(syntheticIdentifier))
            captureNovaSystem("working-cover-vehicle-redacted", compose)

            change.performClick()
            compose.onNodeWithText("Choose a paired vehicle").assertIsDisplayed()
            var flags: Int? = null
            compose.waitUntil(8_000) {
                flags = compose.runOnUiThread {
                    WindowInspector.getGlobalWindowViews().filter { it.isAttachedToWindow && it.windowVisibility == View.VISIBLE }
                        .mapNotNull { it.dialogWindow() }.singleOrNull()?.attributes?.flags
                }
                flags != null
            }
            assertTrue(checkNotNull(flags) and WindowManager.LayoutParams.FLAG_SECURE != 0)
        } finally { persisted.clear() }
    }

    private fun View.dialogWindow(): Window? {
        if (this is DialogWindowProvider) return window
        if (this !is ViewGroup) return null
        return (0 until childCount).firstNotNullOfOrNull { getChildAt(it).dialogWindow() }
    }

    private fun show(wide: Boolean = false) {
        compose.runOnUiThread { compose.activity.enableEdgeToEdge() }
        compose.setContent {
            PrivateGalleryTheme(if (wide) AppTheme.LIGHT else AppTheme.DARK) {
                NovaSystemsPanel(novaSystemFeatures, NovaSystemId.WORKING, drivingHost(wide))
            }
        }
    }

    private fun drivingHost(wide: Boolean = false) = NovaSystemHost(NovaLauncherState(), emptyList(), emptyMap(),
        LauncherUiActions(save = { true }, launchApp = {}, launchShortcut = {}, openWorkspace = {},
            selectHome = {}, continueWithCurrentHome = {}, emergencyPhone = {}, reviewSanctuary = {},
            setOutsideProtection = {}, chooseWallpaper = {}, addWidget = {}, removeWidget = {},
            exportLayout = {}, importLayout = {}, renderWidget = { _, _ -> }),
        wide = wide, available = setOf(NovaSystemId.WORKING), onBack = {}, open = { false },
        platform = LauncherNovaSystemsActions({ true }, { false }, { false }, { false }, { false }),
    )
}
