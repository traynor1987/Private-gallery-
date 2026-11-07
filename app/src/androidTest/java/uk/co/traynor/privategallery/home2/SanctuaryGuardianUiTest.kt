package uk.co.traynor.privategallery.home2

import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.traynor.privategallery.BuildConfig
import uk.co.traynor.privategallery.core.sanctuary.SanctuaryGuardian
import uk.co.traynor.privategallery.core.sanctuary.SanctuaryMode
import uk.co.traynor.privategallery.core.sanctuary.SanctuaryStore

@RunWith(AndroidJUnit4::class)
class SanctuaryGuardianUiTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test
    fun foregroundServiceBlocksAndRevocationRemovesRealWindow() {
        check(Build.VERSION.SDK_INT == 37 && BuildConfig.DEBUG &&
            context.packageName.endsWith(".hiddenprototype") && shell("getprop ro.kernel.qemu").trim() == "1"
        ) { "Guardian permission test requires the disposable API 37 debug emulator" }

        val packageName = context.packageName
        val target = listOf(
            "com.google.android.calculator", "com.android.calculator2", "com.google.android.deskclock", "com.android.deskclock",
            "com.android.chrome", "com.android.camera2",
        ).firstOrNull { it in SanctuaryGuardian.installedApps(context) && it !in SanctuaryGuardian.essentialPackages(context) }
        assertNotNull("A nonessential stock launcher app is required", target)
        val external = requireNotNull(target)
        val store = SanctuaryStore.get(context)
        val original = store.preset(SanctuaryMode.WORK)
        val existing = store.refresh()
        check(existing == null) { "A clean disposable Sanctuary session is required" }

        try {
            shell("pm grant $packageName android.permission.POST_NOTIFICATIONS")
            shell("appops set $packageName SYSTEM_ALERT_WINDOW allow")
            shell("appops set $packageName GET_USAGE_STATS allow")
            assertTrue("Overlay grant missing", SanctuaryGuardian.hasOverlayAccess(context))
            assertTrue("Usage Access grant missing", SanctuaryGuardian.hasUsageAccess(context))
            assertTrue("Notification grant missing", SanctuaryGuardian.hasNotificationAccess(context))

            assertTrue(store.savePreset(SanctuaryMode.WORK, original.copy(androidApps = emptySet())))
            ActivityScenario.launch(ComponentActivity::class.java).use { host ->
                host.onActivity { activity ->
                    assertTrue(store.start(SanctuaryMode.WORK))
                    assertFalse(SanctuaryGuardian.launch(activity, external))
                    assertTrue(SanctuaryGuardian.start(activity))
                }
                waitUntil("Guardian began observing foreground apps") {
                    SanctuaryGuardian.monitorState == SanctuaryGuardian.MonitorState.MONITORING
                }
                val deadline = store.refresh()?.expiresAt
                launchExternal(external)
                waitUntil("Unapproved foreground app received the actual overlay") {
                    SanctuaryGuardian.monitorState == SanctuaryGuardian.MonitorState.BLOCKING && overlayPresent()
                }
                host.close()
                waitUntil("Overlay survived Nova Activity closure") {
                    SanctuaryGuardian.monitorState == SanctuaryGuardian.MonitorState.BLOCKING && overlayPresent()
                }

                shell("appops set $packageName GET_USAGE_STATS ignore")
                waitUntil("Revoking Usage Access removed the actual overlay") {
                    !overlayPresent() && !SanctuaryGuardian.hasUsageAccess(context) &&
                        SanctuaryGuardian.monitorState == SanctuaryGuardian.MonitorState.PAUSED
                }
                assertEquals(deadline, store.refresh()?.expiresAt)
                assertFalse(SanctuaryGuardian.start(context))
            }

            assertTrue(store.end(SanctuaryStore.END_PHRASE))
            SanctuaryGuardian.stop(context)
            shell("appops set $packageName GET_USAGE_STATS allow")
            assertTrue(store.savePreset(SanctuaryMode.WORK, original.copy(androidApps = setOf(external))))
            ActivityScenario.launch(ComponentActivity::class.java).use { host ->
                host.onActivity { activity ->
                    assertTrue(store.start(SanctuaryMode.WORK))
                    assertTrue(SanctuaryGuardian.start(activity))
                }
                waitUntil("Guardian resumed before the approved app launch") {
                    SanctuaryGuardian.monitorState == SanctuaryGuardian.MonitorState.MONITORING
                }
                host.onActivity { activity -> assertTrue(SanctuaryGuardian.launch(activity, external)) }
                // A successful startActivity is insufficient. Confirm the approved app became foreground.
                waitUntil("Approved Android app became the focused window without an overlay") {
                    val windows = shell("dumpsys window windows")
                    windows.lineSequence().any { "mCurrentFocus" in it && external in it } &&
                        !overlayPresent(windows) &&
                        SanctuaryGuardian.monitorState == SanctuaryGuardian.MonitorState.MONITORING
                }
                assertTrue(store.end(SanctuaryStore.END_PHRASE))
                waitUntil("Ending the session stopped Guardian") {
                    SanctuaryGuardian.monitorState == SanctuaryGuardian.MonitorState.PAUSED && !overlayPresent()
                }
            }
        } finally {
            store.end(SanctuaryStore.END_PHRASE)
            SanctuaryGuardian.stop(context)
            store.savePreset(SanctuaryMode.WORK, original)
            shell("appops set $packageName SYSTEM_ALERT_WINDOW default")
            shell("appops set $packageName GET_USAGE_STATS default")
        }
    }

    private fun launchExternal(packageName: String) {
        val output = shell("am start -W -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -p $packageName")
        assertFalse(output, output.contains("Error"))
    }

    private fun overlayPresent(windows: String = shell("dumpsys window windows")): Boolean =
        windows.lineSequence().any { line -> "Window{" in line && "Nova Sanctuary}" in line }

    private fun shell(command: String): String =
        ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command))
            .use { it.readBytes().decodeToString() }

    private fun waitUntil(message: String, predicate: () -> Boolean) {
        val limit = SystemClock.elapsedRealtime() + 15_000L
        while (SystemClock.elapsedRealtime() < limit) {
            if (predicate()) return
            SystemClock.sleep(175)
        }
        throw AssertionError("$message. Current status: ${SanctuaryGuardian.status(context)}")
    }
}
