package uk.co.traynor.privategallery.intelligence

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.os.PersistableBundle
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.launch
import uk.co.traynor.privategallery.launcher.GuardianBlue
import uk.co.traynor.privategallery.launcher.GuardianBorder
import uk.co.traynor.privategallery.launcher.GuardianMint
import uk.co.traynor.privategallery.launcher.GuardianMuted
import uk.co.traynor.privategallery.launcher.GuardianNavy
import uk.co.traynor.privategallery.launcher.GuardianPrimary

private val IntelligencePanel = Color(0xE6152439)
private val IntelligenceRaised = Color(0xFF1A2D44)
private val IntelligenceBlue = Color(0xFF253F61)
private val IntelligenceShape = RoundedCornerShape(22.dp)

@Composable
internal fun IntelligenceExperience(
    onBack: (() -> Unit)?,
    showChat: Boolean,
    onShowChat: (Boolean) -> Unit,
    protected: Boolean,
    dashboard: @Composable (Modifier) -> Unit,
    conversation: @Composable (Modifier, Boolean) -> Unit,
    feedback: String? = null,
) {
    BoxWithConstraints(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF142439), GuardianNavy, Color(0xFF0C192A))))
            .then(if (onBack != null) Modifier.safeDrawingPadding() else Modifier)
            .imePadding(),
    ) {
        val expanded = maxWidth >= 600.dp
        val tightHeight = maxHeight < 420.dp
        val gutter = if (expanded) 20.dp else 12.dp
        Column(
            Modifier.widthIn(max = 1200.dp).fillMaxSize().align(Alignment.TopCenter)
                .padding(horizontal = gutter, vertical = if (tightHeight) 2.dp else 8.dp),
            verticalArrangement = Arrangement.spacedBy(if (tightHeight) 2.dp else 8.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (onBack != null) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                }
                IntelligenceIcon(Icons.Filled.AutoAwesome, GuardianMint)
                Text(
                    "Nova Intelligence", style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                if (expanded && protected) IntelligenceBadge("Sanctuary active", GuardianMint)
            }
            feedback?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
            if (expanded) {
                Row(
                    Modifier.weight(1f).fillMaxWidth().testTag("intelligence-inner"),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    dashboard(Modifier.weight(.42f).fillMaxHeight())
                    conversation(Modifier.weight(.58f).fillMaxHeight(), true)
                }
            } else {
                val keyboard = LocalSoftwareKeyboardController.current
                val haptics = LocalHapticFeedback.current
                Row(Modifier.fillMaxWidth().testTag("intelligence-cover")) {
                    TabRow(
                        selectedTabIndex = if (showChat) 1 else 0,
                        modifier = Modifier.fillMaxWidth(),
                        containerColor = Color.Transparent,
                        contentColor = GuardianMint,
                        divider = { HorizontalDivider(color = GuardianBorder.copy(alpha = .55f)) },
                    ) {
                        Tab(selected = !showChat, onClick = {
                            if (showChat) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            keyboard?.hide()
                            onShowChat(false)
                        }, text = { Text("Briefing") })
                        Tab(selected = showChat, onClick = {
                            if (!showChat) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onShowChat(true)
                        }, text = { Text("Ask Nova") })
                    }
                }
                if (showChat) conversation(Modifier.weight(1f).fillMaxWidth(), false)
                else dashboard(Modifier.weight(1f).fillMaxWidth())
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun IntelligenceDashboard(
    modifier: Modifier,
    now: ZonedDateTime,
    briefingName: String?,
    readyBriefing: String?,
    guardian: IntelligenceGuardian?,
    guardianState: String,
    sanctuaryStatus: String?,
    protected: Boolean,
    guardianVisible: Boolean,
    connection: String,
    email: String?,
    agenda: Result<IntelligenceAgenda>?,
    primaryUnlocked: Boolean,
    busy: Boolean,
    onUnlock: (() -> Unit)?,
    onCloudCheck: () -> Unit,
    onShortcut: (IntelligenceDestination) -> Unit,
    onInbox: () -> Unit,
    onSchedule: () -> Unit,
    onName: () -> Unit,
) {
    Column(
        modifier.verticalScroll(rememberScrollState()).padding(bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val period = when (now.hour) {
            in 0..11 -> "Good morning"
            in 12..17 -> "Good afternoon"
            else -> "Good evening"
        }
        IntelligenceSection("Daily briefing", accent = true) {
            Text(
                briefingName?.let { "$period, $it" } ?: period,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                now.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)),
                color = GuardianMuted, style = MaterialTheme.typography.bodyMedium,
            )
            readyBriefing?.let { Text("$it briefing ready", color = GuardianMint, style = MaterialTheme.typography.labelLarge) }
        }
        IntelligenceSection("Nova Brain") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Cloud, null, tint = GuardianBlue, modifier = Modifier.size(18.dp))
                Text(connection, color = GuardianMuted, style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f).testTag("intelligence-connection"))
            }
            if (!protected) TextButton(enabled = !busy, onClick = onCloudCheck) { Text("Check cloud chat") }
        }
        IntelligenceSection("Guardian") {
            if (guardian == null) Text(guardianState, color = GuardianMuted, style = MaterialTheme.typography.bodyMedium)
            else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IntelligenceCount(guardian.safe, "Safe", GuardianMint, Modifier.weight(1f))
                    IntelligenceCount(guardian.held, "Held", GuardianBlue, Modifier.weight(1f))
                    IntelligenceCount(guardian.newToday, "Today", GuardianPrimary, Modifier.weight(1f))
                }
                Text(guardianState, color = GuardianMuted, style = MaterialTheme.typography.labelMedium)
            }
            if (guardianVisible && !protected) FlowRow {
                TextButton(onClick = { onShortcut(IntelligenceDestination.NOTIFICATIONS) }) { Text("Safe Notifications") }
                TextButton(onClick = onInbox) { Text("Open Guardian Inbox") }
            }
        }
        IntelligenceSection("Sanctuary") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Shield, null, tint = GuardianMint, modifier = Modifier.size(18.dp))
                Text(
                    sanctuaryStatus ?: if (protected) "Protection active" else "Protection inactive",
                    color = GuardianMuted, style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        IntelligenceSection("Upcoming") {
            val upcoming = agenda?.getOrNull()
            when {
                protected -> Text("Private briefing paused during Sanctuary", color = GuardianMuted)
                !primaryUnlocked -> {
                    Text("Unlock Primary Space to see tasks, calendar events and reminders.", color = GuardianMuted)
                    TextButton(enabled = onUnlock != null, onClick = { onUnlock?.invoke() }) { Text("Unlock private briefing") }
                }
                agenda == null -> LinearProgressIndicator(Modifier.fillMaxWidth(), color = GuardianMint)
                upcoming == null -> Text("Tasks and calendar unavailable", color = GuardianMuted)
                else -> {
                    IntelligenceAgendaRows("Tasks", upcoming.tasks, "No tasks due in the next seven days")
                    if (upcoming.unscheduledTasks.isNotEmpty()) {
                        IntelligenceAgendaRows("Tasks without a due date", upcoming.unscheduledTasks, "")
                    }
                    IntelligenceAgendaRows("Nova Calendar", upcoming.events, "No events in the next seven days")
                    IntelligenceAgendaRows("Reminders", upcoming.reminders, "No reminders on this agenda")
                }
            }
        }
        IntelligenceSection("Shortcuts") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IntelligenceShortcut("Notes", Icons.Filled.Notes) { onShortcut(IntelligenceDestination.NOTES) }
                IntelligenceShortcut("Tasks", Icons.Filled.TaskAlt) { onShortcut(IntelligenceDestination.TASKS) }
                IntelligenceShortcut("Calendar", Icons.Filled.CalendarToday) { onShortcut(IntelligenceDestination.CALENDAR) }
                IntelligenceShortcut("Sanctuary", Icons.Filled.Shield) { onShortcut(IntelligenceDestination.SANCTUARY) }
            }
        }
        if (!protected && email != null) Text(email, color = GuardianMuted, style = MaterialTheme.typography.labelMedium)
        if (primaryUnlocked) FlowRow {
            TextButton(onClick = onSchedule) { Text("Briefing schedule") }
            TextButton(onClick = onName) { Text("Greeting name") }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun IntelligenceChat(
    modifier: Modifier,
    expanded: Boolean,
    exchanges: List<IntelligenceExchange>,
    zone: ZoneId,
    input: String,
    onInput: (String) -> Unit,
    onSend: () -> Unit,
    busy: Boolean,
    pendingQuestion: String?,
    retryAvailable: Boolean,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    privatePrompt: Boolean,
    onUnlock: (() -> Unit)?,
    onMemory: (() -> Unit)?,
    onClear: () -> Unit,
) {
    val scroll = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val haptics = LocalHapticFeedback.current
    val isEmpty = exchanges.isEmpty() && pendingQuestion == null
    val showActions = !isEmpty || onMemory != null
    val latestIndex = (exchanges.size * 2 + (if (pendingQuestion != null) 1 else 0) - 1).coerceAtLeast(0)
    val timestampFormat = remember { DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT) }
    LaunchedEffect(latestIndex, isEmpty) {
        if (!isEmpty) scroll.scrollToItem(latestIndex)
    }

    Surface(
        modifier = modifier.testTag("intelligence-chat"),
        color = if (expanded) IntelligencePanel else Color.Transparent,
        shape = IntelligenceShape,
        border = if (expanded) BorderStroke(1.dp, GuardianBorder.copy(alpha = .7f)) else null,
    ) {
        Column(Modifier.padding(if (expanded) 12.dp else 0.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (expanded) Row(
                Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IntelligenceIcon(Icons.Filled.AutoAwesome, GuardianMint)
                Text("Ask Nova", style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f).semantics { heading() })
                if (showActions) {
                    IntelligenceChatActions(isEmpty, busy, onMemory, onClear) { keyboard?.hide() }
                }
            }

            Box(Modifier.weight(1f).fillMaxWidth()) {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    state = scroll,
                    contentPadding = PaddingValues(top = 4.dp, bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (isEmpty) item {
                        Column(
                            Modifier.fillParentMaxHeight().padding(vertical = 8.dp, horizontal = 10.dp),
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text("How can I help?", style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(6.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf("Summarise my day", "What’s on tomorrow?", "Open Nova Notes").forEach { suggestion ->
                                    AssistChip(
                                        enabled = !busy,
                                        onClick = { onInput(suggestion) },
                                        label = { Text(suggestion) },
                                        trailingIcon = {
                                            Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(14.dp))
                                        },
                                    )
                                }
                            }
                        }
                    }
                    items(exchanges.size * 2) { index ->
                        val message = exchanges[index / 2]
                        val isUser = index % 2 == 0
                        val time = message.timestamp?.atZone(zone)?.format(timestampFormat)
                        Column(Modifier.fillMaxWidth()) {
                            IntelligenceBubble(
                                text = if (isUser) message.question else message.answer,
                                user = isUser,
                                cloud = message.cloud,
                                timestamp = time,
                                preview = message.ownerPreview,
                            )
                        }
                    }
                    if (pendingQuestion != null) item {
                        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            IntelligenceBubble(pendingQuestion, true, null, null, copyable = false)
                            Surface(color = IntelligenceRaised, shape = IntelligenceShape) {
                                Row(
                                    Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    CircularProgressIndicator(Modifier.size(16.dp), color = GuardianMint, strokeWidth = 2.dp)
                                    Text("Waiting for Nova Brain…", style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                    if (!isEmpty) item { Spacer(Modifier.height(1.dp)) }
                }
                if (!isEmpty && scroll.canScrollForward) {
                    FilledIconButton(
                        onClick = { scope.launch { scroll.animateScrollToItem(latestIndex + 1) } },
                        modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = IntelligenceBlue, contentColor = GuardianPrimary),
                    ) { Icon(Icons.Filled.ArrowDownward, "Scroll to latest") }
                }
            }
            Column(Modifier.animateContentSize()) {
                if (privatePrompt && onUnlock != null) TextButton(onClick = onUnlock) { Text("Continue in private briefing") }
                if (retryAvailable && !busy) TextButton(onClick = onRetry) { Text("Retry cloud question") }
                if (busy && pendingQuestion == null) LinearProgressIndicator(Modifier.fillMaxWidth(), color = GuardianMint)
            }
            Row(
                Modifier.fillMaxWidth().padding(bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (!expanded && showActions) {
                    IntelligenceChatActions(isEmpty, busy, onMemory, onClear) { keyboard?.hide() }
                }
                OutlinedTextField(
                    value = input, onValueChange = onInput, label = { Text("Message Nova") },
                    modifier = Modifier.weight(1f).testTag("intelligence-input"),
                    singleLine = true, shape = RoundedCornerShape(20.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { if (!busy && input.isNotBlank()) onSend() }),
                )
                FilledIconButton(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        if (pendingQuestion != null) onCancel() else onSend()
                    },
                    modifier = Modifier.size(48.dp),
                    enabled = pendingQuestion != null || (!busy && input.isNotBlank()),
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = GuardianMint, contentColor = GuardianNavy),
                ) {
                    if (pendingQuestion != null) Icon(Icons.Filled.Stop, "Cancel Nova Brain request")
                    else Icon(Icons.AutoMirrored.Filled.Send, "Send message")
                }
            }
        }
    }
}

@Composable
private fun IntelligenceChatActions(
    isEmpty: Boolean, busy: Boolean, onMemory: (() -> Unit)?, onClear: () -> Unit, onOpen: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { onOpen(); menu = true }, enabled = !busy) {
            Icon(Icons.Filled.MoreVert, "Conversation actions")
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            if (onMemory != null) DropdownMenuItem(text = { Text("Assistant memory") }, onClick = {
                menu = false
                onMemory()
            })
            if (!isEmpty) DropdownMenuItem(text = { Text("Clear conversation") }, onClick = {
                menu = false
                onClear()
            })
        }
    }
}

