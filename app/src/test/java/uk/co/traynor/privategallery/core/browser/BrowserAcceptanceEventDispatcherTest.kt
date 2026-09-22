package uk.co.traynor.privategallery.core.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserAcceptanceEventDispatcherTest {
    @Test fun `background WebView event is posted before it reaches Compose state`() {
        val queued = mutableListOf<() -> Unit>()
        val delivered = mutableListOf<String>()
        val dispatcher = BrowserAcceptanceEventDispatcher(
            isMainThread = { false },
            postToMain = { queued += it },
        )

        dispatcher.dispatch { delivered += "RESOURCE_REQUEST" }

        assertTrue(delivered.isEmpty())
        assertEquals(1, queued.size)
        queued.single().invoke()
        assertEquals(listOf("RESOURCE_REQUEST"), delivered)
    }

    @Test fun `main thread event is delivered immediately`() {
        val delivered = mutableListOf<String>()
        val dispatcher = BrowserAcceptanceEventDispatcher(
            isMainThread = { true },
            postToMain = { error("must not post") },
        )

        dispatcher.dispatch { delivered += "MAIN_PAGE_FINISHED" }

        assertEquals(listOf("MAIN_PAGE_FINISHED"), delivered)
    }
}
