package uk.co.traynor.privategallery.novaos2.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class DesktopLayoutTest {
    @Test fun resizingAndReplacingOnePanePreservesDistinctPanes() {
        val initial = DesktopSettings().current
        assertEquals(DESKTOP_MIN_RATIO, initial.resize(11).splitPercent)
        assertEquals(DESKTOP_MAX_RATIO, initial.resize(89).splitPercent)
        val swapped = initial.choosePanel(true, DesktopPanel.PINNED)
        assertEquals(DesktopPanel.PINNED, swapped.left)
        assertEquals(DesktopPanel.NOVA_APPS, swapped.right)
        assertEquals(DesktopPanel.CALCULATOR, swapped.choosePanel(false, DesktopPanel.CALCULATOR).right)
    }

    @Test fun presetsPersistIndependentlyAndKeepPinnedOrder() {
        val one = "com.example.notes/.Main"
        val two = "com.example.dev/.Main"
        val initial = DesktopSettings()
        val edited = initial.update(initial.current.pin(one).pin(two).reorder(two, -1).resize(60))
        val gaming = edited.copy(preset = DesktopPreset.GAMING)
        assertEquals(emptyList<String>(), gaming.current.pins)
        val restored = DesktopSettings.decode(gaming.encode()).copy(preset = DesktopPreset.PRODUCTIVITY)
        assertEquals(listOf(two, one), restored.current.pins)
        assertEquals(60, restored.current.splitPercent)
    }

    @Test fun pinLimitsAndComponentIdentityRejectMalformedRecords() {
        val default = DesktopSettings().current
        val full = (1..DESKTOP_MAX_PINS).fold(default) { layout, value -> layout.pin("com.example$value/.Main") }
        assertEquals(full, full.pin("com.example.other/.Main"))
        assertEquals(full, full.pin(full.pins.first()).reorder(full.pins.first(), -1))
        assertThrows(IllegalArgumentException::class.java) { default.pin("com.example/.Main#4") }
        assertThrows(IllegalArgumentException::class.java) {
            DesktopLayout(DesktopPanel.FILES, DesktopPanel.FILES)
        }
    }
}
