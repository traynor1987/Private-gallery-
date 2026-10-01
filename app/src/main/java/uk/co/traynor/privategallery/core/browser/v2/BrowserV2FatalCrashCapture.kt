package uk.co.traynor.privategallery.core.browser.v2

import android.content.Context
import uk.co.traynor.privategallery.core.browser.BrowserDiagnosticPrivacy
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
            val parent = BrowserDiagnosticPrivacy.parent(webViewParentCategory)
            val oldParent = previous.webViewParentCategory
            previous.copy(
                route = if (event == "BROWSER_ROUTE_ENTERED") "BROWSER" else previous.route,
                stateCategory = BrowserDiagnosticPrivacy.category(event),
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
                lastStructuralEvent = BrowserDiagnosticPrivacy.category(event),
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
    private const val REPORT_HEADER = "Private Gallery acceptance fatal report (fixed categories v2)"
    private val installed = AtomicBoolean(false)

    fun install(context: Context) {
        if (!installed.compareAndSet(false, true)) return
        val appContext = context.applicationContext
        // Do not retain/read back reports produced by the obsolete raw-message serializer.
        readReportFile(File(appContext.filesDir, REPORT_NAME))
        runCatching { File(appContext.filesDir, "$REPORT_NAME.tmp").delete() }
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

    fun readLastReport(context: Context): String? =
        readReportFile(File(context.applicationContext.filesDir, REPORT_NAME))

    internal fun readReportFile(file: File): String? = runCatching {
        if (!file.isFile) return@runCatching null
        val report = file.bufferedReader(Charsets.UTF_8).use { reader ->
            val text = StringBuilder()
            val buffer = CharArray(1024)
            while (text.length <= MAX_REPORT_CHARS) {
                val count = reader.read(buffer, 0, minOf(buffer.size, MAX_REPORT_CHARS + 1 - text.length))
                if (count < 0) break
                text.append(buffer, 0, count)
            }
            text.toString()
        }
        if (report.length > MAX_REPORT_CHARS || !report.startsWith("$REPORT_HEADER\n")) {
            file.delete()
            null
        } else report
    }.getOrNull()

    internal fun formatReport(threadName: String, throwable: Throwable, state: BrowserV2CrashContext): String = buildString {
        appendLine(REPORT_HEADER)
        // Thread names and Throwable messages can contain any owner/page input. Never inspect them.
        @Suppress("UNUSED_VARIABLE") val ignoredThreadName = threadName
        appendLine("Browser route: ${BrowserDiagnosticPrivacy.route(state.route)}")
        appendLine("Browser state category: ${BrowserDiagnosticPrivacy.category(state.stateCategory)}")
        appendLine("Selected tab exists: ${state.selectedTabExists}")
        appendLine("Real WebView parented: ${state.webViewAttached}")
        appendLine("Real WebView attached to window: ${state.webViewAttachedToWindow}")
        appendLine("WebView parent category: ${BrowserDiagnosticPrivacy.parent(state.webViewParentCategory)}")
        appendLine("WebView attach/detach/reparent counts: ${safeCounter(state.attachCount)}/${safeCounter(state.detachCount)}/${safeCounter(state.reparentCount)}")
        appendLine("AndroidView update count: ${safeCounter(state.androidViewUpdateCount)}")
        appendLine("Last Browser structural event: ${BrowserDiagnosticPrivacy.category(state.lastStructuralEvent)}")
        appendLine("Browser route bounds: ${BrowserDiagnosticPrivacy.bounds(state.routeBounds)}")
        appendLine("Browser root bounds: ${BrowserDiagnosticPrivacy.bounds(state.rootBounds)}")
        appendLine("Browser chrome bounds: ${BrowserDiagnosticPrivacy.bounds(state.chromeBounds)}")
        appendLine("Content host bounds: ${BrowserDiagnosticPrivacy.bounds(state.contentHostBounds)}")
        appendLine("AndroidView host bounds: ${BrowserDiagnosticPrivacy.bounds(state.androidViewHostBounds)}")
        appendLine("Native WebView bounds: ${BrowserDiagnosticPrivacy.bounds(state.webViewBounds, native = true)}")

        val seen = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<Throwable, Boolean>())
        var currentCause: Throwable? = throwable
        var causeIndex = 0
        while (currentCause != null && causeIndex < 12 && seen.add(currentCause)) {
            appendLine("Exception[$causeIndex]: ${exceptionCategory(currentCause)}")
            currentCause.stackTrace.forEachIndexed { frameIndex, frame ->
                if (frameIndex < 512) appendLine("  at ${safeFrame(frame)}")
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

    private fun safeCounter(value: Int): Int = value.coerceIn(0, 1_000_000)

    private val exceptionClasses = setOf(
        "java.lang.IllegalStateException", "java.lang.IllegalArgumentException", "java.lang.RuntimeException",
        "java.lang.NullPointerException", "java.lang.IndexOutOfBoundsException", "java.lang.OutOfMemoryError",
        "java.lang.SecurityException", "java.io.IOException", "java.util.concurrent.CancellationException",
        "javax.crypto.AEADBadTagException", "android.security.keystore.KeyPermanentlyInvalidatedException",
    )

    private fun exceptionCategory(failure: Throwable): String =
        failure.javaClass.name.takeIf { it in exceptionClasses } ?: "OTHER"

    // Exact class AND method pairs; package-prefix filters would retain invented sensitive names.
    // File names are reconstructed constants, never copied from a StackTraceElement.
    private val frameMethods = mapOf(
        "uk.co.traynor.privategallery.MainActivity" to setOf("onCreate", "onValueChange", "onResume", "onStop"),
        "uk.co.traynor.privategallery.core.browser.v2.BrowserV2Session" to setOf("onConsole", "onPageState", "recordAcceptanceUiEvent", "recordCrashContext", "close"),
        "androidx.compose.ui.text.input.TextFieldValue" to setOf("copy"),
        "android.os.Handler" to setOf("handleCallback", "dispatchMessage"),
        "android.os.Looper" to setOf("loop", "loopOnce"),
        "java.lang.Thread" to setOf("run"),
    )

    private fun safeFrame(frame: StackTraceElement): String {
        val methods = frameMethods[frame.className] ?: return "OTHER_FRAME"
        if (frame.methodName !in methods) return "OTHER_FRAME"
        val extension = if (frame.className in setOf("android.os.Handler", "android.os.Looper", "java.lang.Thread")) "java" else "kt"
        val fileName = frame.className.substringAfterLast('.') + "." + extension
        val line = frame.lineNumber.takeIf { it in 1..1_000_000 }
        return "${frame.className}.${frame.methodName}($fileName${line?.let { ":$it" } ?: ""})"
    }
}
