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
