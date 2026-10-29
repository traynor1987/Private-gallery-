package uk.co.traynor.privategallery.core.vault

object RecentlyDeletedPolicy {
    const val DAY_MILLIS = 24L * 60 * 60 * 1000
    const val RETENTION_MILLIS = 30L * DAY_MILLIS
    val supportedDays = listOf(7, 15, 30, 60, 0)
    fun validate(days: Int): Int = days.also { require(it in supportedDays) }
    fun expired(deletedAtEpochMillis: Long?, now: Long, days: Int = 30): Boolean {
        validate(days)
        return days != 0 && deletedAtEpochMillis != null && deletedAtEpochMillis > 0 &&
            now >= deletedAtEpochMillis && now - deletedAtEpochMillis >= days * DAY_MILLIS
    }
    fun remainingDays(deletedAtEpochMillis: Long, now: Long, days: Int): Long? {
        validate(days)
        if (days == 0) return null
        val elapsed = if (now >= deletedAtEpochMillis && deletedAtEpochMillis > 0) now - deletedAtEpochMillis else 0L
        val remaining = (days * DAY_MILLIS - elapsed).coerceAtLeast(0)
        return (remaining + DAY_MILLIS - 1) / DAY_MILLIS
    }
}
