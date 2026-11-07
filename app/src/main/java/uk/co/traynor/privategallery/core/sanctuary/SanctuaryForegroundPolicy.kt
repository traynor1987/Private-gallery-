package uk.co.traynor.privategallery.core.sanctuary

/** In-memory, conservative foreground inference. No package events are persisted. */
internal class SanctuaryForegroundPolicy {
    private val resumed = mutableSetOf<Pair<String, String>>()

    fun resumed(packageName: String, activity: String) {
        resumed += packageName to activity
    }

    fun paused(packageName: String, activity: String) {
        resumed -= packageName to activity
    }

    fun reset() = resumed.clear()

    fun foreground(): String? = resumed.asSequence().map { it.first }.distinct().singleOrNull()

    fun shouldBlock(packageName: String?, approved: Set<String>, essential: Set<String>): Boolean =
        packageName != null && packageName !in approved && packageName !in essential
}
