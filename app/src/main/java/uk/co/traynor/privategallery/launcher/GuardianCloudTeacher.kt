package uk.co.traynor.privategallery.launcher

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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import uk.co.traynor.privategallery.core.brain.*

@Composable
internal fun GuardianCloudTeacher(message: String? = null, historicalEvidence: Boolean = false,
    client: CloudTeacherClient = CloudTeacherClient(), canSend: () -> Boolean) {
    var open by remember(message) { mutableStateOf(false) }
    TextButton(onClick = { if (canSend()) open = true }) { Text(when {
        historicalEvidence -> "Review OCR excerpt for cloud analysis"
        message == null -> "Cloud Teacher status"
        else -> "Ask Cloud Teacher"
    }) }
    if (!open) return
    val context = LocalContext.current.applicationContext
    val store = remember(context) { CloudTeacherStore(context) }
    val connectionStore = remember(context) { NovaBrainConnectionStore(context) }
    val revision by CloudTeacherStore.changes.collectAsState()
    val scope = rememberCoroutineScope()
    var configuration by remember { mutableStateOf<NovaBrainConfiguration?>(null) }
    var enabled by remember { mutableStateOf(false) }
    var readingSettings by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var approved by remember { mutableStateOf(false) }
    var upload by remember(message) { mutableStateOf(message.orEmpty()) }
    var server by remember { mutableStateOf<CloudTeacherStatus?>(null) }
    var checkedAt by remember { mutableStateOf<Instant?>(null) }
    var lesson by remember { mutableStateOf<CloudLesson?>(null) }
    var cached by remember { mutableStateOf(false) }
    var forceNew by remember { mutableStateOf(false) }
    var feedback by remember { mutableStateOf<String?>(null) }
    var readGeneration by remember { mutableIntStateOf(0) }
    // Start the reader from committed composition using the remembered scope,
    // as the existing connection screen does. Each revision owns its completion.
    DisposableEffect(revision) {
        val ticket = ++readGeneration
        readingSettings = true
        val reader = scope.launch {
            try {
                val saved = withContext(Dispatchers.IO) { connectionStore.read() to store.enabled() }
                if (ticket == readGeneration && canSend()) { configuration = saved.first; enabled = saved.second }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                if (ticket == readGeneration) { configuration = null; enabled = false; feedback = "Saved settings could not be read securely. Local review remains available." }
            } finally { if (ticket == readGeneration) readingSettings = false }
        }
        onDispose { readGeneration++; reader.cancel() }
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) { approved = false; upload = ""; configuration = null; server = null; lesson = null; open = false }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    GuardianAlertDialog(onDismissRequest = { open = false }, title = { Text("Cloud Teacher") },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(when {
                    readingSettings -> "Reading encrypted Cloud Teacher settings…"
                    enabled -> "Manual Cloud Teacher permitted · per-request approval required"
                    else -> "Cloud Teacher is off · enable it in Settings → Nova Brain after reviewing its disclosure"
                }, Modifier.testTag("cloud-teacher-permission-status"))
                Text("Uses your saved Nova Brain connection. No token needs to be re-entered. Local Guardian protection continues independently.")
                TextButton(enabled = !busy && configuration != null && canSend(), onClick = {
                    if (!canSend()) return@TextButton
                    val saved = configuration ?: return@TextButton
                    busy = true; feedback = null; server = null; approved = false
                    scope.launch {
                        try {
                            val response = withContext(Dispatchers.IO) { client.status(saved) }
                            if (canSend()) { server = response; checkedAt = Instant.now() }
                        } catch (cancelled: CancellationException) { throw cancelled }
                        catch (error: CloudTeacherException) { feedback = error.failure.userMessage }
                        catch (_: Exception) { feedback = "Authenticated status could not be verified. Local review remains available." }
                        finally { busy = false }
                    }
                }) { Text("Check connection and budget") }
                checkedAt?.let { Text("Authenticated budget check: $it") }
                server?.let { status ->
                    Text(if (status.staging) "Staging · analysis disabled" else if (status.analysisEnabled) "Server analysis available" else "Server analysis disabled")
                    status.lines().forEach { Text(it) }
                }
                if (message != null) {
                    Text("Only the exact text below will be sent to your VPS and OpenAI. Edit or redact it first. Nothing else from the conversation, evidence file or private feedback is included.")
                    OutlinedTextField(upload, { upload = it.take(4096); approved = false; forceNew = false; lesson = null }, enabled = !busy,
                        modifier = Modifier.testTag("cloud-teacher-approved-excerpt"), label = { Text("Exact excerpt to send") }, maxLines = 8)
                    Row {
                        Checkbox(approved, { approved = it }, enabled = enabled && !busy && server?.canAnalyze == true,
                            modifier = Modifier.testTag("cloud-teacher-request-consent"))
                        Text("Approve only this edited excerpt and the server's stated maximum reservation")
                    }
                    Text("No automatic paid retry. A timeout may still be charged; reusing the saved request identity cannot charge again.")
                }
                lesson?.let {
                    if (cached) Text("Saved classification reused · no new cloud request or charge")
                    CloudLessonView(it, showSummary = !cached)
                    TextButton(enabled = !busy && server?.canAnalyze == true, onClick = {
                        forceNew = true; approved = false; lesson = null
                        feedback = "A new analysis can incur a new charge. Review the excerpt and approve it again."
                    }) { Text("Start a new paid analysis") }
                }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                feedback?.let { Text(it) }
                Text("Use the existing local labels or Personal Impact feedback after reviewing suggestions. Only your explicit local corrections train the shared Nova Brain; this screen never marks it validated.")
            }
        },
        confirmButton = {
            if (message != null) TextButton(enabled = enabled && !busy && approved && canSend() &&
                configuration != null && server?.canAnalyze == true && upload.isNotBlank(), onClick = {
                if (!canSend()) return@TextButton
                val saved = configuration ?: return@TextButton
                val status = server ?: return@TextButton
                val excerpt = upload
                val newRequest = forceNew
                approved = false; busy = true; lesson = null; feedback = null
                scope.launch {
                    try {
                        val result = withContext(Dispatchers.IO) {
                            val plan = store.prepare(saved, excerpt, if (historicalEvidence) CloudExampleSource.EVIDENCE else CloudExampleSource.GUARDIAN, newRequest)
                            if (plan.cached != null) Triple(plan.cached, true, status)
                            else {
                                val reply = client.analyze(saved, excerpt, true, plan.id, status,
                                    permission = { canSend() && store.enabled() && connectionStore.read() == saved }, cancelled = { !isActive })
                                if (!store.recordSuccess(plan.id, saved, reply.lesson)) error("Configuration or permission changed")
                                Triple(reply.lesson, false, reply.status)
                            }
                        }
                        if (canSend()) { lesson = result.first; cached = result.second; server = result.third; forceNew = false }
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (error: CloudTeacherException) { feedback = error.failure.userMessage }
                    catch (_: Exception) { feedback = "The request or encrypted record could not be completed. No automatic retry will occur; check history and the server budget." }
                    finally { busy = false }
                }
            }) { Text("Send this message to OpenAI") }
        },
        dismissButton = { TextButton(onClick = { open = false }) { Text(if (busy) "Close · a sent request may be charged" else "Close") } },
        properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn))
}
