package uk.co.traynor.privategallery.home2

import android.app.UiAutomation
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.traynor.privategallery.BuildConfig
import uk.co.traynor.privategallery.core.sanctuary.SanctuaryAccessibilityService
import uk.co.traynor.privategallery.core.sanctuary.SanctuaryGuardian
import uk.co.traynor.privategallery.core.sanctuary.SanctuaryMode
import uk.co.traynor.privategallery.core.sanctuary.SanctuaryStore

@RunWith(AndroidJUnit4::class)
class SanctuaryAccessibilityUiTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val automation get() = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)

    @Test fun blockedAppMovesToHomeAndIsNotRestoredAtNaturalExpiry() {
        check(Build.VERSION.SDK_INT == 37 && BuildConfig.DEBUG &&
            context.packageName.endsWith(".hiddenprototype") && shell("getprop ro.kernel.qemu").trim() == "1")
        val pkg = context.packageName
        val home = checkNotNull(context.packageManager.resolveActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
            PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY.toLong()),
        )).activityInfo.packageName
        val blocked = listOf(
            "com.google.android.calculator", "com.android.calculator2", "com.google.android.deskclock",
            "com.android.deskclock", "com.android.chrome", "com.android.camera2",
        ).first { it in SanctuaryGuardian.installedApps(context) && it !in SanctuaryGuardian.essentialPackages(context) }
        val component = ComponentName(context, SanctuaryAccessibilityService::class.java).flattenToString()
        val savedServices = shell("settings get secure enabled_accessibility_services").trim()
        val savedEnabled = shell("settings get secure accessibility_enabled").trim()
        val store = SanctuaryStore.get(context)
        check(store.refresh() == null)
        val preset = store.preset(SanctuaryMode.WORK)
        try {
            shell("pm grant $pkg android.permission.POST_NOTIFICATIONS")
            shell("appops set $pkg SYSTEM_ALERT_WINDOW allow")
            shell("appops set $pkg GET_USAGE_STATS allow")
            // Real grants are user-controlled. This runs only against a disposable emulator.
            shell("settings put secure enabled_accessibility_services $component")
            shell("settings put secure accessibility_enabled 1")
            await("Accessibility connected") { SanctuaryGuardian.isAccessibilityConnected() }
            assertTrue(store.savePreset(SanctuaryMode.WORK, preset.copy(
                minutes = 1, androidApps = emptySet(), enableGuardian = true, enableDnd = false,
            )))
            ActivityScenario.launch(ComponentActivity::class.java).use { host ->
                host.onActivity {
                    assertTrue(store.start(SanctuaryMode.WORK))
                    assertTrue(SanctuaryGuardian.start(it))
                }
                await("Usage monitor ready") {
                    SanctuaryGuardian.monitorState == SanctuaryGuardian.MonitorState.MONITORING
                }
                val launch = shell("am start -W -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -p $blocked")
                assertTrue(launch, "Error" !in launch)
                await("Home under the live overlay") { topIs(home) && overlayPresent() }
                await("Natural expiry removes overlay", 70_000L) { store.current.value == null && !overlayPresent() }
                assertTrue("Home must remain foreground after expiry", topIs(home))
                SystemClock.sleep(1_000L)
                assertTrue("The blocked app must not be restored", topIs(home) && !overlayPresent())
            }
        } finally {
            store.end(SanctuaryStore.END_PHRASE)
            SanctuaryGuardian.stop(context)
            store.savePreset(SanctuaryMode.WORK, preset)
            restoreSetting("enabled_accessibility_services", savedServices)
            restoreSetting("accessibility_enabled", savedEnabled)
            shell("appops set $pkg SYSTEM_ALERT_WINDOW default")
            shell("appops set $pkg GET_USAGE_STATS default")
        }
    }

    private fun topIs(packageName: String): Boolean {
        val resumed = Regex("(?:topResumedActivity|mResumedActivity)[=:].*\\b" + Regex.escape(packageName) + "/")
        return shell("dumpsys activity activities").lineSequence().any(resumed::containsMatchIn)
    }

    private fun overlayPresent(): Boolean =
        shell("dumpsys window windows").lineSequence().any { "Window{" in it && "Nova Sanctuary}" in it }

    private fun restoreSetting(key: String, value: String) {
        if (value == "null" || value.isEmpty()) shell("settings delete secure $key")
        else shell("settings put secure $key '$value'")
    }

    private fun shell(command: String): String =
        ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command)).use {
            it.readBytes().decodeToString()
        }

    private fun await(label: String, timeout: Long = 15_000L, ready: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + timeout
        while (SystemClock.elapsedRealtime() < deadline) {
            if (ready()) return
            SystemClock.sleep(200L)
        }
        error("$label · ${SanctuaryGuardian.status(context)}")
    }
}
