package uk.co.traynor.privategallery.home2

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import kotlinx.coroutines.delay
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import uk.co.traynor.privategallery.core.guardian.*
import uk.co.traynor.privategallery.core.sanctuary.SanctuaryStore
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

@Composable
internal fun KeepMyPeace(sanctuary: SanctuaryStore, onBack: () -> Unit) {
    uk.co.traynor.privategallery.ui.CredentialCaptureProtection()
    val context = LocalContext.current.applicationContext
    val archive = remember(context) { GuardianCaptureStore(context) }
    val scope = rememberCoroutineScope()
    val activeSanctuary by sanctuary.current.collectAsState()
    var journal by remember { mutableStateOf<PeaceJournal?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var activity by remember { mutableStateOf("") }
    var feedback by remember { mutableStateOf<PeaceSession?>(null) }
    var delete by remember { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(30_000); now = System.currentTimeMillis() } }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val snapshot = archive.keepMyPeace()
                    checkNotNull(context.contentResolver.openOutputStream(uri, "wt")).bufferedWriter().use {
                        it.write(JSONArray(snapshot.encode()).toString())
                    }
                }
            }
            status = if (result.isSuccess) "Journal exported to the location you selected." else "Export could not be written."
        }
    }
    BackHandler(onBack = onBack)
    LaunchedEffect(archive) {
        journal = withContext(Dispatchers.IO) { runCatching { archive.keepMyPeace() }.getOrNull() }
        if (journal == null) status = "The encrypted journal could not be opened."
    }
    fun update(change: (PeaceJournal) -> PeaceJournal) {
        if (busy) return
        busy = true
        status = null
        scope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { archive.updateKeepMyPeace(change) } }
            busy = false
            result.onSuccess { journal = it }.onFailure { status = "The encrypted journal could not be saved." }
        }
    }
    SanctuaryScaffold("Keep My Peace", "Private time and optional reflections", onBack, "keep-my-peace") {
        status?.let { Text(it) }
        val current = journal ?: return@SanctuaryScaffold
        if (!current.enabled) {
            Text("Choose to track protected time on this device. You can turn tracking off and keep your existing journal.")
            Button(enabled = !busy, onClick = {
                update { it.copy(enabled = true, trackingSince = System.currentTimeMillis()) }
            }) { Text("Enable protected time tracking") }
        } else {
            val zone = ZoneId.systemDefault()
            val day = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
            val today = day.atStartOfDay(zone).toInstant().toEpochMilli()
            val week = day.minusDays((day.dayOfWeek.value - 1).toLong()).atStartOfDay(zone).toInstant().toEpochMilli()
            val trackingSince = current.trackingSince ?: now
            val recent = sanctuary.history().filter { it.endedAt >= trackingSince && it.endedAt >= now - current.days * 86_400_000L }
            val intervals = current.sessions.map { it.start to it.end } +
                listOfNotNull(current.activeSince?.let { it to now }, activeSanctuary?.startedAt?.let { maxOf(it, trackingSince) to now }) +
                recent.map { maxOf(it.startedAt, trackingSince) to it.endedAt }
            val todayMinutes = peaceProtectedMillis(intervals, today, now) / 60_000L
            val weekMinutes = peaceProtectedMillis(intervals, week, now) / 60_000L
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Protected time", style = MaterialTheme.typography.titleLarge)
                    Text("Today · $todayMinutes min")
                    Text("This week · $weekMinutes min")
                    Text("Personal goal · ${current.goalMinutes} min")
                    if (todayMinutes >= current.goalMinutes) Text("You reached your time goal today.")
                    Text("These totals describe time you chose to protect.")
                }
            }
            if (current.activeSince == null) {
                OutlinedTextField(activity, { activity = it.take(80) }, label = { Text("Chosen activity or app to take a break from") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                Button(enabled = !busy, onClick = {
                    val label = activity.trim()
                    update { it.copy(activeSince = System.currentTimeMillis(), activity = label) }
                }) { Text("Start a protected period") }
            } else {
                Text("Protected period in progress")
                Button(enabled = !busy, onClick = {
                    update { state ->
                        val start = state.activeSince ?: return@update state
                        val session = PeaceSession(UUID.randomUUID().toString(), start,
                            maxOf(start, System.currentTimeMillis()), state.activity)
                        state.copy(activeSince = null, activity = "", sessions = listOf(session) + state.sessions)
                    }
                }) { Text("Finish protected period") }
            }
            Text("Sanctuary history", style = MaterialTheme.typography.titleMedium)
            if (recent.isEmpty()) Text("No completed Sanctuary sessions since tracking was enabled.")
            recent.take(10).forEach {
                Text("${it.mode.label} · ${(it.endedAt - it.startedAt).coerceAtLeast(0L) / 60_000L} min")
            }
            TextButton(enabled = !busy, onClick = {
                update { it.copy(enabled = false, activeSince = null, activity = "") }
            }) { Text("Turn off tracking") }
        }
        if (current.enabled || current.sessions.isNotEmpty()) {
            val reports = summarizePersonalImpact(current.sessions.map(PeaceSession::asImpact))
            Text("${current.sessions.count { it.helpful == true }} periods reported helpful")
            Text("${reports.waiting[WaitingOutcome.BETTER] ?: 0} reports of feeling better after delaying review")
            Text("${current.sessions.count { it.reviewLater }} decisions to review later")
            val formatter = remember { DateTimeFormatter.ofPattern("d MMM, HH:mm") }
            current.sessions.forEach { entry ->
                val time = formatter.format(Instant.ofEpochMilli(entry.start).atZone(ZoneId.systemDefault()))
                TextButton(enabled = !busy, onClick = { feedback = entry }) {
                    Text("Reflect · $time${entry.activity.takeIf(String::isNotEmpty)?.let { " · $it" }.orEmpty()}")
                }
            }
            Text("Goal and retention", style = MaterialTheme.typography.titleMedium)
            Row {
                listOf(15, 30, 60).forEach { minutes ->
                    FilterChip(current.goalMinutes == minutes, { update { it.copy(goalMinutes = minutes) } }, { Text("${minutes}m") })
                }
            }
            Row {
                listOf(7, 30, 90).forEach { days ->
                    FilterChip(current.days == days, { update { it.copy(days = days) } }, { Text("${days}d") })
                }
            }
            Text("Export creates readable JSON in a location you choose. The device journal stays encrypted.")
            TextButton(enabled = !busy, onClick = { export.launch("nova-keep-my-peace.json") }) { Text("Export journal") }
        }
        TextButton(enabled = !busy, onClick = { delete = true }) { Text("Delete Keep My Peace journal") }
    }
    feedback?.let { entry ->
        PeaceReflection(entry, onSave = { revised ->
            update { state -> state.copy(sessions = state.sessions.map { if (it.id == revised.id) revised else it }) }
            feedback = null
        }, onClose = { feedback = null })
    }
    if (delete) AlertDialog(
        onDismissRequest = { delete = false }, title = { Text("Delete this journal?") },
        text = { Text("This removes your protected periods and reflections. Sanctuary’s own history stays in Sanctuary.") },
        confirmButton = { TextButton(onClick = {
            delete = false
            busy = true
            scope.launch {
                val result = withContext(Dispatchers.IO) { runCatching { archive.clearKeepMyPeace() } }
                busy = false
                result.onSuccess { journal = PeaceJournal() }.onFailure { status = "The journal could not be deleted." }
            }
        }) { Text("Delete journal") } },
        dismissButton = { TextButton(onClick = { delete = false }) { Text("Cancel") } },
    )
}

