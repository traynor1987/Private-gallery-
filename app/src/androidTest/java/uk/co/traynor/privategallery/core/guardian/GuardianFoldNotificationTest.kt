package uk.co.traynor.privategallery.core.guardian

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.ComponentName
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import uk.co.traynor.privategallery.launcher.GuardianSafeNotifications

class GuardianFoldNotificationTest {
    @Test fun switchesModeAndReplacesTheThreeMessageWhatsAppGroup() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val automation = instrumentation.uiAutomation
        val manager = checkNotNull(context.getSystemService(NotificationManager::class.java))
        val listener = ComponentName(context, GuardianNotificationListener::class.java).flattenToString()
        val notices = GuardianSafeNotifications.prefs(context)
        val dismissal = context.getSharedPreferences("guardian_main_dismissal", 0)
        val store = GuardianCaptureStore(context)
        fun shell(command: String) = ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command))
            .bufferedReader().use { it.readText() }
        fun send(scenario: String) {
            assertTrue(shell("am broadcast --include-stopped-packages --receiver-foreground " +
                "-a uk.co.traynor.privategallery.guardian.FIXTURE -n com.whatsapp/.SendFixture --es scenario $scenario")
                .contains("Broadcast completed"))
        }
        fun active() = shell("dumpsys notification").substringAfter("Notification List:", "")
            .substringBefore("Enqueued Notification List:").lineSequence().any {
                "NotificationRecord(" in it && "pkg=$GUARDIAN_WHATSAPP " in it && " id=40 " in it &&
                    "tag=guardian93-source" in it
            }
        fun rows() = store.inboxRows(0, guardianInboxClock(context))
        fun await(label: String, condition: () -> Boolean) {
            val deadline = SystemClock.elapsedRealtime() + 15_000
            while (SystemClock.elapsedRealtime() < deadline) {
                if (condition()) return
                SystemClock.sleep(150)
            }
            fail("$label. Archive classifications: ${rows().map { it.assessment }}. " +
                "Source active: ${active()}. Dismissal: ${GuardianNotificationListener.state.value.mainDismissal}")
        }
        check(context.packageName.endsWith(".hiddenprototype"))
        check(shell("getprop ro.kernel.qemu").trim() == "1")
        try {
            store.clear()
            store.resetLearning()
            notices.edit().clear().putBoolean("enabled", true).putBoolean("main", true).commit()
            dismissal.edit().putBoolean("main_fold_accepted", true).commit()
            automation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
            shell("cmd notification allow_listener $listener")
            await("Listener connected") { GuardianNotificationListener.state.value.connected }
            GuardianCapture.setEnabled(context, true, includeMain = true)
            await("Main WhatsApp capture active") { GuardianCapture.state.value.includesMain }
            assertTrue(GuardianNotificationListener.setAutomaticDismissal(context, true))
            shell("input keyevent KEYCODE_HOME")
            assertEquals(GuardianPresentationMode.SMART, GuardianSafeNotifications.presentationMode(context))
            send("fold-hi")
            await("Smart Filtering keeps the grouped low-risk greeting") {
                rows().singleOrNull()?.assessment == GuardianAssessment.LOW_RISK && active()
            }

            notices.edit().putString("presentation_mode", GuardianPresentationMode.GUARDIAN.name).commit()
            assertEquals(GuardianPresentationMode.GUARDIAN, GuardianSafeNotifications.presentationMode(context))
            send("fold-three")
            await("Listener retained all three encrypted excerpts") { rows().size == 3 && rows().all { it.assessment != null } }
            val latest = rows().maxBy { it.time }
            await("Grouped original must be removed and replaced by a neutral Guardian alert") {
                !active() && manager.activeNotifications.any { it.tag == "guardian-safe:${latest.id}" }
            }
            assertEquals(GuardianAssessment.UPSETTING, latest.assessment)
            assertEquals(GuardianInboxSection.COOLING, latest.section)
            val alert = manager.activeNotifications.single { it.tag == "guardian-safe:${latest.id}" }.notification
            assertEquals("Nova Guardian", alert.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
            assertFalse(alert.extras.getCharSequence(Notification.EXTRA_BIG_TEXT).toString().contains("Fuck you"))
            assertNull(GuardianCaptureStore(context).revealInboxMessage(latest.id, 0, guardianInboxClock(context)))
            val clock = guardianInboxClock(context)
            assertEquals("Fuck you", GuardianCaptureStore(context).revealInboxMessage(latest.id, 0,
                clock.copy(wall = clock.wall + GUARDIAN_HOLD_MILLIS + 1, elapsed = clock.elapsed + GUARDIAN_HOLD_MILLIS + 1)))
        } finally {
            shell("cmd notification disallow_listener $listener")
            await("Fixture listener disabled") { !GuardianCapture.isEnabled(context) }
            GuardianSafeNotifications.cancel(context)
            notices.edit().clear().commit()
            dismissal.edit().clear().commit()
            store.clear()
        }
    }
}
