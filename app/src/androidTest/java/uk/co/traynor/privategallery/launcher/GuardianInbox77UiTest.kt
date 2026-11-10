package uk.co.traynor.privategallery.launcher

import android.view.WindowManager
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.test.assertIsDisplayed
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
