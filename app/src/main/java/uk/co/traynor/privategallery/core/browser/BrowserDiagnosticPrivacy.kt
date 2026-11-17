package uk.co.traynor.privategallery.core.browser

/**
 * Closed serialization vocabulary. Untrusted strings are never made safe by regex redaction.
 * Only fixed categories, typed structural geometry/flags and selected provider identity survive.
 * Names, URLs, prompts, tokens, media sizes and container/item counts have no fields here.
 */
internal object BrowserDiagnosticPrivacy {
    private val categories = setOf(
        "NAVIGATION_REQUEST", "NAVIGATION_SUBMITTED", "MAIN_PAGE_STARTED", "MAIN_PAGE_FINISHED",
        "MAIN_PAGE_COMMIT_VISIBLE", "MAIN_PAGE_ERROR", "MAIN_HTTP_ERROR", "PAGE_STARTED",
        "PAGE_FINISHED", "PAGE_COMMIT_VISIBLE", "RESOURCE_REQUEST", "RESOURCE_ERROR",
        "RESOURCE_HTTP_ERROR", "NETWORK_ERROR", "JS_ERROR", "JS_WARNING", "JS_CONSOLE",
        "CONSOLE_WARNING", "CONSOLE_ERROR", "WEBVIEW_PROVIDER", "WEBVIEW_CONFIGURATION",
        "VPN_STATE", "VPN_GATE_BLOCKED_REQUEST", "SSL_ERROR", "RENDER_PROCESS_GONE",
        "WEBVIEW_CREATED", "WEBVIEW_REBOUND", "WEBVIEW_ATTACHED", "WEBVIEW_DETACHED",
        "WEBVIEW_CREATE_FAILED", "WEBVIEW_EVICTED", "WEBVIEW_FOCUS_REQUEST", "WEBVIEW_FOCUS_STATE",
        "WEBVIEW_INITIAL_STATE", "WEBVIEW_PARENT_STATE", "WEBVIEW_BOUNDS", "WEBVIEW_VISIBILITY",
        "WEBVIEW_DOCUMENT_STATE", "WEBVIEW_REPARENTED", "WEBVIEW_NATIVE_MEASURED",
        "WEBVIEW_HOST_CREATED", "WEBVIEW_HOST_UPDATED", "WEBVIEW_HOST_RELEASED",
        "WEBVIEW_HOST_REQUESTED", "WEBVIEW_HOST_MEASURED", "BROWSER_ROUTE_ENTERED",
        "BROWSER_ROUTE_MEASURED", "BROWSER_ROOT_MEASURED", "BROWSER_CHROME_MEASURED",
        "BROWSER_CHROME_COMPOSED", "BROWSER_V2_COMPOSED", "CONTENT_HOST_MEASURED",
        "CONTENT_HOST_COMPOSED", "OMNIBOX_TEXT_CHANGED", "OMNIBOX_FOCUS_GAINED",
        "OMNIBOX_FOCUS_CHANGED", "OMNIBOX_SUBMIT", "OMNIBOX_SUBMIT_STATIC_HOST_IGNORED",
        "REAL_MODE_ENTERED", "EXTERNAL_NAVIGATION_REQUEST", "PERMISSION_REQUEST", "PERMISSION_RESULT",
        "FILE_CHOOSER_REQUEST", "FILE_CHOOSER_RESULT", "FILE_CHOOSER_POLICY", "DOWNLOAD_REQUEST",
        "WINDOW_OPEN_REQUEST", "WINDOW_CREATE_REQUEST", "WINDOW_CREATE_RESULT", "WINDOW_CLOSE_REQUEST",
        "WINDOW_FOCUS_REQUEST", "CREATE_WINDOW_REQUEST", "CHILD_WEBVIEW_CREATED", "CHILD_WEBVIEW_CLOSE",
        "JS_DIALOG_ALERT", "JS_DIALOG_CONFIRM", "JS_DIALOG_PROMPT", "JS_BEFORE_UNLOAD",
        "FULLSCREEN_REQUEST", "FULLSCREEN_EXIT", "SAFE_BROWSING_EVENT", "VIDEO_PATH",
        "MEDIA_DETECTED", "MEDIA_DOWNLOADABLE", "MEDIA_PROTECTED_OR_UNAVAILABLE", "MEDIA_UNSUPPORTED",
        "MEDIA_VALIDATED", "SAVE_TO_VAULT_STARTED", "SAVE_TO_VAULT_COMPLETED", "SAVE_TO_VAULT_FAILED",
        "VAULT_ITEM_SELECTED", "UPLOAD_CONFIRMED", "UPLOAD_CANCELLED", "UPLOAD_TEMP_CLEANED",
        "ACCEPTANCE_VERBOSE", "ACCEPTANCE_LOCAL_PAGE_REQUESTED", "CURRENT_SESSION_TRACE_CLEARED",
        "INTERACTION_ARMED", "INTERACTION_START", "INTERACTION_CANCELED", "RUNTIME_SNAPSHOT",
        "RUNTIME_SNAPSHOT_UNAVAILABLE", "DOCUMENT_READY_STATE", "DOCUMENT_VISIBILITY", "DOCUMENT_FOCUS",
        "IFRAME_COUNT_CHANGE", "DOM_MODAL_COUNT_CHANGE",
    )
    private val flags = setOf(
        "main_frame", "attached", "window_attached", "has_parent", "has_layout_params",
        "parent_clip_children", "parent_clip_padding", "layout_requested", "shown",
        "has_view_background", "hardware_accelerated", "has_document_url", "clip_to_bounds",
        "candidate", "ready", "restore", "local_fixture", "active_tab", "enabled",
        "javascript", "third_party_cookies", "dom_storage", "file_access", "content_access",
    )
    private val geometry = setOf(
        "x", "y", "width", "height", "x_window", "y_window", "left", "top", "left_in_parent",
        "top_in_parent", "parent_width", "parent_height", "measured_width", "measured_height",
        "layout_params_width", "layout_params_height", "content_height", "visibility", "window_visibility",
        "layer_type", "attachment", "transition", "document_generation", "relative_ms",
    )
    private val values = mapOf(
        "scheme" to setOf("http", "https", "none", "other"),
        "parent" to setOf("none", "AndroidViewHolder", "FrameLayout", "ViewGroup"),
        "parent_kind" to setOf("none", "view", "view_group"),
        "phase" to setOf("CREATED", "ATTACHED", "DETACHED", "FIRST_PRE_DRAW", "LAYOUT", "ARMED_BASELINE", "AFTER_INPUT_0", "AFTER_INPUT_250", "AFTER_INPUT_1000", "AFTER_INPUT_2000"),
        "reason" to setOf("browser_destination_enter", "browser_destination_leave", "explicit_navigation", "window_request", "tab_limit"),
        "decision" to setOf("cancel", "allow", "deny"),
        "result" to setOf("true", "false", "not_ready"),
        "state" to setOf("retained", "unconfigured", "disconnected", "connecting", "reconnecting", "failed", "disconnecting", "connected", "UNCONFIGURED", "DISCONNECTED", "CONNECTING", "RECONNECTING", "FAILED", "DISCONNECTING", "CONNECTED"),
        "policy" to setOf("blocked", "vault_only", "device_allowed"),
        "type" to setOf("script", "image", "other", "IllegalStateException", "RuntimeException", "OutOfMemoryError"),
        "tab" to setOf("created"),
        "path" to setOf("MEDIA3_DIRECT"),
        "level" to setOf("DEBUG", "LOG", "INFO", "WARNING", "ERROR", "OTHER"),
        "package" to setOf("com.android.webview", "com.google.android.webview", "com.google.android.webview.beta", "com.google.android.webview.dev", "com.google.android.webview.canary", "com.android.chrome", "unknown"),
    )

