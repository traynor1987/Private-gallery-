package uk.co.traynor.privategallery.core.guardian

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.ComponentName
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.traynor.privategallery.LauncherActivity

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
            assertFalse("Permission alone must not enable encrypted capture", GuardianCapture.isEnabled(context))
            GuardianCapture.setEnabled(context, true)
            await("Separate capture consent enabled the local archive") { GuardianCapture.isEnabled(context) }
            instrumentation.runOnMainSync { GuardianNotificationListener.startProbe(context) }
            assertTrue(GuardianNotificationListener.state.value.running)
            assertTrue(GuardianNotificationListener.state.value.results.isEmpty())
            if (context.packageName.endsWith(".guardianprobe")) {
                ActivityScenario.launch(LauncherActivity::class.java).use { screen ->
                    screen.moveToState(Lifecycle.State.CREATED)
                    assertTrue(GuardianNotificationListener.state.value.running)
                    screen.moveToState(Lifecycle.State.RESUMED)
                }
                assertTrue("Leaving diagnostics must not clear the service-owned probe",
                    GuardianNotificationListener.state.value.running)
            }

            val manager = checkNotNull(context.getSystemService(NotificationManager::class.java))
            val originalDnd = manager.currentInterruptionFilter
            instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
            instrumentation.runOnMainSync { GuardianNotificationListener.postSynthetic(context, dismiss = false) }
            await("The harmless test reached the listener and was securely captured") {
                GuardianNotificationListener.state.value.synthetic == SyntheticResult.PRESENTED
            }
            val presented = manager.activeNotifications.single { it.notification.channelId == "guardian-compatibility-test" }
            assertEquals(Notification.VISIBILITY_SECRET, presented.notification.visibility)
            assertNull(presented.notification.publicVersion)
            assertNotNull(GuardianNotificationListener.state.value.syntheticDndMatch)
            instrumentation.runOnMainSync { GuardianNotificationListener.postSynthetic(context, dismiss = true) }
            await("Android confirmed only the requested synthetic notification was removed") {
                GuardianNotificationListener.state.value.synthetic == SyntheticResult.DISMISSED
            }
            assertTrue(manager.activeNotifications.any { it.key == presented.key })
            assertTrue("Synthetic test callbacks must not become WhatsApp events",
                GuardianNotificationListener.state.value.results.isEmpty())
            assertEquals("The compatibility test must not change DND", originalDnd, manager.currentInterruptionFilter)
            manager.cancel(presented.tag, presented.id)
        } finally {
            setAccess("disallow_listener")
        }
        await("Android revoked listener access and cleared the probe") {
            !GuardianNotificationListener.hasAccess(context) &&
                GuardianNotificationListener.state.value == ListenerProbeStatus() && !GuardianCapture.isEnabled(context)
        }
        try {
            setAccess("allow_listener")
            await("Reauthorising the listener must reconnect without restarting capture") {
                GuardianNotificationListener.hasAccess(context) &&
                    GuardianNotificationListener.state.value.connected &&
                    !GuardianNotificationListener.state.value.running && !GuardianCapture.isEnabled(context)
            }
        } finally {
            setAccess("disallow_listener")
        }
    }

    private fun await(expected: String, predicate: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 15_000
        while (!predicate() && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(100)
        assertTrue(expected, predicate())
    }
}
