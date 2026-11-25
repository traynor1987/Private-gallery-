package uk.co.traynor.privategallery.intelligence

import android.app.KeyguardManager
import android.content.Context
import android.os.UserManager
import java.net.URI
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong
import org.json.JSONArray
import org.json.JSONObject
import uk.co.traynor.privategallery.core.brain.NovaBrainConnectionStore
import uk.co.traynor.privategallery.core.guardian.GuardianCapture
import uk.co.traynor.privategallery.core.guardian.GuardianCaptureStore
import uk.co.traynor.privategallery.core.guardian.GuardianInboxRow
import uk.co.traynor.privategallery.core.guardian.GuardianInboxSection
import uk.co.traynor.privategallery.core.guardian.GuardianNoticeKind
import uk.co.traynor.privategallery.core.guardian.GuardianNotificationListener
import uk.co.traynor.privategallery.core.guardian.guardianInboxClock
import uk.co.traynor.privategallery.core.guardian.noticeKind
import uk.co.traynor.privategallery.core.home2.SpaceContext
import uk.co.traynor.privategallery.core.sanctuary.SanctuaryLauncherPolicy
import uk.co.traynor.privategallery.core.sanctuary.SanctuaryMode
import uk.co.traynor.privategallery.home2.NovaCalendarLibrary
import uk.co.traynor.privategallery.home2.NovaCalendarEvent
import uk.co.traynor.privategallery.home2.NovaNote
import uk.co.traynor.privategallery.home2.NovaNotesLibrary
import uk.co.traynor.privategallery.home2.NovaTask
import uk.co.traynor.privategallery.home2.NovaTaskLibrary
import uk.co.traynor.privategallery.home2.calendarReminderSchedule
import uk.co.traynor.privategallery.home2.taskReminderSchedule

internal data class IntelligenceGuardian(
    val retained: Int,
    val safe: Int,
    val held: Int,
    val pending: Int,
    val reviewable: Int,
    val newToday: Int,
    val importantToday: Int,
    val evidencePresent: Boolean,
)

internal fun intelligenceDeviceReady(context: Context): Boolean =
    context.getSystemService(UserManager::class.java)?.isUserUnlocked == true &&
        context.getSystemService(KeyguardManager::class.java)?.isDeviceLocked == false

internal fun intelligencePrivateAllowed(context: Context): Boolean =
    intelligenceDeviceReady(context) && runCatching {
        !SanctuaryLauncherPolicy.launcherProtectionActive(context)
    }.getOrDefault(false)

internal fun intelligenceGuardian(context: Context, today: LocalDate, zone: ZoneId): IntelligenceGuardian {
    check(intelligencePrivateAllowed(context))
    val store = GuardianCaptureStore(context)
    val rows = store.inboxRows(GuardianCapture.retention(context), guardianInboxClock(context))
    val summary = summarizeIntelligenceGuardian(rows, today, zone, store.evidenceStorageBytes() > 0)
    check(intelligencePrivateAllowed(context))
    return summary
}

internal fun summarizeIntelligenceGuardian(
    rows: List<GuardianInboxRow>, today: LocalDate, zone: ZoneId, evidencePresent: Boolean,
): IntelligenceGuardian {
    val todayRows = rows.filter { Instant.ofEpochMilli(it.arrivedAt).atZone(zone).toLocalDate() == today }
    fun GuardianInboxRow.isHeld() = section == GuardianInboxSection.COOLING || section == GuardianInboxSection.PROTECTED
    return IntelligenceGuardian(
        retained = rows.size,
        safe = rows.count { it.section == GuardianInboxSection.READY && it.noticeKind() == GuardianNoticeKind.LOW },
        held = rows.count { it.isHeld() },
        pending = rows.count { it.section == GuardianInboxSection.PENDING && it.assessment == null },
        reviewable = rows.count { !it.reviewed && it.section in setOf(GuardianInboxSection.READY, GuardianInboxSection.PROTECTED) },
        newToday = todayRows.size,
        importantToday = todayRows.count {
            it.noticeKind() in setOf(GuardianNoticeKind.HIGH, GuardianNoticeKind.UPSETTING) || it.isHeld()
        },
        evidencePresent = evidencePresent,
    )
}

