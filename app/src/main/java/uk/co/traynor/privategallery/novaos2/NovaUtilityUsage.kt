package uk.co.traynor.privategallery.novaos2

import android.app.usage.UsageEvents
import android.app.usage.UsageEventsQuery
import android.app.usage.UsageStatsManager
import android.content.Context
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import uk.co.traynor.privategallery.core.sanctuary.SanctuaryGuardian

internal enum class NovaUsageEventKind { RESUMED, PAUSED, SCREEN_ON, SCREEN_OFF, LOCKED, UNLOCKED, RESET }

internal data class NovaUsageEvent(
    val at: Long,
    val kind: NovaUsageEventKind,
    val packageName: String = "",
    val activity: String = "",
)

internal data class NovaUsageReport(
    val appMillis: Map<String, Long>,
    val dailyMillis: Map<LocalDate, Long>,
)

internal data class NovaUsageExclusion(val start: Long, val end: Long)

/** Observed unlocked intervals. Daily totals count overlapping Fold panes only once. */
internal fun aggregateNovaUsage(
    events: Sequence<NovaUsageEvent>,
    periodStart: Long,
    periodEnd: Long,
    apps: Set<String>,
    zone: ZoneId,
    protectedIntervals: List<NovaUsageExclusion> = emptyList(),
): NovaUsageReport {
    require(periodStart in 0L..periodEnd)
    var interactive = false
    var unlocked = false
    var cursor = periodStart
    val showing = mutableMapOf<String, MutableSet<String>>()
    val appMillis = mutableMapOf<String, Long>()
    val dailyMillis = mutableMapOf<LocalDate, Long>()
    val excluded = protectedIntervals.filter { it.end > it.start }.sortedBy { it.start }
    var exclusionIndex = 0

    fun add(start: Long, end: Long) {
        if (end <= start) return
        for (app in showing.keys) appMillis[app] = (appMillis[app] ?: 0L) + end - start
        var part = start
        while (part < end) {
            val day = Instant.ofEpochMilli(part).atZone(zone).toLocalDate()
            val boundary = minOf(end, day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli())
            dailyMillis[day] = (dailyMillis[day] ?: 0L) + boundary - part
            part = boundary
        }
    }

    fun advance(end: Long) {
        if (interactive && unlocked && showing.isNotEmpty() && end > cursor) {
            var part = cursor
            while (exclusionIndex < excluded.size && excluded[exclusionIndex].end <= cursor) exclusionIndex++
            for (index in exclusionIndex until excluded.size) {
                val interval = excluded[index]
                if (interval.start >= end) break
                add(part, interval.start)
                part = maxOf(part, interval.end)
                if (part >= end) break
            }
            add(part, end)
        }
        cursor = end
    }

    for (event in events) {
        if (event.at >= periodEnd) break
        advance(maxOf(periodStart, cursor, event.at))
        when (event.kind) {
            NovaUsageEventKind.RESUMED -> if (interactive && unlocked && event.packageName in apps) {
                showing.getOrPut(event.packageName) { mutableSetOf() }.add(event.activity)
            }
            NovaUsageEventKind.PAUSED -> {
                val activities = showing[event.packageName] ?: continue
                activities.remove(event.activity)
                if (activities.isEmpty()) showing.remove(event.packageName)
            }
            NovaUsageEventKind.SCREEN_ON -> interactive = true
            NovaUsageEventKind.SCREEN_OFF -> {
                interactive = false
                showing.clear()
            }
            NovaUsageEventKind.LOCKED -> {
                unlocked = false
                showing.clear()
            }
            NovaUsageEventKind.UNLOCKED -> unlocked = true
            NovaUsageEventKind.RESET -> {
                interactive = false
                unlocked = false
                showing.clear()
            }
        }
    }
    advance(periodEnd)
    return NovaUsageReport(appMillis, dailyMillis)
}

internal suspend fun queryNovaUsage(
    context: Context,
    start: Long,
    end: Long,
    apps: Set<String>,
    zone: ZoneId,
    protectedIntervals: List<NovaUsageExclusion>,
): NovaUsageReport? {
    if (!SanctuaryGuardian.hasUsageAccess(context)) return null
    val query = UsageEventsQuery.Builder(maxOf(0, start - 86_400_000L), end).setEventTypes(
        UsageEvents.Event.ACTIVITY_RESUMED, UsageEvents.Event.ACTIVITY_PAUSED, UsageEvents.Event.ACTIVITY_STOPPED,
        UsageEvents.Event.SCREEN_INTERACTIVE, UsageEvents.Event.SCREEN_NON_INTERACTIVE,
        UsageEvents.Event.KEYGUARD_SHOWN, UsageEvents.Event.KEYGUARD_HIDDEN,
        UsageEvents.Event.DEVICE_STARTUP, UsageEvents.Event.DEVICE_SHUTDOWN,
    ).build()
    val events = context.getSystemService(UsageStatsManager::class.java)?.queryEvents(query) ?: return null
    val coroutine = currentCoroutineContext()
    var seen = 0
    val observed = sequence {
        val event = UsageEvents.Event()
        while (events.hasNextEvent() && events.getNextEvent(event)) {
            if (++seen > 100_000) break
            if (seen % 128 == 0) coroutine.ensureActive()
            val kind = when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> NovaUsageEventKind.RESUMED
                UsageEvents.Event.ACTIVITY_PAUSED, UsageEvents.Event.ACTIVITY_STOPPED -> NovaUsageEventKind.PAUSED
                UsageEvents.Event.SCREEN_INTERACTIVE -> NovaUsageEventKind.SCREEN_ON
                UsageEvents.Event.SCREEN_NON_INTERACTIVE -> NovaUsageEventKind.SCREEN_OFF
                UsageEvents.Event.KEYGUARD_SHOWN -> NovaUsageEventKind.LOCKED
                UsageEvents.Event.KEYGUARD_HIDDEN -> NovaUsageEventKind.UNLOCKED
                UsageEvents.Event.DEVICE_STARTUP, UsageEvents.Event.DEVICE_SHUTDOWN -> NovaUsageEventKind.RESET
                else -> continue
            }
            yield(NovaUsageEvent(event.timeStamp, kind, event.packageName.orEmpty(), event.className.orEmpty()))
        }
    }
    val report = aggregateNovaUsage(observed, start, end, apps, zone, protectedIntervals)
    return report.takeIf { seen <= 100_000 && SanctuaryGuardian.hasUsageAccess(context) }
}

internal fun utilityDuration(milliseconds: Long): String {
    if (milliseconds <= 0) return "0 min"
    val minutes = milliseconds / 60_000L
    return when {
        minutes == 0L -> "Under 1 min"
        minutes < 60L -> "$minutes min"
        else -> "${minutes / 60L} h ${minutes % 60L} min"
    }
}
