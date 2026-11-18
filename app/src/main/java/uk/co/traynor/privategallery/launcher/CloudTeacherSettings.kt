package uk.co.traynor.privategallery.launcher

import android.app.Activity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uk.co.traynor.privategallery.core.brain.*
import uk.co.traynor.privategallery.core.guardian.GuardianAssessment
import uk.co.traynor.privategallery.core.guardian.NovaBrainPattern
import java.time.Instant

private fun cloudDate(at: Instant) = java.text.DateFormat.getDateTimeInstance().format(java.util.Date.from(at))

@Composable
internal fun CloudTeacherSettings(canUse: () -> Boolean = { true }, onOpenHistory: (() -> Unit)? = null) {
    val context = LocalContext.current
    val store = remember(context) { CloudTeacherStore(context) }
    val revision by CloudTeacherStore.changes.collectAsState()
    val scope = rememberCoroutineScope()
    var enabled by remember { mutableStateOf(false) }
    var history by remember { mutableStateOf<List<CloudHistoryItem>?>(null) }
    var loading by remember { mutableStateOf(true) }
    var writing by remember { mutableStateOf(false) }
    var disclosure by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(revision) {
        if (!canUse()) return@LaunchedEffect
        loading = true
        try {
            val saved = withContext(Dispatchers.IO) { store.enabled() to store.history() }
            if (canUse()) { enabled = saved.first; history = saved.second; error = null }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) {
            enabled = false; history = null
            error = "Cloud Teacher settings could not be read securely. Paid requests remain blocked."
        } finally { loading = false }
    }
    fun savePermission(value: Boolean) {
        if (!canUse() || writing) return
        writing = true
        scope.launch {
            try {
                withContext(Dispatchers.IO) { store.setEnabled(value) }
                if (canUse()) { enabled = value; error = null }
            } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { error = "Permission could not be saved securely. Retry." }
            finally { writing = false }
        }
    }
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Cloud Teacher", style = MaterialTheme.typography.titleLarge)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Enable manual Cloud Teacher", Modifier.weight(1f))
                Switch(enabled = !loading && !writing && canUse(), checked = enabled,
                    modifier = Modifier.testTag("cloud-teacher-enable"), onCheckedChange = {
                        if (it) disclosure = true else savePermission(false)
                    })
            }
            Text(if (loading) "Reading encrypted permission…" else if (enabled)
                "Manual requests permitted · each excerpt still needs approval" else "Off · no cloud analysis requests")
            Text("No background analysis, automatic WhatsApp/evidence uploads or private feedback sharing. Connecting alone never enables paid analysis.")
            val last = history?.mapNotNull { it.completedAt }?.maxOrNull()
            Text("Last successful cloud analysis: " + if (history == null) "Unavailable" else last?.let(::cloudDate) ?: "Never",
                Modifier.testTag("cloud-teacher-last-success"))
            Text("Guardian, Evidence Locker, local learning and Sanctuary work offline.")
            TextButton(enabled = !writing && canUse(), onClick = {
                if (!canUse()) return@TextButton
                if (onOpenHistory != null) onOpenHistory()
                else (context as? Activity)?.let { GuardianInboxActivity.openSettings(it, "cloud-history") }
            }) { Text("Review or delete private cloud advisories") }
            Text("Private advisory history uses the existing Guardian device-authentication screen. Cloud predictions do not validate the Knowledge Meter.", style = MaterialTheme.typography.bodySmall)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
    if (disclosure) {
        var agreed by remember { mutableStateOf(false) }
        AlertDialog(onDismissRequest = { disclosure = false }, title = { Text("Allow manual Cloud Teacher?") },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Cloud Teacher asks OpenAI to suggest uncertain communication patterns in one excerpt you choose, edit and approve. It cannot establish intent or diagnose people.")
                    Text("Only that approved text goes through your saved authenticated HTTPS VPS connection. No whole conversations, files, hidden Vault contents or private feedback are selected automatically.")
                    Text("The server must permit analysis and reserve its maximum cost first. The current model, reservation and local budget are shown before sending. Those figures are not a verified provider credit balance.")
                    Text("The VPS retains request identities, keyed fingerprints and financial amounts, not excerpts or advice. This device keeps up to 20 encrypted classification results and cost/time metadata until you delete them. Excerpts and model summaries are not duplicated in history. OpenAI store=false does not guarantee zero retention; provider abuse-monitoring retention may still apply, normally up to 30 days under account policy.")
                    Text("Nothing trains Guardian or changes Sanctuary automatically. Review model suggestions and use the existing local owner-feedback controls. Turning this off blocks new requests; a request already sent may finish and be charged.")
                    Row {
                        Checkbox(agreed, { agreed = it }, modifier = Modifier.testTag("cloud-teacher-disclosure-consent"))
                        Text("Allow manual requests with a separate approval for every excerpt")
                    }
                }
            },
            confirmButton = { TextButton(enabled = agreed && canUse(), onClick = { disclosure = false; savePermission(true) }) { Text("Enable manual requests") } },
            dismissButton = { TextButton(onClick = { disclosure = false }) { Text("Cancel") } },
            properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn))
    }
}