internal fun latestHeldIntelligenceEvidence(context: Context): String? {
    check(intelligencePrivateAllowed(context))
    val latest = GuardianCaptureStore(context).inboxRows(GuardianCapture.retention(context), guardianInboxClock(context))
        .filter { it.section == GuardianInboxSection.COOLING || it.section == GuardianInboxSection.PROTECTED }
        .maxByOrNull { it.arrivedAt }?.id
    check(intelligencePrivateAllowed(context))
    return latest
}

internal fun intelligenceGuardianStatus(context: Context): String = when {
    !GuardianCapture.isEnabled(context) -> "Capture is off"
    !GuardianNotificationListener.hasAccess(context) -> "Notification access is off"
    else -> "Capture enabled"
}

internal fun intelligenceSanctuaryStatus(mode: SanctuaryMode, paused: Boolean, remainingMillis: Long? = null): String {
    val state = if (paused) "Launcher protection paused" else "Active"
    val minutes = remainingMillis?.takeIf { it > 0L }?.let { (it + 59_999L) / 60_000L }
    val timeLeft = minutes?.let {
        if (it < 60L) "${it}m left" else "${it / 60}h ${it % 60}m left"
    }
    return listOfNotNull(mode.label, state, timeLeft).joinToString(" · ")
}

internal fun intelligenceConnection(context: Context): String =
    when (val record = NovaBrainConnectionStore(context).readRecord()) {
        null -> "Local commands available · cloud disconnected"
        else -> {
            val host = URI(record.configuration.origin).rawAuthority
            when {
                record.lastAuthenticated == null -> "$host · awaiting authenticated check"
                record.lastAuthenticated.staging -> "$host · last authenticated in staging"
                !record.lastAuthenticated.analysisEnabled -> "$host · last check: cloud analysis disabled"
                else -> "$host · authenticated on last check"
            }
        }
    }

/** Providers must supply their own explicit authorisation. No notification listener fallback. */
internal fun interface IntelligenceEmailProvider {
    fun authorisedSummary(): IntelligenceEmail?
}

internal data class IntelligenceEmail(val providerLabel: String, val importantUnread: Int)
internal val disconnectedIntelligenceEmail = IntelligenceEmailProvider { null }

internal data class IntelligenceAgenda(
    val tasks: List<String>,
    val events: List<String>,
    val reminders: List<String>,
    val unscheduledTasks: List<String> = emptyList(),
)

