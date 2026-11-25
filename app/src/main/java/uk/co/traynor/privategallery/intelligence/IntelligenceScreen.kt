package uk.co.traynor.privategallery.intelligence

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uk.co.traynor.privategallery.core.guardian.GuardianCaptureStore
import uk.co.traynor.privategallery.launcher.GuardianAlertDialog
import uk.co.traynor.privategallery.launcher.GuardianMuted
import uk.co.traynor.privategallery.launcher.GuardianNavy
import uk.co.traynor.privategallery.launcher.GuardianTheme

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
    conversation: IntelligenceConversation = remember(protectedBySanctuary) { IntelligenceConversation() },
    sanctuaryStatus: String? = null,
    assistant: IntelligenceAssistant? = null,
) {
    GuardianTheme {
        Surface(Modifier.fillMaxSize().testTag("nova-home-intelligence"), color = GuardianNavy) {
            if (active) key(protectedBySanctuary) {
                IntelligenceContent(guardianVisible, protectedBySanctuary, openPrivate, openDestination,
                    workspace, apps, openApp, openEvidence, onBack, emailProvider, conversation, sanctuaryStatus, assistant)
            }
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
    sanctuaryStatus: String?,
    assistantOverride: IntelligenceAssistant?,
) {
    val context = LocalContext.current.applicationContext
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val assistant = remember(context, assistantOverride) { assistantOverride ?: NovaIntelligenceAssistant(context) }
    val previewRevision by OwnerPreviewStore.changes.collectAsState()
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
    var memory by remember(workspace) { mutableStateOf<IntelligenceMemory?>(null) }
    var briefingName by remember(workspace) { mutableStateOf<String?>(null) }
    var input by chatSession.input
    var workBusy by remember { mutableStateOf(false) }
    val activeCloud by chatSession.activeCloud
    val activeCloudQuestion = activeCloud?.question
    val retryCloud by chatSession.retryCloud
    val busy = workBusy || activeCloud != null
    var feedback by chatSession.feedback
    var confirmation by remember { mutableStateOf<IntelligenceConfirmation?>(null) }
    var manageMemory by remember { mutableStateOf(false) }
    var scheduleOpen by remember { mutableStateOf(false) }
    var privatePrompt by remember { mutableStateOf(false) }
    var taskDate by chatSession.taskDate
    val privateAllowed = !protectedBySanctuary && workspace != null
    val today = now.toLocalDate()

    BackHandler(enabled = onBack != null) { onBack?.invoke() }
    DisposableEffect(lifecycle, ownerScope, chatSession) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                confirmation = null
                chatSession.cancelCloud()
                ownerScope.coroutineContext.cancelChildren()
            }
            if (event == Lifecycle.Event.ON_STOP && openPrivate == null) {
                chatSession.clear()
                memory = null
                briefingName = null
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
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
    LaunchedEffect(lifecycle, protectedBySanctuary, previewRevision, emailProvider, assistant) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            connection = withContext(Dispatchers.IO) {
                runCatching {
                    when {
                        protectedBySanctuary -> "Cloud chat paused during Sanctuary"
                        assistant.previewEnabled() -> "Owner Preview enabled · approved synthetic test only"
                        else -> intelligenceConnection(context)
                    }
                }.getOrDefault("Connection settings unavailable · local commands available")
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
                runCatching { checkNotNull(workspace).agenda(today, today.plusDays(7), now.zone,
                    includeOverdue = true, upcomingFrom = now.toInstant()) }
            }
        } else agenda = null
    }
    LaunchedEffect(workspace, privateAllowed, lifecycle) {
        if (!privateAllowed) {
            memory = null
            briefingName = null
            if (openPrivate == null) {
                chatSession.clear()
                confirmation = null
            }
        }
        if (privateAllowed) lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            val (stored, name) = withContext(Dispatchers.IO) {
                runCatching { workspace?.memory() }.getOrNull() to
                    runCatching { workspace?.briefingName() }.getOrNull()
            }
            if (memory == null && stored?.autoSave == true) {
                chat = (stored.exchanges + chat).takeLast(IntelligenceWorkspace.HISTORY_LIMIT)
            }
            memory = stored
            briefingName = name
        }
    }

    LaunchedEffect(workspace, privateAllowed, chat, memory?.autoSave) {
        val saved = memory
        if (!privateAllowed || saved?.autoSave != true || chat == saved.exchanges) return@LaunchedEffect
        val ownerWorkspace = workspace ?: return@LaunchedEffect
        val history = chat
        val generation = ownerWorkspace.memoryGeneration()
        val result = withContext(Dispatchers.IO) {
            val operation = coroutineContext.job
            runCatching { ownerWorkspace.saveAutomaticMemory(history, generation) { operation.isActive } }
        }
        if (result.isSuccess && result.getOrThrow() && memory?.autoSave == true) {
            memory = saved.copy(exchanges = history)
        } else if (result.isFailure && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            feedback = "The latest messages could not be saved in encrypted assistant memory."
        }
    }

    fun respond(question: String, answer: String) {
        chatSession.append(question, answer)
    }

    fun <T> runWork(error: String, work: suspend (Job) -> T, done: (T) -> Unit) {
        if (workBusy || chatSession.activeCloud.value != null || !lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return
        workBusy = true
        feedback = null
        ownerScope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                val operation = coroutineContext.job
                val result = withContext(Dispatchers.IO) { work(operation) }
                if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) done(result)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                feedback = error
            } finally {
                workBusy = false
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

    fun checkCloudChat() {
        if (protectedBySanctuary) return
        connection = "Checking Nova Brain connection…"
        runWork("Nova Brain connection settings are unavailable.", work = { job ->
            assistant.check {
                job.isActive && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            }
        }) { status -> connection = status }
    }

    fun confirmCloud(request: IntelligenceCloudRequest) {
        val preview = request.mode == IntelligenceCloudMode.OWNER_PREVIEW
        val title = if (preview) "Send synthetic question to Owner Preview?" else "Send one question to Nova Brain?"
        val destination = if (preview) "your separate Owner Preview on the Nova Brain VPS" else "your configured Nova Brain VPS"
        confirm(title, "This exact text goes to $destination and OpenAI. The server’s paid-request limits apply.\n\n${request.payload}", "Send question") {
            chatSession.sendCloud(request, ownerScope, assistant) {
                !protectedBySanctuary && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            }
        }
    }

    fun askCloud(question: String) {
        if (protectedBySanctuary) {
            respond(question, "Cloud questions are paused during Sanctuary. Local app shortcuts are available.")
            return
        }
        val preview = assistant.previewEnabled()
        val mode = if (preview) IntelligenceCloudMode.OWNER_PREVIEW else IntelligenceCloudMode.CONFIGURED_BRAIN
        val fixture = OwnerPreviewTest.ASK_NOVA.fixture
        if (preview && !question.equals(fixture, ignoreCase = true)) {
            respond(question, "Owner Preview accepts one approved Ask Nova question. Try “$fixture” or use a local command.")
            return
        }
        val allowedQuestion = if (preview) fixture else question
        val request = chatSession.retainedRequest(allowedQuestion, mode)
            ?: IntelligenceCloudRequest.prepare(allowedQuestion, chat, mode)
        confirmCloud(request)
    }

    fun retryCloudQuestion() {
        if (!busy && confirmation == null) retryCloud?.let(::confirmCloud)
    }

    fun cancelCloudQuestion() = chatSession.cancelCloud()

    fun clearConversation() {
        if (busy || confirmation != null) return
        val detail = if (privateAllowed) "This clears the chat here and in encrypted assistant memory."
            else "This clears the chat on this launcher screen."
        confirm("Clear conversation?", detail, "Clear conversation") {
            if (!privateAllowed) chatSession.clear()
            else {
                val autoSave = memory?.autoSave == true
                val empty = IntelligenceMemory(autoSave, emptyList(), autoSave = autoSave)
                runWork("Could not clear encrypted assistant memory.", { checkNotNull(workspace).saveMemory(empty) }) {
                    memory = empty
                    chatSession.clear()
                }
            }
        }
    }

    fun send() {
        val question = input.trim()
        if (question.isEmpty() || busy || confirmation != null || chatSession.activeCloud.value != null) return
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
                            agendaLine("Reminders", brief.reminders), brief.unscheduledTasks.takeIf { command.day == today && it.isNotEmpty() }
                                ?.let { agendaLine("Open tasks without a due date", it) }).joinToString("\n"))
                    }
                }
            }
            IntelligenceCommand.Reminders -> if (!privateAllowed) requirePrivate(question) else {
                runWork("Could not read upcoming reminders.", work = {
                    checkNotNull(workspace).agenda(today, today.plusDays(7), now.zone,
                        includeOverdue = false, upcomingFrom = now.toInstant())
                }) { brief -> respond(question, agendaLine("Upcoming reminders", brief.reminders)) }
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
    var showChat by chatSession.showCoverChat
    var manageName by remember { mutableStateOf(false) }
    IntelligenceExperience(
        onBack = onBack,
        showChat = showChat,
        onShowChat = { next ->
            if (!next) focus.clearFocus()
            showChat = next
        },
        protected = protectedBySanctuary,
        feedback = feedback,
        dashboard = { modifier ->
            IntelligenceDashboard(
                modifier = modifier,
                now = now,
                briefingName = briefingName,
                readyBriefing = briefing,
                guardian = guardian,
                guardianState = guardianState,
                sanctuaryStatus = sanctuaryStatus,
                protected = protectedBySanctuary,
                guardianVisible = guardianVisible,
                connection = connection,
                email = email,
                agenda = agenda,
                primaryUnlocked = privateAllowed,
                busy = busy,
                onUnlock = openPrivate,
                onCloudCheck = ::checkCloudChat,
                onShortcut = ::navigate,
                onInbox = {
                    confirm("Open Guardian Inbox?", "Guardian will apply its own authentication and review rules.",
                        "Open Guardian") { navigate(IntelligenceDestination.GUARDIAN) }
                },
                onSchedule = { scheduleOpen = true },
                onName = { manageName = true },
            )
        },
        conversation = { modifier, expanded ->
            IntelligenceChat(
                modifier = modifier,
                expanded = expanded,
                exchanges = chat,
                zone = now.zone,
                input = input,
                onInput = { input = it.take(IntelligenceCloudRequest.QUESTION_LIMIT).dropLastWhile(Char::isHighSurrogate) },
                onSend = ::send,
                busy = busy,
                pendingQuestion = activeCloudQuestion,
                retryAvailable = retryCloud != null,
                onCancel = ::cancelCloudQuestion,
                onRetry = ::retryCloudQuestion,
                privatePrompt = privatePrompt,
                onUnlock = openPrivate,
                onMemory = if (privateAllowed) ({ manageMemory = true }) else null,
                onClear = ::clearConversation,
            )
        },
    )

    if (manageName && privateAllowed) IntelligenceNameDialog(briefingName, onClose = { manageName = false }) { name ->
        manageName = false
        runWork("Could not save the greeting name.", work = { checkNotNull(workspace).saveBriefingName(name) }) {
            briefingName = name
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
        val autoSave = memory?.autoSave == true
        fun setAutomaticSave(enable: Boolean) {
            val stored = memory?.exchanges.orEmpty()
            val snapshot = IntelligenceMemory(true, if (enable) chat.ifEmpty { stored } else stored, autoSave = enable)
            manageMemory = false
            runWork("Could not change encrypted assistant memory.", { checkNotNull(workspace).saveMemory(snapshot) }) {
                if (snapshot.autoSave && chat.isEmpty()) chat = snapshot.exchanges
                memory = snapshot
            }
        }
        GuardianAlertDialog(onDismissRequest = { manageMemory = false }, title = { Text("Assistant memory") },
            text = {
                Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                    Text("Encrypted history stays in Primary Space. Each cloud send shows the exact text it will share for your approval.")
                    Text(if (autoSave) "Automatic history is on" else "Automatic history is off", color = GuardianMuted)
                    if (memory?.exchanges.orEmpty().isEmpty()) Text("No saved conversation", color = GuardianMuted)
                    if (!autoSave && !memory?.exchanges.isNullOrEmpty()) {
                        TextButton(onClick = {
                            chat = memory?.exchanges.orEmpty()
                            manageMemory = false
                        }) { Text("Load saved conversation") }
                    }
                    if (memory?.enabled == true) {
                        TextButton(onClick = {
                            manageMemory = false
                            confirm("Delete saved conversation?", "This clears the conversation in Primary Space and switches off automatic history.", "Delete memory") {
                                val empty = IntelligenceMemory(false, emptyList())
                                runWork("Could not delete assistant memory.", { checkNotNull(workspace).saveMemory(empty) }) {
                                    memory = empty
                                    chatSession.clear()
                                }
                            }
                        }) { Text("Delete saved conversation") }
                    }
                    if (!autoSave && chat.isNotEmpty()) {
                        TextButton(onClick = {
                            val snapshot = IntelligenceMemory(true, chat)
                            manageMemory = false
                            runWork("Could not save this conversation.", { checkNotNull(workspace).saveMemory(snapshot) }) { memory = snapshot }
                        }) { Text("Save current conversation") }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { setAutomaticSave(!autoSave) }) {
                    Text(if (autoSave) "Turn off auto-save" else "Enable encrypted history")
                }
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

private fun agendaLine(label: String, entries: List<String>) =
    "$label · " + entries.joinToString("; ").ifEmpty { "None scheduled" }

@Composable
private fun IntelligenceNameDialog(current: String?, onClose: () -> Unit, onSave: (String?) -> Unit) {
    var name by remember { mutableStateOf(current.orEmpty()) }
    GuardianAlertDialog(
        onDismissRequest = onClose,
        title = { Text("Greeting name") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { value ->
                    name = value.filterNot(Char::isISOControl).take(IntelligenceWorkspace.BRIEFING_NAME_LIMIT)
                },
                label = { Text("Name shown in private briefing") },
                supportingText = { Text("Leave blank to use the default greeting.") },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(name.trim().ifBlank { null }) }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } },
    )
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
