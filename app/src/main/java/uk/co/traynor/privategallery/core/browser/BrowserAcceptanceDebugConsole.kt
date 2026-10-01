package uk.co.traynor.privategallery.core.browser

/**
 * Bounded, local-only WebView acceptance trace. Callers must supply structural metadata only;
 * serialization applies a closed vocabulary even when a caller supplies untrusted strings.
 */
class BrowserAcceptanceDebugConsole(
    private val enabled: Boolean,
    private val maximumEvents: Int = 750,
    private val nowMillis: () -> Long = { android.os.SystemClock.elapsedRealtime() },
) {
    private var captureEnabled = enabled
    private data class Entry(val at: Long, val category: String, val details: Map<String, String>, val error: Boolean)
    private val entries = ArrayDeque<Entry>()
    private var traceStartedAt: Long = 0L
    private var resourceRequests = 0
    private var resourceErrors = 0
    private var httpErrors = 0
    private var jsWarnings = 0
    private var jsErrors = 0
    private var webViewProvider: String? = null
    private var webViewConfiguration: Map<String, String> = emptyMap()
    private var vpnState: String? = null
    private var mainPageState: String? = null

    fun startNavigation(details: Map<String, String>, preserveEvents: Boolean = false) {
        if (!captureEnabled) return
        if (!preserveEvents) entries.clear()
        if (!preserveEvents || traceStartedAt == 0L) traceStartedAt = nowMillis()
        resourceRequests = 0; resourceErrors = 0; httpErrors = 0; jsWarnings = 0; jsErrors = 0
        append("NAVIGATION_REQUEST", BrowserDiagnosticPrivacy.details("NAVIGATION_REQUEST", details), false)
    }

    fun record(category: String, details: Map<String, String> = emptyMap(), isError: Boolean = false) {
        if (!captureEnabled) return
        val safeCategory = BrowserDiagnosticPrivacy.category(category)
        val safeDetails = BrowserDiagnosticPrivacy.details(safeCategory, details)
        if (traceStartedAt == 0L) traceStartedAt = nowMillis()
        when {
            safeCategory.startsWith("RESOURCE_REQUEST") -> resourceRequests++
            safeCategory.contains("HTTP_ERROR") -> httpErrors++
            safeCategory.contains("RESOURCE_ERROR") || safeCategory.contains("NETWORK_ERROR") -> resourceErrors++
            safeCategory.startsWith("JS_ERROR") -> jsErrors++
            safeCategory.startsWith("JS_WARNING") -> jsWarnings++
        }
        when (safeCategory) {
            "WEBVIEW_PROVIDER" -> webViewProvider = listOfNotNull(safeDetails["package"], safeDetails["version"]).joinToString(" ")
            "WEBVIEW_CONFIGURATION" -> webViewConfiguration = safeDetails.toSortedMap()
            "VPN_STATE" -> vpnState = safeDetails["state"]
            "MAIN_PAGE_STARTED" -> mainPageState = "loading"
            "MAIN_PAGE_COMMIT_VISIBLE" -> mainPageState = "visible"
            "MAIN_PAGE_FINISHED" -> mainPageState = "finished"
            "MAIN_PAGE_ERROR", "MAIN_HTTP_ERROR" -> mainPageState = "error"
        }
        append(safeCategory, safeDetails, isError)
    }

    fun recordConsole(level: String?, message: String?, line: Int? = null) {
        if (!captureEnabled) return
        val normalised = level?.uppercase()?.takeIf { it in setOf("DEBUG", "LOG", "INFO", "WARNING", "ERROR") } ?: "OTHER"
        if (normalised == "WARNING") jsWarnings++
        if (normalised == "ERROR") jsErrors++
        val details = linkedMapOf("level" to normalised, "category" to BrowserDiagnosticsPolicy.consoleMessage(if (normalised == "INFO") "log" else normalised, message))
        // Page source line is intentionally not persisted.
        @Suppress("UNUSED_VARIABLE") val ignoredLine = line
        append(if (normalised == "WARNING") "CONSOLE_WARNING" else if (normalised == "ERROR") "CONSOLE_ERROR" else "JS_CONSOLE", details, normalised == "ERROR")
    }

    fun summary(extra: Map<String, String> = emptyMap()): List<String> = if (!captureEnabled) listOf("Acceptance diagnostics disabled") else buildList {
        webViewProvider?.let { add("WebView provider: ${it}") }
        webViewConfiguration.takeIf { it.isNotEmpty() }?.let { configuration ->
            add("WebView configuration: " + configuration.entries.joinToString(" ") { "${it.key}=${it.value}" })
        }
        vpnState?.let { add("VPN state: ${it}") }
        mainPageState?.let { add("Main page state: ${it}") }
        add("Resource requests: $resourceRequests")
        add("Resource errors: $resourceErrors")
        add("HTTP errors: $httpErrors")
        add("JS warnings: $jsWarnings")
        add("JS errors: $jsErrors")
        // Arbitrary summary labels/values are not diagnostic categories.
        @Suppress("UNUSED_VARIABLE") val ignoredExtra = extra
    }

    fun events(): List<String> = if (!captureEnabled) emptyList() else entries.map { entry ->
        val delta = (entry.at - traceStartedAt).coerceAtLeast(0)
        "+${delta.toString().padStart(4, '0')}ms ${entry.category}" + entry.details.entries.joinToString(separator = " ", prefix = if (entry.details.isEmpty()) "" else " ") { "${it.key}=${it.value}" }
    }

    fun report(extra: Map<String, String> = emptyMap()): String = buildString {
        appendLine("Private Gallery Browser acceptance trace")
        summary(extra).forEach(::appendLine)
        appendLine("Chronological events:")
        events().forEach(::appendLine)
    }

    fun clear() { entries.clear(); traceStartedAt = 0L; resourceRequests = 0; resourceErrors = 0; httpErrors = 0; jsWarnings = 0; jsErrors = 0; mainPageState = null }

    /** Acceptance builds may pause verbose probes without changing Browser production behaviour. */
    fun setCaptureEnabled(value: Boolean) {
        captureEnabled = enabled && value
        clear()
    }

    fun isCaptureEnabled(): Boolean = captureEnabled

    private fun append(category: String, details: Map<String, String>, isError: Boolean) {
        val clean = details
        // Preserve errors; coalesce only adjacent identical non-error successes.
        val last = entries.lastOrNull()
        if (!isError && last != null && !last.error && last.category == category && last.details == clean) return
        entries.addLast(Entry(nowMillis(), category, clean, isError))
        while (entries.size > maximumEvents) {
            val firstNonError = entries.indexOfFirst { !it.error }
            if (firstNonError >= 0) entries.removeAt(firstNonError) else entries.removeFirst()
        }
    }

}
