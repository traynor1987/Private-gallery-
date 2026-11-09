package uk.co.traynor.privategallery.launcher

import org.junit.Assert.assertEquals
import org.junit.Test

class LauncherDrop72Test {
    private val phone = LauncherItem.App("example.phone/.Main")
    private val music = LauncherItem.App("example.music/.Main")
    private val maps = LauncherItem.App("example.maps/.Main")

    @Test fun reorderUsesTheInsertionBoundaryBeforeRemovingTheSource() {
        val layout = LauncherLayout(pages = listOf(listOf(phone, music, maps)))
        val result = layout.dropLauncherItem(phone, LauncherSlot(0, 0), LauncherDrop(LauncherSlot(0, 2)))
        assertEquals(listOf(music, phone, maps), result.pages.single())
    }

    @Test fun appFromDrawerCanJoinAnExistingFolder() {
        val folder = LauncherItem.Folder("Utilities", listOf(phone.component, maps.component))
        val layout = LauncherLayout(pages = listOf(listOf(folder)))
        val result = layout.dropLauncherItem(music, null, LauncherDrop(LauncherSlot(0, 0), group = true))
        assertEquals(listOf(folder.copy(apps = folder.apps + music.component)), result.pages.single())
    }

    @Test fun groupingAppsInTheSamePageKeepsTheTargetPosition() {
        val layout = LauncherLayout(pages = listOf(listOf(phone, maps, music)))
        val result = layout.dropLauncherItem(phone, LauncherSlot(0, 0), LauncherDrop(LauncherSlot(0, 2), group = true))
        val folder = LauncherItem.Folder("Folder", listOf(music.component, phone.component))
        assertEquals(listOf(maps, folder), result.pages.single())
    }

    @Test fun sourceAndDestinationCanBeOnDifferentPagesOrTheDock() {
        val layout = LauncherLayout(pages = listOf(listOf(phone), listOf(maps)))
        val docked = layout.dropLauncherItem(phone, LauncherSlot(0, 0), LauncherDrop(LauncherSlot(null, 0)))
        assertEquals(listOf(emptyList(), listOf(maps)), docked.pages)
        assertEquals(listOf(phone, LauncherItem.Workspace), docked.dock)
        val moved = docked.dropLauncherItem(phone, LauncherSlot(null, 0), LauncherDrop(LauncherSlot(1, 1)))
        assertEquals(listOf(emptyList(), listOf(maps, phone)), moved.pages)
        assertEquals(listOf(LauncherItem.Workspace), moved.dock)
    }

    @Test fun invalidDestinationsLeaveTheSourceInPlace() {
        val layout = LauncherLayout(pages = listOf(listOf(phone, music)), dock = List(MAX_LAUNCHER_DOCK_ITEMS) { maps })
        assertEquals(layout, layout.dropLauncherItem(phone, LauncherSlot(0, 0), LauncherDrop(LauncherSlot(null, 0))))
        assertEquals(layout, layout.dropLauncherItem(phone, LauncherSlot(0, 1), LauncherDrop(LauncherSlot(0, 2))))
        assertEquals(layout, layout.dropLauncherItem(phone, null, LauncherDrop(LauncherSlot(0, 2))))
    }
}
