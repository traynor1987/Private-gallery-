package uk.co.traynor.privategallery.ui

import android.app.ActivityManager
import android.app.KeyguardManager
import android.app.admin.DevicePolicyManager
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ActivityNotFoundException
import android.provider.Settings
import android.view.KeyEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import uk.co.traynor.privategallery.core.security.NovaScreenPinningPolicy.State

/** Component tests replace only external OS responses. One test exercises and cancels the actual
 * public startLockTask confirmation. No system settings or device-owner state are written.
 * Physical Samsung gesture/Fold/call behavior remains owner-device acceptance. */
class NovaScreenPinning61Test {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private class Platform : NovaScreenPinningPlatform {
        var mode = State.NONE
        var locked: Boolean? = false
        var managed: Boolean? = false
        var requests = 0
        var reads = 0
        var beforeRequestRead: (() -> Unit)? = null
        val settings = mutableListOf<String>()
        var failSecurity = false
        var failAllSettings = false
        var denySettings = false
        var failStateRead = false
        var failRequest = false
        override fun state(): State {
            reads++; beforeRequestRead?.invoke()
            if (failStateRead) throw SecurityException()
            return mode
        }
        override fun deviceLocked() = locked
        override fun managedLockTaskPermitted() = managed
        override fun requestPinning() { requests++; if (failRequest) throw SecurityException() }
        override fun openSettings(action: String) {
            settings += action
            if (denySettings) throw SecurityException()
            if (failAllSettings || (failSecurity && action == Settings.ACTION_SECURITY_SETTINGS)) throw ActivityNotFoundException()
        }
    }

    private fun show(platform: Platform, current: () -> Boolean = { true }): NovaScreenPinningController {
        lateinit var controller: NovaScreenPinningController
        compose.runOnIdle { controller = NovaScreenPinningController(compose.activity, current, platform) }
        compose.setContent { PrivateGalleryTheme { NovaScreenPinningSettingsContent(controller) } }
        compose.waitForIdle()
        compose.waitUntil(5_000) { compose.activity.hasWindowFocus() }
        compose.runOnIdle { controller.refresh() }
        return controller
    }

    @Test fun idleIsTruthfulAndRequestIsNeverReportedAsConfirmedPinning() {
        val platform = Platform()
        val controller = show(platform)
        try {
            compose.onNodeWithTag("nova-pinning-status").assertTextEquals("NovaOS is not pinned.")
            compose.onNodeWithText("Enable App pinning in Android Settings if needed.", substring = true).assertExists()
            compose.onNodeWithTag("nova-pinning-request").performClick()
            compose.runOnIdle { assertEquals(1, platform.requests); assertEquals(State.NONE, controller.observation.state) }
            compose.onNodeWithTag("nova-pinning-status").assertTextEquals("NovaOS is not pinned.")
            compose.onNodeWithText("Unpin NovaOS").assertDoesNotExist()
            compose.onNodeWithText("Hidden", substring = true).assertDoesNotExist()
            // Model the external OS result; production refresh reads ActivityManager instead.
            compose.runOnIdle { platform.mode = State.PINNED; controller.refresh() }
            compose.onNodeWithTag("nova-pinning-status").assertTextEquals("Android reports screen pinning is active.")
            compose.onNodeWithTag("nova-pinning-request").assertIsNotEnabled()
            compose.onNodeWithText("Use the Android unpin gesture shown in its confirmation.", substring = true).assertExists()
        } finally { compose.runOnIdle { controller.close() } }
    }

    @Test fun revokedAndUnreadableManagedKeyguardAndLockedStatesCannotInvokeTheOs() {
        val platform = Platform()
        var current = true
        val controller = show(platform) { current }
        try {
            compose.runOnIdle {
                current = false; assertFalse(controller.requestPinning())
                current = true
                platform.managed = true; assertFalse(controller.requestPinning())
                platform.managed = null; assertFalse(controller.requestPinning())
                platform.managed = false; platform.locked = true; assertFalse(controller.requestPinning())
                platform.locked = null; assertFalse(controller.requestPinning())
                platform.locked = false; platform.mode = State.LOCKED; assertFalse(controller.requestPinning())
                platform.mode = State.UNKNOWN; assertFalse(controller.requestPinning())
                platform.mode = State.NONE; platform.failStateRead = true; assertFalse(controller.requestPinning())
                assertEquals(0, platform.requests)
            }
            compose.onNodeWithTag("nova-pinning-status").assertTextEquals("Android pinning state is unavailable.")
        } finally { compose.runOnIdle { controller.close() } }
    }

    @Test fun stateReadCannotReviveRevokedAuthorityAndClosedCallbacksStayRetired() {
        val platform = Platform()
        var current = true
        val controller = show(platform) { current }
        compose.runOnIdle {
            platform.beforeRequestRead = { current = false }
            assertFalse(controller.requestPinning())
            assertEquals(0, platform.requests)
            platform.beforeRequestRead = null; current = true
            controller.close()
            val reads = platform.reads
            assertFalse(controller.requestPinning()); assertFalse(controller.openAndroidSettings())
            controller.refresh()
            assertEquals(reads, platform.reads); assertEquals(0, platform.requests)
        }
    }

