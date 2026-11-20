package uk.co.traynor.privategallery.intelligence

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uk.co.traynor.privategallery.core.brain.CloudTeacherException
import uk.co.traynor.privategallery.core.brain.NovaBrainConnectionStore
import uk.co.traynor.privategallery.core.guardian.GuardianCaptureStore
import uk.co.traynor.privategallery.launcher.GuardianAlertDialog
import uk.co.traynor.privategallery.launcher.GuardianBorder
import uk.co.traynor.privategallery.launcher.GuardianMint
import uk.co.traynor.privategallery.launcher.GuardianMuted
import uk.co.traynor.privategallery.launcher.GuardianNavy
import uk.co.traynor.privategallery.launcher.GuardianTheme
import uk.co.traynor.privategallery.launcher.LauncherExpandedWidth
import uk.co.traynor.privategallery.launcher.guardianCardColors

internal class IntelligenceConversation {
    val history = mutableStateOf(emptyList<IntelligenceExchange>())
    val input = mutableStateOf("")
    val taskDate = mutableStateOf<LocalDate?>(null)

    fun clear() {
        history.value = emptyList()
        input.value = ""
        taskDate.value = null
    }
}

private data class IntelligenceConfirmation(val title: String, val detail: String, val label: String, val action: () -> Unit)

