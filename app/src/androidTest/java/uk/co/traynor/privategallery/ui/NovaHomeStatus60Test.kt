package uk.co.traynor.privategallery.ui

import android.app.KeyguardManager
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.ParcelFileDescriptor
import android.os.PowerManager
import android.view.WindowInsets
import android.view.WindowManager
import androidx.compose.runtime.MutableState
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.ViewCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.Job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import uk.co.traynor.privategallery.BuildConfig
import uk.co.traynor.privategallery.MainActivity
import uk.co.traynor.privategallery.ProtectedSessionState
import uk.co.traynor.privategallery.Route
import uk.co.traynor.privategallery.core.home.HomeLayout
import uk.co.traynor.privategallery.core.security.AutoLockTimeout
import java.io.File

/** Actual Main/Home/SpaceContext and default Android provider. Only synthetic authority/data;
 * BatteryService emits real protected broadcasts. No replaced composition, provider or insets.
 * These tests catch a missing Home window policy separately from provider/rendering failures.
 */
class NovaHomeStatus60Test {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val activity get() = compose.activity
    private lateinit var output: File
    private lateinit var caseName: String
    private var sequence = 0

    private fun call(name: String, vararg args: Any?): Any? = MainActivity::class.java.declaredMethods
        .single { it.name == name }.apply { isAccessible = true }.invoke(activity, *args)

    @Suppress("UNCHECKED_CAST")
    private fun route() = MainActivity::class.java.getDeclaredField("route\$delegate")
        .apply { isAccessible = true }.get(activity) as MutableState<Route>

