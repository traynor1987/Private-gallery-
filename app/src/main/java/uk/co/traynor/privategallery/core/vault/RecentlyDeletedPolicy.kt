package uk.co.traynor.privategallery.core.vault

object RecentlyDeletedPolicy {
    const val RETENTION_MILLIS = 30L * 24 * 60 * 60 * 1000

    fun expired(deletedAtEpochMillis: Long?, now: Long): Boolean =
        deletedAtEpochMillis != null && deletedAtEpochMillis > 0 && now >= deletedAtEpochMillis &&
            now - deletedAtEpochMillis >= RETENTION_MILLIS
}
