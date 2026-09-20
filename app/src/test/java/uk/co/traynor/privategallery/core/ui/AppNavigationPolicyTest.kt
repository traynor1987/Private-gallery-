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

    @Test
    fun `resolves the heart label from the selected favourite name`() {
        assertEquals("Jenna", AppNavigationPolicy.labelFor(AppNavigationDestination.FAVOURITE, "Jenna"))
        assertEquals("Favourite", AppNavigationPolicy.labelFor(AppNavigationDestination.FAVOURITE, null))
        assertEquals("Vault", AppNavigationPolicy.labelFor(AppNavigationDestination.VAULT, "Jenna"))
    }
}
