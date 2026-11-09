package uk.co.traynor.privategallery.launcher

import org.junit.Assert.assertEquals
import org.junit.Test

class LauncherSmartFolder73Test {
    private val shortcut = LauncherItem.App("com.example.app/.Main")
    private val stockFolders = LauncherLayout().withSmartFolderTemplate(onlyIfEmpty = true).pages[1]

    @Test fun initialTemplateAddsPageTwoWithoutChangingOtherLayoutItems() {
        val original = LauncherLayout(
            pages = listOf(listOf(shortcut)),
            dock = listOf(shortcut, LauncherItem.Workspace),
            favourites = setOf(shortcut.component),
        )

        val result = original.withSmartFolderTemplate(onlyIfEmpty = true)

        assertEquals(original.pages.single(), result.pages.first())
        assertEquals(
            listOf(
                LauncherItem.SmartFolder(LauncherCategory.SUGGESTIONS),
                LauncherItem.SmartFolder(LauncherCategory.RECENTLY_USED),
                LauncherItem.SmartFolder(LauncherCategory.RECENTLY_ADDED),
                LauncherItem.SmartFolder(LauncherCategory.NOVA_OS),
            ),
            result.pages[1],
        )
        assertEquals(original.dock, result.dock)
        assertEquals(original.favourites, result.favourites)
        assertEquals(result, result.withSmartFolderTemplate(onlyIfEmpty = true))
    }

    @Test fun automaticTemplateLeavesOccupiedPageTwoAlone() {
        val original = LauncherLayout(pages = listOf(emptyList(), listOf(shortcut)))

        assertEquals(original, original.withSmartFolderTemplate(onlyIfEmpty = true))
    }

    @Test fun manualTemplatePreservesTilesAndDoesNotDuplicateCategoriesOrExceedCapacity() {
        val folder = LauncherItem.SmartFolder(LauncherCategory.PRODUCTIVITY, "My tools")
        val original = LauncherLayout(pages = listOf(emptyList(), listOf(shortcut, folder), listOf(shortcut)))

        val result = original.withSmartFolderTemplate(onlyIfEmpty = false, capacity = 3)

        assertEquals(listOf(shortcut, folder), result.pages[1].take(2))
        assertEquals(3, result.pages[1].size)
        val categories = result.pages[1].filterIsInstance<LauncherItem.SmartFolder>().map { it.category }
        assertEquals(categories.distinct(), categories)
        assertEquals(original.pages[2], result.pages[2])
        assertEquals(result, result.withSmartFolderTemplate(onlyIfEmpty = false, capacity = 3))
    }

    @Test fun stockDuplicateRemovalRetainsShortcutsDocksAndWidgetBindings() {
        val ownerFolder = LauncherItem.SmartFolder(LauncherCategory.NOVA_OS, "My shortcuts")
        val widget = LauncherWidget(19, "com.example.clock/.Widget", wide = false, page = 3)
        val duplicateWithWidget = LauncherWidget(20, "com.example.clock/.Widget", wide = true, page = 2)
        val original = NovaLauncherState(
            separateDisplays = false,
            cover = LauncherLayout(
                pages = listOf(listOf(shortcut), stockFolders, stockFolders, listOf(ownerFolder)),
                dock = listOf(shortcut, LauncherItem.Workspace),
            ),
            inner = LauncherLayout(pages = listOf(emptyList(), stockFolders, stockFolders)),
            widgets = listOf(widget, duplicateWithWidget),
        )

        val migrated = original.withoutRedundantSmartFolderPages()

        assertEquals(listOf(listOf(shortcut), stockFolders, listOf(ownerFolder)), migrated.cover.pages)
        assertEquals(original.cover.dock, migrated.cover.dock)
        assertEquals(original.inner, migrated.inner)
        assertEquals(listOf(widget.copy(page = 2), duplicateWithWidget), migrated.widgets)
        assertEquals(migrated, migrated.withoutRedundantSmartFolderPages())
    }

    @Test fun migrationKeepsAmbiguousPagesAndShortcutUndo() {
        val pages = listOf(listOf(shortcut), stockFolders, stockFolders)
        val withUndo = NovaLauncherState(cover = LauncherLayout(pages = pages), coverCleanupUndo = pages)
        val mixed = NovaLauncherState(cover = LauncherLayout(pages = listOf(emptyList(), stockFolders + shortcut, stockFolders)))

        assertEquals(withUndo, withUndo.withoutRedundantSmartFolderPages())
        assertEquals(mixed, mixed.withoutRedundantSmartFolderPages())
    }

    @Test fun automaticTemplateDoesNotDuplicateAnExistingStockPageMovedByTheOwner() {
        val layout = LauncherLayout(pages = listOf(listOf(shortcut), emptyList(), stockFolders))

        assertEquals(layout, layout.withSmartFolderTemplate(onlyIfEmpty = true))
    }

    @Test fun appPlacementIsOptInAndPreservesSmartFoldersAndExistingShortcutLocations() {
        val primary = "com.whatsapp/.Main"
        val friends = "$primary#14"
        val original = NovaLauncherState(
            configured = true,
            cover = LauncherLayout(
                pages = listOf(listOf(shortcut), stockFolders),
                dock = listOf(LauncherItem.App(primary)),
            ),
            inner = LauncherLayout(
                pages = listOf(listOf(LauncherItem.Folder("Existing", listOf(primary))), stockFolders),
            ),
        )
        val installed = listOf(primary, friends, friends, "invalid")

        assertEquals(original, original.withNewlyInstalledApps(installed))
        val enabled = original.copy(newAppPlacement = LauncherNewAppPlacement.HOME)
        val placed = enabled.withNewlyInstalledApps(installed)

        assertEquals(listOf(shortcut, LauncherItem.App(friends)), placed.cover.pages[0])
        assertEquals(enabled.inner.pages[0] + LauncherItem.App(friends), placed.inner.pages[0])
        assertEquals(enabled.cover.pages[1], placed.cover.pages[1])
        assertEquals(enabled.inner.pages[1], placed.inner.pages[1])
        assertEquals(enabled.cover.dock, placed.cover.dock)
        assertEquals(placed, placed.withNewlyInstalledApps(installed))
    }
}
