package uk.co.traynor.privategallery.launcher

import android.app.Activity
import android.app.KeyguardManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uk.co.traynor.privategallery.core.guardian.*
import uk.co.traynor.privategallery.ui.PrivateGalleryTheme
import uk.co.traynor.privategallery.core.ui.AppTheme
import java.text.DateFormat
import java.util.Date

/** Device-authenticated reader for the Guardian archive. No Vault authority is created here. */
class GuardianInboxActivity : ComponentActivity() {
    private var authenticated by mutableStateOf(false)
    private var rows by mutableStateOf<List<GuardianInboxRow>>(emptyList())
    private var selected by mutableStateOf(GuardianInboxSection.PENDING)
    private var revealed by mutableStateOf<String?>(null)
    private var detail by mutableStateOf<GuardianInboxRow?>(null)
    private var failure by mutableStateOf<String?>(null)
    private var busy by mutableStateOf(false)
    private var confirmDelete by mutableStateOf(false)
    private var settingsOpen by mutableStateOf(false)
    private var conversationContext by mutableStateOf(true)
    private var authenticationPending by mutableStateOf(false)
    private var operation: Job? = null
    private var expiry: Job? = null
    private var countdown: Job? = null

    private val credential = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val requested = authenticationPending
        authenticationPending = false
        if (!requested || result.resultCode != Activity.RESULT_OK ||
            !lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) || deviceLocked()
        ) return@registerForActivityResult
        authenticated = true
        expiry = lifecycleScope.launch {
            delay(120_000)
            lock()
        }
        refresh()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        selected = GuardianInboxSection.entries.firstOrNull { it.name == intent.getStringExtra(SECTION) }
            ?: GuardianInboxSection.PENDING
        setContent {
            PrivateGalleryTheme(AppTheme.DARK) {
                Surface(Modifier.fillMaxSize(), color = GuardianNavy) {
                    Box(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.TopCenter) {
                        Column(Modifier.widthIn(max = 720.dp).fillMaxSize().padding(horizontal = 16.dp)) {
                            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = ::finish) {
                                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back", tint = GuardianBlue)
                                }
                                Column(Modifier.weight(1f)) {
                                    Text("Guardian Inbox", Modifier.semantics { heading() },
                                        style = MaterialTheme.typography.headlineSmall)
                                    Text("Protected by Nova Guardian", color = GuardianMuted,
                                        style = MaterialTheme.typography.bodyMedium)
                                }
                                if (authenticated && !deviceLocked()) IconButton(onClick = {
                                    loadArchive({ store, _, _ -> store.usesConversationContext() }) {
                                        conversationContext = it
                                        settingsOpen = true
                                    }
                                }) {
                                    Icon(Icons.Outlined.Tune, "Inbox settings", tint = GuardianBlue)
                                }
                            }
                            if (authenticated && !deviceLocked()) InboxContent()
                            else Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                                GuardianCard("Inbox locked") {
                                    Text("Unlock with your device credential to review captured messages.", color = GuardianMuted)
                                    Button(onClick = ::authenticate, enabled = !authenticationPending) {
                                        Text("Unlock Guardian Inbox")
                                    }
                                    failure?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onPause() {
        if (authenticated) lock()
        super.onPause()
    }

    private fun deviceLocked(): Boolean = getSystemService(KeyguardManager::class.java)
        ?.let { it.isDeviceLocked || it.isKeyguardLocked } ?: true

    @Suppress("DEPRECATION")
    private fun authenticate() {
        if (authenticationPending || !lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return
        val prompt = getSystemService(KeyguardManager::class.java)
            ?.createConfirmDeviceCredentialIntent("Guardian Inbox", "Confirm your device credential to review messages.")
        val system = prompt?.let { packageManager.resolveActivity(it, PackageManager.MATCH_SYSTEM_ONLY)?.activityInfo }
        if (prompt == null || system == null) {
            failure = "Set up a secure Android screen lock before opening Guardian Inbox."
            return
        }
        failure = null
        authenticationPending = true
        if (runCatching { credential.launch(prompt.setComponent(ComponentName(system.packageName, system.name))) }.isFailure) {
            authenticationPending = false
            failure = "Android authentication could not be opened."
        }
    }

    private fun lock() {
        authenticated = false
        operation?.cancel()
        expiry?.cancel()
        countdown?.cancel()
        rows = emptyList()
        revealed = null
        detail = null
        busy = false
        failure = null
        confirmDelete = false
        settingsOpen = false
    }

    private fun canReview() = authenticated && !deviceLocked() &&
        lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)

    private fun refresh(): Unit = loadArchive({ store, days, now -> store.inboxRows(days, now) }) { loaded ->
        rows = loaded
        countdown?.cancel()
        loaded.map { it.remainingMillis }.filter { it > 0L }.minOrNull()?.let { remaining ->
            countdown = lifecycleScope.launch {
                delay(remaining)
                refresh()
            }
        }
    }

    private fun reveal(row: GuardianInboxRow) {
        loadArchive({ store, days, now -> store.revealInboxMessage(row.id, days, now) }) { text ->
            if (text == null) failure = "This message is no longer available to review."
            else {
                detail = row
                revealed = text
            }
        }
    }

    private fun review(row: GuardianInboxRow, action: GuardianInboxAction) {
        detail = null
        revealed = null
        confirmDelete = false
        loadArchive({ store, days, now -> store.reviewInboxMessage(row.id, action, days, now) }) { saved ->
            if (saved) {
                GuardianCapture.refresh(applicationContext)
                refresh()
            } else failure = "This message could not be updated."
        }
    }

    private fun <T> loadArchive(
        read: (GuardianCaptureStore, Int, GuardianInboxClock) -> T,
        publish: (T) -> Unit,
    ) {
        if (!canReview() || busy) return
        busy = true
        failure = null
        operation = lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    read(GuardianCaptureStore(applicationContext), GuardianCapture.retention(applicationContext),
                        guardianInboxClock(applicationContext))
                }
            }
            if (!canReview()) return@launch
            busy = false
            result.onSuccess(publish).onFailure { failure = "The encrypted Guardian archive could not be read." }
        }
    }

    private fun teach(row: GuardianInboxRow, assessment: GuardianAssessment) {
        loadArchive({ store, days, now ->
            val text = store.revealInboxMessage(row.id, days, now) ?: return@loadArchive false
            store.teach(text, assessment) && store.assessInboxMessage(row.id, days, now) != null
        }) { saved ->
            if (saved) {
                detail = null
                revealed = null
                GuardianCapture.refresh(applicationContext)
                refresh()
            } else failure = "This message does not contain enough text to teach Nova Brain."
        }
    }

    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    private fun ColumnScope.InboxContent() {
        val listed = rows.filter { it.section == selected }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 18.dp)) {
            item {
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val overview = listOf(GuardianInboxSection.PENDING to "Awaiting", GuardianInboxSection.READY to "Ready",
                        GuardianInboxSection.COOLING to "Cooling")
                    overview.forEach { (section, label) ->
                        Card(onClick = { selected = section }, modifier = Modifier.weight(1f).fillMaxHeight(),
                            shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = GuardianGlass),
                            border = BorderStroke(1.dp, if (section == selected) GuardianBlue else GuardianBorder)) {
                            Column(Modifier.fillMaxWidth().padding(horizontal = 5.dp, vertical = 12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(if (busy && rows.isEmpty()) "…" else rows.count { it.section == section }.toString(),
                                    style = MaterialTheme.typography.headlineSmall)
                                Text(label, color = GuardianMuted, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    GuardianInboxSection.entries.forEach { section ->
                        FilterChip(selected = selected == section, onClick = { selected = section },
                            label = { Text(section.label) })
                    }
                }
            }
            item {
                GuardianCard(selected.label, badge = if (busy) "Checking" else listed.size.toString()) {
                    Text(if (listed.isEmpty() && !busy) "No messages in this section."
                        else "Message previews stay hidden until you choose to review them.", color = GuardianMuted)
                    failure?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            }
            items(listed, key = { it.id }) { row ->
                val cooling = row.section == GuardianInboxSection.COOLING
                val caption = if (cooling) "Preview held · about ${(row.remainingMillis + 59_999L) / 60_000L} min remaining"
                    else "Content hidden until reviewed"
                Card(onClick = { reveal(row) }, enabled = !busy && !cooling, modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = GuardianGlass, disabledContainerColor = GuardianGlass,
                        disabledContentColor = MaterialTheme.colorScheme.onSurface)) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Outlined.Lock, null, tint = GuardianBlue, modifier = Modifier.size(20.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text("Protected message", style = MaterialTheme.typography.titleMedium)
                            Text(caption, color = GuardianMuted, style = MaterialTheme.typography.bodySmall)
                            row.assessment?.let { Text("Local estimate · ${it.label}", color = GuardianMuted,
                                style = MaterialTheme.typography.bodySmall) }
                            Text(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(row.time)),
                                color = GuardianMuted, style = MaterialTheme.typography.bodySmall)
                        }
                        if (!cooling) Icon(Icons.Outlined.ChevronRight, "Reveal message", tint = GuardianBlue)
                    }
                }
            }
            item {
                Row(Modifier.padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    Icon(Icons.Outlined.Shield, null, tint = GuardianBlue)
                    Text("Nova Brain uses local examples. Its assessments are estimates.", color = GuardianMuted)
                }
            }
        }
        if (settingsOpen) AlertDialog(
            onDismissRequest = { settingsOpen = false },
            title = { Text("Nova Brain · Local Only") },
            text = {
                Column {
                    Text("Cloud analysis is unavailable. Reset removes saved learning examples for future assessments.")
                    LauncherToggle("Use recent local conversation context", conversationContext) { enabled ->
                        loadArchive({ store, _, _ -> store.setConversationContext(enabled) }) { conversationContext = enabled }
                    }
                }
            },
            confirmButton = { TextButton(enabled = !busy, onClick = {
                settingsOpen = false
                loadArchive({ store, _, _ -> store.resetLearning() }) {
                    GuardianCapture.refresh(applicationContext)
                    refresh()
                }
            }) { Text("Reset local learning") } },
            dismissButton = { TextButton(onClick = { settingsOpen = false }) { Text("Cancel") } },
            properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn),
        )
        detail?.let { row ->
            AlertDialog(
                onDismissRequest = { detail = null; revealed = null; confirmDelete = false },
                title = { Text(if (confirmDelete) "Delete captured message?" else "Captured message") },
                text = {
                    if (confirmDelete) Text("Delete this copy from the Guardian archive.")
                    else Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                        Text(revealed.orEmpty())
                        TextButton(enabled = !busy, onClick = {
                            loadArchive({ store, days, now -> store.assessInboxMessage(row.id, days, now) }) { result ->
                                if (result != null) detail = row.copy(assessment = result)
                            }
                        }) { Text("Analyse on this device") }
                        row.assessment?.let { Text("Local estimate · ${it.label}", color = GuardianMuted) }
                        GuardianAssessment.entries.forEach { assessment ->
                            TextButton(onClick = { teach(row, assessment) }, enabled = !busy) {
                                Text("Teach Nova Brain · ${assessment.label}")
                            }
                        }
                        TextButton(enabled = !busy, onClick = {
                            loadArchive({ store, days, now ->
                                store.revealInboxMessage(row.id, days, now)?.let(store::forgetExample) == true
                            }) { removed ->
                                if (removed) {
                                    GuardianCapture.refresh(applicationContext)
                                    detail = null
                                    revealed = null
                                    refresh()
                                } else failure = "No saved example matched this message."
                            }
                        }) { Text("Forget this training example") }
                        TextButton(onClick = { review(row, GuardianInboxAction.READY) }) { Text("Mark ready") }
                        TextButton(onClick = { review(row, GuardianInboxAction.HOLD) }) { Text("Hold for four hours") }
                        TextButton(onClick = { review(row, GuardianInboxAction.ARCHIVE) }) { Text("Move to history") }
                        TextButton(onClick = { confirmDelete = true }) { Text("Delete message") }
                        failure?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        if (confirmDelete) review(row, GuardianInboxAction.DELETE)
                        else { detail = null; revealed = null }
                    }) { Text(if (confirmDelete) "Delete" else "Close") }
                },
                dismissButton = if (confirmDelete) {
                    { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
                } else null,
                properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn),
            )
        }
    }

    companion object {
        private const val SECTION = "guardian-inbox-section"

        internal fun open(activity: Activity, section: GuardianInboxSection) {
            activity.startActivity(Intent(activity, GuardianInboxActivity::class.java).putExtra(SECTION, section.name))
        }
    }
}
