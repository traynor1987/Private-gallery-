package uk.co.traynor.privategallery.core.guardian

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.ComponentName
import android.os.ParcelFileDescriptor
import android.os.PowerManager
import android.os.Process
import android.os.SystemClock
import android.os.UserManager
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import uk.co.traynor.privategallery.launcher.GuardianSafeNotifications

class GuardianNotificationPipeline93Test {
    @Test fun syntheticWhatsAppUsesTheAndroidListenerAndBothPresentationModes() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val automation = instrumentation.uiAutomation
        val manager = checkNotNull(context.getSystemService(NotificationManager::class.java))
        val power = checkNotNull(context.getSystemService(PowerManager::class.java))
        val component = ComponentName(context, GuardianNotificationListener::class.java).flattenToString()
        val preferences = GuardianSafeNotifications.prefs(context)
        val dismissal = context.getSharedPreferences("guardian_main_dismissal", 0)
        val store = GuardianCaptureStore(context)
        val profile = checkNotNull(context.getSystemService(UserManager::class.java))
            .getSerialNumberForUser(Process.myUserHandle())

        fun shell(command: String) = ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command))
            .bufferedReader().use { it.readText() }
        fun sourceActive(id: Int): Boolean {
            val records = shell("dumpsys notification").substringAfter("Notification List:", "")
                .substringBefore("Enqueued Notification List:")
            check(records.isNotEmpty()) { "Android active notification list unavailable" }
            return records.lineSequence().any {
                it.contains("NotificationRecord(") && it.contains("pkg=$GUARDIAN_WHATSAPP ") &&
                    it.contains(" id=$id ") && it.contains("tag=guardian93-source")
            }
        }
        fun send(scenario: String) {
            val output = shell("am broadcast --include-stopped-packages --receiver-foreground " +
                "-a uk.co.traynor.privategallery.guardian.FIXTURE -n com.whatsapp/.SendFixture --es scenario $scenario")
            assertTrue("Synthetic broadcast delivered", output.contains("Broadcast completed"))
        }
        fun rows() = store.inboxRows(0, guardianInboxClock(context))
        fun alerts() = manager.activeNotifications.filter { it.tag?.startsWith("guardian-safe:") == true }
        fun alertText(notification: Notification) = listOf(
            Notification.EXTRA_TITLE, Notification.EXTRA_TEXT, Notification.EXTRA_BIG_TEXT,
            Notification.EXTRA_SUB_TEXT, Notification.EXTRA_SUMMARY_TEXT,
        ).mapNotNull { notification.extras.getCharSequence(it)?.toString() }.joinToString("\n")
        fun rowFor(id: Int) = rows().singleOrNull { it.conversation?.conversation == "shortcut:fixture-$id" }
        fun diagnostic(fragment: String) = GuardianNotificationListener.state.value.mainDismissal?.contains(fragment) == true
        fun await(label: String, condition: () -> Boolean) {
            val until = SystemClock.elapsedRealtime() + 20_000
            while (SystemClock.elapsedRealtime() < until) {
                if (condition()) return
                SystemClock.sleep(150)
            }
            fail("$label. Last result: ${GuardianNotificationListener.state.value.mainDismissal}; " +
                "capture: ${GuardianCapture.mainCaptureStage.value}")
        }

        check(context.packageName.endsWith(".hiddenprototype"))
        check(shell("getprop ro.kernel.qemu").trim() == "1")
        val hostile = "You need to shut the fuck up. When I was 17."
        val capturePrefs = context.getSharedPreferences("nova_guardian_capture", 0)
        try {
            store.clear()
            store.resetLearning()
            preferences.edit().clear().putBoolean("enabled", true).putBoolean("main", true).commit()
            dismissal.edit().putBoolean("main_fold_accepted", true).commit()
            automation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
            shell("cmd notification allow_listener $component")
            await("Android bound the listener") { GuardianNotificationListener.state.value.connected }
            GuardianCapture.setEnabled(context, true, includeMain = true)
            await("Encrypted capture enabled") { GuardianCapture.state.value.includesMain }
            assertTrue(GuardianNotificationListener.setAutomaticDismissal(context, true))
            shell("input keyevent KEYCODE_HOME")
            await("NovaOS has no foreground window") {
                shell("dumpsys window").lineSequence().any {
                    "mCurrentFocus=" in it && "{" in it && context.packageName !in it
                }
            }

            send("hi")
            await("Smart Filtering kept the real source greeting") {
                rows().singleOrNull()?.assessment == GuardianAssessment.LOW_RISK &&
                    diagnostic("Low risk with no active hold") && sourceActive(17)
            }
            assertTrue(alerts().isEmpty())

            send("hostile")
            await("Android confirmed the updated source removal and posted a neutral replacement") {
                rows().size == 2 && diagnostic("Guardian alert posted") && !sourceActive(17) && alerts().size == 1
            }
            val currentClock = guardianInboxClock(context)
            val held = rows().single { it.section == GuardianInboxSection.COOLING }
            assertEquals(GuardianAssessment.UPSETTING, held.assessment)
            assertNull(store.revealInboxMessage(held.id, 0, currentClock))
            val future = currentClock.copy(wall = currentClock.wall + GUARDIAN_HOLD_MILLIS + 1,
                elapsed = currentClock.elapsed + GUARDIAN_HOLD_MILLIS + 1)
            assertEquals(hostile, GuardianCaptureStore(context).revealInboxMessage(held.id, 0, future))
            val replacement = alerts().single()
            assertEquals("Nova Guardian", replacement.notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
            assertTrue(replacement.notification.extras.getCharSequence(Notification.EXTRA_BIG_TEXT).toString()
                .contains("Message protected until Guardian review"))
            assertFalse(alertText(replacement.notification).contains(hostile))
            assertNull(replacement.notification.publicVersion)
            assertFalse(java.io.File(context.noBackupFilesDir, GuardianCaptureStore.ARCHIVE_NAME)
                .readBytes().toString(Charsets.UTF_8).contains(hostile))

            send("hostile")
            await("An exact repost was dismissed without another replacement or capture") {
                GuardianCapture.mainCaptureStage.value == GuardianCaptureStage.DUPLICATE &&
                    diagnostic("confirmed exact notification removal") && !sourceActive(17)
            }
            assertEquals(2, rows().size)
            assertEquals(replacement.key, alerts().single().key)

            preferences.edit().putString("presentation_mode", GuardianPresentationMode.GUARDIAN.name).commit()
            send("summary")
            await("Unsupported group summary stayed visible") { sourceActive(30) }
            send("morning")
            await("Nova Guardian safely replaced the grouped low-risk child") {
                rows().size == 3 && diagnostic("Guardian alert posted") && !sourceActive(18) && alerts().size == 2
            }
            assertTrue(sourceActive(30))
            assertEquals(GuardianAssessment.LOW_RISK, rows().maxBy { it.time }.assessment)
            assertTrue(alerts().any { it.notification.extras.getCharSequence(Notification.EXTRA_BIG_TEXT).toString()
                .contains("New message · open Guardian to read") })

            send("uncertain")
            await("An unheld uncertain notification kept its original") {
                rows().size == 4 && diagnostic("Uncertain with no active hold") && sourceActive(19)
            }
            assertEquals(2, alerts().size)
            GuardianCapture.setHoldUncertainByDefault(context, true)
            send("held-uncertain")
            await("The configured uncertainty hold removed the source and posted a neutral alert") {
                rows().size == 5 && !sourceActive(20) && alerts().size == 3
            }
            assertEquals(GuardianInboxSection.COOLING, rows().maxBy { it.time }.section)

            val excluded = GuardianConversationKey(GUARDIAN_WHATSAPP, profile, "shortcut:fixture-21")
            val quiet = GuardianConversationKey(GUARDIAN_WHATSAPP, profile, "shortcut:fixture-22")
            store.saveContact(excluded, GuardianContact(excluded, "Excluded", retain = false))
            store.saveContact(quiet, GuardianContact(quiet, "Quiet", quietCollection = true))
            send("excluded")
            await("An excluded contact stayed in the source app") {
                GuardianCapture.mainCaptureStage.value == GuardianCaptureStage.CONSENT && sourceActive(21)
            }
            assertEquals(5, rows().size)
            send("quiet")
            await("The quiet contact was captured and removed without an alert") {
                rows().size == 6 && diagnostic("confirmed exact notification removal") && !sourceActive(22)
            }
            assertEquals(3, alerts().size)

            preferences.edit().putBoolean("main", false).commit()
            send("blocked")
            await("When a replacement is unavailable the ordinary notification is preserved") {
                rows().size == 7 && diagnostic("Guardian alert unavailable") && sourceActive(23)
            }
            assertEquals(3, alerts().size)

            val validation = checkNotNull(store.measureKnowledge(previewHoldout()))
            assertTrue(guardianAlertMayPreview(true, true, GuardianNoticeKind.LOW, validation))
            assertTrue(preferences.edit().putBoolean("main", true).putBoolean("alert_validated_preview", true).commit())
            shell("input keyevent KEYCODE_SLEEP")
            await("The emulator screen is off") { !power.isInteractive }

            send("background")
            await("The background listener replaced a safe alert and posted its opted-in preview") {
                rowFor(24)?.assessment == GuardianAssessment.LOW_RISK && diagnostic("Guardian alert posted") &&
                    !sourceActive(24) && alerts().size == 4 &&
                    alerts().any { alertText(it.notification).contains("Good afternoon") }
            }
            val safe = alerts().single { alertText(it.notification).contains("Good afternoon") }.notification
            assertEquals(Notification.VISIBILITY_SECRET, safe.visibility)
            assertNull(safe.publicVersion)
            assertFalse(power.isInteractive)

            send("background-hostile")
            await("Guardian replaced the hostile original in the background even with safe previews enabled") {
                rowFor(25)?.section == GuardianInboxSection.COOLING && diagnostic("Guardian alert posted") &&
                    !sourceActive(25) && alerts().size == 5
            }
            val backgroundHeld = checkNotNull(rowFor(25))
            assertEquals(GuardianAssessment.UPSETTING, backgroundHeld.assessment)
            val neutral = alerts().single { it.tag == "guardian-safe:${backgroundHeld.id}" }.notification
            assertTrue(alertText(neutral).contains("Message protected until Guardian review"))
            assertFalse(alertText(neutral).contains(hostile))
            assertNull(neutral.publicVersion)
            assertNull(GuardianCaptureStore(context).revealInboxMessage(backgroundHeld.id, 0, guardianInboxClock(context)))
            assertFalse(power.isInteractive)
        } finally {
            shell("input keyevent KEYCODE_WAKEUP")
            shell("wm dismiss-keyguard")
            shell("cmd notification disallow_listener $component")
            await("Test listener stopped") { !GuardianCapture.isEnabled(context) }
            GuardianSafeNotifications.cancel(context)
            preferences.edit().clear().commit()
            dismissal.edit().clear().commit()
            capturePrefs.edit().remove("hold_uncertain_unlisted").commit()
            store.monitoredContacts().forEach { store.saveContact(it.key, null) }
            store.resetLearning()
            store.clear()
        }
    }

    private fun previewHoldout(): List<NovaBrainValidationMessage> = listOf(
        "kitchen table", "garden path", "station ticket", "summer holiday", "school playground",
        "office desk", "weekend market", "hospital entrance", "meeting room", "library shelf",
    ).flatMapIndexed { group, context ->
        val id = "preview-holdout-$group"
        listOf(
            NovaBrainValidationMessage(id, "I will hurt you. Context $context", setOf(NovaBrainPattern.THREAT)),
            NovaBrainValidationMessage(id, "Do as I say or else. Context $context", setOf(NovaBrainPattern.COERCION)),
            NovaBrainValidationMessage(id, "Can we meet for lunch? Context $context", emptySet()),
            NovaBrainValidationMessage(id, "Fine, I guess. Context $context", emptySet()),
        )
    }
}
