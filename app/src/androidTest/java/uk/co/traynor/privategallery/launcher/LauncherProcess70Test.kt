package uk.co.traynor.privategallery.launcher

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

/** The host force-stops the application and proves a different PID between these entry points. */
class LauncherProcess70Test {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val expected = NovaLauncherState(
        configured = true,
        separateDisplays = true,
        cover = LauncherLayout(
            pages = listOf(
                listOf(LauncherItem.Workspace),
                listOf(LauncherItem.App("example.owner/.Main")),
            ),
            dock = listOf(LauncherItem.Workspace),
        ),
        inner = LauncherLayout(
            pages = listOf(
                emptyList(),
                listOf(LauncherItem.SmartFolder(LauncherCategory.RECENTLY_USED, "My recents")),
            ),
            dock = emptyList(),
        ),
        appearance = LauncherAppearance(wallpaper = LauncherWallpaper.MIDNIGHT, grid = LauncherGrid.COMPACT),
    )

    private val savedSeparate = expected.copy(
        cover = expected.cover.copy(pages = expected.cover.pages.reversed()),
        inner = expected.inner.copy(pages = expected.inner.pages.reversed()),
        appearance = expected.appearance.copy(wallpaper = LauncherWallpaper.PLUM),
    )

    @Test fun seedConfiguredLauncher() {
        val store = NovaLauncherStore(context)
        val workspace = WorkspaceLayoutStore(context)
        assertEquals(WorkspaceLayoutMode.SEPARATE, workspace.read().mode)
        assertTrue(store.save(savedSeparate))
        assertTrue(workspace.useLauncher())
        assertTrue(store.save(expected))
        assertEquals(expected, store.read())
    }

    @Test fun verifyConfiguredLauncherAfterProcessDeath() {
        val store = NovaLauncherStore(context)
        val workspace = WorkspaceLayoutStore(context)
        assertEquals(expected, store.read())
        assertEquals(WorkspaceLayoutMode.MIRROR, workspace.read().mode)
        assertTrue(workspace.restorePrevious())
        assertEquals(savedSeparate, store.read())
        assertEquals(WorkspaceLayoutMode.SEPARATE, workspace.read().mode)
    }
}
