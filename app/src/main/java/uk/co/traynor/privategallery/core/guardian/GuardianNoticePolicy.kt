package uk.co.traynor.privategallery.core.guardian

internal enum class GuardianNoticeKind(val title: String, val description: String) {
    LOW("Low concern", "No concerning pattern identified by the local assessment."),
    UPSETTING("Potentially upsetting", "Communication cues may warrant a later review."),
    HIGH("High concern", "Possible direct insult or threat detected."),
    UNCERTAIN("Uncertain", "There is insufficient evidence to classify this message."),
}

internal fun guardianNoticeKind(assessment: GuardianAssessment?, patterns: Set<NovaBrainPattern>): GuardianNoticeKind =
    when {
        NovaBrainPattern.THREAT in patterns || NovaBrainPattern.INSULT in patterns -> GuardianNoticeKind.HIGH
        patterns.isNotEmpty() || assessment == GuardianAssessment.UPSETTING -> GuardianNoticeKind.UPSETTING
        assessment == GuardianAssessment.LOW_RISK -> GuardianNoticeKind.LOW
        else -> GuardianNoticeKind.UNCERTAIN
    }

internal fun guardianNoticeProfile(profile: Long, own: Long?, friends: Long?): ProbeAccount {
    if (own == null || own < 0) return ProbeAccount.UNRESOLVED
    return probeAccount(profile == own, profile, friends)
}

internal fun guardianNoticeQuietHour(hour: Int, start: Int, end: Int): Boolean =
    hour in 0..23 && start in 0..23 && end in 0..23 &&
        guardianNoticeQuietMinute(hour * 60, start * 60, end * 60)

internal enum class GuardianQuietRule(val label: String) {
    HIDE("Hide alert"), SILENT("Silent alert"), NORMAL("Normal alert"),
}

internal fun guardianNoticeQuietMinute(minute: Int, start: Int, end: Int): Boolean =
    when {
        minute !in 0..1439 || start !in 0..1439 || end !in 0..1439 || start == end -> false
        start < end -> minute in start until end
        else -> minute >= start || minute < end
    }

internal fun guardianNoticeTime(value: String): Int? {
    if (!Regex("[0-9]{2}:[0-9]{2}").matches(value)) return null
    val hour = value.take(2).toInt()
    val minute = value.takeLast(2).toInt()
    return if (hour in 0..23 && minute in 0..59) hour * 60 + minute else null
}