@Composable
private fun PeaceReflection(entry: PeaceSession, onSave: (PeaceSession) -> Unit, onClose: () -> Unit) {
    var report by remember(entry.id) { mutableStateOf(entry) }
    AlertDialog(
        onDismissRequest = onClose, title = { Text("How did the protected period feel?") },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                PeaceAnswer("Did it help?", report.helpful) { report = report.copy(helpful = it) }
                PeaceAnswer("Did you feel calmer?", report.calmer) { report = report.copy(calmer = it) }
                PeaceAnswer("Was it too restrictive?", report.tooRestrictive) { report = report.copy(tooRestrictive = it) }
                Text("How did delaying review feel?")
                WaitingOutcome.entries.zip(listOf("Better", "No difference", "Worse", "Not applicable")).forEach { (value, label) ->
                    FilterChip(report.waiting == value, { report = report.copy(waiting = value) }, { Text(label) })
                }
                TextButton(onClick = { report = report.copy(waiting = null) }) { Text("Skip waiting feedback") }
                Row {
                    Checkbox(report.reviewLater, { report = report.copy(reviewLater = it) })
                    Text("I chose to review later")
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(report) }) { Text("Save reflection") } },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } },
        properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn),
    )
}

@Composable
private fun PeaceAnswer(title: String, current: Boolean?, onChange: (Boolean?) -> Unit) {
    Text(title)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(true to "Yes", false to "No", null to "Skip").forEach { (value, label) ->
            FilterChip(current == value, { onChange(value) }, { Text(label) })
        }
    }
}
