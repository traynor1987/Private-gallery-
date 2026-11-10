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
        fun shell(command: String): String {
            val result = instrumentation.uiAutomation.executeShellCommand(command)
            return ParcelFileDescriptor.AutoCloseInputStream(result).bufferedReader().use { it.readText() }
        }
        fun setAccess(command: String) { shell("cmd notification $command $component") }
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
            fun postPresentation() {
                instrumentation.runOnMainSync { GuardianNotificationListener.postSynthetic(context) }
                await("The harmless test reached the listener and was securely captured") {
                    GuardianNotificationListener.state.value.synthetic == SyntheticResult.PRESENTED
                }
            }
            fun dismissPresentation() {
                instrumentation.runOnMainSync { GuardianNotificationListener.dismissPresentedTest(context) }
                await("Android removed the matching presentation notification") {
                    val status = GuardianNotificationListener.state.value
                    status.synthetic == SyntheticResult.DISMISSED && status.dismissal.stillActive == false
                }
            }
            val channel = "guardian-compatibility-test"
            fun presentations() = manager.activeNotifications.filter {
                it.notification.channelId == channel &&
                    it.tag?.startsWith("nova-guardian-presentation-") == true &&
                    it.notification.flags and Notification.FLAG_GROUP_SUMMARY == 0
            }
            postPresentation()
            val presented = presentations().single()
            assertEquals(Notification.VISIBILITY_SECRET, presented.notification.visibility)
            assertNull(presented.notification.publicVersion)
            assertNotNull(GuardianNotificationListener.state.value.syntheticDndMatch)
            dismissPresentation()
            assertTrue(manager.activeNotifications.none { it.tag == presented.tag && it.id == presented.id })

            postPresentation()
            val first = presentations().single()
            postPresentation()
            val second = presentations().single { it.tag != first.tag }
            assertEquals(first.id, second.id)
            val group = Notification.Builder(context, channel).setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Unrelated group fixture").setGroup("unrelated").setGroupSummary(true).build()
            manager.notify("unrelated-group", first.id, group)
            try {
                await("The unrelated group fixture is active") {
                    manager.activeNotifications.any { it.tag == "unrelated-group" && it.id == first.id }
                }
                dismissPresentation()
                assertTrue(manager.activeNotifications.none { it.tag == second.tag && it.id == second.id })
                assertTrue(manager.activeNotifications.any { it.tag == first.tag && it.id == first.id })
                assertTrue(manager.activeNotifications.any { it.tag == "unrelated-group" && it.id == first.id })
                dismissPresentation()
                assertTrue(manager.activeNotifications.none { it.tag == first.tag && it.id == first.id })
            } finally {
                manager.cancel("unrelated-group", first.id)
            }

            val fixtureTag = "nova-guardian-shell-fixture-" + java.util.UUID.randomUUID()
            val postedFixture = shell("cmd notification post -t Guardian-fixture $fixtureTag Harmless-external-test")
            var externalRequested = false
            await("The authorised listener found the shell fixture by exact identity. Shell result: $postedFixture") {
                if (!externalRequested) instrumentation.runOnMainSync {
                    externalRequested = GuardianNotificationListener.dismissExternalTestFixture(context, fixtureTag)
                }
                externalRequested
            }
            await("Android acknowledged external listener cancellation and removed the exact key") {
                val status = GuardianNotificationListener.state.value
                status.synthetic == SyntheticResult.DISMISSED &&
                    status.dismissal.mechanism == "NotificationListenerService · exact key" &&
                    status.dismissal.stillActive == false
            }
            assertTrue("Synthetic test callbacks must not become WhatsApp events",
                GuardianNotificationListener.state.value.results.isEmpty())
            assertEquals("The compatibility test must not change DND", originalDnd, manager.currentInterruptionFilter)
            postPresentation()
        } finally {
            setAccess("disallow_listener")
        }
        await("Android revoked listener access and cleared the probe") {
            !GuardianNotificationListener.hasAccess(context) &&
                GuardianNotificationListener.state.value == ListenerProbeStatus() && !GuardianCapture.isEnabled(context)
        }
        instrumentation.runOnMainSync { GuardianNotificationListener.dismissPresentedTest(context) }
        await("Owned dismissal still works after listener revocation clears its session") {
            val status = GuardianNotificationListener.state.value
            status.synthetic == SyntheticResult.DISMISSED && status.dismissal.stillActive == false
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
