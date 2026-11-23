package uk.co.traynor.privategallery.intelligence

import java.time.LocalDate
import java.util.Locale

internal sealed interface IntelligenceCommand {
    data class Briefing(val day: LocalDate) : IntelligenceCommand
    data object Notifications : IntelligenceCommand
    data object Held : IntelligenceCommand
    data object Inbox : IntelligenceCommand
    data class Open(val name: String) : IntelligenceCommand
    data class CreateTask(val title: String, val due: LocalDate?) : IntelligenceCommand
    data class CreateNote(val title: String, val body: String) : IntelligenceCommand
    data class Find(val query: String) : IntelligenceCommand
    data object LatestHeldEvidence : IntelligenceCommand
    data class Evidence(val id: String) : IntelligenceCommand
    data class Ask(val question: String) : IntelligenceCommand
    data class Clarify(val instruction: String, val taskDue: LocalDate? = null) : IntelligenceCommand
}

private val tomorrowTask = Regex("""(?:create|add) (?:a )?task(?=\s|:|$)(?: for (today|tomorrow))?(?::\s*|\s+)?(.*)""", RegexOption.IGNORE_CASE)
private val taskDateSuffix = Regex("""^(.+?)(?: (?:for )?(today|tomorrow))$""", RegexOption.IGNORE_CASE)
private val createNote = Regex("""(?:create|add) (?:a )?note(?=\s|:|$)(?::\s*|\s+)?(.*)""", RegexOption.IGNORE_CASE)
private val evidenceId = Regex("[a-fA-F0-9]{64}")

internal fun intelligenceCommand(input: String, today: LocalDate): IntelligenceCommand {
    val text = input.trim()
    val normal = text.lowercase(Locale.ROOT).replace('’', '\'').trimEnd('?', '.', '!')
    fun due(day: String?) = when (day?.lowercase(Locale.ROOT)) {
        "today" -> today
        "tomorrow" -> today.plusDays(1)
        else -> null
    }
    return when {
        normal in setOf("what have i missed today", "what's on today", "whats on today", "summarise my day", "summarize my day", "today", "briefing") ->
            IntelligenceCommand.Briefing(today)
        normal in setOf("what's on tomorrow", "whats on tomorrow", "what is on tomorrow", "tomorrow") ->
            IntelligenceCommand.Briefing(today.plusDays(1))
        normal in setOf("any important messages", "important messages", "notifications", "safe notifications") ->
            IntelligenceCommand.Notifications
        normal in setOf("show my held messages", "held messages", "show held messages") -> IntelligenceCommand.Held
        normal in setOf("guardian inbox", "open guardian inbox") -> IntelligenceCommand.Inbox
        normal in setOf("open latest held evidence", "open newest held evidence") -> IntelligenceCommand.LatestHeldEvidence
        normal.startsWith("open evidence ") -> {
            val id = normal.substringAfterLast(' ')
            if (evidenceId.matches(id)) IntelligenceCommand.Evidence(id)
            else IntelligenceCommand.Clarify("To open a Guardian evidence record, use its record ID.")
        }
        normal.startsWith("open ") -> IntelligenceCommand.Open(text.substring(5).trim().trimEnd('.', '!', '?'))
        tomorrowTask.matches(text) -> {
            val match = tomorrowTask.matchEntire(text)!!
            val title = match.groupValues[2].trim()
            val hasTitle = title.any { it.isLetterOrDigit() || !it.isWhitespace() && it !in ".!?" }
            val suffix = taskDateSuffix.matchEntire(title)
            val date = due(match.groups[1]?.value) ?: due(suffix?.groupValues?.get(2))
            val taskTitle = (suffix?.groupValues?.get(1) ?: title).trim()
            if (hasTitle && taskTitle.isNotBlank()) IntelligenceCommand.CreateTask(taskTitle.take(200), date)
            else IntelligenceCommand.Clarify("Add the task name, for example “Create a task for tomorrow: Call the garage”.", date)
        }
        createNote.matches(text) -> {
            val content = createNote.matchEntire(text)!!.groupValues[1].trim()
            if (content.isBlank()) IntelligenceCommand.Clarify("Add a title, for example “Create a note: Weekend ideas | Book a table”.")
            else IntelligenceCommand.CreateNote(content.substringBefore('|').trim().take(200),
                content.substringAfter('|', "").trim())
        }
        normal.startsWith("find ") -> IntelligenceCommand.Find(text.substring(5).trim())
        else -> IntelligenceCommand.Ask(text)
    }
}

internal enum class IntelligenceDestination { NOTES, TASKS, CALENDAR, GUARDIAN, HELD, NOTIFICATIONS, SANCTUARY }

internal fun intelligenceDestination(name: String): IntelligenceDestination? = when (name.lowercase(Locale.ROOT)) {
    "notes", "nova notes" -> IntelligenceDestination.NOTES
    "tasks", "nova tasks" -> IntelligenceDestination.TASKS
    "calendar", "nova calendar" -> IntelligenceDestination.CALENDAR
    "guardian", "guardian inbox", "nova guardian" -> IntelligenceDestination.GUARDIAN
    "safe notifications", "notifications" -> IntelligenceDestination.NOTIFICATIONS
    "sanctuary", "nova sanctuary" -> IntelligenceDestination.SANCTUARY
    else -> null
}

internal data class IntelligenceApp(val label: String, val component: String)
internal fun intelligenceApp(name: String, approved: List<IntelligenceApp>) =
    approved.singleOrNull { it.label.equals(name, ignoreCase = true) }

internal data class IntelligenceExchange(val question: String, val answer: String, val cloud: Boolean = false)
