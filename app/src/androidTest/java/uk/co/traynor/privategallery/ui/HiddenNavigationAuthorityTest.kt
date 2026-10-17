package uk.co.traynor.privategallery.ui

import androidx.compose.runtime.MutableState
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import org.junit.Assert.*
import org.junit.Test
import uk.co.traynor.privategallery.MainActivity
import uk.co.traynor.privategallery.ProtectedSessionState
import uk.co.traynor.privategallery.Route
import uk.co.traynor.privategallery.core.ui.AppNavigationDestination

/** Exercise the real Activity handoff, including stale UI callbacks after Primary revocation. */
class HiddenNavigationAuthorityTest {
    @Test fun externalNavigationRequiresBothPrimaryAuthorityAndKey() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val state=ViewModelProvider(activity)[ProtectedSessionState::class.java]
                val navigate=MainActivity::class.java.getDeclaredMethod("leaveHiddenToDestination",AppNavigationDestination::class.java).apply {isAccessible=true}
                @Suppress("UNCHECKED_CAST")
                val route=MainActivity::class.java.getDeclaredField("route\$delegate").apply {isAccessible=true}.get(activity) as MutableState<Route>
                val before=route.value
                try {
                    for(hasAuthority in listOf(false,true)) for(hasKey in listOf(false,true)) {
                        if(hasAuthority && hasKey)continue
                        state.session.lock();if(hasAuthority)state.session.unlock()
                        state.key=if(hasKey)ByteArray(32){9} else null
                        listOf(AppNavigationDestination.GALLERY,AppNavigationDestination.BROWSER,AppNavigationDestination.SETTINGS).forEach {destination ->
                            navigate.invoke(activity,destination)
                            assertEquals("Rejected $destination without both Primary credentials",before,route.value)
                        }
                        state.key?.fill(0);state.key=null
                    }
                    state.key=ByteArray(32){9};state.session.unlock()
                    navigate.invoke(activity,AppNavigationDestination.GALLERY)
                    assertEquals(Route.GALLERY,route.value)
                } finally {state.key?.fill(0);state.key=null;state.session.lock()}
            }
        }
    }
}
