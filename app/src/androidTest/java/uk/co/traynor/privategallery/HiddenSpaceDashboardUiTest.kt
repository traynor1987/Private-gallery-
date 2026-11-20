package uk.co.traynor.privategallery

import android.view.WindowManager
import androidx.compose.runtime.MutableState
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import uk.co.traynor.privategallery.ui.HiddenPreferences

class HiddenSpaceDashboardUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun hiddenDashboardOpensSharedNotesAndLocksWithoutExposingPrivateTiles() {
        val activity = compose.activity
        val state = ViewModelProvider(activity)[ProtectedSessionState::class.java]
        val preferences = HiddenPreferences(activity)
        val previousCapture = preferences.allowScreenshots
        try {
            compose.onNodeWithTag("hidden-space-apps").assertDoesNotExist()
            compose.runOnIdle {
                preferences.allowScreenshots = true
                state.session.lock()
                state.key = null
                assertTrue(state.hidden.session.complete(state.hidden.session.beginAuthentication(), ByteArray(32) { HIDDEN_HOME_TEST_KEY_BYTE }))
                state.hidden.select(Route.VAULT)
                state.hidden.authenticated = true
                state.homeVisible = true
                state.homeHiddenAuthentication = state.hidden.session.authentication()
                state.route = Route.VAULT
                @Suppress("UNCHECKED_CAST")
                val route = MainActivity::class.java.getDeclaredField("route\$delegate")
                    .apply { isAccessible = true }.get(activity) as MutableState<Route>
                route.value = Route.VAULT
            }
            compose.waitUntil(5_000) { compose.onNodeWithTag("hidden-space-apps").isDisplayed() }
            compose.onNodeWithTag("hidden-app-nova-notes-draft").assertIsDisplayed().performClick()
            compose.onNodeWithText("Nova Notes").assertIsDisplayed()
            compose.runOnIdle {
                assertTrue((activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE) != 0)
            }
            compose.onNodeWithContentDescription("Back").performClick()
            compose.onNodeWithTag("hidden-space-apps").assertIsDisplayed()
            compose.onNodeWithContentDescription("Lock Space").performClick()
            compose.onNodeWithTag("hidden-space-apps").assertDoesNotExist()
            compose.runOnIdle { assertFalse(state.hidden.session.isUnlocked) }
        } finally {
            compose.runOnIdle {
                preferences.allowScreenshots = previousCapture
                activity.panicExit()
            }
        }
    }
}