    @Test fun pauseResumeFocusAndRecreationNeverAutomaticallyRequestPinning() {
        val platform = Platform()
        val controller = show(platform)
        try {
            compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
            InstrumentationRegistry.getInstrumentation().runOnMainSync { assertFalse(controller.requestPinning()) }
            compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
            compose.runOnIdle { controller.refresh(); assertEquals(0, platform.requests) }
            compose.activityRule.scenario.recreate()
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                assertFalse(controller.requestPinning()); assertEquals(0, platform.requests)
            }
        } finally { InstrumentationRegistry.getInstrumentation().runOnMainSync { controller.close() } }
    }

    @Test fun securitySettingsFallbackHandlesUnavailableActivitiesWithoutPretendingToEnablePinning() {
        val platform = Platform().apply { failSecurity = true }
        val controller = show(platform)
        try {
            compose.onNodeWithTag("nova-pinning-settings").performClick()
            compose.runOnIdle {
                assertEquals(listOf(Settings.ACTION_SECURITY_SETTINGS, Settings.ACTION_SETTINGS), platform.settings)
                assertEquals(State.NONE, controller.observation.state); assertEquals(0, platform.requests)
                platform.failAllSettings = true; assertFalse(controller.openAndroidSettings())
                platform.failAllSettings = false; platform.denySettings = true
                assertFalse(controller.openAndroidSettings())
            }
            compose.onNodeWithText("Android Settings could not be opened.").assertExists()
        } finally { compose.runOnIdle { controller.close() } }
    }

    @Test fun AndroidRequestFailureDoesNotClaimAPinOrRetryAutomatically() {
        val platform = Platform().apply { failRequest = true }
        val controller = show(platform)
        try {
            compose.onNodeWithTag("nova-pinning-request").performClick()
            compose.onNodeWithText("Android could not start screen pinning.").assertExists()
            compose.onNodeWithTag("nova-pinning-status").assertTextEquals("NovaOS is not pinned.")
            compose.runOnIdle { controller.refresh(); assertEquals(1, platform.requests) }
        } finally { compose.runOnIdle { controller.close() } }
    }

    @Test fun actualPublicServicesAndLegitimateOsConfirmationCancelLeaveTheDeviceUnpinned() {
        val activity = compose.activity
        val manager = activity.getSystemService(ActivityManager::class.java)
        val keyguard = activity.getSystemService(KeyguardManager::class.java)
        val policy = activity.getSystemService(DevicePolicyManager::class.java)
        assumeTrue("Requires an initially unpinned, unlocked, unmanaged foreground test task",
            manager.lockTaskModeState == ActivityManager.LOCK_TASK_MODE_NONE &&
                !keyguard.isDeviceLocked && !keyguard.isKeyguardLocked && !policy.isLockTaskPermitted(activity.packageName))
        val platform = AndroidNovaScreenPinningPlatform(activity)
        lateinit var controller: NovaScreenPinningController
        compose.runOnIdle {
            assertEquals(State.NONE, platform.state())
            assertEquals(keyguard.isDeviceLocked || keyguard.isKeyguardLocked, platform.deviceLocked())
            assertEquals(policy.isLockTaskPermitted(activity.packageName), platform.managedLockTaskPermitted())
            controller = NovaScreenPinningController(activity, { true }, platform)
        }
        compose.setContent { PrivateGalleryTheme { NovaScreenPinningSettingsContent(controller) } }
        compose.waitUntil(5_000) { activity.hasWindowFocus() }
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val automation = instrumentation.uiAutomation
        val originalInfo = automation.serviceInfo
        val originalFlags = originalInfo.flags
        try {
            originalInfo.flags = originalFlags or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
            automation.serviceInfo = originalInfo
            compose.runOnIdle { assertTrue(controller.requestPinning()) }
            val until = android.os.SystemClock.uptimeMillis() + 8_000
            var cancel: AccessibilityNodeInfo? = null
            while (cancel == null && android.os.SystemClock.uptimeMillis() < until) {
                cancel = findCancel(automation.rootInActiveWindow)
                if (cancel == null) android.os.SystemClock.sleep(50)
            }
            assertNotNull("Expected Android's actual screen-pinning confirmation", cancel)
            assertEquals("A request must not pin before Android confirmation", ActivityManager.LOCK_TASK_MODE_NONE, manager.lockTaskModeState)
            assertTrue(cancel!!.performAction(AccessibilityNodeInfo.ACTION_CLICK))
            instrumentation.waitForIdleSync()
            assertEquals(ActivityManager.LOCK_TASK_MODE_NONE, manager.lockTaskModeState)
        } finally {
            // Never alter a pre-existing pin: baseline NONE was required before this test.
            if (findCancel(automation.rootInActiveWindow) != null) instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
            instrumentation.runOnMainSync {
                if (manager.lockTaskModeState == ActivityManager.LOCK_TASK_MODE_PINNED) activity.stopLockTask()
                controller.close()
            }
            originalInfo.flags = originalFlags
            automation.serviceInfo = originalInfo
        }
        assertEquals("Confirmation cancellation must restore baseline", ActivityManager.LOCK_TASK_MODE_NONE, manager.lockTaskModeState)
    }

    private fun findCancel(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        val resource = node.viewIdResourceName.orEmpty()
        if (node.packageName?.toString() == "com.android.systemui" && node.isClickable &&
            (resource.endsWith(":id/screen_pinning_cancel_button") || node.text?.toString().equals("Cancel", ignoreCase = true))) return node
        for (index in 0 until node.childCount) findCancel(node.getChild(index))?.let { return it }
        return null
    }
}
