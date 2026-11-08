package uk.co.traynor.privategallery

import androidx.compose.runtime.MutableState
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import uk.co.traynor.privategallery.core.security.AutoLockTimeout
import uk.co.traynor.privategallery.ui.HiddenPreferences

class HiddenHomeNavigation67Test {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val owner get() = ViewModelProvider(compose.activity)[ProtectedSessionState::class.java]

    @Test fun hiddenVaultHomeReacquiresRevokedPresentationAndSurvivesBackground() {
        val preferences = HiddenPreferences(compose.activity)
        val previous = preferences.timeout
        try {
            preferences.timeout = AutoLockTimeout.MINUTES_5
            compose.runOnIdle {
                check(compose.activity.packageName.endsWith(".hiddenprototype"))
                val state = owner
                state.session.lock()
                state.key = null
                val hidden = state.hidden
                assertTrue(hidden.session.complete(hidden.session.beginAuthentication(), ByteArray(32) { 12 }))
                hidden.select(Route.VAULT)
                hidden.authenticated = true
                state.homeVisible = false
                state.homeHiddenAuthentication = hidden.session.authentication()
                state.route = Route.VAULT
                @Suppress("UNCHECKED_CAST")
                val route = MainActivity::class.java.getDeclaredField("route\$delegate")
                    .apply { isAccessible = true }.get(compose.activity) as MutableState<Route>
                route.value = Route.VAULT
            }
            compose.onNodeWithTag("nova-home").assertIsDisplayed().performClick()
            compose.onNodeWithTag("preview-home").assertIsDisplayed()
            compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
            compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
            compose.onNodeWithTag("preview-home").assertIsDisplayed()
            compose.runOnIdle {
                assertTrue(owner.hidden.selected && owner.hidden.session.isUnlocked)
                assertFalse(owner.session.isUnlocked)
                assertNull(owner.key)
                assertFalse(compose.activity.privacyCoverVisible())
            }
        } finally {
            compose.runOnIdle {
                preferences.timeout = previous
                compose.activity.panicExit()
            }
        }
    }
}
