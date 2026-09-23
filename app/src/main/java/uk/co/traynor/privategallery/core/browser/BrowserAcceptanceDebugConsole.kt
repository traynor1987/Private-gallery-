package uk.co.traynor.privategallery.core.browser

/**
 * Bounded, local-only WebView acceptance trace. Callers must supply structural metadata only;
 * this class never accepts or stores URLs, headers, cookies, DOM data, or storage contents.
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
        append("NAVIGATION_REQUEST", details, false)
    }

    fun record(category: String, details: Map<String, String> = emptyMap(), isError: Boolean = false) {
        if (!captureEnabled) return
        if (traceStartedAt == 0L) traceStartedAt = nowMillis()
        when {
            category.startsWith("RESOURCE_REQUEST") -> resourceRequests++
            category.contains("HTTP_ERROR") -> httpErrors++
            category.contains("RESOURCE_ERROR") || category.contains("NETWORK_ERROR") -> resourceErrors++
            category.startsWith("JS_ERROR") -> jsErrors++
            category.startsWith("JS_WARNING") -> jsWarnings++
        }
        when (category) {
            "WEBVIEW_PROVIDER" -> webViewProvider = listOfNotNull(details["package"], details["version"]).joinToString(" ")
            "WEBVIEW_CONFIGURATION" -> webViewConfiguration = details.toSortedMap()
            "VPN_STATE" -> vpnState = details["state"]
            "MAIN_PAGE_STARTED" -> mainPageState = "loading"
            "MAIN_PAGE_COMMIT_VISIBLE" -> mainPageState = "visible"
            "MAIN_PAGE_FINISHED" -> mainPageState = "finished"
            "MAIN_PAGE_ERROR", "MAIN_HTTP_ERROR" -> mainPageState = "error"
        }
        append(category, details, isError)
    }

    fun recordConsole(level: String?, message: String?, line: Int? = null) {
        if (!captureEnabled) return
        val normalised = level?.uppercase()?.takeIf { it in setOf("DEBUG", "LOG", "INFO", "WARNING", "ERROR") } ?: "OTHER"
        if (normalised == "WARNING") jsWarnings++
        if (normalised == "ERROR") jsErrors++
        val details = linkedMapOf("level" to normalised, "message" to sanitiseConsole(message))
        line?.takeIf { it >= 0 }?.let { details["line"] = it.toString() }
        append(if (normalised == "WARNING") "CONSOLE_WARNING" else if (normalised == "ERROR") "CONSOLE_ERROR" else "JS_CONSOLE", details, normalised == "ERROR")
    }

    fun summary(extra: Map<String, String> = emptyMap()): List<String> = if (!captureEnabled) listOf("Acceptance diagnostics disabled") else buildList {
        webViewProvider?.let { add("WebView provider: ${safeValue(it)}") }
        webViewConfiguration.takeIf { it.isNotEmpty() }?.let { configuration ->
            add("WebView configuration: " + configuration.entries.joinToString(" ") { "${it.key}=${safeValue(it.value)}" })
        }
        vpnState?.let { add("VPN state: ${safeValue(it)}") }
        mainPageState?.let { add("Main page state: ${safeValue(it)}") }
        add("Resource requests: $resourceRequests")
        add("Resource errors: $resourceErrors")
        add("HTTP errors: $httpErrors")
        add("JS warnings: $jsWarnings")
        add("JS errors: $jsErrors")
        extra.toSortedMap().forEach { (key, value) -> add("$key: ${safeValue(value)}") }
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
        val clean = details.mapValues { (_, value) -> safeValue(value) }
        // Preserve errors; coalesce only adjacent identical non-error successes.
        val last = entries.lastOrNull()
        if (!isError && last != null && !last.error && last.category == category && last.details == clean) return
        entries.addLast(Entry(nowMillis(), category, clean, isError))
        while (entries.size > maximumEvents) {
            val firstNonError = entries.indexOfFirst { !it.error }
            if (firstNonError >= 0) entries.removeAt(firstNonError) else entries.removeFirst()
        }
    }

    private fun sanitiseConsole(raw: String?): String {
        var value = raw.orEmpty().take(280)
        // A console message can include a failed-request address. Acceptance traces must not
        // retain either its full address or a hostname embedded in ordinary prose.
        value = value.replace(Regex("(?i)(?:https?|wss?)://[^\\s\\]\\[(){}<>\\\"']+"), "[url]")
        value = value.replace(Regex("(?i)(?:https?|wss?):\\\\/\\\\/[^\\s\\]\\[(){}<>\\\"']+"), "[url]")
        value = value.replace(Regex("(?i)\\b(?:https?|wss?)://[^@\\s]+@[^\\s\\]\\[(){}<>\\\"']+"), "[url]")
        value = value.replace(
            Regex("(?i)\\b(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)+(?:[a-z]{2,63})(?::\\d{1,5})?(?:/[^\\s\\]\\[(){}<>\\\"']*)?"),
            "[host]",
        )
        value = value.replace(Regex("([?&][A-Za-z0-9_.-]+)=([^&#\\s]+)"), "$1=[redacted]")
        value = value.replace(Regex("(?i)(bearer|authorization|cookie|set-cookie)\\s*[:=]\\s*[^\\s,;]+"), "$1=[redacted]")
        value = value.replace(Regex("(?i)\\b(?:\\d{1,3}\\.){3}\\d{1,3}(?::\\d{1,5})?\\b"), "[ip]")
        value = value.replace(Regex("(?i)\\[[0-9a-f:]{2,}\\]"), "[ip]")
        value = value.replace(Regex("[A-Za-z0-9_\\-]{24,}"), "[redacted]")
        return safeValue(value)
    }

    private fun safeValue(value: String): String = value.replace(Regex("[\\r\\n\\t]"), " ").take(300)
}
