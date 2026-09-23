package uk.co.traynor.privategallery.core.browser.v2

import org.json.JSONObject

/** Treat evaluateJavascript output as untrusted page input, never as a diagnostic message. */
internal object BrowserRuntimeSnapshot {
    private val counts = setOf("dom_nodes", "sampled_elements", "visible_elements", "scripts", "frames", "canvas", "stylesheets", "modals", "viewport_width", "viewport_height", "runtime_errors", "promise_rejections", "resource_failures", "focus_changes", "visibility_changes")
    private val flags = setOf("focus", "local_storage", "indexed_db", "service_worker", "webgl", "scan_truncated")
    fun parse(raw: String): Map<String, String> = runCatching {
        val data = JSONObject(raw)
        buildMap {
            counts.forEach { key ->
                val value = data.opt(key)
                if (value is Number && value.toDouble().isFinite() && value.toDouble() >= 0 && value.toDouble() % 1.0 == 0.0) {
                    put(key, value.toDouble().coerceAtMost(1_000_000.0).toInt().toString())
                }
            }
            flags.forEach { key -> (data.opt(key) as? Boolean)?.let { put(key, it.toString()) } }
            (data.opt("ready") as? String)?.takeIf { it in setOf("loading", "interactive", "complete") }?.let { put("ready", it) }
            (data.opt("visibility") as? String)?.takeIf { it in setOf("visible", "hidden", "prerender") }?.let { put("visibility", it) }
        }
    }.getOrDefault(emptyMap())
}
