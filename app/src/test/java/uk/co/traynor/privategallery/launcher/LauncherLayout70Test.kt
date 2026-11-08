package uk.co.traynor.privategallery.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherLayout70Test {
    private val phone = "com.example.phone/.Main"
    private val browser = "com.example.browser/.Main"
    private val notes = "com.example.notes/.Main"
    private val game = "com.example.games/.Main"

    @Test fun coverAndInnerEditsAreIndependentUntilSharedLayoutIsSelected() {
        val cover = LauncherLayout(listOf(listOf(LauncherItem.App(phone))), emptyList(), emptySet())
        val inner = LauncherLayout(listOf(listOf(LauncherItem.App(browser))), emptyList(), emptySet())
        val state = NovaLauncherState(cover = cover, inner = inner)
        val edited = state.withLayout(true, inner.copy(favourites = setOf(browser)))
        assertEquals(cover, edited.layout(false))
        assertEquals(setOf(browser), edited.layout(true).favourites)
        val shared = edited.copy(separateDisplays = false).withLayout(true, cover.copy(favourites = setOf(phone)))
        assertEquals(shared.layout(false), shared.layout(true))
        assertEquals(edited.inner, shared.inner)
    }

    @Test fun bestEffortCopyUsesOnlyAvailableAppsWithWorkspaceShortcut() {
        val state = launcherInitialState(
            LauncherStartingLayout.COPY_CURRENT, LauncherTemplate.MINIMAL,
            listOf(phone, browser, phone, "not a component", notes),
        )
        val items = state.cover.pages.flatten() + state.cover.dock
        assertEquals(1, items.count { it == LauncherItem.Workspace })
        assertEquals(listOf(phone, browser, notes), items.filterIsInstance<LauncherItem.App>().map { it.component })
        assertFalse(state.configured)
    }

    @Test fun templatesUseDistinctLayoutsAndRetainTheWorkspaceEntry() {
        val states = LauncherTemplate.entries.map { template ->
            launcherInitialState(LauncherStartingLayout.TEMPLATE, template, listOf(phone, browser, notes, game))
        }
        assertEquals(LauncherTemplate.entries.size, states.distinct().size)
        assertTrue(states.all { state -> (state.cover.pages.flatten() + state.cover.dock).contains(LauncherItem.Workspace) })
        assertNotEquals(states[0].cover, states[2].cover)
    }

    @Test fun freshLayoutStartsCleanAndLaunchCountsChangeOnlyForValidComponents() {
        val state = launcherInitialState(LauncherStartingLayout.START_FRESH, LauncherTemplate.GAMING, listOf(phone))
        assertEquals(listOf(LauncherItem.Workspace), state.cover.pages.flatten() + state.cover.dock)
        assertEquals(state, state.withSuccessfulLaunch("intent://unsafe"))
        assertEquals(mapOf(phone to 2), state.withSuccessfulLaunch(phone).withSuccessfulLaunch(phone).launchCounts)
    }
}
