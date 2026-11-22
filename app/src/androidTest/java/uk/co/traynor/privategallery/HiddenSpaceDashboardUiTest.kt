package uk.co.traynor.privategallery

import android.view.WindowManager
import androidx.compose.runtime.MutableState
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import uk.co.traynor.privategallery.core.browser.hidden.HiddenConnectionPolicy
import uk.co.traynor.privategallery.ui.BrowserBetaAcknowledgement
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
                openHiddenSpaceFixtureDashboard(activity, state)
            }
            openOverviewFromWorkspace()
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

    private fun openOverviewFromWorkspace() {
        compose.waitUntil(5_000) { compose.onNodeWithTag("hidden-workspace-pager").isDisplayed() }
        compose.onNodeWithTag("hidden-space-apps").assertDoesNotExist()
        compose.onNodeWithContentDescription("Home menu").performClick()
        compose.onNodeWithText("Hidden overview").performClick()
        compose.onNodeWithTag("hidden-space-apps").assertIsDisplayed()
    }

    @Test fun workspaceDrawerOpensLabsAndHomeReturnsToTheWorkspace() {
        val activity = compose.activity
        val state = ViewModelProvider(activity)[ProtectedSessionState::class.java]
        try {
            compose.onNodeWithTag("hidden-workspace-pager").assertDoesNotExist()
            compose.runOnIdle { openHiddenSpaceFixtureDashboard(activity, state) }
            compose.onNodeWithTag("hidden-workspace-pager").assertIsDisplayed()
            compose.onNodeWithTag("open-apps").performClick()
            compose.onNodeWithTag("drawer-search").performTextInput("Nova Notes")
            compose.onNodeWithTag("app-nova-notes-draft").performClick()
            compose.onNodeWithText("Nova Notes").assertIsDisplayed()
            compose.onNodeWithContentDescription("Back").performClick()
            compose.onNodeWithTag("drawer-sheet").assertIsDisplayed()
            compose.onNodeWithContentDescription("Close app drawer").performClick()
            compose.onNodeWithTag("hidden-workspace-pager").assertIsDisplayed()
            compose.onNodeWithContentDescription("Home menu").performClick()
            compose.onNodeWithText("Lock Space").performClick()
            compose.onNodeWithTag("hidden-workspace-pager").assertDoesNotExist()
            compose.runOnIdle { assertFalse(state.hidden.session.isUnlocked) }
        } finally {
            compose.runOnIdle { activity.panicExit() }
        }
    }

    @Test fun dashboardRoutesBuiltInBrowserAndTorThroughSeparateAdmissions() {
        val activity = compose.activity
        val state = ViewModelProvider(activity)[ProtectedSessionState::class.java]
        val primaryNotice = activity.getSharedPreferences("nova-browser-beta", 0)
        val hiddenNotice = activity.getSharedPreferences("nova-hidden-browser-beta", 0)
        val previous = listOf(primaryNotice, hiddenNotice).map { it.getInt("notice-revision", -1) }
        try {
            compose.runOnIdle {
                assertTrue(BrowserBetaAcknowledgement(activity).accept())
                assertTrue(hiddenNotice.edit().remove("notice-revision").commit())
                openHiddenSpaceFixtureDashboard(activity, state)
            }
            openOverviewFromWorkspace()
            val dashboard = compose.onNodeWithTag("hidden-space-apps")
            for ((tag, mode) in listOf(
                "hidden-app-browser-v1" to HiddenConnectionPolicy.Mode.VPN_ONLY,
                "hidden-app-hidden-tor" to HiddenConnectionPolicy.Mode.TOR_ONLY,
            )) {
                dashboard.performScrollToNode(hasTestTag(tag))
                compose.onNodeWithTag(tag).assertIsDisplayed().performClick()
                compose.onNodeWithText("Beta browser privacy").assertIsDisplayed()
                compose.runOnIdle {
                    assertEquals(mode, activity.novaHiddenBrowserMode)
                    assertEquals(Route.BROWSER, state.hidden.destination)
                    assertNull("Hidden requires its own acknowledgement before network admission", activity.hiddenBrowserOwner)
                }
                compose.onNodeWithText("Return Home").performClick()
                compose.onNodeWithTag("hidden-workspace-pager").assertIsDisplayed()
                openOverviewFromWorkspace()
                dashboard.assertIsDisplayed()
            }
        } finally {
            compose.runOnIdle {
                listOf(primaryNotice, hiddenNotice).zip(previous).forEach { (prefs, value) ->
                    prefs.edit().apply {
                        if (value == -1) remove("notice-revision") else putInt("notice-revision", value)
                    }.commit()
                }
                activity.panicExit()
            }
        }
    }
}

internal fun openHiddenSpaceFixtureDashboard(activity: MainActivity, state: ProtectedSessionState) {
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
