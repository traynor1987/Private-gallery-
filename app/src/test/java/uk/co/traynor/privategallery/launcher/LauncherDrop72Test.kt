package uk.co.traynor.privategallery.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LauncherDrop72Test {
    private val phone = LauncherItem.App("example.phone/.Main")
    private val music = LauncherItem.App("example.music/.Main")
    private val maps = LauncherItem.App("example.maps/.Main")

    @Test fun movingOutDissolvesFolderAndPlacesBothShortcutsWithoutAnIntermediateWrite() {
        val folder = LauncherItem.Folder("Tools", listOf(phone.component, maps.component))
        val slot = LauncherSlot(null, 1)
        val layout = LauncherLayout(dock = listOf(LauncherItem.Workspace, folder))
        assertEquals(listOf(LauncherItem.Workspace, maps, phone),
            layout.moveFolderMemberOut(slot, folder, phone.component)?.dock)
        assertNull(layout.moveFolderMemberOut(slot.copy(index = 0), folder, phone.component))
        assertNull(layout.copy(dock = layout.dock + phone).moveFolderMemberOut(slot, folder, phone.component))
        assertEquals(listOf(LauncherItem.Workspace, folder), layout.dock)
    }

    @Test fun uninstallDissolvesOnlyFoldersContainingTheRemovedProfile() {
        val clone = "${phone.component}#17"
        val personal = LauncherItem.Folder("Personal", listOf(phone.component, maps.component))
        val work = LauncherItem.Folder("Work", listOf(clone, music.component))
        val state = NovaLauncherState(cover = LauncherLayout(pages = listOf(listOf(personal))),
            inner = LauncherLayout(dock = listOf(LauncherItem.Workspace, work)))
        val result = state.removeUninstalledFolderMembers(phone.component.substringBefore('/'), 17)
        assertEquals(state.cover, result.cover)
        assertEquals(listOf(LauncherItem.Workspace, music), result.inner.dock)
        assertEquals(listOf(maps), state.removeUninstalledFolderMembers(phone.component.substringBefore('/'), null)
            .cover.pages.single())
    }

    @Test fun committedFolderEditDissolvesAtItsSlotAndRejectedDropKeepsFolder() {
        val folder = LauncherItem.Folder("Tools", listOf(phone.component, maps.component))
        val layout = LauncherLayout(pages = listOf(listOf(LauncherItem.Workspace, folder, music)))
        val edited = checkNotNull(layout.replaceFolderAt(
            LauncherSlot(0, 1), folder, folder.copy(apps = listOf(maps.component)),
        ))
        val saved = NovaLauncherState(cover = edited).reconcileUserFolders()
        assertEquals(listOf(LauncherItem.Workspace, maps, music), saved.cover.pages.single())
        val empty = NovaLauncherState(cover = edited.copy(dock = listOf(folder.copy(apps = emptyList()))))
        assertEquals(emptyList<LauncherItem>(), empty.reconcileUserFolders().cover.dock)
        val invalid = layout.dropLauncherItem(folder, LauncherSlot(0, 1), LauncherDrop(LauncherSlot(8, 0)))
        assertEquals(layout, invalid)
        assertEquals(layout, NovaLauncherState(cover = invalid).reconcileUserFolders().cover)
    }

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

    @Test fun folderEditReplacesOnlyItsOriginalSlotEvenWhenAnotherFolderIsIdentical() {
        val folder = LauncherItem.Folder("Work", listOf(phone.component, maps.component))
        val replacement = LauncherItem.Folder("Couriers", listOf(maps.component, phone.component, music.component))
        val layout = LauncherLayout(pages = listOf(listOf(folder), listOf(music, folder)))
        assertEquals(null, layout.uniqueFolderSlot(folder))
        assertEquals(
            listOf(listOf(folder), listOf(music, replacement)),
            layout.replaceFolderAt(LauncherSlot(1, 1), folder, replacement)?.pages,
        )
        assertEquals(null, layout.replaceFolderAt(LauncherSlot(1, 0), folder, replacement))
        assertEquals(LauncherSlot(0, 0), layout.copy(pages = layout.pages.take(1)).uniqueFolderSlot(folder))
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
