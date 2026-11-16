package uk.co.traynor.privategallery.home2

import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject
import uk.co.traynor.privategallery.core.home2.NovaCoreApp
import uk.co.traynor.privategallery.core.home2.NovaCoreAppDocuments
import uk.co.traynor.privategallery.core.home2.SpaceContext

internal enum class NovaTaskPriority(val label: String) {
    NONE("None"), LOW("Low"), NORMAL("Normal"), HIGH("High"),
}

internal enum class NovaTaskRepeat(val label: String) {
    NONE("Never"), DAILY("Daily"), WEEKLY("Weekly"), MONTHLY("Monthly"),
}

internal data class NovaTask(
    val id: String,
    val title: String,
    val description: String = "",
    val project: String = "",
    val due: LocalDate? = null,
    val priority: NovaTaskPriority = NovaTaskPriority.NONE,
    val completed: Boolean = false,
    val repeat: NovaTaskRepeat = NovaTaskRepeat.NONE,
    val repeatDay: Int? = null,
    val nextCreated: Boolean = false,
    val tags: String = "",
)

internal fun completeNovaTask(tasks: List<NovaTask>, id: String, completed: Boolean): List<NovaTask> {
    val task = tasks.find { it.id == id } ?: return tasks
    if (task.completed == completed) return tasks
    val date = task.due
    val nextDate = if (completed && !task.nextCreated && date != null) when (task.repeat) {
        NovaTaskRepeat.NONE -> null
        NovaTaskRepeat.DAILY -> date.plusDays(1)
        NovaTaskRepeat.WEEKLY -> date.plusWeeks(1)
        NovaTaskRepeat.MONTHLY -> YearMonth.from(date).plusMonths(1).let { month ->
            month.atDay(minOf(task.repeatDay ?: date.dayOfMonth, month.lengthOfMonth()))
        }
    }?.takeIf { it.year in 1..9999 } else null
    val updated = tasks.map {
        if (it.id == id) it.copy(completed = completed, nextCreated = it.nextCreated || nextDate != null) else it
    }
    if (nextDate == null) return updated
    val nextId = UUID.nameUUIDFromBytes("nova-task-next:$id".toByteArray(Charsets.UTF_8)).toString()
    if (tasks.any { it.id == nextId }) return updated
    return updated + task.copy(id = nextId, due = nextDate, completed = false,
        repeatDay = task.repeatDay ?: date?.dayOfMonth, nextCreated = false)
}

internal enum class NovaTaskView(val label: String) {
    TODAY("Today"), UPCOMING("Upcoming"), ALL("All tasks"), COMPLETED("Completed"), PROJECTS("Projects");

    fun includes(task: NovaTask, today: LocalDate): Boolean = when (this) {
        TODAY -> !task.completed && task.due != null && task.due <= today
        UPCOMING -> !task.completed && task.due != null && task.due > today
        ALL -> true
        COMPLETED -> task.completed
        PROJECTS -> task.project.isNotBlank()
    }
}

internal class NovaTaskLibrary(space: SpaceContext) {
    private val documents = NovaCoreAppDocuments(space, NovaCoreApp.TASKS)

    data class Snapshot(val revision: Long?, val tasks: List<NovaTask>)

    fun read(): Snapshot {
        val document = documents.read("library") ?: return Snapshot(null, emptyList())
        val entries = JSONObject(document.json).getJSONArray("tasks")
        return Snapshot(document.revision, List(entries.length()) { index ->
            val entry = entries.getJSONObject(index)
            NovaTask(
                entry.getString("id"),
                entry.getString("title"),
                entry.getString("description"),
                entry.getString("project"),
                entry.optString("due").takeIf(String::isNotBlank)?.let { LocalDate.parse(it) },
                NovaTaskPriority.valueOf(entry.getString("priority")),
                entry.getBoolean("completed"),
                NovaTaskRepeat.valueOf(entry.optString("repeat", NovaTaskRepeat.NONE.name)),
                entry.optInt("repeatDay").takeIf { it in 1..31 },
                entry.optBoolean("nextCreated"),
                entry.optString("tags"),
            )
        })
    }

    fun save(previous: Snapshot, tasks: List<NovaTask>): Snapshot? {
        val entries = JSONArray()
        for (task in tasks) {
            entries.put(JSONObject()
                .put("id", task.id)
                .put("title", task.title)
                .put("description", task.description)
                .put("project", task.project)
                .put("due", task.due?.toString().orEmpty())
                .put("priority", task.priority.name)
                .put("completed", task.completed)
                .put("repeat", task.repeat.name)
                .put("repeatDay", task.repeatDay)
                .put("nextCreated", task.nextCreated)
                .put("tags", task.tags))
        }
        val document = documents.save("library", previous.revision,
            JSONObject().put("tasks", entries).toString()) ?: return null
        return Snapshot(document.revision, tasks.toList())
    }
}
