package uk.co.traynor.privategallery.intelligence

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.time.LocalTime
import java.time.ZonedDateTime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import uk.co.traynor.privategallery.home2.NovaReminderReceiver

internal data class IntelligenceBriefingTimes(
    val morning: LocalTime? = null,
    val evening: LocalTime? = null,
    val afterWork: LocalTime? = null,
) {
    fun windows() = listOfNotNull(
        morning?.let { "Morning" to it },
        evening?.let { "Evening" to it },
        afterWork?.let { "After work" to it },
    )
}

internal fun intelligenceBriefingWindows(now: ZonedDateTime, times: IntelligenceBriefingTimes) =
    times.windows().map { (label, time) -> label to now.toLocalDate().atTime(time).atZone(now.zone) }

internal object IntelligenceBriefings {
    const val ALARM = "uk.co.traynor.privategallery.INTELLIGENCE_BRIEFING_ALARM"
    private const val REQUEST = 81104
    private val changed = MutableStateFlow(0L)
    val revision = changed.asStateFlow()
    private val lock = Any()

    private fun preferences(context: Context) = context.getSharedPreferences("nova_intelligence_briefings", Context.MODE_PRIVATE)

    fun read(context: Context): IntelligenceBriefingTimes = preferences(context).let { prefs ->
        fun time(key: String) = prefs.getString(key, null)?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
        IntelligenceBriefingTimes(time("morning"), time("evening"), time("after_work"))
    }

    fun save(context: Context, times: IntelligenceBriefingTimes) = synchronized(lock) {
        check(preferences(context).edit().putString("morning", times.morning?.toString())
            .putString("evening", times.evening?.toString()).putString("after_work", times.afterWork?.toString()).commit())
        refresh(context)
    }

    /** The alarm refreshes an in-app card. No push, alert, history access or model call. */
    fun refresh(context: Context) = synchronized(lock) {
        val now = ZonedDateTime.now()
        val next = intelligenceBriefingWindows(now, read(context)).minOfOrNull { (_, at) ->
            if (at > now) at else at.plusDays(1)
        }
        val alarms = context.getSystemService(AlarmManager::class.java)
        val action = Intent(context, NovaReminderReceiver::class.java).setAction(ALARM)
        val flags = PendingIntent.FLAG_IMMUTABLE or if (next == null) PendingIntent.FLAG_NO_CREATE else 0
        PendingIntent.getBroadcast(context, REQUEST, action, flags)?.let { alarm ->
            if (next == null) {
                alarms.cancel(alarm)
                alarm.cancel()
            } else {
                // Non-wakeup and inexact. A sleeping device can wait until the owner returns.
                alarms.set(AlarmManager.RTC, next.toInstant().toEpochMilli(), alarm)
            }
        }
        changed.value++
    }

    fun ready(context: Context, now: ZonedDateTime): String? {
        if (!intelligencePrivateAllowed(context)) return null
        return intelligenceBriefingWindows(now, read(context)).filter { (_, at) ->
            at.toInstant() <= now.toInstant() && at.toInstant() >= now.toInstant().minusSeconds(4 * 3600)
        }.maxByOrNull { it.second.toInstant() }?.first
    }
}