internal fun buildIntelligenceAgenda(
    tasks: List<NovaTask>,
    events: List<NovaCalendarEvent>,
    from: LocalDate,
    until: LocalDate,
    zone: ZoneId,
    includeOverdue: Boolean,
    upcomingFrom: Instant? = null,
): IntelligenceAgenda {
    require(until.isAfter(from) && until.toEpochDay() - from.toEpochDay() <= 7)
    val openTasks = tasks.filterNot { it.completed }
    val datedTasks = openTasks.asSequence().filter {
        val due = it.due
        due != null && due < until && (includeOverdue || due >= from)
    }.sortedBy { it.due }.take(8).map { "${it.title} · ${it.due?.format(IntelligenceWorkspace.dateFormat)}" }.toList()
    val unscheduledTasks = if (includeOverdue) openTasks.asSequence().filter { it.due == null }
        .take(8).map { it.title }.toList() else emptyList()

    val upcomingOccurrences = generateSequence(from) { it.plusDays(1) }.takeWhile { it < until }
        .flatMap { day ->
            events.asSequence().flatMap { it.occurrencesOn(day, zone) }.filter { occurrence ->
                val start = occurrence.start
                upcomingFrom == null || start == null || start.plusSeconds(occurrence.event.minutes * 60L) > upcomingFrom
            }.sortedBy { it.start }.map { day to it }
        }.distinctBy { (_, occurrence) -> occurrence.event.id to occurrence.sourceDate }
    val eventLines = upcomingOccurrences.take(8).map { (day, occurrence) ->
        val start = occurrence.start?.atZone(zone)?.format(IntelligenceWorkspace.timeFormat) ?: "All day"
        "${occurrence.event.title} · ${day.format(IntelligenceWorkspace.dateFormat)} · $start"
    }.toList()

    val reminderStart = maxOf(from.atStartOfDay(zone).toInstant(), upcomingFrom ?: Instant.MIN)
    val reminderEnd = until.atStartOfDay(zone).toInstant()
    val reminderSchedules = openTasks.asSequence().mapNotNull { task ->
        taskReminderSchedule(task)?.let { task.title to it }
    } + events.asSequence().mapNotNull { event ->
        calendarReminderSchedule(event)?.let { event.title to it }
    }
    val reminderLines = reminderSchedules.flatMap { (title, schedule) ->
        generateSequence(schedule.nextAfter(reminderStart.minusNanos(1), zone)) { schedule.nextAfter(it, zone) }
            .takeWhile { it < reminderEnd }.take(4).map { title to it }
    }.sortedBy { (_, at) -> at }.take(4).map { (title, at) ->
        val time = at.atZone(zone)
        "$title · ${time.format(IntelligenceWorkspace.dateFormat)} · ${time.format(IntelligenceWorkspace.timeFormat)}"
    }.toList()
    return IntelligenceAgenda(datedTasks, eventLines, reminderLines, unscheduledTasks)
}

internal class IntelligenceWorkspace(private val space: SpaceContext, private val foreground: () -> Boolean = { true }) {
    private val tasks = NovaTaskLibrary(space)
    private val calendar = NovaCalendarLibrary(space)
    private val notes = NovaNotesLibrary(space)

    fun permitted() = space.spaceId == "primary" && foreground() && space.current() && intelligencePrivateAllowed(space.baseContext)
    private fun verify() = check(permitted()) { "The private briefing is locked or paused by Sanctuary." }

    fun agenda(
        from: LocalDate,
        until: LocalDate,
        zone: ZoneId,
        includeOverdue: Boolean,
        upcomingFrom: Instant? = null,
    ): IntelligenceAgenda {
        verify()
        val result = buildIntelligenceAgenda(tasks.read().tasks, calendar.read().events, from, until, zone,
            includeOverdue, upcomingFrom)
        verify()
        return result
    }

    fun briefingName(): String? {
        verify()
        val name = space.getSharedPreferences(BRIEFING, Context.MODE_PRIVATE).getString("name", null)
        verify()
        return name
    }

    fun saveBriefingName(name: String?) {
        verify()
        val trimmed = name?.trim()?.takeIf(String::isNotEmpty)
        require(trimmed == null || trimmed.length <= BRIEFING_NAME_LIMIT && trimmed.none { it.isISOControl() })
        val edit = space.getSharedPreferences(BRIEFING, Context.MODE_PRIVATE).edit()
        if (trimmed == null) edit.remove("name") else edit.putString("name", trimmed)
        verify()
        check(edit.commit())
    }

    fun createTask(id: String, title: String, due: LocalDate?) {
        verify()
        require(title.isNotBlank() && title.length <= 200)
        val snapshot = tasks.read()
        if (snapshot.tasks.any { it.id == id }) return
        verify()
        checkNotNull(tasks.save(snapshot, snapshot.tasks + NovaTask(id, title, due = due)))
    }

    fun createNote(id: String, title: String, body: String) {
        verify()
        require(title.isNotBlank() && title.length <= 200 && body.length <= 2000)
        val snapshot = notes.read()
        if (snapshot.notes.any { it.id == id }) return
        verify()
        checkNotNull(notes.save(snapshot, snapshot.notes + NovaNote(id, title, body)))
    }