@Composable
internal fun IntelligenceScreen(
    active: Boolean,
    guardianVisible: Boolean,
    protectedBySanctuary: Boolean,
    openPrivate: (() -> Unit)?,
    openDestination: (IntelligenceDestination) -> Boolean,
    workspace: IntelligenceWorkspace? = null,
    apps: List<IntelligenceApp> = emptyList(),
    openApp: (String) -> Boolean = { false },
    openEvidence: (String) -> Boolean = { false },
    onBack: (() -> Unit)? = null,
    emailProvider: IntelligenceEmailProvider = disconnectedIntelligenceEmail,
    conversation: IntelligenceConversation = remember { IntelligenceConversation() },
) {
    GuardianTheme {
        Surface(Modifier.fillMaxSize().testTag("nova-home-intelligence"), color = GuardianNavy) {
            if (active) IntelligenceContent(guardianVisible, protectedBySanctuary, openPrivate, openDestination,
                workspace, apps, openApp, openEvidence, onBack, emailProvider, conversation)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IntelligenceContent(
    guardianVisible: Boolean,
    protectedBySanctuary: Boolean,
    openPrivate: (() -> Unit)?,
    openDestination: (IntelligenceDestination) -> Boolean,
    workspace: IntelligenceWorkspace?,
    apps: List<IntelligenceApp>,
    openApp: (String) -> Boolean,
    openEvidence: (String) -> Boolean,
    onBack: (() -> Unit)?,
    emailProvider: IntelligenceEmailProvider,
    chatSession: IntelligenceConversation,
) {
    val context = LocalContext.current.applicationContext
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val archiveRevision by GuardianCaptureStore.archiveChanges.collectAsState()
    val briefingRevision by IntelligenceBriefings.revision.collectAsState()
    val ownerScope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val focus = LocalFocusManager.current
    var now by remember { mutableStateOf(ZonedDateTime.now()) }
    var guardian by remember { mutableStateOf<IntelligenceGuardian?>(null) }
    var guardianState by remember { mutableStateOf("Checking Guardian…") }
    var connection by remember { mutableStateOf("Checking local configuration…") }
    var email by remember { mutableStateOf<String?>(null) }
    var agenda by remember { mutableStateOf<Result<IntelligenceAgenda>?>(null) }
    var agendaRevision by remember { mutableIntStateOf(0) }
    var chat by chatSession.history
    var memory by remember { mutableStateOf<IntelligenceMemory?>(null) }
    var input by chatSession.input
    var busy by remember { mutableStateOf(false) }
    var feedback by remember { mutableStateOf<String?>(null) }
    var confirmation by remember { mutableStateOf<IntelligenceConfirmation?>(null) }
    var manageMemory by remember { mutableStateOf(false) }
    var scheduleOpen by remember { mutableStateOf(false) }
    var privatePrompt by remember { mutableStateOf(false) }
    var taskDate by chatSession.taskDate
    val privateAllowed = !protectedBySanctuary && workspace != null
    val today = now.toLocalDate()

    BackHandler(enabled = onBack != null) { onBack?.invoke() }
    LaunchedEffect(lifecycle, briefingRevision) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (isActive) {
                now = ZonedDateTime.now()
                delay(30_000)
            }
        }
    }
    LaunchedEffect(lifecycle, archiveRevision, now.toEpochSecond() / 60, guardianVisible, protectedBySanctuary) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            val result = if (guardianVisible && !protectedBySanctuary) withContext(Dispatchers.IO) {
                runCatching { intelligenceGuardian(context, today, now.zone) }
            } else null
            guardian = result?.getOrNull()
            guardianState = when {
                protectedBySanctuary -> "Briefing paused during Sanctuary"
                !guardianVisible -> "Guardian briefing hidden in launcher settings"
                result?.isFailure == true -> "Encrypted archive unavailable"
                else -> intelligenceGuardianStatus(context)
            }
        }
    }
    LaunchedEffect(lifecycle, privateAllowed) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            connection = withContext(Dispatchers.IO) {
                runCatching { intelligenceConnection(context) }.getOrDefault("Connection settings unavailable · local commands available")
            }
            email = withContext(Dispatchers.IO) {
                runCatching {
                    emailProvider.authorisedSummary()?.let { "${it.providerLabel} · ${it.importantUnread} important unread" }
                        ?: "No email provider connected"
                }.getOrDefault("Email summary unavailable")
            }
        }
    }
    LaunchedEffect(workspace, privateAllowed, today, agendaRevision, lifecycle) {
        if (privateAllowed) lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            agenda = withContext(Dispatchers.IO) {
                runCatching { checkNotNull(workspace).agenda(today, today.plusDays(7), now.zone, includeOverdue = true) }
            }
        } else agenda = null
    }
    LaunchedEffect(workspace, privateAllowed) {
        if (!privateAllowed) {
            memory = null
            if (openPrivate == null) {
                chat = emptyList()
                input = ""
                confirmation = null
            }
        }
        if (privateAllowed) memory = withContext(Dispatchers.IO) { runCatching { workspace?.memory() }.getOrNull() }
    }

    fun respond(question: String, answer: String, cloud: Boolean = false) {
        chat = (chat + IntelligenceExchange(question, answer, cloud)).takeLast(IntelligenceWorkspace.HISTORY_LIMIT)
    }

    fun <T> runWork(error: String, work: () -> T, done: (T) -> Unit) {
        if (busy) return
        busy = true
        feedback = null
        ownerScope.launch {
            try {
                val result = withContext(Dispatchers.IO) { work() }
                if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) done(result)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                feedback = error
            } finally {
                busy = false
            }
        }
    }

    fun confirm(title: String, detail: String, label: String, action: () -> Unit) {
        confirmation = IntelligenceConfirmation(title, detail, label, action)
    }

    fun requirePrivate(question: String, context: String = "") {
        val response = when {
            protectedBySanctuary -> "Private assistant actions are paused during Sanctuary."
            openPrivate == null -> "The private workspace is temporarily unavailable."
            else -> "Open the private briefing to use your Primary records."
        }
        respond(question, listOf(context, response).filter(String::isNotEmpty).joinToString("\n"))
        privatePrompt = openPrivate != null && !protectedBySanctuary
    }

    fun navigate(destination: IntelligenceDestination) {
        if (!openDestination(destination)) feedback = "This destination is unavailable under the current protection settings."
    }

    fun askCloud(question: String) {
        if (protectedBySanctuary) {
            respond(question, "Cloud questions are paused during Sanctuary. Local app shortcuts are available.")
            return
        }
        confirm("Send one question to Nova Brain?", "This exact text goes to your configured VPS and OpenAI. The server’s paid-request limits apply.\n\n$question", "Send question") {
            val requestId = UUID.randomUUID().toString()
            val job = ownerScope.coroutineContext[kotlinx.coroutines.Job]
            runWork("Nova Brain is unavailable. Local commands are still available.", work = {
                val store = NovaBrainConnectionStore(context)
                try {
                    val configuration = store.read()
                    if (configuration == null) "Cloud is disconnected. You can configure Nova Brain in Settings or use local commands." to false
                    else {
                        val client = IntelligenceCloud()
                        val status = client.status(configuration)
                        when {
                            !status.assistantAvailable -> "This Nova Brain server has no assistant endpoint yet. Local commands are available." to false
                            !status.budget.canAnalyze -> "The server currently disallows paid requests. Local commands are available." to false
                            else -> client.ask(configuration, question, approved = true, requestId, status) {
                                job?.isActive == true && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) &&
                                    intelligencePrivateAllowed(context) && store.read() == configuration
                            } to true
                        }
                    }
                } catch (failure: CloudTeacherException) {
                    failure.failure.userMessage to false
                }
            }) { (answer, cloud) -> respond(question, answer, cloud) }
        }
    }

    fun send() {
        val question = input.trim()
        if (question.isEmpty() || busy || confirmation != null) return
        input = ""
        focus.clearFocus()
        privatePrompt = false
        feedback = null
        val pendingDue = taskDate
        taskDate = null
        val parsed = intelligenceCommand(question, today)
        val command = if (pendingDue != null && parsed is IntelligenceCommand.Ask)
            IntelligenceCommand.CreateTask(question.take(200), pendingDue) else parsed
        when (command) {
            is IntelligenceCommand.Briefing -> {
                val counts = guardian
                val message = if (command.day == today && counts != null)
                    "Guardian captured ${counts.newToday} today. ${counts.importantToday} flagged for attention." else ""
                if (!privateAllowed) {
                    requirePrivate(question, message)
                } else {
                    runWork("Could not read the private agenda.", work = {
                        checkNotNull(workspace).agenda(command.day, command.day.plusDays(1), now.zone, command.day == today)
                    }) { brief ->
                        respond(question, listOfNotNull(message.takeIf(String::isNotEmpty),
                            agendaLine("Tasks", brief.tasks), agendaLine("Calendar", brief.events),
                            agendaLine("Reminders", brief.reminders)).joinToString("\n"))
                    }
                }
            }
            IntelligenceCommand.Notifications -> respond(question, guardian?.let {
                "${it.newToday} messages captured today. ${it.importantToday} flagged for attention. Open Safe Notifications for the approved list."
            } ?: guardianState)
            IntelligenceCommand.Held, IntelligenceCommand.Inbox -> {
                val held = command == IntelligenceCommand.Held
                val destination = if (held) IntelligenceDestination.HELD else IntelligenceDestination.GUARDIAN
                confirm(if (held) "Open held messages?" else "Open Guardian Inbox?",
                    "Guardian will apply its own authentication and review rules.", "Open Guardian") { navigate(destination) }
            }
            is IntelligenceCommand.Open -> {
                val destination = intelligenceDestination(command.name)
                val app = intelligenceApp(command.name, apps)
                when {
                    destination == IntelligenceDestination.GUARDIAN ->
                        confirm("Open Guardian Inbox?", "Guardian will apply its own authentication and review rules.",
                            "Open Guardian") { navigate(destination) }
                    destination != null -> navigate(destination)
                    app != null -> confirm("Open ${app.label}?", "Nova Launcher will check whether this app can open.",
                        "Open app") { if (!openApp(app.component)) feedback = "This app is unavailable." }
                    else -> respond(question, "That app is outside the assistant's approved destinations.")
                }
            }
            is IntelligenceCommand.CreateTask -> if (!privateAllowed) requirePrivate(question) else {
                val id = IntelligenceWorkspace.newRecordId()
                val detail = listOfNotNull(command.title, command.due?.format(IntelligenceWorkspace.dateFormat)).joinToString("\n")
                confirm("Create this task?", detail, "Create task") {
                    runWork("The task could not be saved. Check Nova Tasks before trying again.",
                        work = { checkNotNull(workspace).createTask(id, command.title, command.due) }) {
                        agendaRevision++
                        respond(question, "Task saved in Nova Tasks.")
                    }
                }
            }
            is IntelligenceCommand.CreateNote -> if (!privateAllowed) requirePrivate(question) else {
                if (command.title.isBlank()) respond(question, "The note needs a title.") else {
                    val id = IntelligenceWorkspace.newRecordId()
                    confirm("Create this note?", listOf(command.title, command.body).filter(String::isNotEmpty).joinToString("\n"), "Create note") {
                        runWork("The note could not be saved. Check Nova Notes before trying again.",
                            work = { checkNotNull(workspace).createNote(id, command.title, command.body) }) {
                            respond(question, "Note saved in Nova Notes.")
                        }
                    }
                }
            }
            is IntelligenceCommand.Find -> if (!privateAllowed) requirePrivate(question) else {
                runWork("The local search could not finish.", work = { checkNotNull(workspace).find(command.query) }) { matches ->
                    respond(question, matches.joinToString("\n") { (area, title) ->
                        "${area.name.lowercase().replaceFirstChar { it.titlecase() }} · $title"
                    }.ifEmpty { "No matching records. Protected notebooks can be searched in Nova Notes." })
                }
            }
            IntelligenceCommand.LatestHeldEvidence -> {
                if (protectedBySanctuary || !guardianVisible) respond(question, "The Guardian briefing is unavailable.")
                else confirm("Open the latest held evidence?", "Guardian will authenticate you and apply the record’s existing hold.",
                    "Open evidence") {
                    runWork("Guardian evidence is unavailable.", { latestHeldIntelligenceEvidence(context) }) { id ->
                        if (id == null) respond(question, "Guardian has no held evidence to open.")
                        else if (!openEvidence(id)) feedback = "Guardian evidence is unavailable."
                    }
                }
            }
            is IntelligenceCommand.Evidence -> confirm("Open Guardian evidence?", "Guardian will authenticate you before opening this record.",
                "Open evidence") { if (!openEvidence(command.id)) feedback = "Guardian evidence is unavailable." }
            is IntelligenceCommand.Ask -> askCloud(command.question)
            is IntelligenceCommand.Clarify -> {
                taskDate = command.taskDue
                respond(question, command.instruction)
            }
        }
    }

    val briefing = remember(briefingRevision, now, protectedBySanctuary) {
        if (protectedBySanctuary) null else IntelligenceBriefings.ready(context, now)
    }
    val layout: @Composable (Modifier) -> Unit = { modifier ->
        Column(modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(now.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)), color = GuardianMuted,
                style = MaterialTheme.typography.labelLarge)
            val greeting = when (now.hour) {
                in 0..11 -> "Good morning"
                in 12..17 -> "Good afternoon"
                else -> "Good evening"
            }
            Text(greeting, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
            briefing?.let { Text("$it briefing ready", color = GuardianMint) }
            IntelligenceCard("Daily briefing") {
                Text(guardian?.let { "${it.newToday} Guardian captures today · ${it.importantToday} flagged for attention" }
                    ?: "Guardian activity · $guardianState")
                Text(connection, color = GuardianMuted, modifier = Modifier.testTag("intelligence-connection"))
                Text(email ?: "Checking email connection…", color = GuardianMuted)
                TextButton(enabled = !protectedBySanctuary, onClick = { navigate(IntelligenceDestination.NOTIFICATIONS) }) {
                    Text("Safe Notifications")
                }
            }
            IntelligenceCard("Guardian") {
                Text(guardianState)
                guardian?.let {
                    Text("${it.retained} captured  ·  ${it.safe} safe  ·  ${it.held} held", style = MaterialTheme.typography.titleMedium)
                    Text("${it.pending} awaiting analysis · ${it.reviewable} ready for review", color = GuardianMuted)
                    Text(if (it.evidencePresent) "Encrypted archive on this device" else "No preserved captures", color = GuardianMuted)
                }
                TextButton(enabled = guardianVisible && !protectedBySanctuary, onClick = {
                    confirm("Open Guardian Inbox?", "Guardian will apply its own authentication and review rules.",
                        "Open Guardian") { navigate(IntelligenceDestination.GUARDIAN) }
                }) { Text("Open Guardian Inbox") }
            }
            IntelligenceCard("Tasks & calendar") {
                val upcoming = agenda?.getOrNull()
                when {
                    protectedBySanctuary -> Text("Private briefing paused during Sanctuary")
                    workspace == null -> {
                        Text("Unlock Primary Space to see upcoming tasks, events and reminders.")
                        TextButton(onClick = { openPrivate?.invoke() }, enabled = openPrivate != null) { Text("Unlock private briefing") }
                    }
                    agenda == null -> LinearProgressIndicator(Modifier.fillMaxWidth())
                    upcoming == null -> Text("Tasks and calendar unavailable")
                    else -> {
                        AgendaItems("Tasks", upcoming.tasks, "No tasks due in the next seven days")
                        AgendaItems("Calendar", upcoming.events, "No events in the next seven days")
                        AgendaItems("Reminders", upcoming.reminders, "No reminders on this agenda")
                    }
                }
                FlowRow {
                    TextButton(onClick = { navigate(IntelligenceDestination.TASKS) }) { Text("Nova Tasks") }
                    TextButton(onClick = { navigate(IntelligenceDestination.CALENDAR) }) { Text("Nova Calendar") }
                }
            }
            if (workspace != null && privateAllowed) {
                TextButton(onClick = { scheduleOpen = true }) { Text("Briefing schedule") }
            }
        }
    }

    val conversation: @Composable (Modifier) -> Unit = { modifier ->
        Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            IntelligenceCard("Ask Nova") {
                Text("Try “Summarise my day”, “What’s on tomorrow?” or “Open Nova Notes”.", color = GuardianMuted)
                if (workspace != null && privateAllowed) {
                    TextButton(onClick = { manageMemory = true }, enabled = !busy) { Text("Assistant memory") }
                }
            }
            val scroll = rememberScrollState()
            LaunchedEffect(chat.size, busy) { scroll.scrollTo(scroll.maxValue) }
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll),
                verticalArrangement = Arrangement.spacedBy(9.dp)) {
                chat.forEach { message ->
                    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.align(Alignment.End)) {
                        Text(message.question, Modifier.padding(12.dp))
                    }
                    IntelligenceCard(if (message.cloud) "Nova Brain · cloud" else "Nova · local") { Text(message.answer) }
                }
                if (privatePrompt) TextButton(onClick = { openPrivate?.invoke() }) { Text("Continue in private briefing") }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            IntelligenceInput(input, { input = it.take(600) }, busy, focus) { send() }
        }
    }

    // The cover uses tabs. The inner screen keeps the briefing and chat in sight.
    BoxWithConstraints(Modifier.fillMaxSize().then(if (onBack != null) Modifier.safeDrawingPadding() else Modifier)
        .imePadding().padding(horizontal = 16.dp, vertical = 10.dp)) {
        val expanded = maxWidth >= (LauncherExpandedWidth - 32).dp
        var showChat by remember { mutableStateOf(false) }
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                Icon(Icons.Filled.AutoAwesome, null, tint = GuardianMint, modifier = Modifier.size(20.dp))
                Text("Nova Intelligence", style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f).semantics { heading() })
            }
            feedback?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (expanded) {
                Row(Modifier.weight(1f).testTag("intelligence-inner"), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    layout(Modifier.weight(1f).fillMaxHeight())
                    conversation(Modifier.weight(1f).fillMaxHeight())
                }
            } else {
                Row(Modifier.testTag("intelligence-cover"), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(!showChat, onClick = { showChat = false }, label = { Text("Briefing") })
                    FilterChip(showChat, onClick = { showChat = true }, label = { Text("Ask Nova") })
                }
                if (showChat) conversation(Modifier.weight(1f).fillMaxWidth())
                else layout(Modifier.weight(1f).fillMaxWidth())
            }
        }
    }

    confirmation?.let { pending ->
        GuardianAlertDialog(onDismissRequest = { confirmation = null }, title = { Text(pending.title) },
            text = { Text(pending.detail, Modifier.heightIn(max = 300.dp).verticalScroll(rememberScrollState())) },
            confirmButton = {
                TextButton(onClick = {
                    confirmation = null
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    pending.action()
                }) { Text(pending.label) }
            },
            dismissButton = { TextButton(onClick = { confirmation = null }) { Text("Cancel") } })
    }
    if (manageMemory && privateAllowed) {
        GuardianAlertDialog(onDismissRequest = { manageMemory = false }, title = { Text("Assistant memory") },
            text = {
                Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                    Text("Save this conversation in encrypted Primary Space. Recalls stay on this device.")
                    if (memory?.enabled == true) memory?.exchanges.orEmpty().forEach {
                        HorizontalDivider(Modifier.padding(vertical = 8.dp))
                        Text(it.question, style = MaterialTheme.typography.titleSmall)
                        Text(it.answer, color = GuardianMuted)
                    } else Text("No saved conversation", color = GuardianMuted, modifier = Modifier.padding(top = 8.dp))
                    if (memory?.enabled == true) {
                        TextButton(onClick = {
                            chat = memory?.exchanges.orEmpty()
                            manageMemory = false
                        }) { Text("Load saved conversation") }
                        TextButton(onClick = {
                            manageMemory = false
                            confirm("Delete saved conversation?", "This removes the saved assistant memory from Primary Space.", "Delete memory") {
                                val empty = IntelligenceMemory(false, emptyList())
                                runWork("Could not delete assistant memory.", { checkNotNull(workspace).saveMemory(empty) }) {
                                    memory = empty
                                    chat = emptyList()
                                }
                            }
                        }) { Text("Delete saved conversation") }
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = chat.isNotEmpty(), onClick = {
                    val snapshot = IntelligenceMemory(true, chat)
                    manageMemory = false
                    runWork("Could not save this conversation.", { checkNotNull(workspace).saveMemory(snapshot) }) { memory = snapshot }
                }) { Text("Save current conversation") }
            }, dismissButton = { TextButton(onClick = { manageMemory = false }) { Text("Close") } })
    }
    if (scheduleOpen && privateAllowed) IntelligenceScheduleDialog(context, onClose = { scheduleOpen = false }) { times ->
        scheduleOpen = false
        runWork("Could not save the briefing schedule.", {
            check(workspace?.permitted() == true)
            IntelligenceBriefings.save(context, times)
        }) { now = ZonedDateTime.now() }
    }
}

