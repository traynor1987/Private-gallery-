package uk.co.traynor.privategallery.launcher

import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherLayoutStore70Test {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val browser = "com.example.browser/.Main"
    private val other = "com.example.other/.Main"
    private val widget = LauncherWidget(17, "com.example.clock/.Widget", wide = true)

    @Test fun layoutAndAppearanceSurviveAStoreReopen() {
        val store = NovaLauncherStore(context)
        val before = store.read()
        try {
            val expected = NovaLauncherState(
                configured = true,
                cover = LauncherLayout(listOf(listOf(LauncherItem.App(browser))), listOf(LauncherItem.Workspace), setOf(browser)),
                inner = LauncherLayout(listOf(listOf(LauncherItem.Folder("Tools", listOf(other)))), listOf(LauncherItem.Workspace), setOf(other)),
                appearance = LauncherAppearance(wallpaper = LauncherWallpaper.MIDNIGHT, theme = LauncherTheme.DARK),
                drawer = LauncherDrawerOptions(orientation = LauncherDrawerOrientation.HORIZONTAL),
                workspaceGesture = LauncherGesture.DOUBLE_TAP,
                hideBlocked = true,
                widgets = listOf(widget),
                launchCounts = mapOf(browser to 3),
            )
            assertTrue(store.save(expected))
            assertEquals(expected, NovaLauncherStore(context).read())
        } finally {
            assertTrue(store.save(before))
        }
    }

    @Test fun iconStyleMigratesAndSurvivesStoreReopenWithoutChangingLayouts() {
        val store = NovaLauncherStore(context)
        val before = store.read()
        val prefs = context.getSharedPreferences("nova_android_home_v1", android.content.Context.MODE_PRIVATE)
        val cover = LauncherLayout(pages = listOf(listOf(LauncherItem.App(browser))))
        val inner = LauncherLayout(dock = listOf(LauncherItem.App(other)))
        try {
            assertEquals(LauncherIconStyle.FULL_ICONS, LauncherAppearance().iconStyle)
            val owner = NovaLauncherState(
                configured = true, cover = cover, inner = inner,
                appearance = LauncherAppearance(labels = false, iconSize = LauncherIconSize.LARGE),
                dockOptions = LauncherDockOptions(locked = true, labels = true),
            )
            assertTrue(store.save(owner))
            val legacy = JSONObject(checkNotNull(prefs.getString("state", null)))
            legacy.getJSONObject("appearance").remove("iconStyle")
            assertTrue(prefs.edit().putString("state", legacy.toString()).commit())
            assertEquals(owner, NovaLauncherStore(context).read())

            for (style in LauncherIconStyle.entries) {
                for (size in LauncherIconSize.entries) {
                    val expected = owner.copy(appearance = owner.appearance.copy(iconStyle = style, iconSize = size))
                    assertTrue(store.save(expected))
                    assertEquals(expected, NovaLauncherStore(context).read())
                }
            }
        } finally {
            assertTrue(store.save(before))
        }
    }

    @Test fun portableBackupPreservesLocalBindingsAndRejectsMalformedLayouts() {
        val store = NovaLauncherStore(context)
        val before = store.read()
        try {
            val template = launcherInitialState(
                LauncherStartingLayout.TEMPLATE, LauncherTemplate.PRODUCTIVITY, listOf(browser, other),
            ).copy(configured = true, launchCounts = mapOf(other to 8), widgets = listOf(widget))
            assertTrue(store.save(template))
            val bytes = store.exportLayout()
            val payload = JSONObject(String(bytes, Charsets.UTF_8))
            assertFalse(payload.has("widgets"))
            assertFalse(payload.has("launchCounts"))

            val current = NovaLauncherState(configured = true, widgets = listOf(widget.copy(id = 22)), launchCounts = mapOf(browser to 1))
            assertTrue(store.save(current))
            assertTrue(store.restoreLayout(bytes))
            val restored = NovaLauncherStore(context).read()
            assertEquals(template.cover, restored.cover)
            assertEquals(template.inner, restored.inner)
            assertEquals(current.widgets, restored.widgets)
            assertEquals(current.launchCounts, restored.launchCounts)

            val pages = payload.getJSONObject("cover").getJSONArray("pages")
            pages.getJSONArray(0).put(JSONObject().put("type", "app").put("component", "intent://unsafe"))
            assertFalse(store.restoreLayout(payload.toString().toByteArray()))
            assertEquals(restored, store.read())
            assertFalse(store.restoreLayout(("{\"unknown\":" + "[".repeat(100) + "0" + "]".repeat(100) + "}").encodeToByteArray()))
            assertFalse(store.restoreLayout(("# comment\n" + String(bytes, Charsets.UTF_8)).encodeToByteArray()))
            assertEquals(restored, store.read())
        } finally {
            assertTrue(store.save(before))
        }
    }

    @Test fun cleanupUndoAndWidgetPlacementSurviveRestartWithLegacyWidgetCompatibility() {
        val store = NovaLauncherStore(context)
        val before = store.read()
        try {
            val original = NovaLauncherState(
                configured = true,
                inner = LauncherLayout(pages = listOf(
                    listOf(LauncherItem.App(browser)), listOf(LauncherItem.App(other)),
                )),
                widgets = listOf(widget.copy(page = 1)),
            )
            val cleaned = original.cleanUpShortcuts(true, setOf(LauncherShortcutPosition(0, 0)))
            assertTrue(store.save(cleaned))
            val reopened = NovaLauncherStore(context).read()
            assertEquals(cleaned, reopened)
            assertEquals(original, reopened.undoShortcutCleanup(true))
            val portable = JSONObject(String(store.exportLayout(), Charsets.UTF_8))
            assertFalse(portable.has("innerCleanupUndo"))

            val prefs = context.getSharedPreferences("nova_android_home_v1", android.content.Context.MODE_PRIVATE)
            val legacy = JSONObject(checkNotNull(prefs.getString("state", null)))
            legacy.remove("innerCleanupUndo")
            legacy.getJSONArray("widgets").getJSONObject(0).remove("page")
            assertTrue(prefs.edit().putString("state", legacy.toString()).commit())
            val legacyRead = NovaLauncherStore(context).read()
            assertEquals(cleaned.inner, legacyRead.inner)
            assertEquals(listOf(widget), legacyRead.widgets)
        } finally {
            assertTrue(store.save(before))
        }
    }

    @Test fun legacyStockDuplicateIsRemovedWithoutRecreatingOwnerDockOrWidgets() {
        val store = NovaLauncherStore(context)
        val before = store.read()
        val prefs = context.getSharedPreferences("nova_android_home_v1", android.content.Context.MODE_PRIVATE)
        val stock = LauncherLayout().withSmartFolderTemplate(onlyIfEmpty = true).pages[1]
        val cover = LauncherLayout(
            pages = listOf(listOf(LauncherItem.App(browser)), stock, stock, listOf(LauncherItem.App(other))),
            dock = listOf(LauncherItem.Workspace, LauncherItem.App(browser)),
        )
        val coverWidget = widget.copy(wide = false, page = 3)
        val original = NovaLauncherState(configured = true, cover = cover, widgets = listOf(coverWidget))
        try {
            assertTrue(store.save(original))
            assertEquals(original, store.read())
            val legacy = JSONObject(checkNotNull(prefs.getString("state", null)))
            legacy.remove("dockOptions")
            legacy.remove("newAppPlacement")
            assertTrue(prefs.edit().putString("state", legacy.toString()).commit())

            val migrated = NovaLauncherStore(context).read()
            assertEquals(original.cover.pages.filterIndexed { index, _ -> index != 2 }, migrated.cover.pages)
            assertEquals(original.cover.dock, migrated.cover.dock)
            assertEquals(listOf(coverWidget.copy(page = 2)), migrated.widgets)
            assertTrue(migrated.dockOptions.locked)
            assertFalse(migrated.dockOptions.showInLibrary)
            assertFalse(migrated.dockOptions.labels)

            val configured = migrated.copy(
                dockOptions = LauncherDockOptions(locked = false, showInLibrary = false, labels = true),
                appearance = migrated.appearance.copy(dock = LauncherDock.TRANSPARENT),
                newAppPlacement = LauncherNewAppPlacement.HOME,
            )
            assertTrue(store.save(configured))
            assertEquals(configured, NovaLauncherStore(context).read())
        } finally {
            assertTrue(store.save(before))
        }
    }

    @Test fun newlyInstalledAppsArePlacedAfterOptInAcrossStoreReopens() {
        val store = NovaLauncherStore(context)
        val before = store.read()
        try {
            assertTrue(store.save(NovaLauncherState(configured = true)))
            val start = System.currentTimeMillis()
            val browserApp = LauncherApp(browser, "com.example.browser", "Browser", firstInstallTime = start - 100)
            assertEquals(null, store.observeInstalledApps(listOf(browserApp), start))
            val enabled = store.read().copy(newAppPlacement = LauncherNewAppPlacement.HOME)
            assertTrue(store.save(enabled))

            val reopened = NovaLauncherStore(context)
            val scan = System.currentTimeMillis()
            val installed = LauncherApp(other, "com.example.other", "New app", firstInstallTime = scan + 1)
            val placed = checkNotNull(reopened.observeInstalledApps(listOf(browserApp, installed), scan + 2))
            assertEquals(listOf(LauncherItem.App(other)), placed.cover.pages.single())
            assertEquals(listOf(LauncherItem.App(other)), placed.inner.pages.single())
            assertEquals(placed, store.read())
            assertEquals(null, reopened.observeInstalledApps(listOf(browserApp, installed), scan + 3))
        } finally {
            assertTrue(store.save(before))
        }
    }

    @Test fun categoryOverridesRetainAppProfileIdentityAcrossStoreReopens() {
        val main = "com.whatsapp/.Main"
        val friends = "$main#99"
        val settings = LauncherIdentitySettings(context)
        val before = settings.categoryOverrides()
        try {
            assertTrue(settings.saveCategoryOverride(main, LauncherCategory.SOCIAL))
            assertTrue(settings.saveCategoryOverride(friends, LauncherCategory.PRODUCTIVITY))
            assertFalse(settings.saveCategoryOverride(friends, LauncherCategory.SUGGESTIONS))
            val reopened = LauncherIdentitySettings(context)
            assertEquals(LauncherCategory.SOCIAL, reopened.categoryOverrides()[main])
            assertEquals(LauncherCategory.PRODUCTIVITY, reopened.categoryOverrides()[friends])
            assertTrue(reopened.saveCategoryOverride(friends, null))
            assertEquals(null, settings.categoryOverrides()[friends])
            assertEquals(LauncherCategory.SOCIAL, settings.categoryOverrides()[main])
        } finally {
            assertTrue(settings.saveCategoryOverride(main, before[main]))
            assertTrue(settings.saveCategoryOverride(friends, before[friends]))
        }
    }
}