@Composable
internal fun CloudLessonView(lesson: CloudLesson, showSummary: Boolean = true) {
    Text("AI advisory · model prediction, not verified fact", style = MaterialTheme.typography.titleSmall)
    Text("Suggested assessment: ${GuardianAssessment.valueOf(lesson.assessment).label}")
    Text("Uncertainty: ${lesson.uncertainty.lowercase().replaceFirstChar(Char::uppercase)}")
    if (lesson.patterns.isNotEmpty()) Text("Possible patterns: " + lesson.patterns.joinToString { name ->
        runCatching { NovaBrainPattern.valueOf(name).label }.getOrDefault("Repeated contact · context required")
    })
    Text(if (showSummary) lesson.summary else "Model summary was not retained for privacy. Review your original local example and feedback.")
    Text(lesson.usageCost?.let { "Token-price cost estimate: \$" + java.math.BigDecimal.valueOf(it, 6).toPlainString() }
        ?: "Usage cost not verified · the full reservation remains held")
    Text("Use the existing local labels or Personal Impact feedback to confirm your own experience. This advisory does not train, validate or change protection settings automatically.")
}

@Composable
internal fun CloudTeacherHistory(canUse: () -> Boolean, onClose: () -> Unit, onManageLearning: () -> Unit) {
    if (!canUse()) return
    val context = LocalContext.current.applicationContext
    val store = remember(context) { CloudTeacherStore(context) }
    val revision by CloudTeacherStore.changes.collectAsState()
    val scope = rememberCoroutineScope()
    var rows by remember { mutableStateOf<List<Pair<CloudHistoryItem, CloudLesson?>>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var deleteAll by remember { mutableStateOf(false) }
    LaunchedEffect(revision) {
        if (!canUse()) return@LaunchedEffect
        try {
            val saved = withContext(Dispatchers.IO) { store.history().map { it to store.readResult(it.id) } }
            if (canUse()) { rows = saved; error = null }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { rows = emptyList(); error = "History could not be read securely. You can retry deletion of this feature's encrypted records." }
    }
    fun remove(id: String?) {
        if (!canUse() || busy) return
        busy = true
        scope.launch {
            try {
                withContext(Dispatchers.IO) { if (id == null) store.clearHistory() else store.delete(id) }
                if (canUse()) error = null
            } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { error = "Deletion did not finish. Retry; the connection and Vault are untouched." }
            finally { busy = false }
        }
    }
    GuardianAlertDialog(onDismissRequest = onClose, title = { Text("Private cloud advisories") },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Encrypted classification and cost/time metadata on this device. No excerpts or model summaries are duplicated here. These are model suggestions, separate from confirmed local feedback.")
                if (rows.isEmpty() && error == null) Text("No saved cloud advisories")
                rows.forEach { (item, lesson) ->
                    Text("${item.source.name.lowercase().replaceFirstChar(Char::uppercase)} · ${cloudDate(item.completedAt ?: item.startedAt)}")
                    if (item.deleting) Text("Deletion pending · retry to finish")
                    else if (lesson != null) CloudLessonView(lesson, showSummary = false)
                    else Text("Outcome not confirmed · a sent request may have been charged. Reusing its request identity cannot charge again.")
                    TextButton(enabled = !busy && canUse(), onClick = { remove(item.id) }) { Text("Delete this advisory") }
                    HorizontalDivider()
                }
                error?.let { Text(it) }
                Text("Deleting history does not erase the server spending ledger, provider records or owner-confirmed local training. Existing Guardian and Teach Guardian controls can remove/reset local examples separately.")
                TextButton(enabled = !busy && canUse(), onClick = onManageLearning) { Text("Manage existing local learning") }
                TextButton(enabled = !busy && canUse(), onClick = { deleteAll = true }) { Text("Delete all cloud advisory history") }
            }
        }, confirmButton = { TextButton(onClick = onClose) { Text("Close") } },
        properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn))
    if (deleteAll && canUse()) GuardianAlertDialog(onDismissRequest = { deleteAll = false },
        title = { Text("Delete cloud advisory history?") },
        text = { Text("Remove this feature's encrypted advisories and pending request identities. An already-sent request may still be charged. Future new analyses need fresh approval and can incur a new charge. Your saved connection, Vault and local owner feedback remain.") },
        confirmButton = { TextButton(enabled = !busy, onClick = { deleteAll = false; remove(null) }) { Text("Delete history") } },
        dismissButton = { TextButton(onClick = { deleteAll = false }) { Text("Cancel") } },
        properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn))
}
