package uk.co.traynor.privategallery.launcher

import android.view.WindowManager
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Assert.assertTrue
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
            val entry = GuardianCaptureEntry(now - 1, "synthetic sender", "private sample inbox text")
            store.append(GuardianCaptureEvent(0, "conversation", now, listOf(entry)), 7, now)
            compose.onNodeWithText("Unlock Guardian Inbox").assertIsDisplayed()
            compose.onNodeWithText("private sample inbox text").assertDoesNotExist()
            compose.onNodeWithText("Reveal message").assertDoesNotExist()
            compose.runOnIdle {
                assertTrue(activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
            }
        } finally {
            store.clear()
        }
    }
}
