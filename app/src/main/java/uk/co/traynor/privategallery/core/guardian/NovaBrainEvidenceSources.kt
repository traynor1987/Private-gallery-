package uk.co.traynor.privategallery.core.guardian

import org.json.JSONArray
import org.json.JSONObject

internal data class NovaBrainSourceRemoval(val found: Boolean, val retained: JSONObject?)

internal fun removeNovaBrainEvidenceSource(example: JSONObject, sourceId: String): NovaBrainSourceRemoval {
    val stored = example.optJSONArray("evidenceSources") ?: return NovaBrainSourceRemoval(false, example)
    val sources = List(stored.length(), stored::getString)
    if (sourceId !in sources) return NovaBrainSourceRemoval(false, example)
    val remaining = sources.filterNot { it == sourceId }
    if (remaining.isEmpty() && !example.optBoolean("unlinkedApproval")) return NovaBrainSourceRemoval(true, null)
    return NovaBrainSourceRemoval(true, JSONObject(example.toString()).put("evidenceSources", JSONArray(remaining)))
}
