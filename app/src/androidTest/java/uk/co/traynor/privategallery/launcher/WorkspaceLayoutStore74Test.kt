package uk.co.traynor.privategallery.launcher

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import uk.co.traynor.privategallery.home2.Profile

class WorkspaceLayoutStore74Test {
    private val base = InstrumentationRegistry.getInstrumentation().targetContext
    private val namespace = "workspace-test-${UUID.randomUUID()}"
    private val context = object : ContextWrapper(base) {
        override fun getApplicationContext(): Context = this
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
            base.getSharedPreferences("$namespace-$name", mode)
    }

    @After fun removeOnlyFixturePreferences() {
        base.deleteSharedPreferences("$namespace-$LAUNCHER_LAYOUT_PREFS")
    }

    @Test fun reopeningKeepsMirrorSnapshotAndRestoresBothLauncherDisplays() {
        val launcher = NovaLauncherStore(context)
        val before = NovaLauncherState(
            configured = true,
            cover = LauncherLayout(pages = listOf(listOf(LauncherItem.App("org.example/.Cover")))),
            inner = LauncherLayout(pages = listOf(listOf(LauncherItem.App("org.example/.Inner")))),
            appearance = LauncherAppearance(wallpaper = LauncherWallpaper.PLUM),
        )
        assertTrue(launcher.save(before))
        assertTrue(WorkspaceLayoutStore(context).useLauncher())
        assertTrue(launcher.save(before.copy(cover = LauncherLayout(), inner = LauncherLayout(),
            appearance = before.appearance.copy(wallpaper = LauncherWallpaper.MIDNIGHT))))
        val reopened = WorkspaceLayoutStore(context)
        assertEquals(WorkspaceLayoutMode.MIRROR, reopened.read().mode)
        assertTrue(reopened.restorePrevious())
        assertEquals(WorkspaceLayoutMode.SEPARATE, WorkspaceLayoutStore(context).read().mode)
        assertEquals(before, NovaLauncherStore(context).read())
    }

    @Test fun primaryProjectionRequiresCurrentRequestAndCopyIsDurable() {
        val store = WorkspaceLayoutStore(context)
        val applicationId = context.packageName
        val projection = Profile(pages = listOf(listOf("arcade"))).publicProjection(applicationId)
        assertTrue(store.requestPrimary())
        val cancelled = checkNotNull(store.read().requestId)
        assertTrue(store.cancelPending())
        assertFalse(store.usePrimary(cancelled, projection, applicationId))
        assertTrue(store.requestPrimary())
        val current = checkNotNull(store.read().requestId)
        assertTrue(store.usePrimary(current, projection, applicationId))
        assertTrue(store.requestCopyToBoth())
        val copy = checkNotNull(store.read().requestId)
        assertTrue(store.copyToBoth(copy))
        val reopened = WorkspaceLayoutStore(context)
        assertEquals(WorkspaceLayoutMode.SEPARATE, reopened.read().mode)
        assertEquals(copy, reopened.primaryCopy()?.id)
        assertNotNull(reopened.primaryCopy()?.layout?.cover?.pages?.single()?.single())
        assertFalse(reopened.acknowledgePrimaryCopy(cancelled))
        assertTrue(reopened.acknowledgePrimaryCopy(copy))
        assertEquals(null, reopened.primaryCopy())
        assertTrue(reopened.hasEarlierLauncherLayout())
        assertTrue(reopened.restorePrevious())
        assertEquals(NovaLauncherState(), NovaLauncherStore(context).read())
    }
}
