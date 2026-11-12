package uk.co.traynor.privategallery.core.guardian

import org.json.JSONObject

internal enum class MessageImpact { UPSETTING, NEUTRAL, FINE }
internal enum class WarningHelp { YES, NO, UNSURE }
internal enum class WaitingOutcome { BETTER, SAME, WORSE, NOT_APPLICABLE }
internal enum class WarningCorrection { FALSE_POSITIVE, FALSE_NEGATIVE }

internal data class PersonalImpact(
    val impact: MessageImpact? = null,
    val warning: WarningHelp? = null,
    val waiting: WaitingOutcome? = null,
    val correction: WarningCorrection? = null,
    val excluded: Boolean = false,
) {
    fun hasFeedback() = impact != null || warning != null || waiting != null || correction != null

    fun encode(): JSONObject = JSONObject().put("impact", impact?.name)
        .put("warning", warning?.name).put("waiting", waiting?.name)
        .put("correction", correction?.name).put("excluded", excluded)

    companion object {
        fun decode(row: JSONObject) = PersonalImpact(
            MessageImpact.entries.firstOrNull { it.name == row.optString("impact") },
            WarningHelp.entries.firstOrNull { it.name == row.optString("warning") },
            WaitingOutcome.entries.firstOrNull { it.name == row.optString("waiting") },
            WarningCorrection.entries.firstOrNull { it.name == row.optString("correction") },
            row.optBoolean("excluded"),
        )
    }
}

internal data class PersonalImpactSettings(
    val collect: Boolean = false,
    val learn: Boolean = false,
    val days: Int = 30,
)

internal data class PersonalImpactSummary(
    val reports: Int,
    val useful: Int,
    val warningAnswers: Int,
    val falsePositives: Int,
    val falseNegatives: Int,
    val waiting: Map<WaitingOutcome, Int>,
)

/** Owner reports are descriptive, independent of communication classification. */
internal fun summarizePersonalImpact(entries: List<PersonalImpact>): PersonalImpactSummary =
    PersonalImpactSummary(
        entries.size,
        entries.count { it.warning == WarningHelp.YES },
        entries.count { it.warning == WarningHelp.YES || it.warning == WarningHelp.NO },
        entries.count { it.correction == WarningCorrection.FALSE_POSITIVE },
        entries.count { it.correction == WarningCorrection.FALSE_NEGATIVE },
        entries.mapNotNull { it.waiting }.groupingBy { it }.eachCount(),
    )

internal fun predictPersonalImpact(settings: PersonalImpactSettings, entries: List<PersonalImpact>): MessageImpact? {
    if (!settings.collect || !settings.learn) return null
    val labels = entries.filterNot { it.excluded }.mapNotNull { it.impact }
    if (labels.size < 3) return null
    val counts = labels.groupingBy { it }.eachCount()
    return counts.entries.singleOrNull { it.value > labels.size / 2 }?.key
}

internal data class PersonalImpactExample(val group: String, val feedback: PersonalImpact)

internal data class PersonalImpactValidation(val correct: Int, val tested: Int)

/** Keep every message from one conversation in the same partition and weight conversations equally. */
internal fun validatePersonalImpact(
    settings: PersonalImpactSettings,
    entries: List<PersonalImpactExample>,
): PersonalImpactValidation? {
    if (!settings.collect || !settings.learn) return null
    val labels = entries.filterNot { it.feedback.excluded }
        .groupBy { it.group }.mapNotNull { (group, reports) ->
            val counts = reports.mapNotNull { it.feedback.impact }.groupingBy { it }.eachCount()
            val highest = counts.values.maxOrNull()
            counts.entries.singleOrNull { it.value == highest }?.let { group to it.key }
        }
    val (test, train) = labels.partition { (group, _) -> Math.floorMod(group.hashCode(), 3) == 0 }
    if (train.size < 10 || test.size < 5 || test.map { it.second }.distinct().size < 2) return null
    val counts = train.map { it.second }.groupingBy { it }.eachCount()
    val highest = counts.values.max()
    val prediction = counts.entries.singleOrNull { it.value == highest }?.key ?: return null
    return PersonalImpactValidation(test.count { it.second == prediction }, test.size)
}
