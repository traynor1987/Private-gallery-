package uk.co.traynor.privategallery.core.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class AppNavigationPolicyTest {
    @Test
    fun `keeps primary and planned destinations visible in navigation`() {
        assertEquals(
            listOf("Gallery", "Vault", "Favourite", "Browser", "Settings"),
            AppNavigationPolicy.destinations.map { it.label },
        )
    }
}
