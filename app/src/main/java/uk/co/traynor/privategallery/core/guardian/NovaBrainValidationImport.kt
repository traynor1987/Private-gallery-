package uk.co.traynor.privategallery.core.guardian

import org.json.JSONArray

internal const val MAX_VALIDATION_INPUT = 262_144

internal fun parseNovaBrainValidation(input: String): List<NovaBrainValidationMessage> {
    require(input.length in 1..MAX_VALIDATION_INPUT)
    val rows = JSONArray(input)
    require(rows.length() in 40..500)
    return List(rows.length()) { index ->
        val row = rows.getJSONObject(index)
        val conversation = row.get("conversationId") as String
        val text = row.get("text") as String
        val labels = row.getJSONArray("patterns")
        require(conversation.length in 1..1024 && text.length in 1..2048)
        require(labels.length() <= NovaBrainPattern.entries.size)
        val patterns = List(labels.length()) { NovaBrainPattern.valueOf(labels.get(it) as String) }
        require(patterns.distinct().size == patterns.size)
        NovaBrainValidationMessage(conversation, text, patterns.toSet())
    }
}
