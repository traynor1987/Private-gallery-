package uk.co.traynor.privategallery

import org.junit.Assert.*
import org.junit.Test

class PrimaryRouteRestorationTest {
    @Test fun lostOneTimeRecoveryDisplayRequiresAuthenticationBeforeRendering() {
        assertEquals(Route.LOCK, restoredPrimaryRoute(true, true, Route.RECOVERY_KEY_SETUP, true, false))
    }
    @Test fun ordinaryConfigurationRecreationRetainsItsLivePrimaryDestination() {
        for (route in listOf(Route.VAULT, Route.GALLERY, Route.SETTINGS, Route.BROWSER)) {
            assertEquals(route, restoredPrimaryRoute(true, true, route, true, false))
        }
        assertEquals(Route.RECOVERY_KEY_SETUP, restoredPrimaryRoute(true, true, Route.RECOVERY_KEY_SETUP, true, true))
        assertEquals(Route.LOCK, restoredPrimaryRoute(false, false, Route.VAULT, true, false))
        assertEquals(Route.SETUP, restoredPrimaryRoute(false, false, Route.VAULT, false, false))
    }
}
