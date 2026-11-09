package uk.co.traynor.privategallery.launcher

import org.junit.Assert.assertEquals
import org.junit.Test

class LauncherSmartFolder73Test {
    private val shortcut = LauncherItem.App("com.example.app/.Main")

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
}
