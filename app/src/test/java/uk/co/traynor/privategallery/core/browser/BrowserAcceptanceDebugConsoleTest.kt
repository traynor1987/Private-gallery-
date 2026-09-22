package uk.co.traynor.privategallery.core.browser

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserAcceptanceDebugConsoleTest {
    @Test fun `acceptance console keeps ordered bounded trace and preserves errors`() {
        var now = 1_000L
        val console = BrowserAcceptanceDebugConsole(enabled = true, maximumEvents = 4) { now }

        console.startNavigation(mapOf("scheme" to "https"))
        now += 12
        console.record("RESOURCE_REQUEST", mapOf("type" to "script"))
        now += 8
        console.record("JS_ERROR", mapOf("message" to "TypeError"), isError = true)
        now += 3
        console.record("RESOURCE_REQUEST", mapOf("type" to "image"))

        val report = console.report()
        assertTrue(report.contains("+0000ms NAVIGATION_REQUEST scheme=https"))
        assertTrue(report.contains("+0020ms JS_ERROR message=TypeError"))
        assertTrue(report.contains("JS errors: 1"))
    }

    @Test fun `acceptance console redacts console secrets and query strings`() {
        val console = BrowserAcceptanceDebugConsole(enabled = true) { 1L }

        console.recordConsole("ERROR", "https://example.test/a?token=supersecret Authorization: Bearer abcdefghijklmnopqrstuvwxyz0123456789")

        val report = console.report()
        assertTrue(report.contains("[redacted]"))
        assertFalse(report.contains("supersecret"))
        assertFalse(report.contains("abcdefghijklmnopqrstuvwxyz"))
    }

    @Test fun `acceptance console redacts complete URLs and embedded hostnames`() {
        val console = BrowserAcceptanceDebugConsole(enabled = true) { 1L }

        console.recordConsole(
            "ERROR",
            "request https://private.example.test/path?token=keep-secret failed; fallback cdn.private.example.test:8443/script.js",
        )

        val report = console.report()
        assertTrue(report.contains("[url]"))
        assertTrue(report.contains("[host]"))
        assertFalse(report.contains("private.example.test"))
        assertFalse(report.contains("cdn.private.example.test"))
        assertFalse(report.contains("keep-secret"))
    }

    @Test fun `acceptance console redacts socket addresses IP literals credentials and escaped URL forms`() {
        val console = BrowserAcceptanceDebugConsole(enabled = true) { 1L }

        console.recordConsole("ERROR", "ws://user:password@192.0.2.44:8080/socket?token=secret wss:\\/\\/private.example.test\\/api?key=secret [2001:db8::1] cookie=session=abcdef authorization=Basic abcdefghijklmnopqrstuvwxyz0123456789")

        val report = console.report()
        listOf("192.0.2.44", "password", "private.example.test", "2001:db8", "session=", "secret", "abcdefghijklmnopqrstuvwxyz").forEach { value -> assertFalse(report.contains(value)) }
        assertTrue(report.contains("[url]"))
    }

    @Test fun `disabled acceptance console never retains events`() {
        val console = BrowserAcceptanceDebugConsole(enabled = false) { 1L }
        console.startNavigation(mapOf("scheme" to "https"))
        console.recordConsole("ERROR", "private token=secret")

        assertTrue(console.report().contains("Acceptance diagnostics disabled"))
        assertFalse(console.report().contains("secret"))
    }

    @Test fun `acceptance events separate safe detail fields for readable copy all output`() {
        val console = BrowserAcceptanceDebugConsole(enabled = true) { 1L }

        console.record("MAIN_PAGE_STARTED", mapOf("scheme" to "https", "main_frame" to "true"))

        assertTrue(console.events().single().contains("scheme=https main_frame=true"))
    }

    @Test fun `environment summary survives a new navigation trace`() {
        val console = BrowserAcceptanceDebugConsole(enabled = true) { 1L }
        console.record("WEBVIEW_PROVIDER", mapOf("package" to "com.android.webview", "version" to "123"))
        console.record("WEBVIEW_CONFIGURATION", mapOf("javascript" to "true", "third_party_cookies" to "false"))

        console.startNavigation(mapOf("scheme" to "https"))

        val report = console.report()
        assertTrue(report.contains("WebView provider: com.android.webview 123"))
        assertTrue(report.contains("WebView configuration: javascript=true third_party_cookies=false"))
    }
}
