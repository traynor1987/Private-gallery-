package uk.co.traynor.privategallery.core.guardian

import android.content.ComponentName
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GuardianNotificationCompatibility77Test {
    @Test fun androidBindsAndRevokesAnExplicitlyGrantedListener() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val component = ComponentName(context, GuardianNotificationListener::class.java).flattenToString()
        fun setAccess(command: String) {
            val result = instrumentation.uiAutomation.executeShellCommand("cmd notification $command $component")
            ParcelFileDescriptor.AutoCloseInputStream(result).bufferedReader().use { it.readText() }
        }
        assertFalse(GuardianNotificationListener.hasAccess(context))
        try {
            instrumentation.runOnMainSync { GuardianNotificationListener.startProbe(context) }
            assertFalse(GuardianNotificationListener.state.value.running)
            setAccess("allow_listener")
            await("Android bound the authorised service") {
                GuardianNotificationListener.hasAccess(context) && GuardianNotificationListener.state.value.connected
            }
            assertFalse("Permission alone must not start a probe", GuardianNotificationListener.state.value.running)
            instrumentation.runOnMainSync { GuardianNotificationListener.startProbe(context) }
            assertTrue(GuardianNotificationListener.state.value.running)
            assertTrue(GuardianNotificationListener.state.value.results.isEmpty())
        } finally {
            setAccess("disallow_listener")
        }
        await("Android revoked listener access and cleared the probe") {
            !GuardianNotificationListener.hasAccess(context) &&
                GuardianNotificationListener.state.value == ListenerProbeStatus()
        }
    }

    private fun await(expected: String, predicate: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 15_000
        while (!predicate() && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(100)
        assertTrue(expected, predicate())
    }
}
