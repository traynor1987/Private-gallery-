package uk.co.traynor.privategallery.launcher

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class LauncherThemesStudioTest {
    @Test fun presetsChangeOnlyAppearanceAndKeepBothLayouts() {
        val homeApp = LauncherItem.App("example.notes/.Main")
        val previous = NovaLauncherState(
            configured = true,
            cover = LauncherLayout(pages = listOf(listOf(homeApp))),
            inner = LauncherLayout(pages = listOf(listOf(LauncherItem.Workspace, LauncherItem.Folder(
                "Work", listOf(homeApp.component, "example.calendar/.Main"),
            ))), dock = listOf(homeApp)),
            widgets = listOf(LauncherWidget(25, "example.widgets/.Clock", true)),
            launchCounts = mapOf(homeApp.component to 4),
            hideBlocked = true,
        )
        val presets = LauncherLook.entries.map { it.preset(previous.appearance) }
        assertEquals(3, presets.toSet().size)
        for (preset in presets) {
            val updated = previous.copy(appearance = preset)
            assertNotEquals(previous.appearance, updated.appearance)
            assertEquals(previous, updated.copy(appearance = previous.appearance))
            assertEquals(updated, LauncherLayoutCodec.decode(LauncherLayoutCodec.encode(updated, false), false))
        }
    }

    @Test fun portableAppearanceRoundTripsWithoutLauncherContents() {
        val appearance = LauncherLook.FLOATING.preset(LauncherAppearance(theme = LauncherTheme.LIGHT)).copy(
            studio = LauncherStudioStyle(
                look = LauncherLook.FLOATING, typeface = LauncherTypeface.EDITORIAL, widgetFrame = LauncherWidgetFrame.GLASS,
                motion = LauncherMotion.SMOOTH, innerGrid = LauncherGrid.COMPACT, customAccent = 0xAA5500,
                schedule = LauncherColourSchedule(6, 22), folder = LauncherFolderAppearance(LauncherFolderMode.LIST),
            ),
        )
        val bytes = LauncherThemePresetCodec.encode(appearance)
        assertEquals(appearance, LauncherThemePresetCodec.decode(bytes))
        val root = JSONObject(bytes.decodeToString())
        assertEquals(setOf("type", "schema", "appearance"), root.keys().asSequence().toSet())
        assertFalse(bytes.decodeToString().contains("launchCounts"))
    }

    @Test fun malformedThemeCannotMutateOrReplaceLauncherData() {
        val good = LauncherThemePresetCodec.encode(LauncherAppearance(studio = LauncherStudioStyle(customAccent = 34)))
        val extra = JSONObject(good.decodeToString()).put("cover", JSONObject())
        val badColor = JSONObject(good.decodeToString()).apply {
            getJSONObject("appearance").getJSONObject("studio").put("customAccent", -1)
        }
        val deep = "{\"data\":${"[".repeat(18)}0${"]".repeat(18)}}".encodeToByteArray()
        val invalid = listOf(extra.toString().encodeToByteArray(), badColor.toString().encodeToByteArray(),
            deep, good + 0.toByte(), ByteArray(LauncherThemePresetCodec.MAX_BYTES + 1), byteArrayOf(0xC3.toByte()),
            LauncherLayoutCodec.encode(NovaLauncherState(), true).encodeToByteArray())
        for (bytes in invalid) assertThrows(Exception::class.java) { LauncherThemePresetCodec.decode(bytes) }
    }

    @Test fun optionalStyleCanRecoverLocallyWithoutDiscardingLegacyLayout() {
        val original = NovaLauncherState(configured = true, cover = LauncherLayout(pages = listOf(listOf(
            LauncherItem.App("example.app/.Main"),
        ))))
        val raw = LauncherLayoutCodec.encode(original, false)
        assertEquals(original, LauncherLayoutCodec.decode(raw, false))
        val changed = JSONObject(raw).apply { getJSONObject("appearance").put("studio", JSONObject().put("unexpected", true)) }
        assertEquals(original, LauncherLayoutCodec.decode(changed.toString(), false))
    }

    @Test fun localHoursAndIndependentFoldGridUseExistingFallbacks() {
        val lightDay = LauncherColourSchedule(7, 20)
        assertEquals(LauncherTheme.DARK, lightDay.themeAt(6))
        assertEquals(LauncherTheme.LIGHT, lightDay.themeAt(7))
        assertEquals(LauncherTheme.DARK, lightDay.themeAt(20))
        val lightNight = LauncherColourSchedule(20, 7)
        assertEquals(LauncherTheme.LIGHT, lightNight.themeAt(0))
        assertEquals(LauncherTheme.DARK, lightNight.themeAt(11))
        assertThrows(IllegalArgumentException::class.java) { LauncherColourSchedule(7, 7) }
        val cover = LauncherAppearance(grid = LauncherGrid.STANDARD)
        val split = cover.copy(studio = LauncherStudioStyle(innerGrid = LauncherGrid.COMPACT))
        assertEquals(4, split.gridFor(false).columns(false))
        assertEquals(8, split.gridFor(true).columns(true))
        assertEquals(6, cover.gridFor(true).columns(true))
    }
}