@Composable
private fun ColumnScope.IntelligenceBubble(
    text: String, user: Boolean, cloud: Boolean?, timestamp: String?, copyable: Boolean = true,
    preview: Boolean = false,
) {
    val context = LocalContext.current
    Surface(
        modifier = Modifier.fillMaxWidth(if (user) .86f else .96f)
            .align(if (user) Alignment.End else Alignment.Start),
        shape = if (user) RoundedCornerShape(19.dp, 19.dp, 5.dp, 19.dp) else RoundedCornerShape(19.dp, 19.dp, 19.dp, 5.dp),
        color = if (user) IntelligenceBlue else IntelligenceRaised,
        border = if (user) null else BorderStroke(1.dp, GuardianBorder.copy(alpha = .5f)),
    ) {
        Column(Modifier.padding(start = 12.dp, end = 9.dp, top = 9.dp, bottom = 9.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (!user) Icon(Icons.Filled.AutoAwesome, null, tint = GuardianMint, modifier = Modifier.size(13.dp))
                Text(
                    when {
                        user -> "You"
                        preview && cloud == true -> "Nova Brain · preview"
                        cloud == true -> "Nova Brain · cloud"
                        else -> "Nova · local"
                    },
                    color = if (cloud == true) GuardianMint else GuardianMuted,
                    style = MaterialTheme.typography.labelMedium,
                )
                timestamp?.let { Text(it, color = GuardianMuted, style = MaterialTheme.typography.labelSmall) }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                if (copyable) IconButton(
                    onClick = {
                        val clip = ClipData.newPlainText("Nova Intelligence message", text).apply {
                            description.extras = PersistableBundle().apply {
                                putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
                            }
                        }
                        context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(clip)
                    },
                ) { Icon(Icons.Filled.ContentCopy, "Copy message", Modifier.size(17.dp), tint = GuardianMuted) }
            }
        }
    }
}

