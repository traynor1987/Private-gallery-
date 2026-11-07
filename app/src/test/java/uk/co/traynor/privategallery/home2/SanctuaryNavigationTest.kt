package uk.co.traynor.privategallery.home2

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SanctuaryNavigationTest {
    @Test fun approvedAppsAndRecoveryDoNotGrantUnrelatedRoutes() {
        val approved = setOf("arcade", "mind", "web:trusted")
        for (id in listOf("arcade", "arcade-sudoku", "mind:home", "web:trusted", "resume-internal:mind",
                "sanctuary", "native-settings", "panic", "lock-now")) {
            assertTrue(id, sanctuaryAllowsNovaRoute(approved, id))
        }
        for (id in listOf("web:whatsapp", "private-apps", "browser-v1", "resume-internal:store", "store:whatsapp")) {
            assertFalse(id, sanctuaryAllowsNovaRoute(approved, id))
        }
    }
}
