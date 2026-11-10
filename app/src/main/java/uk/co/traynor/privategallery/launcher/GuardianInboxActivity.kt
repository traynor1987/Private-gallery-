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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
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
            PrivateGalleryTheme {
                Surface(Modifier.fillMaxSize()) {
                    LauncherPanel("Guardian Inbox", ::finish) {
                        if (authenticated && !deviceLocked()) InboxContent()
                        else {
                            Text("Unlock with your device credential to review captured messages.")
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
    }

    private fun canReview() = authenticated && !deviceLocked() &&
        lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)

    private fun refresh() = loadArchive({ store, days, now -> store.inboxRows(days, now) }) { loaded ->
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

    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    private fun ColumnScope.InboxContent() {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            GuardianInboxSection.entries.forEach { section ->
                FilterChip(selected = selected == section, onClick = { selected = section },
                    label = { Text("${section.label} ${rows.count { it.section == section }}") })
            }
        }
        Text("Messages stay hidden until you choose to reveal one. Analysis is unavailable in this build.",
            style = MaterialTheme.typography.bodySmall)
        failure?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        val listed = rows.filter { it.section == selected }
        if (!busy && listed.isEmpty()) Text("No messages in this section.", Modifier.padding(vertical = 12.dp))
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(listed, key = { it.id }) { row ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Text(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(row.time)))
                        if (row.section == GuardianInboxSection.COOLING) {
                            val minutes = (row.remainingMillis + 59_999L) / 60_000L
                            Text("Preview held · about $minutes min remaining")
                        } else TextButton(onClick = { reveal(row) }, enabled = !busy) { Text("Reveal message") }
                    }
                }
            }
        }
        detail?.let { row ->
            AlertDialog(
                onDismissRequest = { detail = null; revealed = null },
                title = { Text("Captured message") },
                text = {
                    Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                        Text(revealed.orEmpty())
                        TextButton(onClick = { review(row, GuardianInboxAction.READY) }) { Text("Mark ready") }
                        TextButton(onClick = { review(row, GuardianInboxAction.HOLD) }) { Text("Hold for four hours") }
                        TextButton(onClick = { review(row, GuardianInboxAction.ARCHIVE) }) { Text("Move to history") }
                    }
                },
                confirmButton = { TextButton(onClick = { detail = null; revealed = null }) { Text("Close") } },
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