    private fun shell(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(
        instrumentation.uiAutomation.executeShellCommand(command)
    ).bufferedReader().use { it.readText().trim() }

    private class Backup(private val root: File) {
        private val files = if (root.exists()) root.walkTopDown().filter { it.isFile }
            .associate { it.relativeTo(root).path to it.readBytes() } else emptyMap()
        init { check(!root.exists() || root.deleteRecursively()) }
        fun restore() {
            check(!root.exists() || root.deleteRecursively())
            files.forEach { (path, bytes) ->
                File(root, path).apply { parentFile?.mkdirs(); writeBytes(bytes) }
                bytes.fill(0)
            }
        }
    }

    private fun restoreSettings(settings: SharedPreferences, values: Map<String, *>) {
        check(settings.edit().clear().apply {
            values.forEach { (name, value) -> when (value) {
                is Boolean -> putBoolean(name, value)
                is String -> putString(name, value)
                is Int -> putInt(name, value)
                is Long -> putLong(name, value)
                is Float -> putFloat(name, value)
                is Set<*> -> @Suppress("UNCHECKED_CAST") putStringSet(name, value as Set<String>)
                else -> error("Unsupported fixture preference")
            } }
        }.commit())
    }

    private fun fixture(name: String, body: () -> Unit) {
        check(BuildConfig.DEBUG) { "Synthetic status fixtures require the isolated test identity" }
        caseName = name
        sequence = 0
        // UTP collects this directory before uninstalling the isolated application. External
        // files disappear at uninstall and cannot be recovered by a post-Gradle adb pull.
        val additionalOutput = InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
            ?: File(checkNotNull(activity.externalMediaDirs.firstOrNull()), "additional_test_output").absolutePath
        output = File(additionalOutput, "nova-status60").apply { check(isDirectory || mkdirs()) }
        val retained = ViewModelProvider(activity)[ProtectedSessionState::class.java]
        val settings = activity.getSharedPreferences("private-gallery-settings", 0)
        val previous = settings.all.toMap()
        val roots = listOf("private-apps", "browser-session", "evidence-state/home2", "evidence-state/home1710")
            .map { Backup(File(activity.filesDir, it)) }
        val key = ByteArray(32) { 83 }
        val lifetime = activity.lifecycleScope.coroutineContext[Job]
        val originalBars = barsVisible()
        try {
            setBattery(73, false)
            compose.runOnIdle {
                call("applyHideContent", false)
                call("applyAutoLockTimeout", AutoLockTimeout.MINUTES_5)
                retained.hidden.leave(); retained.key = key; retained.session.unlock()
                retained.homePrimaryAuthentication = null; retained.homeVisible = false
                route().value = Route.VAULT
                // The ordinary owner preference is changed only inside this DEBUG synthetic
                // Primary fixture, and the complete settings snapshot is restored in finally.
                call("applyAllowScreenshots", true)
            }
            compose.waitUntil(15_000) { nodes("preview-home").size == 1 }
            compose.onNodeWithTag("preview-home").assertIsDisplayed()
            compose.waitUntil(10_000) {
                compose.runOnIdle {
                    activity.window.decorView.hasWindowFocus() && !activity.privacyCoverVisible() &&
                        activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE == 0
                }
            }
            checkpoint("default-home-before-window-manipulation")
            body()
        } catch (failure: Throwable) {
            runCatching { checkpoint("failure") }
            throw failure
        } finally {
            // Restore device state even after an assertion fails. No synthetic battery survives.
            try { shell("dumpsys battery reset") } finally {
                try {
                    instrumentation.runOnMainSync {
                        val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
                        if (originalBars == false) controller.hide(WindowInsetsCompat.Type.statusBars())
                        else controller.show(WindowInsetsCompat.Type.statusBars())
                        activity.lockNovaSpace(); retained.hidden.leave()
                        retained.key = null; retained.session.lock(); route().value = Route.LOCK
                    }
                    compose.activityRule.scenario.close()
                    runBlocking { lifetime?.children?.toList()?.joinAll() }
                } finally {
                    roots.forEach { it.restore() }
                    restoreSettings(settings, previous)
                    key.fill(0)
                }
            }
        }
    }

    // Removing Home's ownership of its immersive bar policy must fail this contract. Unlike the
    // isolated code59 tests, this method never hides bars on the Activity's behalf.
    @Test fun actualHomeInitiallyShowsRealStatusWithoutExternalWindowManipulation() = fixture("initial-home") {
        compose.waitUntil(10_000) { barsVisible() == false }
        assertEquals("Actual Home must establish its immersive status-bar policy", false, barsVisible())
        assertBattery(73)
        assertStatusFits()
        checkpoint("initial-home-contract", requireScreenshot = true)
        compose.runOnIdle { activity.lockNovaSpace() }
        compose.waitUntil(10_000) { nodes("preview-home").isEmpty() && barsVisible() == true }
        compose.onNodeWithTag("nova-system-status").assertDoesNotExist()
        checkpoint("locked-home-restores-android-bar")
    }

    // Manual hiding is diagnostic isolation: failures here implicate the actual provider, Space
    // context, rendering or lifecycle rather than the separate default Home bar policy contract.
    @Test fun manuallyHiddenActualHomeReceivesBatteryUpdatesAndRestoresAfterNativeReturnAndResume() = fixture("real-provider") {
        setBars(false)
        assertBattery(73) // Seeded before Home: exercises the real sticky initial snapshot.
        assertStatusFits()
        checkpoint("manual-hide-sticky73", requireScreenshot = true)
        setBattery(42, true)
        assertBattery(42)
        assertCharging()
        checkpoint("real-battery-broadcast42-charging", requireScreenshot = true)
        setBars(true)
        compose.onNodeWithTag("nova-system-status").assertDoesNotExist()
        checkpoint("android-bar-visible-no-duplicate")
        setBattery(37, false)
        setBars(false)
        assertBattery(37)
        compose.runOnIdle { call("launchHomeShortcut", HomeLayout.Shortcut.SETTINGS) }
        compose.onNodeWithTag("preview-home").assertDoesNotExist()
        checkpoint("native-settings")
        compose.onNodeWithTag("nova-home").assertIsDisplayed().performClick()
        compose.waitUntil(15_000) { nodes("preview-home").size == 1 }
        assertBattery(37)
        checkpoint("returned-to-actual-home", requireScreenshot = true)
        compose.activityRule.scenario.moveToState(Lifecycle.State.STARTED)
        instrumentation.runOnMainSync {
            assertFalse("Paused Home must not retain a resumed lifecycle",
                activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
        }
        // A deliberately paused/stopped Activity can unregister its entire Compose root.
        // That is a valid absence here; foreground queries must still require a hierarchy.
        compose.waitUntil(10_000) { nodes("nova-system-status", atLeastOneRootRequired = false).isEmpty() }
        checkpoint("paused-no-custom-status", allowNoRoots = true)
        setBattery(61, true)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.waitUntil(15_000) { nodes("preview-home").size == 1 }
        assertBattery(61)
        assertCharging()
        assertStatusFits()
        checkpoint("resumed-real-battery61", requireScreenshot = true)
    }

    @Test fun manuallyHiddenActualHomeStatusFitsCompactWideAndLandscapeConfigurations() = fixture("actual-window-bounds") {
        val oldSize = Regex("Override size: (\\d+x\\d+)").find(shell("wm size"))?.groupValues?.get(1)
        val oldDensity = Regex("Override density: (\\d+)").find(shell("wm density"))?.groupValues?.get(1)
        val oldRotation = shell("settings get system user_rotation").takeIf { it.matches(Regex("[0-3]")) }
        val oldAutoRotation = shell("settings get system accelerometer_rotation").takeIf { it in listOf("0", "1") }
        try {
            shell("settings put system accelerometer_rotation 0")
            shell("settings put system user_rotation 0")
            shell("wm density 160")
            for ((label, dimensions) in listOf("compact" to "360x800", "wide" to "840x800", "landscape" to "840x360")) {
                shell("wm size $dimensions")
                compose.waitUntil(15_000) {
                    compose.runOnIdle {
                        val config = activity.resources.configuration
                        if (label == "compact") config.screenWidthDp < 420
                        else config.screenWidthDp >= 600 && (label != "landscape" || config.screenHeightDp < 500)
                    } && nodes("preview-home").size == 1
                }
                setBars(false)
                assertBattery(73)
                assertStatusFits()
                checkpoint("$label-real-window", requireScreenshot = true)
                compose.onNodeWithContentDescription("Open Quick Panel").assertIsDisplayed()
            }
        } finally {
            // Preserve any pre-existing override instead of assuming a particular AVD size.
            shell("wm size ${oldSize ?: "reset"}")
            shell("wm density ${oldDensity ?: "reset"}")
            shell(if (oldRotation == null) "settings delete system user_rotation" else "settings put system user_rotation $oldRotation")
            shell(if (oldAutoRotation == null) "settings delete system accelerometer_rotation" else "settings put system accelerometer_rotation $oldAutoRotation")
        }
    }

    private fun barsVisible(): Boolean? {
        var visible: Boolean? = null
        instrumentation.runOnMainSync {
            visible = activity.window.decorView.rootWindowInsets?.isVisible(WindowInsets.Type.statusBars())
        }
        return visible
    }

    private fun setBars(visible: Boolean) {
        compose.runOnUiThread {
            val decor = activity.window.decorView
            val controller = WindowCompat.getInsetsController(activity.window, decor)
            if (visible) controller.show(WindowInsetsCompat.Type.statusBars())
            else controller.hide(WindowInsetsCompat.Type.statusBars())
            ViewCompat.requestApplyInsets(decor)
        }
        compose.waitUntil(10_000) { barsVisible() == visible }
        compose.waitForIdle()
    }

    private fun setBattery(percent: Int, charging: Boolean) {
        shell("dumpsys battery unplug")
        shell("dumpsys battery set level $percent")
        shell("dumpsys battery set ac ${if (charging) 1 else 0}")
        shell("dumpsys battery set status ${if (charging) BatteryManager.BATTERY_STATUS_CHARGING else BatteryManager.BATTERY_STATUS_DISCHARGING}")
        val expectedStatus = if (charging) BatteryManager.BATTERY_STATUS_CHARGING else BatteryManager.BATTERY_STATUS_DISCHARGING
        compose.waitUntil(10_000) { stickyBattery().let {
            it?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) == percent &&
                it?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) == expectedStatus
        } }
        val initial = stickyBattery()
        assertEquals("BatteryService fixture must publish the requested level", percent,
            initial?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1))
        assertEquals("BatteryService fixture must publish the requested charging state",
            expectedStatus,
            initial?.getIntExtra(BatteryManager.EXTRA_STATUS, -1))
    }

    private fun stickyBattery(): Intent? {
        var battery: Intent? = null
        instrumentation.runOnMainSync { battery = activity.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) }
        return battery
    }

    private fun nodes(tag: String, atLeastOneRootRequired: Boolean = true) =
        compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes(atLeastOneRootRequired)

    private fun assertBattery(percent: Int) {
        compose.waitUntil(10_000) { nodes("nova-status-percent").singleOrNull()?.config
            ?.getOrNull(SemanticsProperties.Text)?.singleOrNull()?.text == "$percent%" }
        compose.onNodeWithTag("nova-status-percent", useUnmergedTree = true).assertTextEquals("$percent%")
        compose.onNodeWithTag("nova-system-status").assertIsDisplayed()
        compose.onAllNodesWithTag("nova-system-status").assertCountEquals(1)
    }

    private fun assertCharging() {
        compose.waitUntil(10_000) { nodes("nova-system-status").singleOrNull()?.config
            ?.getOrNull(SemanticsProperties.ContentDescription)?.any { "Charging" in it.split(", ") } == true }
        val descriptions = compose.onNodeWithTag("nova-system-status").fetchSemanticsNode()
            .config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()
        assertTrue("Status must report the real charging state: $descriptions",
            descriptions.any { "Charging" in it.split(", ") })
    }

    private fun assertStatusFits() {
        val home = compose.onNodeWithTag("preview-home").fetchSemanticsNode().boundsInRoot
        val status = compose.onNodeWithTag("nova-system-status").fetchSemanticsNode().boundsInRoot
        assertTrue("Status must have measured content", status.width > 0 && status.height > 0)
        assertTrue("Status must fit Home horizontally: $status in $home", status.left >= home.left && status.right <= home.right)
        assertTrue("Status must fit Home vertically: $status in $home", status.top >= home.top && status.bottom <= home.bottom)
    }

    private fun checkpoint(phase: String, requireScreenshot: Boolean = false, allowNoRoots: Boolean = false) {
        val record = JSONObject().put("case", caseName).put("phase", phase).put("synthetic", true)
        instrumentation.runOnMainSync {
            val decor = activity.window.decorView
            val insets = decor.rootWindowInsets
            val config = activity.resources.configuration
            val battery = activity.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val connectivity = activity.getSystemService(ConnectivityManager::class.java)
            val caps = connectivity?.activeNetwork?.let { connectivity?.getNetworkCapabilities(it) }
            val transport = when {
                caps == null -> "no-default-network"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "mobile"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
                else -> "other"
            }
            record.put("lifecycle", activity.lifecycle.currentState.name)
                .put("statusBarVisible", insets?.isVisible(WindowInsets.Type.statusBars()) ?: JSONObject.NULL)
                .put("rootInsetsPresent", insets != null)
                .put("statusBarInsetTop", insets?.getInsets(WindowInsets.Type.statusBars())?.top ?: JSONObject.NULL)
                .put("interactive", activity.getSystemService(PowerManager::class.java)?.isInteractive ?: JSONObject.NULL)
                .put("keyguardLocked", activity.getSystemService(KeyguardManager::class.java)?.isKeyguardLocked ?: JSONObject.NULL)
                .put("secureWindow", activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
                .put("privacyCoverVisible", activity.privacyCoverVisible())
                .put("windowFocused", decor.hasWindowFocus())
                .put("screenWidthDp", config.screenWidthDp).put("screenHeightDp", config.screenHeightDp)
                .put("densityDpi", config.densityDpi).put("fontScale", config.fontScale)
                .put("homeRequested", ViewModelProvider(activity)[ProtectedSessionState::class.java].homeVisible)
                .put("batteryLevel", battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: JSONObject.NULL)
                .put("batteryScale", battery?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: JSONObject.NULL)
                .put("batteryStatus", battery?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: JSONObject.NULL)
                .put("batteryPlugged", battery?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: JSONObject.NULL)
                .put("defaultTransport", transport)
                .put("internetValidated", caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) ?: false)
        }
        try {
            val views = JSONArray()
            for (tag in listOf("preview-home", "nova-system-status", "nova-status-percent", "nova-status-radio")) {
                val found = nodes(tag, atLeastOneRootRequired = !allowNoRoots)
                val entry = JSONObject().put("tag", tag).put("count", found.size)
                val bounds = JSONArray()
                found.forEach { node ->
                    val rect = node.boundsInRoot
                    val item = JSONObject().put("left", rect.left).put("top", rect.top).put("right", rect.right).put("bottom", rect.bottom)
                    if (tag != "preview-home") {
                        item.put("description", JSONArray(node.config.getOrNull(SemanticsProperties.ContentDescription) ?: emptyList<String>()))
                        item.put("text", JSONArray(node.config.getOrNull(SemanticsProperties.Text)?.map { it.text } ?: emptyList<String>()))
                    }
                    bounds.put(item)
                }
                entry.put("nodes", bounds); views.put(entry)
            }
            record.put("semantics", views)
        } catch (failure: Throwable) { record.put("semanticsError", failure.javaClass.name) }
        val stem = "$caseName-${sequence++}-$phase"
        try {
            if (nodes("preview-home", atLeastOneRootRequired = !allowNoRoots).size == 1) {
                val bitmap = compose.onNodeWithTag("preview-home").captureToImage().asAndroidBitmap()
                File(output, "$stem.png").outputStream().use {
                    check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
                }
                record.put("screenshot", "$stem.png")
            }
        } catch (failure: Throwable) {
            // FLAG_SECURE remains governed by production policy. Capture failure cannot replace
            // or weaken the mandatory visibility, percentage and geometry assertions above.
            record.put("screenshotError", failure.javaClass.name)
        } finally {
            File(output, "$stem.json").writeText(record.toString(2))
        }
        if (requireScreenshot) assertTrue("Actual Home screenshot is required; capture state: $record", record.has("screenshot"))
    }
}
