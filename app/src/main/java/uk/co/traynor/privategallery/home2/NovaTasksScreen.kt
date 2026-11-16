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
import uk.co.traynor.privategallery.core.home2.SpaceContext

private data class TaskEditor(val task: NovaTask, val dueText: String)
private enum class TaskConfirmation { DELETE, DISCARD }

@Composable
internal fun NovaTasksScreen(space: SpaceContext, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var store by remember(space) { mutableStateOf<NovaTaskLibrary?>(null) }
    var snapshot by remember(space) { mutableStateOf<NovaTaskLibrary.Snapshot?>(null) }
    var editor by remember(space) { mutableStateOf<TaskEditor?>(null) }
    var view by remember(space) { mutableStateOf(NovaTaskView.ALL) }
    var query by remember(space) { mutableStateOf("") }
    var status by remember(space) { mutableStateOf<String?>(null) }
    var loading by remember(space) { mutableStateOf(true) }
    var reload by remember(space) { mutableIntStateOf(0) }
    var confirmation by remember(space) { mutableStateOf<TaskConfirmation?>(null) }
    LaunchedEffect(space, reload) {
        loading = true
        val result = withContext(Dispatchers.IO) {
            runCatching { NovaTaskLibrary(space).let { it to it.read() } }
        }
        if (!space.current()) return@LaunchedEffect
        result.onSuccess { (opened, saved) ->
            store = opened
            snapshot = saved
            status = null
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
        if (current.task != saved || current.dueText != saved.due?.toString().orEmpty()) {
            confirmation = TaskConfirmation.DISCARD
        } else editor = null
    }

    BackHandler(enabled = loading || editor != null) {
        if (!loading) closeEditor()
    }
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
            val search = query.trim()
            val today = LocalDate.now()
            val matches = savedTasks.filter { task ->
                view.includes(task, today) && listOf(task.title, task.description, task.project)
                    .any { it.contains(search, ignoreCase = true) }
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
                                persist(savedTasks.map { if (it.id == task.id) it.copy(completed = completed) else it },
                                    if (completed) "Task completed" else "Task reopened")
                            }, enabled = !loading)
                            Column(Modifier.weight(1f).padding(vertical = 10.dp)) {
                                Text(task.title)
                                val details = listOfNotNull(
                                    task.project.takeIf { it.isNotBlank() },
                                    task.due?.toString(),
                                    task.priority.takeUnless { it == NovaTaskPriority.NONE }?.label,
                                )
                                if (details.isNotEmpty()) Text(details.joinToString(" · "),
                                    style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }
        } else {
            val task = current.task
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
                Text("Priority")
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    NovaTaskPriority.entries.forEach { priority ->
                        FilterChip(task.priority == priority, { editor = current.copy(task = task.copy(priority = priority)) },
                            { Text(priority.label) }, enabled = !loading)
                    }
                }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(enabled = !loading && task.title.isNotBlank() && !invalidDate, onClick = {
                        val updated = task.copy(title = task.title.trim(), project = task.project.trim(), due = due)
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