@Composable
private fun IntelligenceCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(shape = MaterialTheme.shapes.large, colors = guardianCardColors(), border = BorderStroke(1.dp, GuardianBorder)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            content()
        }
    }
}

@Composable
private fun AgendaItems(title: String, items: List<String>, empty: String) {
    Text(title, style = MaterialTheme.typography.labelLarge)
    if (items.isEmpty()) Text(empty, color = GuardianMuted)
    else items.take(4).forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
}

private fun agendaLine(label: String, entries: List<String>) =
    "$label · " + entries.joinToString("; ").ifEmpty { "None scheduled" }

@Composable
private fun IntelligenceInput(input: String, onText: (String) -> Unit, busy: Boolean, focus: FocusManager, send: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(value = input, onValueChange = onText, label = { Text("Message Nova") },
            modifier = Modifier.weight(1f).testTag("intelligence-input"), singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { if (!busy) { focus.clearFocus(); send() } }))
        IconButton(onClick = send, enabled = input.isNotBlank() && !busy) {
            Icon(Icons.AutoMirrored.Filled.Send, "Send message")
        }
    }
}

@Composable
private fun IntelligenceScheduleDialog(context: Context, onClose: () -> Unit, onSave: (IntelligenceBriefingTimes) -> Unit) {
    val times = remember { IntelligenceBriefings.read(context) }
    val format = remember { DateTimeFormatter.ofPattern("HH:mm").withResolverStyle(java.time.format.ResolverStyle.STRICT) }
    var morning by remember { mutableStateOf(times.morning?.format(format).orEmpty()) }
    var evening by remember { mutableStateOf(times.evening?.format(format).orEmpty()) }
    var afterWork by remember { mutableStateOf(times.afterWork?.format(format).orEmpty()) }
    fun parse(value: String) = runCatching { LocalTime.parse(value, format) }.getOrNull()
    val valid = listOf(morning, evening, afterWork).all { it.isBlank() || parse(it) != null }
    GuardianAlertDialog(onDismissRequest = onClose, title = { Text("Briefing schedule") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Use 24-hour times. Leave a field empty to turn it off. Briefings appear silently in Nova Intelligence.")
            OutlinedTextField(morning, { morning = it.take(5) }, label = { Text("Morning · HH:mm") }, singleLine = true)
            OutlinedTextField(evening, { evening = it.take(5) }, label = { Text("Evening · HH:mm") }, singleLine = true)
            OutlinedTextField(afterWork, { afterWork = it.take(5) }, label = { Text("After work · HH:mm") }, singleLine = true)
        }
    }, confirmButton = {
        TextButton(enabled = valid, onClick = {
            onSave(IntelligenceBriefingTimes(parse(morning), parse(evening), parse(afterWork)))
        }) { Text("Save schedule") }
    }, dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } })
}
