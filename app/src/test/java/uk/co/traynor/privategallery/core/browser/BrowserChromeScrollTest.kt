package uk.co.traynor.privategallery.core.browser

import org.junit.Assert.*
import org.junit.Test

class BrowserChromeScrollTest {
    @Test fun deliberateDownwardScrollHidesAndUpwardScrollReveals() {
        val chrome = BrowserChromeScroll(48)
        assertTrue(chrome.onScroll(30, false))
        assertFalse(chrome.onScroll(20, false))
        assertFalse(chrome.onScroll(-10, false))
        assertTrue(chrome.onScroll(-40, false))
    }
    @Test fun directionChangeDiscardsPreviousDistanceAndTopAlwaysReveals() {
        val chrome = BrowserChromeScroll(48)
        chrome.onScroll(40, false)
        assertTrue(chrome.onScroll(-5, false))
        assertTrue(chrome.onScroll(20, false))
        assertFalse(chrome.onScroll(30, false))
        assertTrue(chrome.onScroll(0, true))
    }
    @Test fun pinnedControlsAndNewPageResetAccumulation() {
        val chrome = BrowserChromeScroll(48)
        chrome.onScroll(80, false)
        chrome.reveal()
        assertTrue(chrome.onScroll(20, false))
    }
}
