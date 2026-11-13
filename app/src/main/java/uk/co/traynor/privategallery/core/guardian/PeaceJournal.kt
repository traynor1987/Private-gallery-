package uk.co.traynor.privategallery.core.guardian

import org.json.JSONObject

internal data class PeaceSession(
    val id: String,
    val start: Long,
    val end: Long,
    val activity: String,
    val calmer: Boolean? = null,
    val helpful: Boolean? = null,
    val tooRestrictive: Boolean? = null,
    val reviewLater: Boolean = false,
    val waiting: WaitingOutcome? = null,
) {
    fun asImpact() = PersonalImpact(waiting = waiting)
}

internal data class PeaceJournal(
    val enabled: Boolean = false,
    val days: Int = 30,
    val goalMinutes: Int = 30,
    val activeSince: Long? = null,
    val activity: String = "",
    val sessions: List<PeaceSession> = emptyList(),
    val trackingSince: Long? = null,
) {
    fun retained(now: Long) = copy(sessions = sessions.filter { it.end >= now - days * 86_400_000L }.take(200))

    fun encode(): List<JSONObject> = listOf(JSONObject().put("enabled", enabled).put("days", days)
        .put("trackingSince", trackingSince).put("goalMinutes", goalMinutes).put("activeSince", activeSince).put("activity", activity)) + sessions.map {
        JSONObject().put("id", it.id).put("start", it.start).put("end", it.end).put("activity", it.activity)
            .put("calmer", it.calmer).put("helpful", it.helpful).put("tooRestrictive", it.tooRestrictive)
            .put("reviewLater", it.reviewLater).put("waiting", it.waiting?.name)
    }

    companion object {
        fun decode(rows: List<JSONObject>): PeaceJournal {
            val header = rows.firstOrNull() ?: return PeaceJournal()
            fun JSONObject.answer(key: String): Boolean? = if (isNull(key)) null else getBoolean(key)
            return PeaceJournal(header.optBoolean("enabled"), header.optInt("days", 30).coerceIn(1, 90),
                header.optInt("goalMinutes", 30).coerceIn(5, 240), header.optLong("activeSince").takeIf { it > 0 },
                header.optString("activity"), rows.drop(1).map {
                    PeaceSession(it.getString("id"), it.getLong("start"), it.getLong("end"), it.optString("activity"),
                        it.answer("calmer"), it.answer("helpful"), it.answer("tooRestrictive"), it.optBoolean("reviewLater"),
                        WaitingOutcome.entries.firstOrNull { value -> value.name == it.optString("waiting") })
                }, header.optLong("trackingSince").takeIf { it > 0 })
        }
    }
}

/** Count elapsed protected time once, including overlapping voluntary and Sanctuary sessions. */
internal fun peaceProtectedMillis(intervals: List<Pair<Long, Long>>, from: Long, through: Long): Long {
    if (from >= through) return 0
    var total = 0L
    var coveredUntil = from
    for ((start, end) in intervals.sortedBy { it.first }) {
        val lower = maxOf(start, from, coveredUntil)
        val upper = minOf(end, through)
        if (upper > lower) {
            total += upper - lower
            coveredUntil = upper
        }
    }
    return total
}