@Composable
private fun IntelligenceSection(
    title: String,
    accent: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = IntelligenceShape,
        color = if (accent) IntelligenceBlue else IntelligencePanel,
        border = BorderStroke(1.dp, GuardianBorder.copy(alpha = .7f)),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = if (accent) GuardianMint else GuardianPrimary,
                modifier = Modifier.semantics { heading() })
            content()
        }
    }
}

@Composable
private fun IntelligenceIcon(icon: ImageVector, tint: Color) {
    Box(Modifier.size(32.dp).background(tint.copy(alpha = .13f), CircleShape), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(17.dp))
    }
}

@Composable
private fun IntelligenceBadge(label: String, tint: Color) {
    Surface(shape = RoundedCornerShape(12.dp), color = tint.copy(alpha = .12f)) {
        Text(label, color = tint, style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp))
    }
}

@Composable
private fun IntelligenceCount(count: Int, label: String, tint: Color, modifier: Modifier) {
    Column(modifier.background(IntelligenceRaised, RoundedCornerShape(12.dp)).padding(8.dp)) {
        Text(count.toString(), color = tint, style = MaterialTheme.typography.titleLarge)
        Text(label, color = GuardianMuted, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun IntelligenceShortcut(label: String, icon: ImageVector, onClick: () -> Unit) {
    TextButton(onClick = onClick, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)) {
        Icon(icon, null, Modifier.size(15.dp))
        Spacer(Modifier.width(6.dp))
        Text(label)
    }
}

@Composable
private fun IntelligenceAgendaRows(title: String, items: List<String>, empty: String) {
    Text(title, color = GuardianBlue, style = MaterialTheme.typography.labelLarge)
    if (items.isEmpty()) Text(empty, color = GuardianMuted, style = MaterialTheme.typography.bodyMedium)
    else items.take(3).forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
}
