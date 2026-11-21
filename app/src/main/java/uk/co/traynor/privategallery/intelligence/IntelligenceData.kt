package uk.co.traynor.privategallery.intelligence

import android.app.KeyguardManager
import android.content.Context
import android.os.UserManager
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.UUID
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
import uk.co.traynor.privategallery.home2.NovaCalendarLibrary
import uk.co.traynor.privategallery.home2.NovaNote
import uk.co.traynor.privategallery.home2.NovaNotesLibrary
import uk.co.traynor.privategallery.home2.NovaTask
import uk.co.traynor.privategallery.home2.NovaTaskLibrary

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
    check(intelligenceDeviceReady(context))
    val store = GuardianCaptureStore(context)
    val rows = store.inboxRows(GuardianCapture.retention(context), guardianInboxClock(context))
    return summarizeIntelligenceGuardian(rows, today, zone, store.evidenceStorageBytes() > 0)
}

internal fun summarizeIntelligenceGuardian(
    rows: List<GuardianInboxRow>, today: LocalDate, zone: ZoneId, evidencePresent: Boolean,
): IntelligenceGuardian {
    val todayRows = rows.filter { Instant.ofEpochMilli(it.arrivedAt).atZone(zone).toLocalDate() == today }
    return IntelligenceGuardian(
        retained = rows.size,
        safe = rows.count { it.noticeKind() == GuardianNoticeKind.LOW },
        held = rows.count { it.section in setOf(GuardianInboxSection.COOLING, GuardianInboxSection.PROTECTED) },
        pending = rows.count { it.section == GuardianInboxSection.PENDING && it.assessment == null },
        reviewable = rows.count { !it.reviewed && it.section in setOf(GuardianInboxSection.READY, GuardianInboxSection.PROTECTED) },
        newToday = todayRows.size,
        importantToday = todayRows.count {
            it.noticeKind() in setOf(GuardianNoticeKind.HIGH, GuardianNoticeKind.UPSETTING) ||
                it.section == GuardianInboxSection.COOLING || it.section == GuardianInboxSection.PROTECTED
        },
        evidencePresent = evidencePresent,
    )
}

internal fun latestHeldIntelligenceEvidence(context: Context): String? {
    check(intelligencePrivateAllowed(context))
    return GuardianCaptureStore(context).inboxRows(GuardianCapture.retention(context), guardianInboxClock(context))
        .filter { it.section == GuardianInboxSection.COOLING || it.section == GuardianInboxSection.PROTECTED }
        .maxByOrNull { it.arrivedAt }?.id
}

internal fun intelligenceGuardianStatus(context: Context): String = when {
    !GuardianCapture.isEnabled(context) -> "Capture is off"
    !GuardianNotificationListener.hasAccess(context) -> "Notification access is off"
    else -> "Capture enabled"
}

internal fun intelligenceConnection(context: Context): String =
    when (val record = NovaBrainConnectionStore(context).readRecord()) {
        null -> "Local Nova Brain · cloud disconnected"
        else -> when {
            record.lastAuthenticated == null -> "Cloud configured · awaiting authenticated check"
            record.lastAuthenticated.staging -> "Last authenticated on staging · local assistant"
            !record.lastAuthenticated.analysisEnabled -> "Last authenticated · cloud analysis disabled"
            else -> "Cloud configured · authentication checked on request"
        }
    }

/** Providers must supply their own explicit authorisation. No notification listener fallback. */
internal fun interface IntelligenceEmailProvider {
    fun authorisedSummary(): IntelligenceEmail?
}

internal data class IntelligenceEmail(val providerLabel: String, val importantUnread: Int)
internal val disconnectedIntelligenceEmail = IntelligenceEmailProvider { null }

internal data class IntelligenceAgenda(val tasks: List<String>, val events: List<String>, val reminders: List<String>)

internal class IntelligenceWorkspace(private val space: SpaceContext, private val foreground: () -> Boolean = { true }) {
    private val tasks = NovaTaskLibrary(space)
    private val calendar = NovaCalendarLibrary(space)
    private val notes = NovaNotesLibrary(space)

    fun permitted() = space.spaceId == "primary" && foreground() && space.current() && intelligencePrivateAllowed(space.baseContext)
    private fun verify() = check(permitted()) { "The private briefing is locked or paused by Sanctuary." }

    fun agenda(from: LocalDate, until: LocalDate, zone: ZoneId, includeOverdue: Boolean): IntelligenceAgenda {
        verify()
        require(until.isAfter(from) && until.toEpochDay() - from.toEpochDay() <= 7)
        val openTasks = tasks.read().tasks.asSequence().filter {
            val due = it.due
            !it.completed && due != null && due < until && (includeOverdue || due >= from)
        }.sortedBy { it.due }.toList()
        val upcoming = openTasks.take(8).map { "${it.title} · ${it.due?.format(dateFormat)}" }
        val allEvents = calendar.read().events
        val upcomingOccurrences = generateSequence(from) { it.plusDays(1) }.takeWhile { it < until }
            .flatMap { day ->
                allEvents.flatMap { it.occurrencesOn(day, zone) }.sortedBy { it.start }
                    .asSequence().map { day to it }
            }.distinctBy { (_, occurrence) -> occurrence.event.id to occurrence.sourceDate }
        val occurrences = upcomingOccurrences.take(8).map { (day, occurrence) ->
            val start = occurrence.start?.atZone(zone)?.format(timeFormat) ?: "All day"
            "${occurrence.event.title} · ${day.format(dateFormat)} · $start"
        }.toList()
        val reminders = openTasks.asSequence().mapNotNull { task ->
            task.remindAt?.let { "${task.title} · ${task.due?.format(dateFormat)} · ${it.format(timeFormat)}" }
        }.take(4).toList() + upcomingOccurrences.filter { it.second.event.reminderMinutes != null }
            .take(4).map { (day, occurrence) -> "${occurrence.event.title} · ${day.format(dateFormat)}" }.toList()
        verify()
        return IntelligenceAgenda(upcoming, occurrences, reminders)
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

    fun memory(): IntelligenceMemory {
        verify()
        val prefs = space.getSharedPreferences(MEMORY, Context.MODE_PRIVATE)
        val raw = prefs.getString("entries", null) ?: return IntelligenceMemory(false, emptyList())
        val rows = JSONArray(raw)
        require(rows.length() <= HISTORY_LIMIT)
        val entries = List(rows.length()) {
            val row = rows.getJSONObject(it)
            IntelligenceExchange(row.getString("question"), row.getString("answer"), row.getBoolean("cloud"))
        }
        verify()
        return IntelligenceMemory(true, entries)
    }

    fun saveMemory(memory: IntelligenceMemory) {
        verify()
        val edit = space.getSharedPreferences(MEMORY, Context.MODE_PRIVATE).edit()
        if (!memory.enabled) edit.remove("entries")
        else edit.putString("entries", JSONArray(memory.exchanges.takeLast(HISTORY_LIMIT).map {
            JSONObject().put("question", it.question).put("answer", it.answer).put("cloud", it.cloud)
        }).toString())
        verify()
        check(edit.commit())
    }

    companion object {
        const val HISTORY_LIMIT = 24
        private const val MEMORY = "nova_home_intelligence_memory_v1"
        val dateFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
        val timeFormat = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
        fun newRecordId() = UUID.randomUUID().toString()
    }
}

internal data class IntelligenceMemory(val enabled: Boolean, val exchanges: List<IntelligenceExchange>)
