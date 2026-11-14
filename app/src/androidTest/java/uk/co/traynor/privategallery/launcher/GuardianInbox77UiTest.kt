package uk.co.traynor.privategallery.launcher

import android.view.WindowManager
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.platform.app.InstrumentationRegistry
import android.os.ParcelFileDescriptor
import android.app.KeyguardManager
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Assert.assertTrue
import kotlin.math.max
import kotlin.math.min
import org.junit.Rule
import org.junit.Test
import uk.co.traynor.privategallery.core.guardian.GuardianCaptureEntry
import uk.co.traynor.privategallery.core.guardian.GuardianCaptureEvent
import uk.co.traynor.privategallery.core.guardian.GuardianCaptureStore

class GuardianInbox77UiTest {
    @get:Rule val compose = createAndroidComposeRule<GuardianInboxActivity>()

    @Test fun safeNotificationActionsKeepSanctuaryActiveAndOpenOnlyLockedGuardian() {
        check(android.os.Build.FINGERPRINT.contains("generic") || android.os.Build.MODEL.contains("sdk_gphone"))
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = compose.activity
        val manager = checkNotNull(context.getSystemService(android.app.NotificationManager::class.java))
        val listener = android.content.ComponentName(context,
            uk.co.traynor.privategallery.core.guardian.GuardianNotificationListener::class.java).flattenToString()
        val preferences = GuardianSafeNotifications.prefs(context)
        val sanctuary = uk.co.traynor.privategallery.core.sanctuary.SanctuaryStore.get(context)
        val own = checkNotNull(context.getSystemService(android.os.UserManager::class.java))
            .getSerialNumberForUser(android.os.Process.myUserHandle())
        val permission = android.Manifest.permission.POST_NOTIFICATIONS
        val hadAccess = uk.co.traynor.privategallery.core.guardian.GuardianNotificationListener.hasAccess(context)
        fun shell(command: String) = ParcelFileDescriptor.AutoCloseInputStream(
            instrumentation.uiAutomation.executeShellCommand(command),
        ).bufferedReader().use { it.readText() }
        fun posted() = manager.activeNotifications.singleOrNull { it.tag == "guardian-safe" }?.notification
        check(preferences.all.isEmpty()) { "Safe notification fixture requires clean preferences" }
        check(sanctuary.refresh() == null)
        var opened: GuardianInboxActivity? = null
        try {
            instrumentation.uiAutomation.grantRuntimePermission(context.packageName, permission)
            if (!hadAccess) shell("cmd notification allow_listener $listener")
            compose.waitUntil(10_000) {
                uk.co.traynor.privategallery.core.guardian.GuardianNotificationListener.hasAccess(context)
            }
            check(preferences.edit().putBoolean("enabled", true).putBoolean("main", true)
                .putBoolean("quiet", false).putInt("duration", 60).commit())
            assertTrue(sanctuary.startFoldTest(uk.co.traynor.privategallery.core.sanctuary.SanctuaryMode.PANIC))
            val session = checkNotNull(sanctuary.refresh()).id
            val row = uk.co.traynor.privategallery.core.guardian.GuardianInboxRow(
                "private-row-id", System.currentTimeMillis(),
                uk.co.traynor.privategallery.core.guardian.GuardianInboxSection.COOLING, 60_000,
                uk.co.traynor.privategallery.core.guardian.GuardianAssessment.UPSETTING,
                senderName = "Private sender fixture", conversation =
                    uk.co.traynor.privategallery.core.guardian.GuardianConversationKey("com.whatsapp", own, "private-thread"),
            )
            GuardianSafeNotifications.post(context, row)
            compose.waitUntil(5_000) { posted() != null }
            val alert = checkNotNull(posted())
            org.junit.Assert.assertEquals(android.app.Notification.VISIBILITY_SECRET, alert.visibility)
            org.junit.Assert.assertNull(alert.publicVersion)
            val preview = listOf(android.app.Notification.EXTRA_TITLE, android.app.Notification.EXTRA_TEXT,
                android.app.Notification.EXTRA_BIG_TEXT, android.app.Notification.EXTRA_SUB_TEXT,
                android.app.Notification.EXTRA_SUMMARY_TEXT)
                .mapNotNull { alert.extras.getCharSequence(it)?.toString() }.joinToString("\n")
            assertTrue(preview.contains("Nova Guardian"))
            assertTrue(preview.let {
                !it.contains("Private sender fixture") && !it.contains("private-thread") && !it.contains("private-row-id")
            })
            assertTrue(alert.contentIntent.isImmutable)
            org.junit.Assert.assertEquals(context.packageName, alert.contentIntent.creatorPackage)
            val open = alert.actions.single { it.title.toString() == "Open Safe Notifications" }.actionIntent
            org.junit.Assert.assertEquals(alert.contentIntent, open)
            val later = alert.actions.single { it.title.toString() == "Review later" }.actionIntent
            assertTrue(later.isImmutable)
            later.send()
            compose.waitUntil(5_000) { posted() == null }
            org.junit.Assert.assertEquals(session, sanctuary.refresh()?.id)
            compose.onNodeWithText("Unlock Guardian Inbox").assertIsDisplayed()

            // Exercise the actual immutable activity PendingIntent, including an
            // attempted foreign fill-in: it must still enter locked Guardian.
            instrumentation.runOnMainSync {
                open.send(context, 0, android.content.Intent().setClassName("com.whatsapp", "com.whatsapp.Main"))
            }
            compose.waitUntil(10_000) {
                instrumentation.runOnMainSync {
                    opened = androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry.getInstance()
                        .getActivitiesInStage(androidx.test.runner.lifecycle.Stage.RESUMED)
                        .filterIsInstance<GuardianInboxActivity>().singleOrNull { it !== context }
                }
                opened != null
            }
            compose.onNodeWithText("Unlock Guardian Inbox").assertIsDisplayed()
            compose.onNodeWithText("Private sender fixture").assertDoesNotExist()
            assertTrue(checkNotNull(opened).window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
            org.junit.Assert.assertEquals(session, sanctuary.refresh()?.id)
            org.junit.Assert.assertEquals(
                uk.co.traynor.privategallery.core.sanctuary.SanctuaryLauncherPolicy.Decision.UNAPPROVED,
                uk.co.traynor.privategallery.core.sanctuary.SanctuaryLauncherPolicy.decision(context, "com.whatsapp"),
            )
        } finally {
            instrumentation.runOnMainSync { opened?.finish() }
            GuardianSafeNotifications.cancel(context)
            preferences.edit().clear().commit()
            sanctuary.end(uk.co.traynor.privategallery.core.sanctuary.SanctuaryStore.END_PHRASE)
            if (!hadAccess) shell("cmd notification disallow_listener $listener")
            // Runtime permission revocation can kill this instrumented process.
            // The isolated emulator owns the grant, as in the listener fixture.
        }
    }

    @Test fun reviewLaterOnlyClearsTheMatchingGuardianAlert() {
        val context = compose.activity
        val prefs = GuardianSafeNotifications.prefs(context)
        val token = "synthetic-active-alert"
        val key = "posted_token"
        val action = "uk.co.traynor.privategallery.GUARDIAN_REVIEW_LATER"
        val receiver = GuardianReviewLaterReceiver()
        prefs.edit().putString(key, token).commit()
        try {
            receiver.onReceive(context, android.content.Intent(action).setIdentifier("stale-alert"))
            assertTrue(prefs.getString(key, null) == token)
            receiver.onReceive(context, android.content.Intent("unrelated").setIdentifier(token))
            assertTrue(prefs.getString(key, null) == token)
            receiver.onReceive(context, android.content.Intent(action).setIdentifier(token))
            assertTrue(!prefs.contains(key))
        } finally {
            GuardianSafeNotifications.cancel(context)
        }
    }

    @Test fun privateInboxStartsLockedEvenWhenArchiveContainsMessages() {
        val activity = compose.activity
        val store = GuardianCaptureStore(activity)
        store.clear()
        try {
            val now = System.currentTimeMillis()
            val entry = GuardianCaptureEntry(now - 1, "private key", "private sample inbox text", "synthetic sender")
            store.append(GuardianCaptureEvent(0, "conversation", now, listOf(entry)), 7, now)
            compose.onNodeWithText("Unlock Guardian Inbox").assertIsDisplayed()
            compose.onNodeWithText("private sample inbox text").assertDoesNotExist()
            compose.onNodeWithText("synthetic sender").assertDoesNotExist()
            compose.onNodeWithText("Reveal message").assertDoesNotExist()
            compose.runOnIdle {
                assertTrue(activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
            }
        } finally {
            store.clear()
        }
    }
    @Test fun inboxNotificationOpensFullScreenConversationAfterDeviceAuthentication() {
        check(android.os.Build.FINGERPRINT.contains("generic") || android.os.Build.MODEL.contains("sdk_gphone"))
        val activity = compose.activity
        val store = GuardianCaptureStore(activity)
        val keyguard = checkNotNull(activity.getSystemService(KeyguardManager::class.java))
        check(!keyguard.isDeviceSecure)
        val pin = "436890"
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        fun shell(command: String) = ParcelFileDescriptor.AutoCloseInputStream(
            automation.executeShellCommand(command),
        ).bufferedReader().use { it.readText() }
        try {
            assertTrue(shell("locksettings set-pin $pin").contains("Pin set", ignoreCase = true))
            compose.waitUntil(5_000) { keyguard.isDeviceSecure }
            store.clear()
            val now = System.currentTimeMillis()
            val message = "After everything I have done for you. Synthetic fixture."
            store.append(GuardianCaptureEvent(0, "conversation", now,
                listOf(GuardianCaptureEntry(now - 1, "sender-key", message, "Fixture contact"))), 7, now)
            compose.onNodeWithText("Unlock Guardian Inbox").performClick()
            var focusedSince: Long? = null
            compose.waitUntil(15_000) {
                val focused = shell("dumpsys window").lineSequence().any {
                    "mCurrentFocus=" in it && ("ConfirmLock" in it || "BiometricPrompt" in it)
                }
                val elapsed = android.os.SystemClock.elapsedRealtime()
                focusedSince = if (focused) focusedSince ?: elapsed else null
                focusedSince?.let { elapsed - it >= 1_000L } == true
            }
            shell("input keyevent ${pin.map { "KEYCODE_$it" }.joinToString(" ")} KEYCODE_ENTER")
            compose.waitUntil(15_000) {
                compose.activityRule.scenario.state == androidx.lifecycle.Lifecycle.State.RESUMED
            }
            compose.waitUntil(15_000) { compose.onAllNodesWithText("Fixture contact").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Fixture contact").performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithText("Guardian Conversation").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("guardian-conversation-history")
                .performScrollToNode(hasText("Possible patterns · Guilt-tripping"))
            compose.onNodeWithText(message).assertDoesNotExist()
            compose.onNodeWithText("Possible patterns · Guilt-tripping").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Review message").performScrollTo().performClick()
            compose.waitUntil(10_000) {
                compose.onAllNodesWithText(message).fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText(message).performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Copy Message").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Save Screenshot").performScrollTo().performClick()
            compose.onNodeWithText("Save to Gallery").assertIsDisplayed()
            compose.onNodeWithText("Cancel export").performClick()
            compose.onNodeWithText("Not Upsetting").performScrollTo().performClick()
            compose.onNodeWithText("Save learning example").performScrollTo().performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithText("Remove learning example").fetchSemanticsNodes().isNotEmpty() }
            val saved = store.inboxRows(7, uk.co.traynor.privategallery.core.guardian.GuardianInboxClock(
                System.currentTimeMillis(), android.os.SystemClock.elapsedRealtime(), -1)).single()
            assertTrue(saved.ownerLabel == uk.co.traynor.privategallery.core.guardian.GuardianAssessment.LOW_RISK)
        } finally {
            store.clear()
            shell("locksettings clear --old $pin")
        }
    }

    @Test fun guardianExportPreservesClipboardAndRendersLongMessage() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val message = "First line! 👋\nSecond line — exact.\n".repeat(55)
        val clip = guardianSensitiveMessageClip(message)
        assertTrue(clip.description.extras!!.getBoolean(android.content.ClipDescription.EXTRA_IS_SENSITIVE))
        assertTrue(clip.getItemAt(0).text.toString() == message)
        val short = createGuardianShareFile(context, "Short", true)
        val long = createGuardianShareFile(context, message, true)
        try {
            val shortSize = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            val longSize = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            android.graphics.BitmapFactory.decodeFile(short.path, shortSize)
            android.graphics.BitmapFactory.decodeFile(long.path, longSize)
            assertTrue(longSize.outMimeType == "image/png")
            assertTrue(longSize.outWidth == shortSize.outWidth)
            assertTrue(longSize.outHeight > shortSize.outHeight * 10)
            val uri = stageGuardianGalleryImage(context, long)
            try {
                publishGuardianGalleryImage(context, uri)
                val published = context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
                assertTrue(published.contentEquals(long.readBytes()))
            } finally {
                context.contentResolver.delete(uri, null, null)
            }
        } finally {
            short.delete()
            long.delete()
        }
    }

    @Test fun primarySecondaryAndAccentTextMeetNormalTextContrast() {
        for (foreground in listOf(GuardianPrimary, GuardianMuted, GuardianBlue, GuardianAmber, GuardianMint)) {
            for (background in listOf(GuardianNavy, GuardianGlass)) {
                val a = foreground.luminance()
                val b = background.luminance()
                assertTrue("Contrast for $foreground on $background",
                    (max(a, b) + .05f) / (min(a, b) + .05f) >= 4.5f)
            }
        }
    }
}
