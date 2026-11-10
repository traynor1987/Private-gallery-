package uk.co.traynor.privategallery.core.guardian

internal const val GUARDIAN_HOLD_MILLIS = 4 * 60 * 60 * 1_000L

internal enum class GuardianInboxSection(val label: String) {
    PENDING("Awaiting analysis"), READY("Ready to Read"), COOLING("Cooling Down"),
    PROTECTED("Protected"), HISTORY("History"),
}

internal data class GuardianInboxClock(val wall: Long, val elapsed: Long, val boot: Int)
internal data class GuardianInboxHold(val wallDeadline: Long, val elapsedDeadline: Long, val boot: Int) {
    fun remaining(now: GuardianInboxClock): Long =
        (if (boot >= 0 && boot == now.boot) elapsedDeadline - now.elapsed else wallDeadline - now.wall)
            .coerceIn(0L, GUARDIAN_HOLD_MILLIS)
}

internal fun guardianInboxSection(
    hold: GuardianInboxHold?, ready: Boolean, now: GuardianInboxClock, archived: Boolean = false,
): GuardianInboxSection = when {
    hold != null -> if (hold.remaining(now) > 0) GuardianInboxSection.COOLING else GuardianInboxSection.PROTECTED
    archived -> GuardianInboxSection.HISTORY
    ready -> GuardianInboxSection.READY
    else -> GuardianInboxSection.PENDING
}

internal data class GuardianInboxCounts(
    val pending: Int, val ready: Int, val cooling: Int, val protected: Int, val history: Int,
    val refreshAfterMillis: Long?,
)
