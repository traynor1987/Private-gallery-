package uk.co.traynor.privategallery.home2

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import org.json.JSONArray
import org.json.JSONObject
import uk.co.traynor.privategallery.core.home2.NovaCoreApp
import uk.co.traynor.privategallery.core.home2.NovaCoreAppDocuments
import uk.co.traynor.privategallery.core.home2.SpaceContext

internal enum class NovaEventRepeat(val label: String) {
    NONE("Does not repeat"), DAILY("Daily"), WEEKLY("Weekly"), MONTHLY("Monthly"), YEARLY("Yearly"),
}

internal data class NovaCalendarEvent(
    val id: String,
    val title: String,
    val description: String,
    val firstDate: LocalDate,
    val time: LocalTime?,
    val zone: ZoneId,
    val minutes: Int,
    val repeat: NovaEventRepeat,
    val skippedDates: Set<LocalDate> = emptySet(),
    val noteId: String? = null,
) {
    init {
        require(minutes in 1..1440)
    }

    private fun repeatsOn(date: LocalDate): Boolean {
        if (date < firstDate || date in skippedDates) return false
        return when (repeat) {
            NovaEventRepeat.NONE -> date == firstDate
            NovaEventRepeat.DAILY -> true
            NovaEventRepeat.WEEKLY -> date.dayOfWeek == firstDate.dayOfWeek
            NovaEventRepeat.MONTHLY -> {
                val months = ChronoUnit.MONTHS.between(YearMonth.from(firstDate), YearMonth.from(date))
                firstDate.plusMonths(months) == date
            }
            NovaEventRepeat.YEARLY -> firstDate.plusYears((date.year - firstDate.year).toLong()) == date
        }
    }

    fun occurrencesOn(day: LocalDate, displayZone: ZoneId): List<NovaOccurrence> {
        if (time == null) {
            return if (repeatsOn(day)) listOf(NovaOccurrence(this, day, null)) else emptyList()
        }
        val startOfDay = day.atStartOfDay(displayZone).toInstant()
        val nextDay = day.plusDays(1).atStartOfDay(displayZone).toInstant()
        var sourceDate = startOfDay.minusSeconds(minutes * 60L).atZone(zone).toLocalDate()
        val lastSourceDate = nextDay.atZone(zone).toLocalDate()
        return buildList {
            while (sourceDate <= lastSourceDate) {
                if (repeatsOn(sourceDate)) {
                    val start = sourceDate.atTime(time).atZone(zone).toInstant()
                    if (start < nextDay && start.plusSeconds(minutes * 60L) > startOfDay) {
                        add(NovaOccurrence(this@NovaCalendarEvent, sourceDate, start))
                    }
                }
                sourceDate = sourceDate.plusDays(1)
            }
        }
    }
}

internal data class NovaOccurrence(val event: NovaCalendarEvent, val sourceDate: LocalDate, val start: Instant?)

internal class NovaCalendarLibrary(space: SpaceContext) {
    private val documents = NovaCoreAppDocuments(space, NovaCoreApp.CALENDAR)

    data class Snapshot(val revision: Long?, val events: List<NovaCalendarEvent>)

    fun read(): Snapshot {
        val document = documents.read("library") ?: return Snapshot(null, emptyList())
        val entries = JSONObject(document.json).getJSONArray("events")
        return Snapshot(document.revision, List(entries.length()) { index ->
            val event = entries.getJSONObject(index)
            NovaCalendarEvent(
                event.getString("id"),
                event.getString("title"),
                event.getString("description"),
                LocalDate.parse(event.getString("firstDate")),
                event.getString("time").takeIf(String::isNotEmpty)?.let { LocalTime.parse(it) },
                ZoneId.of(event.getString("zone")),
                event.getInt("minutes"),
                NovaEventRepeat.valueOf(event.getString("repeat")),
                event.optJSONArray("skippedDates")?.let { dates ->
                    (0 until dates.length()).map { LocalDate.parse(dates.getString(it)) }.toSet()
                }.orEmpty(),
                event.optString("noteId").takeIf(String::isNotBlank),
            )
        })
    }

    fun save(previous: Snapshot, events: List<NovaCalendarEvent>): Snapshot? {
        val entries = JSONArray()
        for (event in events) {
            entries.put(JSONObject()
                .put("id", event.id)
                .put("title", event.title)
                .put("description", event.description)
                .put("firstDate", event.firstDate.toString())
                .put("time", event.time?.toString().orEmpty())
                .put("zone", event.zone.id)
                .put("minutes", event.minutes)
                .put("repeat", event.repeat.name)
                .apply {
                    event.noteId?.let { put("noteId", it) }
                    if (event.skippedDates.isNotEmpty()) put("skippedDates", JSONArray(event.skippedDates.sorted().map(LocalDate::toString)))
                })
        }
        val saved = documents.save("library", previous.revision,
            JSONObject().put("events", entries).toString()) ?: return null
        return Snapshot(saved.revision, events.toList())
    }
}
