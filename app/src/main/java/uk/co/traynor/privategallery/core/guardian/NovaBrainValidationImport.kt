package uk.co.traynor.privategallery.core.guardian

import org.json.JSONArray

internal const val MAX_VALIDATION_INPUT = 262_144

internal enum class NovaBrainValidationScope(val groupKey: String) {
    CONVERSATION("conversationId"),
    INCIDENT("incidentId"),
}

internal fun parseNovaBrainValidation(
    input: String,
    scope: NovaBrainValidationScope = NovaBrainValidationScope.CONVERSATION,
): List<NovaBrainValidationMessage> {
    require(input.length in 1..MAX_VALIDATION_INPUT)
    val rows = JSONArray(input)
    require(rows.length() in 40..500)
    val evidenceIds = mutableSetOf<String>()
    return List(rows.length()) { index ->
        val row = rows.getJSONObject(index)
        val conversation = row.get(scope.groupKey) as String
        val text = row.get("text") as String
        val labels = row.getJSONArray("patterns")
        require(conversation.length in 1..1024 && text.length in 1..2048)
        require(labels.length() <= NovaBrainPattern.entries.size)
        val patterns = List(labels.length()) { NovaBrainPattern.valueOf(labels.get(it) as String) }
        require(patterns.distinct().size == patterns.size)
        val evidenceId = if (scope == NovaBrainValidationScope.INCIDENT) {
            (row.get("evidenceId") as String).also {
                require(it.isNotBlank() && it.length <= 1024 && evidenceIds.add(it))
            }
        } else null
        NovaBrainValidationMessage(conversation, text, patterns.toSet(), evidenceId)
    }
}
