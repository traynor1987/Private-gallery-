package uk.co.traynor.privategallery.core.guardian

/** Inbox visibility is independent of durable storage. Zero means no automatic archiving. */
internal object GuardianRetention89 {
    val choices = listOf(0, 1, 7, 30)
    const val DAY = 86_400_000L
    fun archiveAfter(days: Int, capturedAt: Long, now: Long): Long? {
        require(days in choices)
        if (days == 0) return null
        return (days * DAY - (now - capturedAt).coerceAtLeast(0)).coerceAtLeast(0)
    }
    fun label(days: Int) = if (days == 0) "Keep Forever — No Automatic Deletion"
        else "$days days in Inbox, then History"
}

internal data class GuardianRecoveryReport89(val stored: Int, val copies: Int, val recoverable: Int, val unreadable: Int)
internal data class GuardianDeletion89(val deleted: Int, val trainingPreserved: Int, val held: Int)
