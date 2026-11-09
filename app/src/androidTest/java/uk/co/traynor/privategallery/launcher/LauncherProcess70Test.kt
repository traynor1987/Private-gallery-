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

    @Test fun seedConfiguredLauncher() {
        val store = NovaLauncherStore(context)
        assertTrue(store.save(expected))
        assertEquals(expected, store.read())
    }

    @Test fun verifyConfiguredLauncherAfterProcessDeath() {
        assertEquals(expected, NovaLauncherStore(context).read())
    }
}
