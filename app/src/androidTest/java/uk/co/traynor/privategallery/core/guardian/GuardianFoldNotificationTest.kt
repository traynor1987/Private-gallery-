package uk.co.traynor.privategallery.core.guardian

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.ComponentName
import android.os.ParcelFileDescriptor
import android.os.PowerManager
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
        val power = checkNotNull(context.getSystemService(PowerManager::class.java))
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
        fun active(id: Int = 40) = shell("dumpsys notification").substringAfter("Notification List:", "")
            .substringBefore("Enqueued Notification List:").lineSequence().any {
                "NotificationRecord(" in it && "pkg=$GUARDIAN_WHATSAPP " in it && " id=$id " in it &&
                    "tag=guardian93-source" in it
            }
        fun posted(row: GuardianInboxRow) =
            manager.activeNotifications.singleOrNull { it.tag == "guardian-safe:${row.id}" }?.notification
        fun alertText(alert: Notification) = listOf(Notification.EXTRA_TEXT, Notification.EXTRA_BIG_TEXT,
            Notification.EXTRA_TITLE, Notification.EXTRA_SUB_TEXT, Notification.EXTRA_SUMMARY_TEXT)
            .mapNotNull { alert.extras.getCharSequence(it)?.toString() }.joinToString("\n")
        fun rows() = store.inboxRows(0, guardianInboxClock(context))
        fun observation(condition: (GuardianPipelineObservation) -> Boolean) =
            GuardianPipelineDiagnostics.recent.value.lastOrNull(condition)
        fun await(label: String, condition: () -> Boolean) {
            val deadline = SystemClock.elapsedRealtime() + 15_000
            while (SystemClock.elapsedRealtime() < deadline) {
                if (condition()) return
                SystemClock.sleep(150)
            }
            fail("$label. Archive classifications: ${rows().map { it.assessment }}. " +
                "Source active: ${active()}. Dismissal: ${GuardianNotificationListener.state.value.mainDismissal}. " +
                "Trace: ${GuardianPipelineDiagnostics.recent.value}")
        }
        check(context.packageName.endsWith(".hiddenprototype"))
        check(shell("getprop ro.kernel.qemu").trim() == "1")
        try {
            store.clear()
            store.resetLearning()
            GuardianPipelineDiagnostics.clear()
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
            send("fold-summary")
            await("Summary callback recorded without capture") {
                observation { it.summary && it.grouped && it.capture == "Group summary left to Android" } != null
            }
            send("fold-hi")
            await("Smart Filtering keeps the grouped low-risk greeting") {
                rows().singleOrNull()?.assessment == GuardianAssessment.LOW_RISK && active() &&
                    observation { it.entries == 1 && it.action == GuardianPresentationAction.KEEP_ORIGINAL } != null
            }
            val greeting = checkNotNull(observation {
                it.entries == 1 && it.action == GuardianPresentationAction.KEEP_ORIGINAL
            })

            GuardianSafeNotifications.setPresentationMode(context, GuardianPresentationMode.GUARDIAN)
            assertEquals(GuardianPresentationMode.GUARDIAN, GuardianSafeNotifications.presentationMode(context))
            send("fold-three")
            await("Listener retained all three encrypted excerpts") { rows().size == 3 && rows().all { it.assessment != null } }
            val latest = rows().maxBy { it.time }
            await("Grouped original must be removed and replaced by a neutral Guardian alert") {
                !active() && posted(latest) != null
            }
            assertEquals(GuardianAssessment.UPSETTING, latest.assessment)
            assertEquals(GuardianInboxSection.COOLING, latest.section)
            val alert = checkNotNull(posted(latest))
            assertEquals("Nova Guardian", alert.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
            assertFalse(alertText(alert).contains("Fuck you"))
            assertNull(alert.publicVersion)
            await("Trace follows the same updated key through all stages") {
                observation {
                    it.slot == greeting.slot && it.grouped && it.entries == 3 && it.assessed == 3 && it.held == 1 &&
                        it.mode == GuardianPresentationMode.GUARDIAN && it.action == GuardianPresentationAction.REPLACE &&
                        it.removal?.contains("confirmed exact notification removal") == true &&
                        it.delivery == "Guardian alert active in Android"
                } != null
            }
            assertNull(GuardianCaptureStore(context).revealInboxMessage(latest.id, 0, guardianInboxClock(context)))
            val clock = guardianInboxClock(context)
            assertEquals("Fuck you", GuardianCaptureStore(context).revealInboxMessage(latest.id, 0,
                clock.copy(wall = clock.wall + GUARDIAN_HOLD_MILLIS + 1, elapsed = clock.elapsed + GUARDIAN_HOLD_MILLIS + 1)))
            assertFalse(java.io.File(context.noBackupFilesDir, GuardianCaptureStore.ARCHIVE_NAME)
                .readBytes().toString(Charsets.UTF_8).contains("Fuck you"))

            val beforeRepost = GuardianPipelineDiagnostics.recent.value.last().sequence
            send("fold-three")
            await("An exact repost removed without another alert or archive row") {
                !active() && observation { it.sequence > beforeRepost && it.slot == greeting.slot &&
                    it.delivery == "Replacement already delivered" } != null
            }
            assertEquals(3, rows().size)
            assertEquals(1, manager.activeNotifications.count { it.tag?.startsWith("guardian-safe:") == true })

            send("fold-uncertain")
            await("An unheld uncertain message receives a text-free Guardian alert") {
                val uncertain = rows().maxBy { it.time }
                rows().size == 4 && uncertain.assessment == GuardianAssessment.UNCERTAIN &&
                    uncertain.section == GuardianInboxSection.READY && !active(41) &&
                    posted(uncertain)?.let { !alertText(it).contains("When I was 17.") } == true
            }

            shell("cmd notification set_dnd priority")
            await("Android priority DND active") { manager.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_PRIORITY }
            shell("input keyevent KEYCODE_SLEEP")
            await("Screen is off") { !power.isInteractive }
            send("fold-safe")
            await("Screen-off safe replacement under DND preserves the inbound greeting") {
                val safe = rows().maxBy { it.time }
                rows().size == 5 && safe.assessment == GuardianAssessment.LOW_RISK && !active(42) &&
                    posted(safe)?.let { alertText(it).contains("Good afternoon") &&
                        !alertText(it).contains("I will call later.") && it.publicVersion == null &&
                        it.visibility == Notification.VISIBILITY_SECRET } == true
            }
            assertFalse(power.isInteractive)
            await("Trace records DND and Android delivery") {
                observation { it.dnd && it.grouped && it.entries == 1 &&
                    it.delivery == "Guardian alert active in Android" } != null
            }
            shell("input keyevent KEYCODE_WAKEUP")
            shell("wm dismiss-keyguard")
            shell("cmd notification set_dnd all")

            GuardianSafeNotifications.setPresentationMode(context, GuardianPresentationMode.SMART)
            send("fold-smart-greeting")
            await("Switching back to Smart Filtering restores native safe alerts") {
                rows().size == 6 && active(43) && posted(rows().maxBy { it.time }) == null &&
                    observation { it.mode == GuardianPresentationMode.SMART && it.latest == GuardianAssessment.LOW_RISK &&
                        it.action == GuardianPresentationAction.KEEP_ORIGINAL } != null
            }
            send("fold-smart-hostile")
            await("Smart Filtering still removes a new hostile update to the grouped original") {
                val held = rows().maxBy { it.time }
                rows().size == 7 && held.section == GuardianInboxSection.COOLING && !active() &&
                    posted(held)?.let { !alertText(it).contains("Fuck you") } == true
            }
            assertFalse(GuardianPipelineDiagnostics.recent.value.toString().contains("Fuck you"))
            assertFalse(GuardianPipelineDiagnostics.recent.value.toString().contains("Fixture sender"))
        } finally {
            shell("input keyevent KEYCODE_WAKEUP")
            shell("wm dismiss-keyguard")
            shell("cmd notification set_dnd all")
            shell("cmd notification disallow_listener $listener")
            await("Fixture listener disabled") { !GuardianCapture.isEnabled(context) }
            GuardianSafeNotifications.cancel(context)
            notices.edit().clear().commit()
            dismissal.edit().clear().commit()
            store.clear()
        }
    }
}
