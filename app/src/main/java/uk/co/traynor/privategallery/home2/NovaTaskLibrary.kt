package uk.co.traynor.privategallery.home2

import java.time.LocalDate
import org.json.JSONArray
import org.json.JSONObject
import uk.co.traynor.privategallery.core.home2.NovaCoreApp
import uk.co.traynor.privategallery.core.home2.NovaCoreAppDocuments
import uk.co.traynor.privategallery.core.home2.SpaceContext

internal enum class NovaTaskPriority(val label: String) {
    NONE("None"), LOW("Low"), NORMAL("Normal"), HIGH("High"),
}

internal data class NovaTask(
    val id: String,
    val title: String,
    val description: String = "",
    val project: String = "",
    val due: LocalDate? = null,
    val priority: NovaTaskPriority = NovaTaskPriority.NONE,
    val completed: Boolean = false,
)

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
                .put("completed", task.completed))
        }
        val document = documents.save("library", previous.revision,
            JSONObject().put("tasks", entries).toString()) ?: return null
        return Snapshot(document.revision, tasks.toList())
    }
}
