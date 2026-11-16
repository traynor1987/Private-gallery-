package uk.co.traynor.privategallery.home2

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uk.co.traynor.privategallery.core.home2.NovaCoreApp
import uk.co.traynor.privategallery.core.home2.SpaceContext

private data class TaskEditor(val task: NovaTask, val dueText: String, val newSubtask: String = "")
private data class ProjectEditor(val original: String, val name: String)
private enum class TaskConfirmation { DELETE, DISCARD }

@Composable
internal fun NovaTasksScreen(
    space: SpaceContext,
    onBack: () -> Unit,
    openTaskId: String? = null,
    onOpenLink: ((NovaCoreLink) -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    var store by remember(space) { mutableStateOf<NovaTaskLibrary?>(null) }
    var snapshot by remember(space) { mutableStateOf<NovaTaskLibrary.Snapshot?>(null) }
    var editor by remember(space) { mutableStateOf<TaskEditor?>(null) }
    var pickNote by remember(space) { mutableStateOf(false) }
    var projectFilter by remember(space) { mutableStateOf<String?>(null) }
    var projectEditor by remember(space) { mutableStateOf<ProjectEditor?>(null) }
    var removeProject by remember(space) { mutableStateOf<String?>(null) }
    var view by remember(space) { mutableStateOf(NovaTaskView.ALL) }
    var query by remember(space) { mutableStateOf("") }
    var status by remember(space) { mutableStateOf<String?>(null) }
    var loading by remember(space) { mutableStateOf(true) }
    var reload by remember(space) { mutableIntStateOf(0) }
    var confirmation by remember(space) { mutableStateOf<TaskConfirmation?>(null) }
    LaunchedEffect(space, reload, openTaskId) {
        loading = true
        val result = withContext(Dispatchers.IO) {
            runCatching { NovaTaskLibrary(space).let { it to it.read() } }
        }
        if (!space.current()) return@LaunchedEffect
        result.onSuccess { (opened, saved) ->
            store = opened
            snapshot = saved
            val requested = openTaskId?.let { id -> saved.tasks.find { it.id == id } }
            if (requested != null) editor = TaskEditor(requested, requested.due?.toString().orEmpty())
            status = if (openTaskId != null && requested == null) "The linked task is unavailable." else null
        }.onFailure { status = "Encrypted tasks could not be opened." }
        loading = false
    }

    fun persist(tasks: List<NovaTask>, confirmationMessage: String) {
        val library = store ?: return
        val previous = snapshot ?: return
        loading = true
        scope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { library.save(previous, tasks) } }
            if (!space.current()) return@launch
            result.onSuccess { saved ->
                if (saved == null) {
                    status = "Tasks changed elsewhere. Return to the list and reload."
                } else {
                    snapshot = saved
                    editor = null
                    status = confirmationMessage
                }
            }.onFailure { status = "Tasks could not be saved. Encrypted storage may be full or unavailable." }
            loading = false
        }
    }

    fun closeEditor() {
        val current = editor ?: return
        val saved = snapshot?.tasks?.find { it.id == current.task.id } ?: NovaTask(current.task.id, "")
        if (current.task != saved || current.dueText != saved.due?.toString().orEmpty() || current.newSubtask.isNotBlank()) {
            confirmation = TaskConfirmation.DISCARD
        } else editor = null
    }

    BackHandler(enabled = loading || editor != null) {
        if (!loading) closeEditor()
    }
    val projects = snapshot?.tasks.orEmpty().map { it.project }.filter(String::isNotBlank).distinct().sorted()
    val activeProject = projectFilter?.takeIf { view == NovaTaskView.PROJECTS && it in projects }
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(16.dp).testTag("nova-tasks"),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Nova Tasks", style = MaterialTheme.typography.headlineSmall)
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        status?.let { Text(it) }
        val current = editor
        val savedTasks = snapshot?.tasks.orEmpty()
        if (current == null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(enabled = !loading && snapshot != null, onClick = {
                    status = null
                    editor = TaskEditor(NovaTask(UUID.randomUUID().toString(), ""), "")
                }) { Text("New task") }
                TextButton(enabled = !loading, onClick = { reload++ }) { Text("Reload") }
                TextButton(enabled = !loading, onClick = onBack) { Text("Done") }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                NovaTaskView.entries.forEach { tab ->
                    FilterChip(view == tab, { view = tab }, { Text(tab.label) })
                }
            }
            OutlinedTextField(query, { query = it }, enabled = !loading && snapshot != null,
                label = { Text("Search tasks") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            if (view == NovaTaskView.PROJECTS && projects.isNotEmpty()) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(activeProject == null, { projectFilter = null }, { Text("All projects") })
                    projects.forEach { project ->
                        FilterChip(activeProject == project, { projectFilter = project }, { Text(project) })
                    }
                }
                if (activeProject != null) {
                    Row(Modifier.horizontalScroll(rememberScrollState())) {
                        TextButton(enabled = !loading, onClick = {
                            projectEditor = ProjectEditor(activeProject, activeProject)
                        }) { Text("Rename project") }
                        TextButton(enabled = !loading, onClick = { removeProject = activeProject }) { Text("Remove project") }
                    }
                }
            }
            val search = query.trim()
            val today = LocalDate.now()
            val matches = savedTasks.filter { task ->
                view.includes(task, today) && (activeProject == null || task.project == activeProject) && (listOf(task.title, task.description, task.project, task.tags).any { it.contains(search, true) } ||
                    task.subtasks.any { it.title.contains(search, true) })
            }
            val ordered = if (view == NovaTaskView.PROJECTS) {
                matches.sortedWith(compareBy<NovaTask> { it.project.lowercase() }.thenBy { it.completed }
                    .thenBy { it.due ?: LocalDate.MAX })
            } else matches.sortedWith(compareBy<NovaTask> { it.completed }.thenBy { it.due ?: LocalDate.MAX }
                .thenByDescending { it.priority.ordinal })
            if (!loading && snapshot != null && ordered.isEmpty()) Text("No tasks in this view")
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(ordered, key = { it.id }) { task ->
                    OutlinedCard(
                        onClick = { editor = TaskEditor(task, task.due?.toString().orEmpty()); status = null },
                        enabled = !loading,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(Modifier.padding(end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(task.completed, onCheckedChange = { completed ->
                                persist(completeNovaTask(savedTasks, task.id, completed),
                                    if (completed) "Task completed" else "Task reopened")
                            }, enabled = !loading)
                            Column(Modifier.weight(1f).padding(vertical = 10.dp)) {
                                Text(task.title)
                                val details = listOfNotNull(
                                    task.project.takeIf { it.isNotBlank() },
                                    task.due?.toString(),
                                    task.tags.takeIf(String::isNotBlank),
                                    task.subtasks.takeIf { it.isNotEmpty() }?.let {
                                        "${it.count { item -> item.completed }}/${it.size} subtasks"
                                    },
                                    task.priority.takeUnless { it == NovaTaskPriority.NONE }?.label,
                                )
                                if (details.isNotEmpty()) Text(details.joinToString(" · "),
                                    style = MaterialTheme.typography.labelMedium)
                                if (onOpenLink != null) {
                                    task.noteId?.let { id ->
                                        TextButton(enabled = !loading, onClick = {
                                            onOpenLink(NovaCoreLink.Record(NovaCoreApp.NOTES, id))
                                        }) { Text("Open linked note") }
                                    }
                                    task.due?.let { date ->
                                        TextButton(enabled = !loading, onClick = {
                                            onOpenLink(NovaCoreLink.DueDay(date))
                                        }) { Text("Show due date in Calendar") }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            val task = current.task
            val subtaskTitle = current.newSubtask
            val date = current.dueText.trim()
            val due = date.takeIf { it.isNotEmpty() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            val invalidDate = date.isNotEmpty() && due == null
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(task.title, { editor = current.copy(task = task.copy(title = it.take(200))) },
                    modifier = Modifier.fillMaxWidth(), label = { Text("Title") }, singleLine = true, enabled = !loading)
                OutlinedTextField(task.description, { editor = current.copy(task = task.copy(description = it.take(4096))) },
                    modifier = Modifier.fillMaxWidth(), label = { Text("Description") }, enabled = !loading)
                OutlinedTextField(task.project, { editor = current.copy(task = task.copy(project = it.take(120))) },
                    modifier = Modifier.fillMaxWidth(), label = { Text("Project") }, singleLine = true, enabled = !loading)
                OutlinedTextField(current.dueText, { editor = current.copy(dueText = it.take(10)) },
                    modifier = Modifier.fillMaxWidth(), label = { Text("Due date (YYYY-MM-DD)") },
                    isError = invalidDate, singleLine = true, enabled = !loading)
                OutlinedTextField(task.tags, { editor = current.copy(task = task.copy(tags = it.take(200))) },
                    modifier = Modifier.fillMaxWidth(), label = { Text("Tags") }, singleLine = true, enabled = !loading)
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    TextButton(enabled = !loading, onClick = { pickNote = true }) {
                        Text(if (task.noteId == null) "Link a note" else "Change linked note")
                    }
                    if (task.noteId != null) TextButton(enabled = !loading, onClick = {
                        editor = current.copy(task = task.copy(noteId = null))
                    }) { Text("Unlink note") }
                }
                Text("Subtasks", style = MaterialTheme.typography.titleSmall)
                task.subtasks.forEach { subtask ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(subtask.completed, onCheckedChange = { completed ->
                            editor = current.copy(task = task.copy(subtasks = task.subtasks.map {
                                if (it.id == subtask.id) it.copy(completed = completed) else it
                            }))
                        }, enabled = !loading)
                        OutlinedTextField(subtask.title, { title ->
                            editor = current.copy(task = task.copy(subtasks = task.subtasks.map {
                                if (it.id == subtask.id) it.copy(title = title.take(200)) else it
                            }))
                        }, modifier = Modifier.weight(1f), label = { Text("Subtask") }, singleLine = true, enabled = !loading)
                        TextButton(enabled = !loading, onClick = {
                            editor = current.copy(task = task.copy(subtasks = task.subtasks.filterNot { it.id == subtask.id }))
                        }) { Text("Remove") }
                    }
                }
                if (task.subtasks.size < NovaTaskLibrary.MAX_SUBTASKS) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(subtaskTitle, { editor = current.copy(newSubtask = it.take(200)) }, enabled = !loading,
                            modifier = Modifier.weight(1f), label = { Text("New subtask") }, singleLine = true)
                        TextButton(enabled = !loading && subtaskTitle.isNotBlank(), onClick = {
                            val subtask = NovaSubtask(UUID.randomUUID().toString(), subtaskTitle.trim())
                            editor = current.copy(task = task.copy(subtasks = task.subtasks + subtask), newSubtask = "")
                        }) { Text("Add") }
                    }
                }
                Text("Repeat after completion")
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    NovaTaskRepeat.entries.forEach { repeat ->
                        FilterChip(task.repeat == repeat, { editor = current.copy(task = task.copy(repeat = repeat)) },
                            { Text(repeat.label) }, enabled = !loading)
                    }
                }
                if (task.repeat != NovaTaskRepeat.NONE && due == null) Text("Set a due date for recurring tasks.")
                Text("Priority")
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    NovaTaskPriority.entries.forEach { priority ->
                        FilterChip(task.priority == priority, { editor = current.copy(task = task.copy(priority = priority)) },
                            { Text(priority.label) }, enabled = !loading)
                    }
                }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(enabled = !loading && task.title.isNotBlank() && task.subtasks.all { it.title.isNotBlank() } &&
                        subtaskTitle.isBlank() && !invalidDate && (task.repeat == NovaTaskRepeat.NONE || due != null), onClick = {
                        val updated = task.copy(title = task.title.trim(), project = task.project.trim(), due = due,
                            subtasks = task.subtasks.map { it.copy(title = it.title.trim()) },
                            repeatDay = if (due == task.due) task.repeatDay else due?.dayOfMonth)
                        val tasks = if (savedTasks.any { it.id == task.id }) {
                            savedTasks.map { if (it.id == task.id) updated else it }
                        } else savedTasks + updated
                        persist(tasks, "Task saved")
                    }) { Text("Save task") }
                    TextButton(enabled = !loading, onClick = { closeEditor() }) { Text("Back to tasks") }
                    if (savedTasks.any { it.id == task.id }) {
                        TextButton(enabled = !loading, onClick = { confirmation = TaskConfirmation.DELETE }) { Text("Delete") }
                    }
                }
            }
        }
    }
    if (pickNote) NovaNotePicker(space, onChoose = { id ->
        editor = editor?.let { it.copy(task = it.task.copy(noteId = id)) }
        pickNote = false
    }, onDismiss = { pickNote = false })
    projectEditor?.let { project ->
        val name = project.name.trim()
        val valid = name.isNotEmpty() && name != project.original &&
            projects.none { it != project.original && it.equals(name, ignoreCase = true) }
        AlertDialog(
            onDismissRequest = { projectEditor = null },
            title = { Text("Rename project") },
            text = {
                OutlinedTextField(project.name, { projectEditor = project.copy(name = it.take(120)) },
                    singleLine = true, label = { Text("Project name") })
            },
            confirmButton = {
                TextButton(enabled = valid && !loading, onClick = {
                    projectEditor = null
                    persist(snapshot?.tasks.orEmpty().map {
                        if (it.project == project.original) it.copy(project = name) else it
                    }, "Project renamed")
                }) { Text("Rename") }
            },
            dismissButton = { TextButton(onClick = { projectEditor = null }) { Text("Cancel") } },
        )
    }
    removeProject?.let { project ->
        AlertDialog(
            onDismissRequest = { removeProject = null },
            title = { Text("Remove project?") },
            text = { Text("The tasks will be kept without this project label.") },
            confirmButton = {
                TextButton(enabled = !loading, onClick = {
                    removeProject = null
                    persist(snapshot?.tasks.orEmpty().map {
                        if (it.project == project) it.copy(project = "") else it
                    }, "Project removed")
                }) { Text("Remove project") }
            },
            dismissButton = { TextButton(onClick = { removeProject = null }) { Text("Cancel") } },
        )
    }
    confirmation?.let { action ->
        val deleting = action == TaskConfirmation.DELETE
        AlertDialog(
            onDismissRequest = { confirmation = null },
            title = { Text(if (deleting) "Delete this task?" else "Discard unsaved changes?") },
            confirmButton = {
                TextButton(onClick = {
                    confirmation = null
                    if (deleting) persist(snapshot?.tasks.orEmpty().filterNot { it.id == editor?.task?.id }, "Task deleted")
                    else editor = null
                }) { Text(if (deleting) "Delete" else "Discard") }
            },
            dismissButton = { TextButton(onClick = { confirmation = null }) { Text("Cancel") } },
        )
    }
}
