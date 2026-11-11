package uk.co.traynor.privategallery.core.guardian

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.app.Person
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
import uk.co.traynor.privategallery.core.sanctuary.SanctuaryMode
import uk.co.traynor.privategallery.core.sanctuary.SanctuaryStore

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
            val sanctuary = SanctuaryStore.get(context)
            assertTrue(sanctuary.startFoldTest(SanctuaryMode.PANIC))
            try {
                val panic = checkNotNull(sanctuary.refresh())
                assertFalse(panic.allowsWhatsAppDestinations)
                postPresentation()
                assertTrue("Panic must leave the notification listener bound",
                    GuardianNotificationListener.state.value.connected)
                assertEquals(SanctuaryMode.PANIC, sanctuary.refresh()?.mode)
            } finally {
                assertTrue(sanctuary.end(SanctuaryStore.END_PHRASE))
            }
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

            val fixtureTag = GuardianNotificationListener.FOLD_FIXTURE_TAG
            val postedFixture = shell("cmd notification post -t Nova-Guardian-fixture $fixtureTag Harmless-Nova-Guardian-test")
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
                    status.dismissal.stillActive == false && status.foldFixtureVerified
            }
            assertFalse("An emulator cancellation does not grant owner acceptance",
                GuardianNotificationListener.foldDismissalAccepted(context))
            assertFalse("Main WhatsApp dismissal must still be gated",
                GuardianNotificationListener.setAutomaticDismissal(context, true))
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

    @Test fun mainMessagingFixturesReachEncryptedAwaitingAnalysisWithDndOffAndOn() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val manager = checkNotNull(context.getSystemService(NotificationManager::class.java))
        val component = ComponentName(context, GuardianNotificationListener::class.java).flattenToString()
        fun shell(command: String): String =
            ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command))
                .bufferedReader().use { it.readText() }

        val oldDnd = when (manager.currentInterruptionFilter) {
            NotificationManager.INTERRUPTION_FILTER_PRIORITY -> "priority"
            NotificationManager.INTERRUPTION_FILTER_ALARMS -> "alarms"
            NotificationManager.INTERRUPTION_FILTER_NONE -> "none"
            else -> "all"
        }
        val store = GuardianCaptureStore(context)
        store.clear()
        store.resetLearning()
        try {
            shell("cmd notification allow_listener $component")
            await("Android connected the listener") { GuardianNotificationListener.state.value.connected }
            GuardianCapture.setEnabled(context, true, includeMain = true)
            await("Main WhatsApp was explicitly selected") { GuardianCapture.state.value.includesMain }
            assertTrue("The new fixture sender has no contact rule", store.monitoredContacts().isEmpty())
            assertEquals(0, store.learningCount())
            val local = Person.Builder().setName("Phone owner").setKey("self").build()
            val sender = Person.Builder().setName("Test sender").build()
            val filters = listOf("all" to NotificationManager.INTERRUPTION_FILTER_ALL,
                "priority" to NotificationManager.INTERRUPTION_FILTER_PRIORITY)
            filters.forEachIndexed { index, (mode, filter) ->
                val command = shell("cmd notification set_dnd $mode")
                await("Android applied $mode DND mode. $command") {
                    manager.currentInterruptionFilter == filter && GuardianNotificationListener.state.value.filter == filter
                }
                val now = System.currentTimeMillis()
                val style = Notification.MessagingStyle(local)
                    .addMessage("A harmless incoming fixture $index", now - 2, sender)
                    .addMessage("An outgoing fixture", now - 1, local)
                // Some delivered MessagingStyle notifications omit both category and a stable conversation ID.
                val builder = Notification.Builder(context, "fixture").setStyle(style)
                if (index > 0) builder.setCategory(Notification.CATEGORY_MESSAGE).setShortcutId("enrolled-by-app")
                val notification = builder.build()
                val key = "0|com.whatsapp|17|personal|10123"
                val main = probeAccount(personal = true, serial = 0, friendsSerial = 95)
                val friends = probeAccount(personal = false, serial = 95, friendsSerial = 95)
                val unresolved = probeAccount(personal = false, serial = 96, friendsSerial = 95)
                assertEquals(ProbeAccount.FRIENDS, friends)
                assertNull(guardianMainNotificationEvent(notification, friends, GUARDIAN_WHATSAPP, true, 95, key, now))
                assertNull(guardianMainNotificationEvent(notification, unresolved, GUARDIAN_WHATSAPP, true, 96, key, now))
                assertNull(guardianMainNotificationEvent(notification, main, GUARDIAN_WHATSAPP, false, 0, key, now))
                val event = checkNotNull(guardianMainNotificationEvent(notification, main, GUARDIAN_WHATSAPP, true, 0, key, now))
                assertEquals(if (index == 0) "notification:$key" else "shortcut:enrolled-by-app", event.conversation)
                assertEquals("A harmless incoming fixture $index", event.entries.single().text)
                val conversation = GuardianConversationKey(event.app, event.profileSerial, event.conversation)
                GuardianCapture.record(context, event, conversation)
                await("The durable Inbox and Home counts show the unclassified fixture under $mode DND") {
                    val capture = GuardianCapture.state.value
                    capture.retained == index + 1 && capture.inbox?.pending == index + 1
                }
                val rows = GuardianCaptureStore(context).inboxRows(7, guardianInboxClock(context))
                assertEquals(index + 1, rows.size)
                assertTrue(rows.all { it.assessment == null && it.section == GuardianInboxSection.PENDING })
                GuardianCapture.record(context, event, conversation)
                await("A repeated delivery is recognised without adding another Inbox row") {
                    GuardianCapture.mainCaptureStage.value == GuardianCaptureStage.DUPLICATE
                }
                assertEquals(index + 1, GuardianCapture.state.value.inbox?.pending)
                assertEquals(filter, manager.currentInterruptionFilter)
            }
        } finally {
            shell("cmd notification set_dnd $oldDnd")
            shell("cmd notification disallow_listener $component")
            await("The test listener stopped") { !GuardianCapture.isEnabled(context) }
            store.clear()
        }
    }

    private fun await(expected: String, predicate: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 15_000
        while (!predicate() && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(100)
        assertTrue(expected, predicate())
    }
}
