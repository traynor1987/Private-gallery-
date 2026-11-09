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

    @Test fun copyCurrentHomeNeverUsesTheCatalogueAsAnImportedLayout() {
        val state = launcherInitialState(
            LauncherStartingLayout.COPY_CURRENT, LauncherTemplate.MINIMAL, listOf(phone, browser, notes),
        )
        assertEquals(listOf(LauncherItem.Workspace), state.cover.pages.flatten() + state.cover.dock)
        assertEquals(listOf(LauncherItem.Workspace), state.inner.pages.flatten() + state.inner.dock)
        assertFalse(state.configured)
    }

    @Test fun copyCurrentHomeKeepsOnlyTheExplicitlySelectedAvailableShortcuts() {
        val state = launcherInitialState(
            LauncherStartingLayout.COPY_CURRENT, LauncherTemplate.MINIMAL,
            listOf(phone, browser, notes), listOf(notes, phone, notes, game),
        )
        val expected = listOf(LauncherItem.App(notes), LauncherItem.App(phone))
        assertEquals(expected, state.cover.pages.single())
        assertEquals(expected, state.inner.pages.single())
    }

    @Test fun classicTemplateLeavesUnrelatedAppsInTheDrawer() {
        val unrelated = (1..80).map { "com.example.app${it}/.Main" }
        val state = launcherInitialState(
            LauncherStartingLayout.TEMPLATE, LauncherTemplate.CLASSIC, unrelated + phone,
        )
        for (layout in listOf(state.cover, state.inner)) {
            assertEquals(listOf(emptyList<LauncherItem>()), layout.pages)
            assertEquals(listOf(LauncherItem.App(phone), LauncherItem.Workspace), layout.dock)
        }
    }

    @Test fun categoryTemplatesLeaveUnrelatedAppsInTheDrawer() {
        for ((template, matching) in listOf(LauncherTemplate.PRODUCTIVITY to notes, LauncherTemplate.GAMING to game)) {
            val state = launcherInitialState(
                LauncherStartingLayout.TEMPLATE, template, listOf(browser, matching),
            )
            for (layout in listOf(state.cover, state.inner)) {
                assertEquals(listOf(emptyList<LauncherItem>()), layout.pages)
                assertEquals(listOf(LauncherItem.App(matching), LauncherItem.Workspace), layout.dock)
            }
        }
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

    @Test fun cleanupRemovesOnlyExplicitlyReviewedShortcutsAndCanBeUndone() {
        val chosen = LauncherItem.App(phone)
        val folder = LauncherItem.Folder("My tools", listOf(phone, browser))
        val layout = LauncherLayout(
            pages = listOf(listOf(chosen, folder), listOf(LauncherItem.App(browser)), listOf(chosen)),
            dock = listOf(chosen, LauncherItem.Workspace),
        )
        val widget = LauncherWidget(1, "com.example.clock/.Widget", wide = false, page = 1)
        val original = NovaLauncherState(cover = layout, widgets = listOf(widget))
        val cleaned = original.cleanUpShortcuts(false, setOf(
            LauncherShortcutPosition(0, 0), LauncherShortcutPosition(0, 1), LauncherShortcutPosition(2, 0),
        ))
        assertEquals(listOf(listOf(folder), listOf(LauncherItem.App(browser))), cleaned.cover.pages)
        assertEquals(layout.dock, cleaned.cover.dock)
        assertEquals(original.inner, cleaned.inner)
        assertEquals(original.widgets, cleaned.widgets)
        assertEquals(original, cleaned.undoShortcutCleanup(false))
    }

    @Test fun laterHomeEditsCannotBeOverwrittenByCleanupUndo() {
        val phoneItem = LauncherItem.App(phone)
        val browserItem = LauncherItem.App(browser)
        val original = NovaLauncherState(inner = LauncherLayout(pages = listOf(listOf(phoneItem, browserItem))))
        val cleaned = original.cleanUpShortcuts(true, setOf(LauncherShortcutPosition(0, 0)))
        val dockEdited = cleaned.withLayout(true, cleaned.inner.copy(dock = listOf(LauncherItem.App(notes))))
        assertEquals(original.inner.pages, dockEdited.undoShortcutCleanup(true).inner.pages)
        assertEquals(dockEdited.inner.dock, dockEdited.undoShortcutCleanup(true).inner.dock)
        val homeEdited = cleaned.withLayout(true, cleaned.inner.copy(pages = listOf(listOf(LauncherItem.App(notes)))))
        assertEquals(homeEdited, homeEdited.undoShortcutCleanup(true))
    }

    @Test fun widgetPositionsFollowPageRemovalOnlyOnTheEditedDisplay() {
        val layout = LauncherLayout(pages = List(3) { emptyList() })
        val provider = "com.example.clock/.Widget"
        val original = NovaLauncherState(
            cover = layout, inner = layout,
            widgets = listOf(
                LauncherWidget(1, provider, wide = true, page = 1),
                LauncherWidget(2, provider, wide = true, page = 2),
                LauncherWidget(3, provider, wide = false, page = 2),
            ),
        )
        val removed = original.withoutPage(true, 1)
        assertEquals(2, removed.inner.pages.size)
        assertEquals(layout, removed.cover)
        assertEquals(listOf(0, 1, 2), removed.widgets.map(LauncherWidget::page))
    }

    @Test fun dropChangesTheSavedOrderWithoutDiscardingAnyItems() {
        val items = listOf(LauncherItem.App(phone), LauncherItem.App(browser), LauncherItem.Workspace)
        assertEquals(listOf(items[1], items[2], items[0]), items.movingLauncherItem(0, 2))
        assertEquals(items, items.movingLauncherItem(0, 3))
    }
}
