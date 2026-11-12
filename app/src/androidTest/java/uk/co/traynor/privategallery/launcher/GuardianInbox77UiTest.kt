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
