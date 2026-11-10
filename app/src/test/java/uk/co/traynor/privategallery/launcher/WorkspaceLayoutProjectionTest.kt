package uk.co.traynor.privategallery.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import uk.co.traynor.privategallery.home2.AppFolder
import uk.co.traynor.privategallery.home2.Profile
import uk.co.traynor.privategallery.home2.StoreEntry
import uk.co.traynor.privategallery.home2.Wallpaper

class WorkspaceLayoutProjectionTest {
    private val applicationId = "uk.co.traynor.privategallery"
    private val store = LauncherBuiltIn.STORE.app(applicationId).component
    private val arcade = LauncherBuiltIn.ARCADE.app(applicationId).component

    @Test fun primaryProjectionNeverIncludesPrivateRoutesNamesOrMedia() {
        val privateApp = "web:01234567-89ab-cdef-0123-456789abcdef"
        val profile = Profile(
            installed = listOf(StoreEntry(privateApp, "private title", "https://private.invalid", "", "", "")),
            pages = listOf(listOf("store", "evidence", "folder:mixed", "folder:public", privateApp)),
            folders = listOf(
                AppFolder("folder:mixed", "private folder name", listOf(privateApp, "store")),
                AppFolder("folder:public", "also a private name", listOf("store", "arcade")),
            ),
            dock = listOf("store", privateApp),
            wallpaper = Wallpaper(builtin = "Dusk", image = "private-image.jpg"),
        )
        val projection = profile.publicProjection(applicationId)
        assertEquals(listOf(listOf(LauncherItem.App(store), LauncherItem.Folder("Nova apps", listOf(store, arcade)))), projection.pages)
        assertEquals(listOf(LauncherItem.App(store)), projection.dock)
        assertEquals(null, projection.wallpaper)
        for (secret in listOf(privateApp, "private title", "private.invalid", "private name", "private-image")) {
            assertFalse(projection.toString().contains(secret))
        }
    }

    @Test fun projectionChoosesTheDisplayAndLeavesPrimaryOnlyDataUntouched() {
        val profile = Profile(
            pages = listOf(listOf("evidence", "store")),
            dock = listOf("camera", "store"),
            wallpaper = Wallpaper(builtin = "Dusk", image = "personal"),
        )
        val state = NovaLauncherState(
            cover = LauncherLayout(pages = listOf(listOf(LauncherItem.App(store))), dock = listOf(LauncherItem.App(store))),
            inner = LauncherLayout(pages = listOf(listOf(LauncherItem.App(arcade))), dock = listOf(LauncherItem.App(arcade))),
        )
        val cover = profile.withLauncherProjection(state, applicationId, wide = false)
        val inner = profile.withLauncherProjection(state, applicationId, wide = true, lastWidgetPage = 2)
        assertEquals(listOf("evidence", "store"), cover.pages.single())
        assertEquals(listOf("evidence", "arcade"), inner.pages.first())
        assertEquals(listOf("camera", "arcade"), inner.dock)
        assertEquals(3, inner.pages.size)
        assertSame(profile.wallpaper, inner.wallpaper)
        assertEquals(listOf("evidence", "store"), profile.pages.single())
    }

    @Test fun repeatedCopiesReplaceGeneratedFoldersAndKeepOwnerFolders() {
        val owner = AppFolder("folder:owner", "Owner tools", listOf("store", "arcade"))
        val original = Profile(pages = listOf(listOf(owner.id)), folders = listOf(owner))
        val publicFolder = LauncherItem.Folder("Shared tools", listOf(store, arcade))
        val state = NovaLauncherState(cover = LauncherLayout(pages = listOf(listOf(publicFolder))))
        val first = original.withLauncherProjection(state, applicationId, wide = false)
        val second = first.withLauncherProjection(state, applicationId, wide = false)
        assertEquals(first, second)
        assertEquals(2, second.folders.size)
        assertTrue(owner.id in second.pages.single())

        val generated = second.folders.single { it.id != owner.id }
        val customized = generated.copy(name = "My renamed tools")
        val edited = second.copy(folders = listOf(owner, customized))
        val afterEdit = edited.withLauncherProjection(state, applicationId, wide = false)
        assertTrue(customized in afterEdit.folders)
        assertTrue(customized.id in afterEdit.pages.single())

        val cleared = second.withLauncherProjection(NovaLauncherState(), applicationId, wide = false)
        assertEquals(listOf(owner), cleared.folders)
        assertEquals(listOf(owner.id), cleared.pages.single())
    }

    @Test fun primarySourceKeepsLauncherOnlyAppsAndProfileIdentities() {
        val android = LauncherItem.App("com.whatsapp/.Main#155")
        val smart = LauncherItem.SmartFolder(LauncherCategory.SUGGESTIONS)
        val widgets = listOf(LauncherWidget(5, "com.example/.Widget", false, 1))
        val before = NovaLauncherState(
            cover = LauncherLayout(pages = listOf(listOf(android, LauncherItem.App(store)), listOf(smart))),
            widgets = widgets,
        )
        val after = before.mergePrimaryProjection(Profile(pages = listOf(listOf("arcade"))).publicProjection(applicationId), applicationId)
        assertEquals(listOf(android, LauncherItem.App(arcade)), after.cover.pages.first())
        assertEquals(listOf(smart), after.cover.pages[1])
        assertEquals(widgets, after.widgets)
        assertTrue(after.separateDisplays)
    }
}