    fun find(query: String): List<Pair<IntelligenceDestination, String>> {
        verify()
        require(query.isNotBlank())
        val notebook = notes.read()
        val matches = (notebook.notes.asSequence().filter {
            !notebook.isProtected(it) && it.title.contains(query, ignoreCase = true)
        }.take(4).map { IntelligenceDestination.NOTES to it.title } +
            tasks.read().tasks.asSequence().filter { it.title.contains(query, ignoreCase = true) }
                .take(4).map { IntelligenceDestination.TASKS to it.title } +
            calendar.read().events.asSequence().filter { it.title.contains(query, ignoreCase = true) }
                .take(4).map { IntelligenceDestination.CALENDAR to it.title }).toList()
        verify()
        return matches
    }

    fun memory(): IntelligenceMemory = synchronized(MEMORY_LOCK) {
        verify()
        val prefs = space.getSharedPreferences(MEMORY, Context.MODE_PRIVATE)
        val raw = prefs.getString("entries", null)
            ?: return@synchronized IntelligenceMemory(false, emptyList())
        val rows = JSONArray(raw)
        require(rows.length() <= HISTORY_LIMIT)
        val entries = List(rows.length()) {
            val row = rows.getJSONObject(it)
            IntelligenceExchange(row.getString("question"), row.getString("answer"), row.getBoolean("cloud"),
                row.optString("timestamp").takeIf(String::isNotBlank)?.let(Instant::parse), row.optBoolean("ownerPreview"))
        }
        verify()
        IntelligenceMemory(true, entries, autoSave = prefs.getBoolean("autoSave", false))
    }

    fun saveMemory(memory: IntelligenceMemory) = synchronized(MEMORY_LOCK) {
        MEMORY_GENERATION.incrementAndGet()
        writeMemory(memory) { true }
        Unit
    }

    fun memoryGeneration(): Long = MEMORY_GENERATION.get()

    fun saveAutomaticMemory(exchanges: List<IntelligenceExchange>, generation: Long, permitted: () -> Boolean): Boolean =
        synchronized(MEMORY_LOCK) {
            verify()
            if (generation != MEMORY_GENERATION.get() || !permitted() ||
                !space.getSharedPreferences(MEMORY, Context.MODE_PRIVATE).getBoolean("autoSave", false)) {
                return@synchronized false
            }
            writeMemory(IntelligenceMemory(true, exchanges, autoSave = true), permitted)
        }

    private fun writeMemory(memory: IntelligenceMemory, permitted: () -> Boolean): Boolean {
        verify()
        val edit = space.getSharedPreferences(MEMORY, Context.MODE_PRIVATE).edit()
        if (!memory.enabled) edit.remove("entries").remove("autoSave")
        else edit.putString("entries", JSONArray(memory.exchanges.takeLast(HISTORY_LIMIT).map {
            JSONObject().put("question", it.question).put("answer", it.answer).put("cloud", it.cloud)
                .put("timestamp", it.timestamp?.toString()).put("ownerPreview", it.ownerPreview)
        }).toString()).putBoolean("autoSave", memory.autoSave)
        verify()
        if (!permitted()) return false
        check(edit.commit())
        return true
    }

    companion object {
        const val HISTORY_LIMIT = 24
        const val BRIEFING_NAME_LIMIT = 40
        private const val BRIEFING = "nova_home_intelligence_briefing_v1"
        private const val MEMORY = "nova_home_intelligence_memory_v1"
        private val MEMORY_LOCK = Any()
        private val MEMORY_GENERATION = AtomicLong()
        val dateFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
        val timeFormat = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
        fun newRecordId() = UUID.randomUUID().toString()
    }
}

internal data class IntelligenceMemory(
    val enabled: Boolean,
    val exchanges: List<IntelligenceExchange>,
    val autoSave: Boolean = false,
) {
    init { require(enabled || !autoSave) }
}
