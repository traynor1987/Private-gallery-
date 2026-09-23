package uk.co.traynor.privategallery.core.browser.v2

import org.junit.Assert.*
import org.junit.Test

class BrowserRuntimeSnapshotTest {
    @Test fun `page-controlled snapshot admits only bounded structural values`() {
        val result = BrowserRuntimeSnapshot.parse("""{"dom_nodes":12,"visible_elements":3,"ready":"complete","visibility":"visible","focus":true,"scripts":999999999,"canvas":-1,"frames":"secret","runtime_errors":2,"url":"https://private.invalid/token","text":"private content","local_storage":true}""")
        assertEquals("12", result["dom_nodes"])
        assertEquals("true", result["focus"])
        assertEquals("complete", result["ready"])
        assertFalse(result.containsKey("url"))
        assertFalse(result.containsKey("text"))
        assertFalse(result.containsKey("canvas"))
        assertFalse(result.containsKey("frames"))
        assertEquals("1000000", result["scripts"])
        assertFalse(result.toString().contains("secret"))
    }

    @Test fun `malformed enums nested objects and fractions cannot become diagnostics`() {
        assertTrue(BrowserRuntimeSnapshot.parse("not json").isEmpty())
        assertTrue(BrowserRuntimeSnapshot.parse("""{"ready":"private text","visibility":"https://private.invalid","focus":"yes","dom_nodes":{},"scripts":1.5}""").isEmpty())
    }
}
