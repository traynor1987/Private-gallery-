package uk.co.traynor.privategallery.launcher

import android.app.Activity
import android.app.KeyguardManager
import android.content.ClipData
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.core.content.FileProvider
import androidx.compose.ui.platform.testTag
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
import java.io.File
import java.util.UUID
import java.text.DateFormat
import java.util.Date

/** Device-authenticated reader for the Guardian archive. No Vault authority is created here. */
class GuardianInboxActivity : ComponentActivity() {
    private var authenticated by mutableStateOf(false)
    private var rows by mutableStateOf<List<GuardianInboxRow>>(emptyList())
    private var selected by mutableStateOf(GuardianInboxSection.PENDING)
    private var detail by mutableStateOf<GuardianInboxRow?>(null)
    private var failure by mutableStateOf<String?>(null)
    private var busy by mutableStateOf(false)
    private var confirmDelete by mutableStateOf(false)
    private var settingsOpen by mutableStateOf(false)
    private var impactSettings by mutableStateOf(PersonalImpactSettings())
    private var impactSuggestions by mutableStateOf<List<String>>(emptyList())
    private var impactMessage by mutableStateOf<String?>(null)
    private var pastImpact by mutableStateOf<MessageImpact?>(null)
    private var impactFeedback by mutableStateOf<PersonalImpact?>(null)
    private var clearImpactOpen by mutableStateOf(false)
    private var validationOpen by mutableStateOf(false)
    private var callShieldOpen by mutableStateOf(false)
    private val privacy by lazy { getSharedPreferences("guardian_inbox_privacy", MODE_PRIVATE) }
    private var showSenders by mutableStateOf(true)
    private var diagnostic by mutableStateOf(false)
    private var conversationsOpen by mutableStateOf(false)
    private var conversation by mutableStateOf<GuardianConversationKey?>(null)
    private var conversationPreview by mutableStateOf<Pair<String, String>?>(null)
    private var cloudConversations by mutableStateOf(emptySet<GuardianConversationKey>())
    private var contactsOpen by mutableStateOf(false)
    private var contacts by mutableStateOf<List<GuardianContact>>(emptyList())
    private var query by mutableStateOf("")
    private var matches by mutableStateOf<Set<String>?>(null)
    private var conversationContext by mutableStateOf(true)
    private var authenticationPending by mutableStateOf(false)
    private var operation: Job? = null
    private var expiry: Job? = null
    private var countdown: Job? = null
    private var linkedMessageId: String? = null

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
        if (intent.getBooleanExtra(OPEN_IMPACT, false)) {
            intent.removeExtra(OPEN_IMPACT)
            loadArchive({ store, _, _ ->
                val settings = store.personalImpactSettings()
                settings to personalImpactSuggestions(settings, store.personalImpactReport().first)
            }) {
                impactSettings = it.first
                impactSuggestions = it.second
                validationOpen = true
                refresh()
            }
        } else refresh()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        linkedMessageId = intent.getStringExtra(LINKED_MESSAGE)
        intent.removeExtra(LINKED_MESSAGE)
        showSenders = privacy.getBoolean("authenticated_sender_names", true)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        selected = GuardianInboxSection.entries.firstOrNull { it.name == intent.getStringExtra(SECTION) }
            ?: GuardianInboxSection.PENDING
        setContent {
            GuardianTheme {
                Surface(Modifier.fillMaxSize(), color = GuardianNavy, contentColor = GuardianPrimary) {
                    Box(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.TopCenter) {
                        val layer = rememberGraphicsLayer()
                        Column(Modifier.widthIn(max = 720.dp).fillMaxSize().then(if (diagnostic) Modifier.drawWithContent {
                            layer.record { this@drawWithContent.drawContent() }
                            drawLayer(layer)
                        } else Modifier).background(GuardianNavy).padding(horizontal = 16.dp)) {
                            val thread = conversation.takeIf { authenticated && !deviceLocked() && !diagnostic && !contactsOpen }
                            BackHandler(enabled = diagnostic || contactsOpen || conversationsOpen || detail != null) { navigateBack() }
                            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = ::navigateBack) {
                                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back", tint = GuardianBlue)
                                }
                                Column(Modifier.weight(1f)) {
                                    Text(if (thread == null && detail == null) "Guardian Inbox" else "Guardian Conversation",
                                        Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineSmall)
                                    Text(if (thread != null) conversationSubtitle(thread) else detail?.let { row -> listOfNotNull(row.senderName.takeIf { showSenders }, appLabel(row.conversation)).joinToString(" · ") } ?: "Protected by Nova Guardian",
                                        color = GuardianMuted, style = MaterialTheme.typography.bodyMedium)
                                }
                                if (thread != null || detail != null) {
                                    Icon(Icons.Outlined.Shield, null, tint = GuardianMint)
                                } else if (authenticated && !deviceLocked() && !diagnostic) IconButton(onClick = {
                                    loadArchive({ store, _, _ -> store.usesConversationContext() }) {
                                        conversationContext = it
                                        settingsOpen = true
                                    }
                                }) {
                                    Icon(Icons.Outlined.Tune, "Inbox settings", tint = GuardianBlue)
                                }
                            }
                            if (authenticated && !deviceLocked()) {
                                if (diagnostic) {
                                    Text("Safe diagnostic preview · names, dates and messages omitted", color = GuardianMuted)
                                    TextButton(enabled = !busy, onClick = {
                                        if (canReview() && !busy) {
                                            busy = true
                                            operation = lifecycleScope.launch {
                                                val result = runCatching {
                                                    val bitmap = layer.toImageBitmap().asAndroidBitmap()
                                                    withContext(Dispatchers.IO) { diagnosticImage(bitmap) }
                                                }
                                                if (!canReview() || !diagnostic) return@launch
                                                busy = false
                                                result.onSuccess { file ->
                                                    val uri = FileProvider.getUriForFile(this@GuardianInboxActivity,
                                                        "$packageName.fileprovider", file)
                                                    val send = Intent(Intent.ACTION_SEND).setType("image/png")
                                                        .putExtra(Intent.EXTRA_STREAM, uri)
                                                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                    send.clipData = ClipData.newUri(contentResolver, "Guardian diagnostic", uri)
                                                    if (runCatching { startActivity(Intent.createChooser(send, "Share safe diagnostic view")) }.isFailure) {
                                                        failure = "Android could not open the share menu."
                                                    }
                                                }.onFailure { failure = "The safe diagnostic image could not be created." }
                                            }
                                        }
                                    }) { Text("Share redacted image") }
                                }
                                if (contactsOpen && !diagnostic) MonitoredContacts()
                                else if (thread != null || detail != null) ConversationDetail(thread)
                                else if (conversationsOpen && !diagnostic) Conversations()
                                else InboxContent(redacted = diagnostic)
                            }
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
        cloudConversations = emptySet()
        operation?.cancel()
        expiry?.cancel()
        countdown?.cancel()
        rows = emptyList()
        detail = null
        busy = false
        failure = null
        confirmDelete = false
        settingsOpen = false
        impactSettings = PersonalImpactSettings()
        impactSuggestions = emptyList()
        impactMessage = null
        impactFeedback = null
        pastImpact = null
        clearImpactOpen = false
        validationOpen = false
        callShieldOpen = false
        diagnostic = false
        conversationsOpen = false
        conversation = null
        conversationPreview = null
        contactsOpen = false
        contacts = emptyList()
        query = ""
        matches = null
        GuardianCapture.stopContactDiscovery()
    }

    private fun navigateBack() {
        when {
            diagnostic -> diagnostic = false
            contactsOpen -> { contactsOpen = false; GuardianCapture.stopContactDiscovery() }
            conversation != null || detail != null -> {
                detail = null
                confirmDelete = false
                conversation = null
                conversationPreview = null
                query = ""
                matches = null
                failure = null
            }
            conversationsOpen -> conversationsOpen = false
            else -> finish()
        }
    }

    private fun canReview() = authenticated && !deviceLocked() &&
        lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)

    private fun refresh(): Unit = loadArchive({ store, days, now -> store.inboxRows(days, now) }) { loaded ->
        rows = loaded
        linkedMessageId?.let { id ->
            linkedMessageId = null
            val linked = loaded.firstOrNull { it.id == id }
            if (linked == null) failure = "The linked Guardian message is no longer available."
            else {
                selected = linked.section
                detail = linked
                conversation = linked.conversation
                conversationPreview = null
            }
        }
        if (conversationPreview?.first?.let { id ->
                loaded.none { it.id == id && it.section != GuardianInboxSection.COOLING }
            } == true) conversationPreview = null
        matches = null
        query = ""
        countdown?.cancel()
        loaded.map { it.remainingMillis }.filter { it > 0L }.minOrNull()?.let { remaining ->
            countdown = lifecycleScope.launch {
                delay(remaining)
                refresh()
            }
        }
    }

    private fun reveal(row: GuardianInboxRow) {
        if (!canReview() || busy) return
        detail = row
        conversation = row.conversation
        conversationPreview = null
    }

    private fun review(row: GuardianInboxRow, action: GuardianInboxAction) {
        conversationPreview = null
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

    private fun diagnosticImage(bitmap: android.graphics.Bitmap): File {
        val directory = File(cacheDir, "share-copies").apply { mkdirs() }
        directory.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() >= 15 * 60 * 1000L }
            ?.forEach { it.delete() }
        val output = File(directory, "${UUID.randomUUID()}.png")
        try {
            output.outputStream().use { check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)) }
        } catch (failure: Exception) {
            output.delete()
            throw failure
        }
        return output
    }

    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    private fun ColumnScope.InboxContent(redacted: Boolean) {
        val displayed = if (redacted) guardianDiagnosticRows(rows) else rows
        val listed = displayed.filter { it.section == selected }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 18.dp)) {
            item {
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val overview = listOf(GuardianInboxSection.PENDING to "Awaiting", GuardianInboxSection.READY to "Ready",
                        GuardianInboxSection.COOLING to "Cooling")
                    overview.forEach { (section, label) ->
                        Card(onClick = { selected = section }, modifier = Modifier.weight(1f).fillMaxHeight(),
                            shape = RoundedCornerShape(18.dp), colors = guardianCardColors(),
                            border = BorderStroke(1.dp, if (section == selected) GuardianBlue else GuardianBorder)) {
                            Column(Modifier.fillMaxWidth().padding(horizontal = 5.dp, vertical = 12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(if (busy && rows.isEmpty()) "…" else displayed.count { it.section == section }.toString(),
                                    style = MaterialTheme.typography.headlineSmall)
                                Text(label, color = GuardianMuted, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (!redacted) TextButton(onClick = { conversationsOpen = true }) { Text("Conversations") }
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
            items(listed, key = { it.id }) { row -> MessageCard(row, redacted) }
            item {
                Row(Modifier.padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    Icon(Icons.Outlined.Shield, null, tint = GuardianBlue)
                    Text("Nova Brain uses local examples. Its assessments are estimates.", color = GuardianMuted)
                }
            }
        }
        InboxDialogs()
    }

    @Composable
    private fun InboxDialogs() {
        if (canReview()) impactMessage?.let { id ->
            PersonalImpactDialog(impactFeedback, pastImpact, busy, onSave = { feedback ->
                loadArchive({ store, days, now -> store.savePersonalImpact(id, feedback, days, now) }) {
                    if (!it) failure = "Feedback could not be saved for this message."
                    impactMessage = null
                    impactFeedback = null
                }
            }, onDelete = {
                loadArchive({ store, _, _ -> store.deletePersonalImpact(id) }) {
                    impactMessage = null
                    impactFeedback = null
                }
            }, onClose = { impactMessage = null; impactFeedback = null })
        }
        if (clearImpactOpen && canReview()) GuardianAlertDialog(
            onDismissRequest = { clearImpactOpen = false },
            title = { Text("Delete all personal impact feedback?") },
            text = { Text("This also turns off collection and learning. Guardian and Evidence Locker records are kept.") },
            confirmButton = {
                TextButton(enabled = !busy, onClick = {
                    loadArchive({ store, _, _ -> store.clearPersonalImpact() }) {
                        impactSettings = PersonalImpactSettings()
                        clearImpactOpen = false
                    }
                }) { Text("Delete all feedback") }
            },
            dismissButton = { TextButton(onClick = { clearImpactOpen = false }) { Text("Cancel") } },
        )

        if (validationOpen && canReview()) {
            var input by remember { mutableStateOf("") }
            var result by remember { mutableStateOf<String?>(null) }
            GuardianAlertDialog(
                onDismissRequest = { if (!busy) validationOpen = false },
                title = { Text("Nova Brain settings") },
                text = {
                    Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                        NovaBrainKnowledgeCard(showPersonalImpact = true)
                        if (impactSettings.collect && impactSettings.learn) impactSuggestions.forEach { Text(it) }
                        GuardianCloudTeacher { validationOpen && canReview() }
                        LauncherToggle("Collect personal impact feedback", impactSettings.collect) { enabled ->
                            val updated = impactSettings.copy(collect = enabled, learn = enabled && impactSettings.learn)
                            loadArchive({ store, _, _ -> store.savePersonalImpactSettings(updated) }) { impactSettings = updated }
                        }
                        if (impactSettings.collect) LauncherToggle("Use feedback for local personalisation", impactSettings.learn) { enabled ->
                            val updated = impactSettings.copy(learn = enabled)
                            loadArchive({ store, _, _ -> store.savePersonalImpactSettings(updated) }) { impactSettings = updated }
                        }
                        Text("Feedback retention · ${impactSettings.days} days")
                        GuardianCaptureStore.RETENTION_DAYS.sorted().forEach { days ->
                            TextButton(enabled = !busy && days != impactSettings.days, onClick = {
                                val updated = impactSettings.copy(days = days)
                                loadArchive({ store, _, _ -> store.savePersonalImpactSettings(updated) }) { impactSettings = updated }
                            }) { Text("Keep for $days days") }
                        }
                        TextButton(enabled = !busy, onClick = { clearImpactOpen = true }) { Text("Delete all personal impact feedback") }
                        Text("Paste independently labelled examples from conversations excluded from training. Use JSON rows with conversationId, text and patterns. Use an empty patterns array for ordinary messages.")
                        Text("Pattern labels: " + NovaBrainPattern.entries.joinToString { it.name })
                        OutlinedTextField(input, { if (it.length <= MAX_VALIDATION_INPUT) input = it },
                            enabled = !busy, label = { Text("Held-out examples JSON") }, maxLines = 6)
                        result?.let { Text(it) }
                        failure?.let { Text(it) }
                    }
                },
                confirmButton = {
                    TextButton(enabled = !busy && input.isNotBlank(), onClick = {
                        val approved = input
                        input = ""
                        loadArchive({ store, _, _ ->
                            runCatching { store.measureKnowledge(parseNovaBrainValidation(approved)) }.getOrNull()
                        }) { measured ->
                            result = measured?.knowledgePercent()?.let { "Validation score saved: $it%" }
                                ?: "Validation rejected. Check the format, sample counts and overlap with training."
                        }
                    }) { Text("Run local evaluation") }
                },
                dismissButton = { TextButton(enabled = !busy, onClick = { validationOpen = false }) { Text("Close") } },
                properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn),
            )
        }
        if (callShieldOpen) GuardianCallShieldScreen { callShieldOpen = false }
        if (settingsOpen) GuardianAlertDialog(
            onDismissRequest = { settingsOpen = false },
            title = { Text("Inbox privacy") },
            text = {
                Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
                    LauncherToggle("Show sender names after authentication", showSenders) { enabled ->
                        privacy.edit().putBoolean("authenticated_sender_names", enabled).apply()
                        showSenders = enabled
                    }
                    Text("Guardian Home and pop-ups keep sender names hidden.")
                    Text("Message details require authentication. Active holds always hide message text.")
                    TextButton(onClick = {
                        loadArchive({ store, _, _ -> store.monitoredContacts() }) {
                            contacts = it
                            settingsOpen = false
                            contactsOpen = true
                        }
                    }) { Text("Monitored Contacts") }
                    GuardianCloudTeacher { settingsOpen && canReview() }
                    TextButton(onClick = {
                        loadArchive({ store, _, _ -> store.personalImpactSettings() }) {
                            impactSettings = it
                            settingsOpen = false
                            validationOpen = true
                        }
                    }) {
                        Text("Nova Brain settings")
                    }
                    TextButton(onClick = { settingsOpen = false; callShieldOpen = true }) { Text("Call Shield") }
                    TextButton(onClick = { settingsOpen = false; diagnostic = true }) {
                        Text("Share safe diagnostic view")
                    }
                    Text("Cloud Teacher is optional and requires approval for each message. Reset removes saved local learning examples.")
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
    }

    private fun appLabel(key: GuardianConversationKey?): String =
        if (key == null || key.app == GUARDIAN_WHATSAPP) "WhatsApp · Main"
        else "SMS/MMS · ${key.app}"

    @Composable
    private fun MessageCard(row: GuardianInboxRow, redacted: Boolean) {
        val cooling = row.section == GuardianInboxSection.COOLING
        val caption = when {
            redacted -> "Preview redacted"
            row.assessment == null -> "Awaiting analysis · preview hidden"
            row.assessment == GuardianAssessment.UNCERTAIN -> "Uncertain · preview hidden until reviewed"
            else -> "Content hidden until reviewed"
        }
        Card(onClick = { if (!redacted) reveal(row) }, enabled = !busy && !redacted, modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp), colors = guardianCardColors()) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Outlined.Lock, null, tint = if (cooling) GuardianAmber else GuardianBlue, modifier = Modifier.size(20.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    val sender = if (showSenders && !redacted) row.senderName else null
                    if (cooling) {
                        GuardianCooldownCopy(if (redacted) null else row.availableAfter,
                            potentiallyUpsetting = row.assessment == GuardianAssessment.UPSETTING)
                        sender?.let { Text(it, color = GuardianMuted, style = MaterialTheme.typography.bodySmall) }
                    } else {
                        Text(when {
                            redacted -> "Sender redacted"
                            showSenders -> sender ?: "Sender unavailable"
                            else -> "Protected message"
                        }, style = MaterialTheme.typography.titleMedium, color = GuardianPrimary)
                        Text(caption, color = GuardianMuted, style = MaterialTheme.typography.bodyMedium)
                    }
                    Text(if (redacted) "App and profile redacted" else appLabel(row.conversation),
                        color = GuardianMuted, style = MaterialTheme.typography.bodySmall)
                    row.assessment?.let { Text("Local estimate · ${it.label}", color = GuardianMuted,
                        style = MaterialTheme.typography.bodySmall) }
                    Text(if (redacted) "Arrival time redacted"
                        else DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(row.arrivedAt)),
                        color = GuardianMuted, style = MaterialTheme.typography.bodySmall)
                }
                if (!cooling) Icon(Icons.Outlined.ChevronRight, if (redacted) null else "Reveal message", tint = GuardianBlue)
            }
        }
    }

    @Composable
    private fun ColumnScope.Conversations() {
        val grouped = rows.filter { it.conversation != null }.groupBy { checkNotNull(it.conversation) }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 18.dp)) {
            item {
                GuardianCard("Conversations") {
                    Text("Incoming Android notifications only. Some messages or outgoing replies may be missing.", color = GuardianMuted)
                    TextButton(onClick = {
                        loadArchive({ store, _, _ -> store.monitoredContacts() }) { contacts = it; contactsOpen = true }
                    }) { Text("Monitored Contacts") }
                    TextButton(onClick = { settingsOpen = false; callShieldOpen = true }) { Text("Call Shield") }
                    failure?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            }
            items(grouped.entries.toList()) { (key, messages) ->
                val newest = messages.maxBy { it.arrivedAt }
                Card(onClick = { conversation = key }, colors = guardianCardColors(), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp)) {
                        Text(if (showSenders) newest.senderName ?: "Conversation" else "Conversation",
                            style = MaterialTheme.typography.titleMedium)
                        Text("${appLabel(key)} · ${messages.size} captured", color = GuardianMuted)
                    }
                }
            }
        }
        InboxDialogs()
    }

    private fun conversationSubtitle(key: GuardianConversationKey): String {
        val sender = if (showSenders) rows.filter { it.conversation == key }.maxByOrNull { it.arrivedAt }?.senderName
            else null
        return listOfNotNull(sender, appLabel(key)).joinToString(" · ")
    }

    @Composable
    private fun ColumnScope.ConversationDetail(thread: GuardianConversationKey?) {
        val ordered = rows.filter { (if (thread == null) it.id == detail?.id else it.conversation == thread) && (matches?.contains(it.id) ?: true) }.sortedBy { it.arrivedAt }
        val dates = ordered.groupBy { DateFormat.getDateInstance(DateFormat.FULL).format(Date(it.arrivedAt)) }
        val scroll = rememberLazyListState()
        val openedId = detail?.id
        LaunchedEffect(openedId) {
            if (openedId == null) return@LaunchedEffect
            var index = 1 // The introduction precedes the date groups.
            for (messages in dates.values) {
                val offset = messages.indexOfFirst { it.id == openedId }
                if (offset >= 0) {
                    scroll.scrollToItem(index + 1 + offset)
                    break
                }
                index += 1 + messages.size
            }
        }
        LazyColumn(Modifier.weight(1f).testTag("guardian-conversation-history"), state = scroll, verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 22.dp)) {
            item {
                GuardianCard("Notification-derived history") {
                    Text("Incoming messages only. The history may be incomplete.", color = GuardianMuted)
                    OutlinedTextField(query, { query = it.take(100); matches = null }, modifier = Modifier.fillMaxWidth(),
                        singleLine = true, label = { Text("Search eligible message text") })
                    TextButton(enabled = query.isNotBlank() && !busy, onClick = {
                        val requested = query.trim()
                        conversationPreview = null
                        loadArchive({ store, days, now -> store.searchInbox(requested, days, now) }) {
                            if (conversation == thread && query.trim() == requested) matches = it
                        }
                    }) { Text("Search") }
                    failure?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            }
            dates.forEach { (date, messages) ->
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        Surface(color = GuardianGlass, contentColor = GuardianMuted, shape = RoundedCornerShape(20.dp)) {
                            Text(date, Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                                style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
                items(messages, key = { it.id }) { row ->
                    GuardianConversationCard(row, conversationPreview?.takeIf { it.first == row.id }?.second,
                        busy = busy, onReview = { reviewConversationMessage(thread, row) },
                        onLabel = { label -> labelConversationMessage(thread, row, label) },
                        onSkip = { conversationPreview = null; confirmDelete = false })
                    if (conversationPreview?.first == row.id && row.section != GuardianInboxSection.COOLING) {
                        MessageActions(row)
                    }
                }
            }
            if (ordered.isEmpty()) item { Text("No eligible messages found.", color = GuardianMuted) }
        }
    }

    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    private fun MessageActions(row: GuardianInboxRow) {
        val preview = conversationPreview?.takeIf { it.first == row.id }?.second ?: return
        GuardianMessageCopyActions(row, preview, appLabel(row.conversation), !busy && canReview()) {
            canReview() && !busy && conversationPreview == (row.id to preview) &&
                rows.any { it.id == row.id && it.section != GuardianInboxSection.COOLING }
        }
        val cloudKey = row.conversation
        val cloudAllowed = cloudKey != null && cloudKey in cloudConversations
        if (cloudKey != null && cloudAllowed) {
            GuardianCloudTeacher(preview) {
                canReview() && !busy && cloudKey in cloudConversations &&
                    conversationPreview == (row.id to preview) &&
                    rows.any { it.id == row.id && it.section != GuardianInboxSection.COOLING }
            }
        }
        GuardianCard("Message actions") {
            if (cloudKey != null) TextButton(enabled = !busy, onClick = {
                loadArchive({ store, _, _ -> store.personalImpactReview(row.id, cloudKey) }) {
                    if (it.first.collect || it.second != null) {
                        impactFeedback = it.second
                        pastImpact = it.third
                        impactMessage = row.id
                    } else {
                        impactSettings = it.first
                        validationOpen = true
                    }
                }
            }) { Text("Personal impact feedback") }
            if (cloudKey != null) LauncherToggle("Allow Cloud Teacher for this conversation until lock", cloudAllowed) { enabled ->
                if (canReview() && !busy && conversationPreview == (row.id to preview)) {
                    cloudConversations = if (enabled) cloudConversations + cloudKey
                    else cloudConversations - cloudKey
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(enabled = !busy, onClick = {
                    conversationPreview = null
                    loadArchive({ store, days, now -> store.assessInboxMessage(row.id, days, now) }) {
                        GuardianCapture.refresh(applicationContext)
                        refresh()
                    }
                }) { Text("Analyse on this device") }
                TextButton(enabled = !busy, onClick = {
                    conversationPreview = null
                    loadArchive({ store, days, now ->
                        val text = store.revealInboxMessage(row.id, days, now) ?: return@loadArchive false
                        store.teach(text, GuardianAssessment.UNCERTAIN) &&
                            store.assessInboxMessage(row.id, days, now) != null
                    }) { saved ->
                        if (saved) {
                            GuardianCapture.refresh(applicationContext)
                            refresh()
                        } else failure = "This message could not be saved as an uncertain example."
                    }
                }) { Text("Teach as uncertain") }
                TextButton(enabled = !busy, onClick = {
                    conversationPreview = null
                    loadArchive({ store, days, now ->
                        store.revealInboxMessage(row.id, days, now)?.let(store::forgetExample) == true
                    }) { removed ->
                        if (removed) {
                            GuardianCapture.refresh(applicationContext)
                            refresh()
                        } else failure = "No saved example matched this message."
                    }
                }) { Text("Forget saved training example") }
                TextButton(enabled = !busy, onClick = { review(row, GuardianInboxAction.READY) }) { Text("Mark ready") }
                TextButton(enabled = !busy, onClick = { review(row, GuardianInboxAction.HOLD) }) { Text("Hold for four hours") }
                TextButton(enabled = !busy, onClick = { review(row, GuardianInboxAction.ARCHIVE) }) { Text("Move to history") }
                TextButton(enabled = !busy, onClick = { confirmDelete = true }) { Text("Delete message") }
            }
            if (confirmDelete) {
                Text("Delete this copy from the encrypted Guardian archive?")
                TextButton(enabled = !busy, onClick = { review(row, GuardianInboxAction.DELETE) }) { Text("Delete copy") }
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            }
        }
    }

    private fun reviewConversationMessage(thread: GuardianConversationKey?, row: GuardianInboxRow) {
        val openedDetail = detail
        confirmDelete = false
        conversationPreview = null
        loadArchive({ store, days, now -> store.prepareInboxReview(row.id, days, now) }) { reviewed ->
            if (conversation != thread || (thread == null && detail != openedDetail)) return@loadArchive
            if (reviewed == null) {
                GuardianCapture.refresh(applicationContext)
                refresh()
            } else {
                rows = rows.map { if (it.id == row.id) reviewed.first else it }
                conversationPreview = row.id to reviewed.second
            }
        }
    }

    private fun labelConversationMessage(thread: GuardianConversationKey?, row: GuardianInboxRow, label: GuardianAssessment?) {
        if (conversationPreview?.first != row.id) return
        val openedDetail = detail
        loadArchive({ store, days, now -> store.labelConversationMessage(row.id, label, days, now) }) { saved ->
            if (conversation != thread || (thread == null && detail != openedDetail)) return@loadArchive
            if (saved) {
                rows = rows.map { if (it.id == row.id) it.copy(ownerLabel = label) else it }
                GuardianCapture.refresh(applicationContext)
            } else {
                conversationPreview = null
                refresh()
            }
        }
    }

    private fun saveContact(key: GuardianConversationKey, contact: GuardianContact?) {
        if (busy || !canReview()) return
        GuardianCapture.invalidatePending()
        loadArchive({ store, _, _ -> store.saveContact(key, contact); store.monitoredContacts() }) { contacts = it }
    }

    @Composable
    private fun ColumnScope.MonitoredContacts() {
        val discovered by GuardianCapture.contactCandidates.collectAsState()
        val existing = if (showSenders) rows.mapNotNull { row ->
            val key = row.conversation ?: return@mapNotNull null
            val name = row.senderName ?: return@mapNotNull null
            if (key.conversation.startsWith("notification:")) null else GuardianContact(key, name)
        } else emptyList()
        val available = (discovered + existing).distinctBy { it.key }.filterNot { entry -> contacts.any { it.key == entry.key } }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 18.dp)) {
            item {
                GuardianCard("Monitored Contacts") {
                    Text("Choose individual conversations to capture. Keep this screen open and ask the contact to send a message. Detection runs for 90 seconds and shows matching contact names without retaining message text.", color = GuardianMuted)
                    TextButton(onClick = { GuardianCapture.startContactDiscovery() }) { Text("Detect incoming contacts for 90 seconds") }
                    failure?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            }
            items(available, key = { it.key.toString() }) { candidate ->
                GuardianCard(if (showSenders) candidate.name else "Detected conversation") {
                    Text(appLabel(candidate.key), color = GuardianMuted)
                    TextButton(enabled = !busy, onClick = { saveContact(candidate.key, candidate) }) { Text("Monitor this conversation") }
                }
            }
            items(contacts, key = { it.key.toString() }) { entry ->
                GuardianCard(if (showSenders) entry.name else "Monitored conversation") {
                    Text(appLabel(entry.key), color = GuardianMuted)
                    LauncherToggle("Monitoring enabled", entry.enabled) {
                        saveContact(entry.key, entry.copy(enabled = it))
                    }
                    LauncherToggle("Retain captured messages", entry.retain) {
                        saveContact(entry.key, entry.copy(retain = it))
                    }
                    if (entry.key.app == GUARDIAN_WHATSAPP && entry.key.profile == getSystemService(android.os.UserManager::class.java)
                            ?.getSerialNumberForUser(android.os.Process.myUserHandle())) {
                        LauncherToggle("Dismiss supported alerts after encrypted capture", entry.quietCollection) {
                            saveContact(entry.key, entry.copy(quietCollection = it))
                        }
                    }
                    LauncherToggle("Hold uncertain messages for four hours", entry.holdUncertain) {
                        saveContact(entry.key, entry.copy(holdUncertain = it))
                    }
                    Text("Sensitivity · ${entry.sensitivity.label}", color = GuardianMuted)
                    TextButton(enabled = !busy, onClick = {
                        val levels = GuardianSensitivity.entries
                        saveContact(entry.key, entry.copy(sensitivity = levels[(entry.sensitivity.ordinal + 1) % levels.size]))
                    }) { Text("Change sensitivity") }
                    TextButton(enabled = !busy, onClick = { saveContact(entry.key, null) }) { Text("Remove monitoring") }
                }
            }
        }
    }

    companion object {
        private const val SECTION = "guardian-inbox-section"
        private const val OPEN_IMPACT = "guardian-open-personal-impact"
        private const val LINKED_MESSAGE = "guardian-linked-message"

        internal fun openPersonalImpact(activity: Activity) {
            activity.startActivity(Intent(activity, GuardianInboxActivity::class.java).putExtra(OPEN_IMPACT, true))
        }

        internal fun openEvidenceSource(context: android.content.Context, messageId: String) {
            context.startActivity(Intent(context, GuardianInboxActivity::class.java).putExtra(LINKED_MESSAGE, messageId))
        }

        internal fun open(activity: Activity, section: GuardianInboxSection) {
            activity.startActivity(Intent(activity, GuardianInboxActivity::class.java).putExtra(SECTION, section.name))
        }
    }
}


/** The held-card copy accepts only protection metadata. */
@Composable
internal fun GuardianCooldownCopy(availableAfter: Long?, potentiallyUpsetting: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text("Message protected", style = MaterialTheme.typography.titleMedium, color = GuardianPrimary)
        Text(if (potentiallyUpsetting) "Potentially upsetting content is cooling down."
            else "Message preview is cooling down.", color = GuardianMuted, style = MaterialTheme.typography.bodyMedium)
        if (availableAfter != null) {
            val deadline = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(availableAfter))
            Text("Preview available after $deadline", color = GuardianPrimary, style = MaterialTheme.typography.labelLarge)
        }
    }
}


