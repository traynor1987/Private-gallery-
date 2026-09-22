package uk.co.traynor.privategallery.core.browser.v2

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** Privacy-safe process-local context retained for the next acceptance crash report. */
internal data class BrowserV2CrashContext(
    val route: String = "UNKNOWN",
    val stateCategory: String = "UNKNOWN",
    val activeTabCount: Int = 0,
    val selectedTabExists: Boolean = false,
    val webViewAttached: Boolean = false,
    val webViewAttachedToWindow: Boolean = false,
    val webViewParentCategory: String = "none",
    val attachCount: Int = 0,
    val detachCount: Int = 0,
    val reparentCount: Int = 0,
    val androidViewUpdateCount: Int = 0,
    val lastStructuralEvent: String = "none",
    val routeBounds: String = "unmeasured",
    val rootBounds: String = "unmeasured",
    val chromeBounds: String = "unmeasured",
    val contentHostBounds: String = "unmeasured",
    val androidViewHostBounds: String = "unmeasured",
    val webViewBounds: String = "unmeasured",
)

/** No URLs, tab identifiers, entered text, page contents or View identifiers are retained. */
internal object BrowserV2CrashContextStore {
    private val current = AtomicReference(BrowserV2CrashContext())

    fun snapshot(): BrowserV2CrashContext = current.get()

    fun record(
        event: String,
        activeTabCount: Int,
        selectedTabExists: Boolean,
        webViewAttached: Boolean,
        webViewAttachedToWindow: Boolean,
        webViewParentCategory: String,
        androidViewUpdateCount: Int,
        numericDetails: Map<String, String> = emptyMap(),
    ) {
        current.updateAndGet { previous ->
            val parent = webViewParentCategory.take(80).ifBlank { "none" }
            val oldParent = previous.webViewParentCategory
            previous.copy(
                route = if (event == "BROWSER_ROUTE_ENTERED") "BROWSER" else previous.route,
                stateCategory = event.take(80),
                activeTabCount = activeTabCount.coerceIn(0, 32),
                selectedTabExists = selectedTabExists,
                webViewAttached = webViewAttached,
                webViewAttachedToWindow = webViewAttachedToWindow,
                webViewParentCategory = parent,
                attachCount = previous.attachCount + if (!previous.webViewAttached && webViewAttached) 1 else 0,
                detachCount = previous.detachCount + if (previous.webViewAttached && !webViewAttached) 1 else 0,
                reparentCount = previous.reparentCount + if (
                    previous.webViewAttached && webViewAttached && oldParent != "none" && oldParent != parent
                ) 1 else 0,
                androidViewUpdateCount = androidViewUpdateCount.coerceIn(0, 1_000_000),
                lastStructuralEvent = event.take(80),
                routeBounds = if (event == "BROWSER_ROUTE_MEASURED") bounds(numericDetails, "x", "y", "width", "height") else previous.routeBounds,
                rootBounds = if (event == "BROWSER_ROOT_MEASURED") bounds(numericDetails, "x", "y", "width", "height") else previous.rootBounds,
                chromeBounds = if (event == "BROWSER_CHROME_MEASURED") bounds(numericDetails, "x", "y", "width", "height") else previous.chromeBounds,
                contentHostBounds = if (event == "CONTENT_HOST_MEASURED") bounds(numericDetails, "x", "y", "width", "height") else previous.contentHostBounds,
                androidViewHostBounds = if (event == "WEBVIEW_HOST_MEASURED") bounds(numericDetails, "x", "y", "width", "height") else previous.androidViewHostBounds,
                webViewBounds = if (event == "WEBVIEW_NATIVE_MEASURED") bounds(numericDetails, "x_window", "y_window", "width", "height", "left_in_parent", "top_in_parent", "parent_width", "parent_height") else previous.webViewBounds,
            )
        }
    }

    private fun bounds(details: Map<String, String>, vararg keys: String): String =
        keys.joinToString(" ") { key -> "$key=${details[key]?.toIntOrNull() ?: 0}" }
}

/** Installs only in signed acceptance builds; report is written to app-private filesDir. */
internal object BrowserV2FatalCrashCapture {
    private const val REPORT_NAME = "browser-v2-fatal-report.txt"
    private const val MAX_REPORT_CHARS = 64_000
    private val installed = AtomicBoolean(false)

