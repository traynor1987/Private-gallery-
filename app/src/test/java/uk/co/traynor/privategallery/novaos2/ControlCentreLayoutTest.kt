package uk.co.traynor.privategallery.novaos2

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ControlCentreLayoutTest {
    @Test fun persistsDistinctDisplayOrderVisibilityAndPins() {
        val original = ControlCentreLayout()
        val a = "example.maps/.Main"
        val b = "example.maps/.Main#18"
        val next = original.changePlacement(false) {
            it.move(ControlTile.VOLUME, -1).toggle(ControlTile.INTERNET).toggleApp(a)
        }.changePlacement(true) { it.toggleApp(b) }.copy(mediaEnabled = true)
        val loaded = ControlCentreLayout.decode(next.encode())
        assertEquals(next, loaded)
        assertEquals(ControlTile.VOLUME, loaded.cover.order.first())
        assertEquals(ControlTile.INTERNET, loaded.inner.order.first())
        assertEquals(listOf(a), loaded.cover.pinnedApps)
        assertEquals(listOf(b), loaded.inner.pinnedApps)
        assertFalse(ControlTile.INTERNET in loaded.cover.visible(ControlTile.entries.toSet()))
        assertEquals(emptyList<String>(), original.cover.pinnedApps)
        assertTrue(loaded.mediaEnabled)
        assertFalse(ControlCentreLayout.decode(null).mediaEnabled)
    }

    @Test fun orderingSkipsModulesOutsideTheCurrentReviewBranch() {
        val noWorking = ControlTile.entries.toSet() - ControlTile.WORKING
        val changed = ControlPlacement().move(ControlTile.THEME, -1, noWorking)
        assertEquals(ControlTile.THEME, changed.visible(noWorking)[4])
        assertTrue(ControlTile.WORKING in changed.order)
        assertEquals(changed, changed.move(ControlTile.INTERNET, -1, noWorking))
    }

    @Test fun boundsPinsAndRejectsInvalidOrAmbiguousPersistedRows() {
        val placement = (0..4).fold(ControlPlacement()) { p, index -> p.toggleApp("example.app/.Entry$index") }
        assertEquals(ControlPlacement.PIN_LIMIT, placement.pinnedApps.size)
        assertEquals(placement, placement.toggleApp("example.maps/.Main|file"))
        assertEquals(placement.pinnedApps.drop(1), placement.toggleApp(placement.pinnedApps.first()).pinnedApps)
        assertThrows(IllegalArgumentException::class.java) { ControlCentreLayout.decode("unsafe") }
        assertThrows(IllegalArgumentException::class.java) {
            ControlCentreLayout.decode("control-centre-v1\nfalse\nINTERNET,INTERNET||\n||")
        }
    }

    @Test fun unsupportedBatteryMeasurementsStayUnknown() {
        assertNull(controlBatteryPercent(-1, 100))
        assertNull(controlBatteryPercent(7, 0))
        assertNull(controlBatteryPercent(100, 2))
        assertEquals(67, controlBatteryPercent(2, 3))
        assertEquals(100, controlBatteryPercent(Int.MAX_VALUE, Int.MAX_VALUE))
    }
}
