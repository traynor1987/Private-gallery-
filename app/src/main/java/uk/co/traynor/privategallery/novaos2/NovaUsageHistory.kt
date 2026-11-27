package uk.co.traynor.privategallery.novaos2

import android.content.Context
import java.time.LocalDate
import uk.co.traynor.privategallery.core.home2.SpaceContext

/** Owner-saved daily totals. The encrypted Primary namespace holds no app identifiers or event timestamps. */
internal object NovaUsageHistory {
    private const val DAYS = 31
    private const val MAX_DAY_MS = 48 * 60 * 60 * 1_000L

    private fun preferences(space: SpaceContext) =
        space.getSharedPreferences("nova-usage-daily-v1", Context.MODE_PRIVATE)

    @Synchronized
    fun read(space: SpaceContext): Map<LocalDate, Long> {
        check(space.spaceId == "primary" && space.current())
        return preferences(space).all.map { (key, value) ->
            LocalDate.parse(key) to (value as Long).also { require(it in 1..MAX_DAY_MS) }
        }.toMap().toSortedMap().also { require(it.size <= DAYS) }
    }

    @Synchronized
    fun save(space: SpaceContext, daily: Map<LocalDate, Long>, allowed: () -> Boolean): Map<LocalDate, Long> {
        check(allowed())
        require(daily.size in 1..7 && daily.values.all { it in 1..MAX_DAY_MS })
        val updated = (read(space) + daily).toSortedMap().entries.toList().takeLast(DAYS)
        val edit = preferences(space).edit().clear()
        for ((day, millis) in updated) edit.putLong(day.toString(), millis)
        check(allowed())
        check(edit.commit())
        return updated.associate { it.key to it.value }
    }

    @Synchronized
    fun clear(space: SpaceContext, allowed: () -> Boolean) {
        check(space.spaceId == "primary")
        check(allowed())
        check(preferences(space).edit().clear().commit())
    }
}