    fun category(value: String): String = if (value in categories) value else "UNKNOWN_EVENT"
    fun route(value: String): String = if (value == "BROWSER") value else "UNKNOWN"
    fun parent(value: String): String = value.takeIf { it in values.getValue("parent") } ?: "other"

    fun details(category: String, supplied: Map<String, String>): Map<String, String> {
        if (category == "UNKNOWN_EVENT") return emptyMap()
        val result = linkedMapOf<String, String>()
        supplied.forEach { (key, value) ->
            val safe = when {
                key in flags -> value.takeIf { it == "true" || it == "false" }
                key in geometry -> value.toIntOrNull()?.takeIf { it in -1_000_000..1_000_000 }?.toString()
                key == "code" -> value.toIntOrNull()?.takeIf { it in setOf(-2, -6, -7, -8, -9, -11, -15, 0) }?.toString()
                key == "status" -> value.toIntOrNull()?.takeIf { it in 100..599 }?.let { "${it / 100}xx" }
                key == "version" && category == "WEBVIEW_PROVIDER" -> value.takeIf { it.length <= 40 && it.matches(Regex("[0-9]{1,10}(\\.[0-9]{1,10}){0,3}")) }
                key in values -> value.takeIf { it in values.getValue(key) }
                else -> null
            }
            if (safe != null) result[key] = safe
        }
        return result
    }

    /** Geometry is parsed as an exact typed schema, then rebuilt; arbitrary atoms cannot survive. */
    fun bounds(value: String, native: Boolean = false): String {
        val keys = if (native) listOf("x_window", "y_window", "width", "height", "left_in_parent", "top_in_parent", "parent_width", "parent_height")
                   else listOf("x", "y", "width", "height")
        val atoms = value.split(' ')
        if (atoms.size != keys.size) return "unmeasured"
        val parsed = keys.mapIndexed { index, key ->
            val parts = atoms[index].split('=')
            if (parts.size != 2 || parts[0] != key) return "unmeasured"
            val number = parts[1].toIntOrNull()?.takeIf { it in -1_000_000..1_000_000 } ?: return "unmeasured"
            "$key=$number"
        }
        return parsed.joinToString(" ")
    }
}
