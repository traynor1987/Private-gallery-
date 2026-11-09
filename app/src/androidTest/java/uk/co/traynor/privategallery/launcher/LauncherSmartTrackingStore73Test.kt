package uk.co.traynor.privategallery.launcher

import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.os.ParcelFileDescriptor
import android.provider.Settings
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherSmartTrackingStore73Test {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val target = instrumentation.targetContext

    @Test fun persistsModeAndPrivacyControlsAcrossStoreReopen() {
        val context = trackingTestContext()
        val tracking = LauncherSmartTracking(context)
        assertEquals(LauncherTrackingMode.AUTOMATIC, tracking.state.value.mode)
        tracking.setMode(LauncherTrackingMode.OFF)
        tracking.setRecentLimit(16)
        tracking.setShowHomeRecents(false)
        tracking.setShowSuggestions(false)
        tracking.togglePin("example.first/.Main")
        tracking.toggleExclusion("example.second/.Main#42")
        val reopened = LauncherSmartTracking(context).state.value
        assertEquals(LauncherTrackingMode.OFF, reopened.mode)
        assertEquals(16, reopened.recentLimit)
        assertEquals(setOf("example.first/.Main"), reopened.pinned)
        assertEquals(setOf("example.second/.Main#42"), reopened.excluded)
        assertFalse(reopened.showHomeRecents)
        assertFalse(reopened.showSuggestions)
    }

    @Test fun denyingUsageAccessKeepsThePreviousMode() {
        check(Build.VERSION.SDK_INT == 37 && target.packageName.endsWith(".hiddenprototype"))
        check(shell("getprop ro.kernel.qemu").trim() == "1")
        val pkg = target.packageName
        val previous = Regex("GET_USAGE_STATS: (allow|deny|ignore|default|foreground)")
            .find(shell("appops get $pkg GET_USAGE_STATS"))?.groupValues?.get(1) ?: "default"
        try {
            shell("appops set $pkg GET_USAGE_STATS deny")
            val tracking = LauncherSmartTracking(trackingTestContext())
            tracking.setMode(LauncherTrackingMode.OFF)
            assertEquals(Settings.ACTION_USAGE_ACCESS_SETTINGS, tracking.beginExtendedConsent()?.action)
            assertFalse(tracking.finishExtendedConsent())
            assertEquals(LauncherTrackingMode.OFF, tracking.state.value.mode)
            assertTrue(tracking.state.value.history.isEmpty())
        } finally {
            shell("appops set $pkg GET_USAGE_STATS $previous")
        }
    }

    private fun trackingTestContext(): Context = object : ContextWrapper(target) {
        private val suffix = UUID.randomUUID().toString()
        override fun getApplicationContext(): Context = this
        override fun getSharedPreferences(name: String, mode: Int) =
            baseContext.getSharedPreferences("$name-test-$suffix", mode)
    }

    private fun shell(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(
        instrumentation.uiAutomation.executeShellCommand(command)
    ).bufferedReader().use { it.readText() }
}
