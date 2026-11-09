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
}