@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun GuardianConversationCard(
    row: GuardianInboxRow, preview: String?, busy: Boolean,
    onReview: () -> Unit, onLabel: (GuardianAssessment?) -> Unit, onSkip: () -> Unit = {},
) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = guardianCardColors()) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            val timestamp = DateFormat.getDateTimeInstance().format(Date(row.arrivedAt))
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Incoming message", color = GuardianMuted, style = MaterialTheme.typography.labelLarge)
                Text(timestamp, color = GuardianMuted, style = MaterialTheme.typography.labelLarge)
            }
            if (row.section == GuardianInboxSection.COOLING) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Outlined.Lock, null, tint = GuardianAmber, modifier = Modifier.size(20.dp))
                    GuardianCooldownCopy(row.availableAfter, row.assessment == GuardianAssessment.UPSETTING)
                }
                return@Column
            }
            if (preview == null) {
                val patterns = row.patterns
                Text(row.assessment?.let { "Local estimate · ${it.label}" } ?: "Assessment pending", color = GuardianMuted)
                when {
                    patterns == null -> Text("Pre-read pattern assessment is unavailable for this capture.")
                    patterns.isEmpty() -> Text("No supported pattern cues detected. Context may change the interpretation.")
                    else -> {
                        Text("Possible patterns · " + patterns.joinToString { it.label }, color = GuardianAmber)
                        Text("Local wording cues suggest these patterns. Context may change the interpretation.")
                    }
                }
                Text("Preview hidden until you review it.", color = GuardianMuted)
                OutlinedButton(onClick = onReview, enabled = !busy) { Text("Review message") }
            } else {
                Text(preview, color = GuardianPrimary, style = MaterialTheme.typography.bodyLarge)
                val patterns = remember(preview) { recogniseNovaBrainPatterns(preview) }
                if (patterns.isNotEmpty()) {
                    Text("Possible patterns · " + patterns.joinToString { it.label }, color = GuardianMuted)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                var proposedLabel by remember(row.id, preview, row.ownerLabel) {
                    mutableStateOf<GuardianAssessment?>(null)
                }
                val view = androidx.compose.ui.platform.LocalView.current
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = (proposedLabel ?: row.ownerLabel) == GuardianAssessment.LOW_RISK, enabled = !busy,
                        onClick = {
                            view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                            proposedLabel = GuardianAssessment.LOW_RISK
                        }, label = { Text("Not Upsetting") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = androidx.compose.ui.graphics.Color(0xFF136345),
                            selectedLabelColor = GuardianPrimary,
                        ))
                    FilterChip(selected = (proposedLabel ?: row.ownerLabel) == GuardianAssessment.UPSETTING, enabled = !busy,
                        onClick = {
                            view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                            proposedLabel = GuardianAssessment.UPSETTING
                        }, label = { Text("Upsetting") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = androidx.compose.ui.graphics.Color(0xFF962C42),
                            selectedLabelColor = GuardianPrimary,
                        ))
                }
                if (row.ownerLabel != null) {
                    TextButton(onClick = onSkip, enabled = !busy) { Text("Conceal") }
                }
                proposedLabel?.let { label ->
                    Text("Save this label as an encrypted local learning example.")
                    Button(onClick = { onLabel(label); proposedLabel = null }, enabled = !busy) {
                        Text("Save learning example")
                    }
                    TextButton(onClick = { proposedLabel = null }, enabled = !busy) { Text("Cancel label") }
                }
                TextButton(onClick = { if (row.ownerLabel != null) onLabel(null) else onSkip() }, enabled = !busy) {
                    Text(if (row.ownerLabel == null) "Conceal" else "Remove learning example")
                }
            }
        }
    }
}
