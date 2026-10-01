package uk.co.traynor.privategallery

import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import org.junit.Assert.*
import org.junit.Test

class MainActivityRecoveryRecreationTest {
    @Test fun retainedRecoveryRouteWithoutActivityDisplayCannotCrashOrRemainAuthorized() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val retained = ViewModelProvider(activity)[ProtectedSessionState::class.java]
                retained.authority.revoke()
                retained.authority.open(ByteArray(32) { 37 })
                retained.session.unlock()
                retained.route = Route.RECOVERY_KEY_SETUP
                MainActivity::class.java.getDeclaredField("pendingRecoveryKey").apply { isAccessible = true }
                    .set(activity, "SYNTHETIC-ONE-TIME-DISPLAY".toCharArray())
                MainActivity::class.java.getDeclaredMethod("setRoute", Route::class.java).apply { isAccessible = true }
                    .invoke(activity, Route.RECOVERY_KEY_SETUP)
            }
            scenario.recreate()
            scenario.onActivity { activity ->
                val retained = ViewModelProvider(activity)[ProtectedSessionState::class.java]
                assertFalse(retained.session.isUnlocked)
                assertNull(retained.authority.operationOrNull(uk.co.traynor.privategallery.core.security.PrimaryScope.entries.toSet()))
                assertNotEquals(Route.RECOVERY_KEY_SETUP, retained.route)
            }
        }
    }
}
