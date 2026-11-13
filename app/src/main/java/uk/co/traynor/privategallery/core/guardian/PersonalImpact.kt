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

internal data class PersonalImpactValidation(
    val correct: Int,
    val tested: Int,
    val falsePositives: Int = 0,
    val nonUpsetting: Int = 0,
    val falseNegatives: Int = 0,
    val upsetting: Int = 0,
    val byCategory: Map<MessageImpact, Pair<Int, Int>> = emptyMap(),
) {
    fun falsePositiveRate() = if (nonUpsetting >= 3) falsePositives.toDouble() / nonUpsetting else null
    fun falseNegativeRate() = if (upsetting >= 3) falseNegatives.toDouble() / upsetting else null
}

internal fun personalImpactSuggestions(settings: PersonalImpactSettings, summary: PersonalImpactSummary): List<String> =
    if (!settings.collect || !settings.learn) emptyList() else buildList {
        val better = summary.waiting[WaitingOutcome.BETTER] ?: 0
        val worse = summary.waiting[WaitingOutcome.WORSE] ?: 0
        val same = summary.waiting[WaitingOutcome.SAME] ?: 0
        if (better >= 3 && better > worse + same) {
            add("You reported feeling better after waiting $better times. Consider reviewing selected conversations later or taking a quiet period.")
        }
        if (summary.falsePositives >= 3 && summary.falsePositives > summary.falseNegatives) {
            add("You marked ${summary.falsePositives} warnings as false positives. Consider reviewing Guardian’s warning settings.")
        }
    }

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
    val actual = test.map { it.second }
    val upsetting = actual.count { it == MessageImpact.UPSETTING }
    val nonUpsetting = actual.size - upsetting
    return PersonalImpactValidation(
        actual.count { it == prediction }, actual.size,
        if (prediction == MessageImpact.UPSETTING) nonUpsetting else 0, nonUpsetting,
        if (prediction != MessageImpact.UPSETTING) upsetting else 0, upsetting,
        actual.groupingBy { it }.eachCount().mapValues { (category, total) ->
            (if (category == prediction) total else 0) to total
        },
    )
}

internal data class ConversationImpactReport(
    val conversation: String,
    val capturedAt: Long,
    val reportedAt: Long,
    val feedback: PersonalImpact,
)

internal fun priorConversationImpact(
    entries: List<ConversationImpactReport>,
    capturedBefore: Long,
    reportedBefore: Long,
): List<PersonalImpact> = entries.filter {
    it.capturedAt < capturedBefore && it.reportedAt < reportedBefore
}.map { it.feedback }

/** Evaluate the same majority predictor using only feedback available before the held-out capture. */
internal fun validateConversationImpact(
    settings: PersonalImpactSettings,
    entries: List<ConversationImpactReport>,
): PersonalImpactValidation? {
    if (!settings.collect || !settings.learn) return null
    val predictions = entries.filterNot { it.feedback.excluded }
        .groupBy { it.conversation }.values.mapNotNull { group ->
        // One final test observation per conversation prevents a busy thread dominating.
        val latest = group.maxByOrNull { it.capturedAt } ?: return@mapNotNull null
        if (group.count { it.capturedAt == latest.capturedAt } != 1) return@mapNotNull null
        val actual = latest.feedback.impact ?: return@mapNotNull null
        val predicted = predictPersonalImpact(settings,
            priorConversationImpact(group, latest.capturedAt, latest.capturedAt))
            ?: return@mapNotNull null
        actual to predicted
    }
    if (predictions.size < 10 || predictions.map { it.first }.distinct().size < 2) return null
    val upsetting = predictions.count { it.first == MessageImpact.UPSETTING }
    return PersonalImpactValidation(
        predictions.count { it.first == it.second }, predictions.size,
        predictions.count { it.first != MessageImpact.UPSETTING && it.second == MessageImpact.UPSETTING },
        predictions.size - upsetting,
        predictions.count { it.first == MessageImpact.UPSETTING && it.second != MessageImpact.UPSETTING },
        upsetting,
        predictions.groupBy { it.first }.mapValues { (_, rows) -> rows.count { it.first == it.second } to rows.size },
    )
}
