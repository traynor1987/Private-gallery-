package uk.co.traynor.privategallery.core.browser.v2

import org.json.JSONObject

/** Page data is untrusted: only fixed enum/boolean/bounded numeric fields can leave the probe. */
internal object BrowserRuntimeSnapshot {
    private val counts = setOf("dom_nodes", "sampled_elements", "visible_elements", "scripts", "frames", "canvas", "stylesheets", "modals", "viewport_width", "viewport_height", "runtime_errors", "promise_rejections", "resource_failures", "focus_changes", "visibility_changes",
        "document_marker", "mutation_batches", "nodes_added", "nodes_removed", "attribute_changes", "ancestor_hidden", "own_hidden", "display_none", "visibility_hidden", "opacity_zero", "opacity_reduced", "content_visibility_hidden", "viewport_intersecting", "outside_viewport", "clipped_elements", "fully_clipped", "content_candidates", "sampled_candidates", "sample_unobscured", "sample_obscured", "sample_unknown", "covering_layers", "fixed_layers", "absolute_layers", "stacking_contexts", "complex_clip_elements", "transformed_elements", "shadow_hosts", "top_layer_elements")
    private val flags = setOf("focus", "local_storage", "indexed_db", "service_worker", "webgl", "scan_truncated", "ancestor_scan_truncated", "candidate_scan_truncated", "layer_scan_truncated", "visual_scan_truncated", "root_replaced", "body_replaced", "pre_input_captured")
    private fun number(data: JSONObject, key: String, max: Int = 1_000_000): String? {
        val value = data.opt(key) as? Number ?: return null
        val n = value.toDouble()
        return if (n.isFinite() && n >= 0 && n % 1.0 == 0.0) n.coerceAtMost(max.toDouble()).toInt().toString() else null
    }
    fun parse(raw: String): Map<String, String> = runCatching {
        val data = JSONObject(raw)
        buildMap {
            fun fields(source: JSONObject, prefix: String) {
                counts.forEach { key -> number(source, key)?.let { put(prefix + key, it) } }
                flags.forEach { key -> (source.opt(key) as? Boolean)?.let { put(prefix + key, it.toString()) } }
                mapOf("ready" to setOf("loading", "interactive", "complete"), "visibility" to setOf("visible", "hidden", "prerender")).forEach { (key, allowed) ->
                    (source.opt(key) as? String)?.takeIf { it in allowed }?.let { put(prefix + key, it) }
                }
                source.optJSONArray("layers")?.let { layers ->
                    for (i in 0 until minOf(layers.length(), 8)) {
                        val layer = layers.optJSONObject(i) ?: continue
                        val p = "${prefix}layer_${i}_"
                        mapOf("position" to setOf("fixed", "absolute", "other"), "z_band" to setOf("auto", "negative", "zero", "positive")).forEach { (key, allowed) ->
                            (layer.opt(key) as? String)?.takeIf { it in allowed }?.let { put(p + key, it) }
                        }
                        listOf("coverage_percent", "opacity_percent").forEach { key -> number(layer, key, 100)?.let { put(p + key, it) } }
                        listOf("context_depth", "hit_top_points", "in_front_samples", "behind_samples").forEach { key -> number(layer, key, 1000)?.let { put(p + key, it) } }
                        listOf("pointer_none", "top_layer").forEach { key -> (layer.opt(key) as? Boolean)?.let { put(p + key, it.toString()) } }
                    }
                }
            }
            fields(data, "")
            // One level only: nested page objects cannot recursively expand the report.
            data.optJSONObject("interaction_before")?.let { fields(it, "before_") }
        }
    }.getOrDefault(emptyMap())
}
