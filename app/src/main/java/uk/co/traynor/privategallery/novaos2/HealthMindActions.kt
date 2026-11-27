package uk.co.traynor.privategallery.novaos2

import android.net.Uri
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject
import uk.co.traynor.privategallery.core.home2.SpaceContext
import uk.co.traynor.privategallery.core.sanctuary.SanctuaryHistoryEntry
import uk.co.traynor.privategallery.home2.MindEntry
import uk.co.traynor.privategallery.home2.NovaCalendarEvent
import uk.co.traynor.privategallery.home2.NovaCalendarLibrary
import uk.co.traynor.privategallery.home2.NovaEventRepeat
import uk.co.traynor.privategallery.home2.NovaReminders

private val routineId = UUID.nameUUIDFromBytes("novaos-health-sleep-routine-v1".encodeToByteArray()).toString()

/** Health creates one explicit local Calendar event and uses the existing encrypted reminder scheduler. */
internal class HealthSleepRoutine(private val space: SpaceContext) {
    private val calendar = NovaCalendarLibrary(space)

    fun time(): LocalTime? = calendar.read().events.find { it.id == routineId }?.time

    fun setTime(time: LocalTime?, through: LocalDate, zone: ZoneId): Boolean {
        val before = calendar.read()
        val retained = before.events.filterNot { it.id == routineId }.map { event ->
            if (event.originalOccurrence?.seriesId == routineId) event.copy(originalOccurrence = null) else event
        }
        val after = if (time == null) retained else retained + NovaCalendarEvent(
            id = routineId,
            title = "Sleep routine",
            description = "",
            firstDate = through,
            time = time,
            zone = zone,
            minutes = 1,
            repeat = NovaEventRepeat.DAILY,
            reminderMinutes = 0,
        )
        val saved = calendar.save(before, after) ?: error("Calendar changed before the reminder could be saved")
        return runCatching { NovaReminders.syncCalendar(space, saved.events) }.isSuccess
    }
}

internal data class HealthSanctuaryWeek(val sessions: Int, val minutes: Long)

internal fun healthSanctuaryWeek(
    history: List<SanctuaryHistoryEntry>, through: LocalDate, now: Instant, zone: ZoneId,
): HealthSanctuaryWeek {
    val start = through.minusDays(6).atStartOfDay(zone).toInstant().toEpochMilli()
    val end = minOf(through.plusDays(1).atStartOfDay(zone).toInstant(), now).toEpochMilli()
    val overlapping = history.mapNotNull { entry ->
        val overlap = minOf(entry.endedAt, end) - maxOf(entry.startedAt, start)
        overlap.takeIf { it > 0 }
    }
    return HealthSanctuaryWeek(overlapping.size, overlapping.sum() / 60_000)
}

/** Exports exactly the explicitly selected local Primary data through the owner-selected document. */
internal fun writeHealthExport(
    space: SpaceContext,
    uri: Uri,
    records: HealthRecords,
    entries: List<MindEntry>,
    allowed: () -> Boolean,
) {
    require(uri.scheme == "content" && space.spaceId == "primary")
    val payload = healthExportJson(records, entries).encodeToByteArray()
    try {
        check(space.current() && allowed())
        val resolver = space.baseContext.contentResolver
        checkNotNull(resolver.openOutputStream(uri, "wt")).use { stream ->
            var offset = 0
            while (offset < payload.size) {
                check(space.current() && allowed())
                val count = minOf(8192, payload.size - offset)
                stream.write(payload, offset, count)
                offset += count
            }
        }
    } finally {
        payload.fill(0)
    }
}

internal fun healthExportJson(records: HealthRecords, entries: List<MindEntry>): String = JSONObject()
    .put("schema", 1)
    .put("health", JSONObject(HealthRecordCodec.encode(records)))
    .put("mind", JSONArray(entries.map { entry ->
        JSONObject().put("id", entry.id).put("recordedAt", Instant.ofEpochMilli(entry.time).toString())
            .put("type", if (entry.journal) "journal" else "mood")
            .put("mood", entry.mood).put("text", entry.text)
    })).toString()