    fun install(context: Context) {
        if (!installed.compareAndSet(false, true)) return
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                val report = formatReport(thread.name.orEmpty(), throwable, BrowserV2CrashContextStore.snapshot())
                persist(File(appContext.filesDir, REPORT_NAME), report)
            }
            if (previous != null) previous.uncaughtException(thread, throwable)
            else android.os.Process.killProcess(android.os.Process.myPid())
        }
    }

    fun readLastReport(context: Context): String? = runCatching {
        File(context.applicationContext.filesDir, REPORT_NAME)
            .takeIf { it.isFile }
            ?.readText(Charsets.UTF_8)
            ?.take(MAX_REPORT_CHARS)
    }.getOrNull()

    internal fun formatReport(threadName: String, throwable: Throwable, state: BrowserV2CrashContext): String = buildString {
        appendLine("Private Gallery acceptance fatal report")
        appendLine("Captured at epoch ms: ${System.currentTimeMillis()}")
        appendLine("Thread: ${safeAtom(threadName, 100)}")
        appendLine("Browser route: ${safeAtom(state.route, 80)}")
        appendLine("Browser state category: ${safeAtom(state.stateCategory, 80)}")
        appendLine("Active tab count: ${state.activeTabCount.coerceIn(0, 32)}")
        appendLine("Selected tab exists: ${state.selectedTabExists}")
        appendLine("Real WebView parented: ${state.webViewAttached}")
        appendLine("Real WebView attached to window: ${state.webViewAttachedToWindow}")
        appendLine("WebView parent category: ${safeAtom(state.webViewParentCategory, 80)}")
        appendLine("WebView attach/detach/reparent counts: ${state.attachCount}/${state.detachCount}/${state.reparentCount}")
        appendLine("AndroidView update count: ${state.androidViewUpdateCount}")
        appendLine("Last Browser structural event: ${safeAtom(state.lastStructuralEvent, 80)}")
        appendLine("Browser route bounds: ${safeAtom(state.routeBounds, 300)}")
        appendLine("Browser root bounds: ${safeAtom(state.rootBounds, 300)}")
        appendLine("Browser chrome bounds: ${safeAtom(state.chromeBounds, 300)}")
        appendLine("Content host bounds: ${safeAtom(state.contentHostBounds, 300)}")
        appendLine("AndroidView host bounds: ${safeAtom(state.androidViewHostBounds, 300)}")
        appendLine("Native WebView bounds: ${safeAtom(state.webViewBounds, 500)}")

        val seen = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<Throwable, Boolean>())
        var currentCause: Throwable? = throwable
        var causeIndex = 0
        while (currentCause != null && causeIndex < 12 && seen.add(currentCause)) {
            appendLine("Exception[$causeIndex]: ${currentCause.javaClass.name}")
            appendLine("Message[$causeIndex]: ${sanitizeMessage(currentCause.message)}")
            currentCause.stackTrace.forEachIndexed { frameIndex, frame ->
                if (frameIndex < 512) appendLine("  at ${safeAtom(frame.toString(), 500)}")
            }
            currentCause = currentCause.cause
            causeIndex++
        }
        if (currentCause != null) appendLine("Cause chain truncated after $causeIndex exceptions")
    }.take(MAX_REPORT_CHARS)

    private fun persist(file: File, report: String) {
        val temporary = File(file.parentFile, "${file.name}.tmp")
        FileOutputStream(temporary).use { stream ->
            stream.write(report.toByteArray(Charsets.UTF_8))
            stream.fd.sync()
        }
        if (!temporary.renameTo(file)) {
            temporary.copyTo(file, overwrite = true)
            temporary.delete()
        }
    }

    private fun sanitizeMessage(message: String?): String {
        var clean = message.orEmpty().take(2_000)
        clean = clean.replace(Regex("(?i)\\b(?:https?|wss?)://[^\\s\\]\\[(){}<>\\\"']+"), "[URL REDACTED]")
        clean = clean.replace(Regex("(?i)\\bdata:[^\\s\\]\\[(){}<>]+"), "[DATA REDACTED]")
        clean = clean.replace(Regex("(?i)\\b(address|url|uri|query|text|value)\\s*[=:]\\s*[^\\s,;]+"), "$1=[REDACTED]")
        clean = clean.replace(Regex("(?i)\\b(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)+(?:[a-z]{2,63})(?::\\d{1,5})?(?:/[^\\s,;]*)?"), "[HOST REDACTED]")
        clean = clean.replace(Regex("(?i)\\b(?:\\d{1,3}\\.){3}\\d{1,3}(?::\\d{1,5})?\\b"), "[IP REDACTED]")
        clean = clean.replace(Regex("(?i)\\b(bearer|authorization|cookie|set-cookie)\\s*[:=]\\s*[^\\s,;]+"), "$1=[REDACTED]")
        clean = clean.replace(Regex("\"[^\"]*\"|'[^']*'"), "[QUOTED TEXT REDACTED]")
        clean = clean.replace(Regex("[\\r\\n\\t]"), " ")
        return clean.take(500)
    }

    private fun safeAtom(value: String, maximum: Int): String =
        value.replace(Regex("[\\r\\n\\t]"), " ").take(maximum)
}
