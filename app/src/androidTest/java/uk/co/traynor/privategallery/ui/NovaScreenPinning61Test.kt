package uk.co.traynor.privategallery.ui

import android.app.ActivityManager
import android.app.UiAutomation
import android.app.KeyguardManager
import android.app.admin.DevicePolicyManager
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ActivityNotFoundException
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.view.KeyEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.io.File
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
        val power = activity.getSystemService(PowerManager::class.java)
        assumeTrue("Requires an initially unpinned, unsecured, unlocked, interactive unmanaged fixture; secure Samsung exit remains physical acceptance",
            manager.lockTaskModeState == ActivityManager.LOCK_TASK_MODE_NONE &&
                power.isInteractive && !keyguard.isDeviceSecure && !keyguard.isDeviceLocked &&
                !keyguard.isKeyguardLocked && !policy.isLockTaskPermitted(activity.packageName))
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
        var requestAttempted = false
        var cancellationObserved = false
        try {
            // The genuine confirmation is a non-focusable SystemUI navigation-panel window.
            // rootInActiveWindow remains the app, so retrieve ALL interactive window roots.
            originalInfo.flags = originalFlags or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            automation.serviceInfo = originalInfo
            compose.runOnIdle { requestAttempted = true; assertTrue(controller.requestPinning()) }
            try {
                waitFor("Expected Android's actual screen-pinning confirmation", { windowSummary(automation) }) {
                    pinningWindow(automation) != null
                }
            } catch (failure: AssertionError) {
                try {
                    // Evidence only: a generic SystemUI hierarchy is never accepted as a prompt.
                    saveConfirmationEvidence(activity, automation, null, "actual-android-confirmation-timeout")
                } catch (captureFailure: Exception) { failure.addSuppressed(captureFailure) }
                throw failure
            }
            val confirmation = checkNotNull(pinningWindow(automation))
            val evidence = saveConfirmationEvidence(activity, automation, confirmation)
            assertEquals("A request must not pin before Android confirmation", ActivityManager.LOCK_TASK_MODE_NONE, manager.lockTaskModeState)
            val cancel = findCancel(confirmation.root)
            if (cancel != null) {
                evidence.appendText("cancel_path=native_systemui_cancel_node_ACTION_CLICK\n")
                assertTrue(cancel.performAction(AccessibilityNodeInfo.ACTION_CLICK))
            } else {
                // Android hides the button group on gesture/large-screen layouts. Its real
                // ACTION_SCREEN_OFF handler cancels the confirmation without accepting a pin.
                // This fixture is unsecured, so normal wake/menu restores it without credentials.
                evidence.appendText("cancel_path=native_KEYCODE_SLEEP_ACTION_SCREEN_OFF\n")
                injectKey(automation, KeyEvent.KEYCODE_SLEEP)
                waitFor("Android screen-off must occur") { !power.isInteractive }
            }
            waitFor("Actual Android confirmation must disappear after cancellation") { pinningWindow(automation) == null }
            cancellationObserved = true
            wakeUnsecuredFixture(automation, power)
            waitFor("Unsecured fixture must return to its unlocked baseline") { !keyguard.isDeviceLocked && !keyguard.isKeyguardLocked }
            instrumentation.waitForIdleSync()
            assertEquals(ActivityManager.LOCK_TASK_MODE_NONE, manager.lockTaskModeState)
            evidence.appendText("confirmation_after_cancel=absent\nlockTaskModeState=${manager.lockTaskModeState}\ninteractive=${power.isInteractive}\nkeyguardLocked=${keyguard.isKeyguardLocked}\nrestore_actions=KEYCODE_WAKEUP_if_asleep,KEYCODE_MENU_unsecured_only\n")
        } finally {
            // Never alter a pre-existing pin: baseline NONE was required before this test.
            try {
                // Cleanup must not depend on the selector whose assertion may have failed.
                // Otherwise a full-screen nonfocusable panel can intercept later tests' touches.
                if (requestAttempted && !cancellationObserved) {
                    if (power.isInteractive) injectKey(automation, KeyEvent.KEYCODE_SLEEP)
                    waitFor("Cleanup screen-off must cancel pending confirmation") { !power.isInteractive }
                }
                wakeUnsecuredFixture(automation, power)
                waitFor("Cleanup must restore unlocked fixture") { !keyguard.isDeviceLocked && !keyguard.isKeyguardLocked }
            } finally {
                try {
                    instrumentation.runOnMainSync {
                        try {
                            if (manager.lockTaskModeState == ActivityManager.LOCK_TASK_MODE_PINNED) activity.stopLockTask()
                        } finally { controller.close() }
                    }
                } finally {
                    originalInfo.flags = originalFlags
                    automation.serviceInfo = originalInfo
                }
            }
        }
        assertEquals("Confirmation cancellation must restore baseline", ActivityManager.LOCK_TASK_MODE_NONE, manager.lockTaskModeState)
        assertTrue("Interactive baseline must be restored", power.isInteractive)
    }

    private fun pinningWindow(automation: UiAutomation): AccessibilityWindowInfo? = automation.windows.firstOrNull { window ->
        val root = window.root
        // AccessibilityWindowInfo.title is legitimately null for this native panel on API37.
        // Identify actual SystemUI pinning instructions, never an arbitrary SystemUI window.
        window.type == AccessibilityWindowInfo.TYPE_SYSTEM && root?.packageName?.toString() == "com.android.systemui" &&
            root.findAccessibilityNodeInfosByViewId("com.android.systemui:id/screen_pinning_description").any {
                it.isVisibleToUser && it.packageName?.toString() == "com.android.systemui" &&
                    it.text?.toString()?.contains("unpin", ignoreCase = true) == true
            }
    }

    private fun windowSummary(automation: UiAutomation): String = automation.windows.joinToString {
        val bounds = Rect().also { bounds -> it.getBoundsInScreen(bounds) }
        val root = it.root
        val pinning = if (root?.packageName?.toString() == "com.android.systemui") {
            root.findAccessibilityNodeInfosByViewId("com.android.systemui:id/screen_pinning_description").joinToString { node ->
                "${node.viewIdResourceName}:visible=${node.isVisibleToUser}:text=${node.text}"
            }
        } else ""
        "${it.title}:${it.type}:focused=${it.isFocused}:package=${root?.packageName}:bounds=$bounds:pinningInstructions=[$pinning]"
    }

    private fun waitFor(message: String, diagnostics: () -> String = { "" }, condition: () -> Boolean) {
        val until = SystemClock.uptimeMillis() + 8_000
        while (!condition() && SystemClock.uptimeMillis() < until) SystemClock.sleep(50)
        assertTrue("$message ${diagnostics()}", condition())
    }

    private fun injectKey(automation: UiAutomation, code: Int) {
        val now = SystemClock.uptimeMillis()
        assertTrue(automation.injectInputEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, code, 0), true))
        assertTrue(automation.injectInputEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, code, 0), true))
    }

    private fun wakeUnsecuredFixture(automation: UiAutomation, power: PowerManager) {
        if (!power.isInteractive) injectKey(automation, KeyEvent.KEYCODE_WAKEUP)
        waitFor("Interactive fixture must wake") { power.isInteractive }
        injectKey(automation, KeyEvent.KEYCODE_MENU)
    }

    private fun saveConfirmationEvidence(
        activity: ComponentActivity,
        automation: UiAutomation,
        window: AccessibilityWindowInfo?,
        name: String = "actual-android-confirmation",
    ): File {
        assertEquals(ComponentActivity::class.java, activity.javaClass)
        assertTrue(activity.packageName.endsWith(".hiddenprototype"))
        val path = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
            ?: File(checkNotNull(activity.externalMediaDirs.firstOrNull()), "additional_test_output").absolutePath
        val directory = File(path, "nova-pinning61").apply { check(isDirectory || mkdirs()) }
        val image = checkNotNull(automation.takeScreenshot())
        try {
            File(directory, "$name.png").outputStream().use {
                assertTrue(image.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
        } finally { image.recycle() }
        val metadata = File(directory, "$name.txt")
        metadata.writeText(buildString {
            appendLine("capture=actual_android_systemui_over_synthetic_component_activity")
            appendLine(windowSummary(automation))
            fun appendNode(node: AccessibilityNodeInfo?, depth: Int = 0) {
                if (node == null || depth > 16) return
                appendLine("${" ".repeat(depth)}${node.viewIdResourceName} text=${node.text} clickable=${node.isClickable}")
                for (index in 0 until node.childCount) appendNode(node.getChild(index), depth + 1)
            }
            if (window != null) appendNode(window.root)
            else automation.windows.filter { it.root?.packageName?.toString() == "com.android.systemui" }.forEach {
                appendLine("diagnostic_only_system_window=${it.title}")
                appendNode(it.root)
            }
        })
        return metadata
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
