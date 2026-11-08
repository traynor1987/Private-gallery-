package uk.co.traynor.privategallery.home2

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.WorkOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import uk.co.traynor.privategallery.core.sanctuary.SanctuaryMode
import uk.co.traynor.privategallery.core.sanctuary.SanctuaryPreset
import uk.co.traynor.privategallery.core.sanctuary.SanctuarySession
import uk.co.traynor.privategallery.core.sanctuary.SanctuaryStore
import uk.co.traynor.privategallery.core.sanctuary.SanctuaryWhatsApp
import uk.co.traynor.privategallery.core.sanctuary.sanctuaryEssentialNovaRoutes
import java.text.DateFormat
import java.util.Date
import java.util.Locale

private val SanctuaryShape = RoundedCornerShape(24.dp)

@Composable
internal fun SanctuaryHome(
    store: SanctuaryStore,
    androidApps: Map<String, String>,
    novaApps: Map<String, String>,
    protectionStatus: String,
    onStart: (SanctuaryMode) -> Boolean,
    onPermissions: () -> Unit,
    onLaunchAndroid: (String) -> Unit,
    onLaunchNova: (String) -> Unit,
    onBack: () -> Unit,
    pinningControls: (@Composable () -> Unit)? = null,
    dndStatus: String? = null,
    onDndSettings: (() -> Unit)? = null,
    onExtended: (() -> Unit)? = null,
    onPhone: (() -> Unit)? = null,
    novaAppUrls: Map<String, String> = emptyMap(),
    onStartFoldTest: ((SanctuaryMode) -> Boolean)? = null,
) {
    val session by store.current.collectAsState()
    var selected by remember { mutableStateOf(SanctuaryMode.SELF_CARE) }
    var edit by remember { mutableStateOf<SanctuaryMode?>(null) }
    var presetRevision by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }
    var viewHistory by remember { mutableStateOf(false) }
    var remaining by remember { mutableLongStateOf(0L) }
    LaunchedEffect(store, session?.id, session?.expiresAt) {
        while (true) {
            val current = store.refresh() ?: break
            remaining = store.remainingMillis(current)
            delay(500)
        }
    }
    BackHandler(edit != null || viewHistory) {
        edit = null
        viewHistory = false
    }
    edit?.let { mode ->
        SanctuaryPresetEditor(
            mode, remember(mode) { store.preset(mode) }, androidApps, novaApps, novaAppUrls, { edit = null },
        ) {
            if (store.savePreset(mode, it)) {
                presetRevision++
                edit = null
                true
            } else {
                false
            }
        }
        return
    }
    if (viewHistory) {
        SanctuaryHistory(store) { viewHistory = false }
        return
    }
    val presets = remember(store, presetRevision) { SanctuaryMode.entries.associateWith(store::preset) }
    val current = session
    SanctuaryScaffold("Nova Sanctuary", "Focus with room to breathe", onBack, "nova-sanctuary") { wide ->
        SanctuaryHero(current, remaining, protectionStatus)
        message?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("sanctuary-feedback")) }
        if (current == null) {
            SanctuaryTitle("Choose your mode")
            val onSelect: (SanctuaryMode) -> Unit = { selected = it; message = null }
            if (wide) {
                SanctuaryMode.entries.chunked(2).forEach { group ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        group.forEach { mode ->
                            SanctuaryModeTile(mode, presets.getValue(mode), selected == mode, Modifier.weight(1f)) { onSelect(mode) }
                        }
                        if (group.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            } else {
                SanctuaryMode.entries.forEach { mode ->
                    SanctuaryModeTile(mode, presets.getValue(mode), selected == mode, Modifier.fillMaxWidth()) { onSelect(mode) }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = { edit = selected },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("sanctuary-edit-preset"),
                ) { Text("Edit mode") }
                Button(
                    onClick = { message = if (onStart(selected)) null else "Could not start. Check App Guardian setup." },
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("sanctuary-start"),
                ) { Text("Start ${selected.label}") }
            }
            if (onStartFoldTest != null) {
                SanctuaryMenuRow(
                    "Start a 5-minute Fold test",
                    "Uses this mode’s app choices with App Guardian enabled. Do Not Disturb and pinning stay off.",
                    {
                        message = if (onStartFoldTest(selected)) null
                        else "Could not start the Fold test. Check App Guardian setup."
                    },
                    "sanctuary-fold-test",
                )
            }
        } else {
            SanctuaryLauncher(current, androidApps, novaApps, novaAppUrls, onLaunchAndroid, onLaunchNova, wide)
            if (current.offerPinning && pinningControls != null) {
                SanctuaryTitle("Android screen pinning")
                Text(
                    "Pinning keeps NovaOS on screen and makes switching to approved Android apps harder. Android provides the unpin gesture.",
                    style = MaterialTheme.typography.bodySmall,
                )
                pinningControls()
            }
            SanctuaryBreathing()
            if (current.mode == SanctuaryMode.PANIC) {
                SanctuaryCheckIn(remaining, onExtend = { hours ->
                    message = if (store.extend(hours)) {
                        onExtended?.invoke()
                        null
                    } else "The session could not be extended."
                })
            }
            Text("To end early, use NovaOS Settings → Sanctuary.", style = MaterialTheme.typography.bodySmall)
        }
        val dndSelected = current?.enableDnd ?: presets.getValue(selected).enableDnd
        if (dndSelected) {
            dndStatus?.let { Text(it, modifier = Modifier.testTag("sanctuary-dnd-status"), style = MaterialTheme.typography.bodySmall) }
            onDndSettings?.let { SanctuaryMenuRow("Do Not Disturb access", "Android controls notification filtering and exceptions", it, "sanctuary-dnd-settings") }
        }
        SanctuaryMenuRow("App Guardian setup", "Permissions and protection status", onPermissions, "sanctuary-permissions")
        SanctuaryMenuRow("Session history", "Recent sessions on this device", { viewHistory = true }, "sanctuary-history")
        if (onPhone != null) SanctuaryMenuRow(
            "Open Phone", "Call a trusted contact or reach emergency services",
            onPhone, "sanctuary-open-phone",
        )
        SanctuaryRecoveryNote()
    }
}

@Composable
internal fun SanctuarySettings(store: SanctuaryStore, onEnded: () -> Unit, onBack: () -> Unit) {
    val session by store.current.collectAsState()
    var phrase by remember(session?.id) { mutableStateOf("") }
    var confirming by remember(session?.id) { mutableStateOf(false) }
    var failure by remember(session?.id) { mutableStateOf(false) }
    LaunchedEffect(store, session?.id) { store.refresh() }
    SanctuaryScaffold("Sanctuary settings", "Session and recovery", onBack, "sanctuary-settings") {
        SanctuaryTitle("End session")
        if (session == null) {
            Text("There is no active Sanctuary session.")
        } else {
            Text("${session?.mode?.label} is active. Type the phrase below to continue.")
            Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                Text(SanctuaryStore.END_PHRASE, Modifier.padding(14.dp), fontWeight = FontWeight.SemiBold)
            }
            OutlinedTextField(
                value = phrase,
                onValueChange = { phrase = it; failure = false },
                label = { Text("Confirmation phrase") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("sanctuary-end-phrase"),
            )
            Button(
                onClick = { confirming = true },
                enabled = phrase == SanctuaryStore.END_PHRASE,
                modifier = Modifier.heightIn(min = 48.dp).testTag("sanctuary-end-review"),
            ) { Text("Review end session") }
            if (failure) Text("The session could not be ended.", color = MaterialTheme.colorScheme.error)
        }
        SanctuaryRecoveryNote()
    }
    if (confirming) {
        PanicHomeAlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text("End this Sanctuary session?") },
            text = { Text("App Guardian will stop protecting this session.") },
            confirmButton = {
                TextButton(onClick = {
                    confirming = false
                    if (store.end(phrase)) onEnded() else failure = true
                }, modifier = Modifier.testTag("sanctuary-confirm-end")) { Text("End session") }
            },
            dismissButton = { TextButton(onClick = { confirming = false }) { Text("Keep session") } },
        )
    }
}

@Composable
private fun SanctuaryScaffold(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    tag: String,
    content: @Composable ColumnScope.(wide: Boolean) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding().testTag(tag)) {
        val wide = maxWidth >= 600.dp
        Column(
            Modifier.align(Alignment.TopCenter).widthIn(max = 1000.dp).fillMaxWidth()
                .verticalScroll(rememberScrollState()).padding(horizontal = if (wide) 28.dp else 16.dp)
                .padding(top = 6.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            content(wide)
        }
    }
}

@Composable
private fun SanctuaryHero(session: SanctuarySession?, remaining: Long, status: String) {
    val tint = Color(0xFF98E8D7)
    val elapsedSpan = session?.let { (it.expiresAt - it.startedAt).coerceAtLeast(1L) }
    val fraction = elapsedSpan?.let { (remaining.toDouble() / it).toFloat().coerceIn(0f, 1f) } ?: 1f
    Surface(shape = SanctuaryShape, color = Color(0xFF142F35), contentColor = Color(0xFFF0FAF7)) {
        Column(
            Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xFF254550), Color(0xFF151D32)))).padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(session?.mode?.label ?: "Make space.", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Box(Modifier.size(146.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val stroke = 8.dp.toPx()
                    val arcSize = Size(size.width - stroke, size.height - stroke)
                    val origin = Offset(stroke / 2, stroke / 2)
                    drawArc(tint.copy(alpha = .23f), -90f, 360f, false, origin, arcSize, style = Stroke(stroke))
                    drawArc(tint, -90f, fraction * 360f, false, origin, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                }
                Text(
                    if (session == null) "Ready" else sanctuaryClock(remaining),
                    style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.testTag("sanctuary-countdown"),
                )
            }
            Text(if (session == null) "Choose what gets your attention." else "Time remaining",
                style = MaterialTheme.typography.bodySmall, color = Color(0xFFD4E9E8))
            Surface(shape = CircleShape, color = Color(0xFF2A4855), contentColor = Color(0xFFE4F7F2)) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Security, null, Modifier.size(15.dp), tint = tint)
                    Spacer(Modifier.width(6.dp))
                    Text(status, style = MaterialTheme.typography.labelMedium, modifier = Modifier.testTag("sanctuary-protection-status"))
                }
            }
        }
    }
}

private fun sanctuaryClock(remaining: Long): String {
    val seconds = (remaining.coerceAtLeast(0L) + 999) / 1000
    return String.format(Locale.ROOT, "%02d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60)
}

@Composable
private fun SanctuaryTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun SanctuaryModeTile(mode: SanctuaryMode, preset: SanctuaryPreset, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val (icon, detail) = when (mode) {
        SanctuaryMode.GAMING -> Icons.Default.SportsEsports to "Games and entertainment"
        SanctuaryMode.LEARNING -> Icons.Default.School to "Study and development"
        SanctuaryMode.SELF_CARE -> Icons.Default.SelfImprovement to "Calm and trusted connection"
        SanctuaryMode.WORK -> Icons.Default.WorkOutline to "Work apps and navigation"
        SanctuaryMode.PANIC -> Icons.Default.FavoriteBorder to "Extended breathing room"
    }
    Surface(
        onClick = onClick,
        modifier = modifier.heightIn(min = 72.dp).testTag("sanctuary-mode-${mode.name.lowercase(Locale.ROOT)}"),
        shape = RoundedCornerShape(17.dp),
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(Modifier.padding(11.dp), verticalAlignment = Alignment.CenterVertically) {
            SanctuaryIcon(icon)
            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                Text(mode.label, style = MaterialTheme.typography.titleMedium)
                Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("${preset.minutes} min", style = MaterialTheme.typography.labelMedium)
            if (selected) Icon(Icons.Default.Check, "Selected", Modifier.padding(start = 6.dp).size(17.dp))
        }
    }
}

@Composable
private fun SanctuaryIcon(icon: ImageVector) {
    Surface(shape = RoundedCornerShape(11.dp), color = MaterialTheme.colorScheme.primaryContainer) {
        Box(Modifier.size(38.dp), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(21.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

@Composable
private fun SanctuaryPresetEditor(
    mode: SanctuaryMode, initial: SanctuaryPreset, androidApps: Map<String, String>, novaApps: Map<String, String>,
    novaAppUrls: Map<String, String>, onBack: () -> Unit, onSave: (SanctuaryPreset) -> Boolean,
) {
    var duration by remember(mode) { mutableStateOf(initial.minutes.toString()) }
    var android by remember(mode) { mutableStateOf(initial.androidApps) }
    var blockedNova by remember(mode) { mutableStateOf(initial.blockedNovaApps) }
    var allowWhatsApp by remember(mode) { mutableStateOf(initial.allowWhatsApp) }
    val configurableNova = novaApps.filterKeys { id ->
        id !in sanctuaryEssentialNovaRoutes && !SanctuaryWhatsApp.isRelatedApp(id) &&
            novaAppUrls[id]?.let(SanctuaryWhatsApp::isRelatedUrl) != true
    }
    var dnd by remember(mode) { mutableStateOf(initial.enableDnd) }
    var pinning by remember(mode) { mutableStateOf(initial.offerPinning) }
    var guardian by remember(mode) { mutableStateOf(initial.enableGuardian) }
    var externalApps by remember(mode) { mutableStateOf(initial.allowExternalApps) }
    var error by remember(mode) { mutableStateOf(false) }
    val minutes = duration.toIntOrNull()?.takeIf { it in 1..1440 }
    SanctuaryScaffold(mode.label, "Mode preferences", onBack, "sanctuary-preset-editor") {
        OutlinedTextField(
            value = duration, onValueChange = { duration = it.take(4).filter { c -> c in '0'..'9' } },
            label = { Text("Duration in minutes") },
            supportingText = { Text("1 to 1440 minutes · defaults to ${mode.defaultMinutes}") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("sanctuary-preset-duration"),
        )
        Button(
            onClick = {
                minutes?.let {
                    error = !onSave(initial.copy(
                        minutes = it,
                        androidApps = android,
                        blockedNovaApps = blockedNova,
                        allowWhatsApp = allowWhatsApp,
                        enableDnd = dnd,
                        offerPinning = pinning,
                        enableGuardian = guardian,
                        allowExternalApps = externalApps,
                    ))
                }
            },
            enabled = minutes != null,
            modifier = Modifier.heightIn(min = 48.dp).testTag("sanctuary-save-preset"),
        ) { Text("Save mode") }
        if (error) Text("Could not save this preset.", color = MaterialTheme.colorScheme.error)
        SanctuaryTitle("Focus options")
        SanctuaryModeSwitch("Do Not Disturb", "Filters interruptions through Android. Calls and alerts follow your system exceptions.",
            dnd, "sanctuary-option-dnd") { dnd = it }
        SanctuaryModeSwitch("Offer Android screen pinning", "You decide when to pin. Pinning makes Android app switching harder.",
            pinning, "sanctuary-option-pinning") { pinning = it }
        SanctuaryModeSwitch("App Guardian", "Shows the blocking screen when Android detects an unapproved app and permissions are granted.",
            guardian, "sanctuary-option-guardian") { guardian = it }
        SanctuaryModeSwitch("Allow approved Android apps", "Shows your selected Android apps in the launcher. Phone and recovery controls stay available.",
            externalApps, "sanctuary-option-external-apps") { externalApps = it }
        SanctuaryTitle("NovaOS apps")
        Text(
            "Installed NovaOS apps are approved by default. Untick an app to restrict it in this mode. " +
                "Recovery controls stay available.",
            style = MaterialTheme.typography.bodySmall,
        )
        SanctuaryModeSwitch(
            "Allow WhatsApp inside NovaOS",
            "Off by default. Turning this on permits WhatsApp destinations, including WhatsApp Web. " +
                "Chat Lite and the Favourites filter do not isolate conversations.",
            allowWhatsApp, "sanctuary-option-whatsapp",
        ) { allowWhatsApp = it }
        SanctuaryChoices(configurableNova, configurableNova.keys - blockedNova, "internal") { id, enabled ->
            blockedNova = if (enabled) blockedNova - id else blockedNova + id
        }
        SanctuaryTitle("Android apps")
        if (externalApps) {
            Text("Select installed apps. Essential device and recovery controls stay available.", style = MaterialTheme.typography.bodySmall)
            SanctuaryChoices(androidApps, android, "android") { id, enabled -> android = if (enabled) android + id else android - id }
        } else {
            Text("The Android allowlist is saved for when this option is enabled again.", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun SanctuaryModeSwitch(title: String, description: String, enabled: Boolean, tag: String, onChange: (Boolean) -> Unit) {
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).toggleable(enabled, role = Role.Switch, onValueChange = onChange)
                .testTag(tag).padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = enabled, onCheckedChange = null)
        }
    }
}

@Composable
private fun SanctuaryChoices(apps: Map<String, String>, selected: Set<String>, group: String, onChange: (String, Boolean) -> Unit) {
    if (apps.isEmpty()) Text("No available apps in this group.", style = MaterialTheme.typography.bodySmall)
    apps.entries.sortedBy { it.value.lowercase(Locale.ROOT) }.forEach { (id, name) ->
        val checked = id in selected
        Row(
            Modifier.fillMaxWidth().heightIn(min = 52.dp)
                .toggleable(value = checked, role = Role.Checkbox, onValueChange = { onChange(id, it) })
                .testTag("sanctuary-allow-$group-$id").padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Checkbox(checked = checked, onCheckedChange = null)
        }
        HorizontalDivider()
    }
}

@Composable
private fun SanctuaryLauncher(
    session: SanctuarySession, androidApps: Map<String, String>, novaApps: Map<String, String>,
    novaAppUrls: Map<String, String>, onAndroid: (String) -> Unit, onNova: (String) -> Unit, wide: Boolean,
) {
    SanctuaryTitle("Approved apps")
    val internal = novaApps.filterKeys { session.allowsNovaApp(it, novaAppUrls[it]) }
    val external = androidApps.filterKeys { it in session.androidApps }
    if (!session.allowExternalApps) {
        SanctuaryLaunchGroup("Inside NovaOS", internal, "internal", onNova)
        Text("Approved Android app shortcuts are off for this mode.", style = MaterialTheme.typography.bodySmall)
    } else if (wide) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) { SanctuaryLaunchGroup("Inside NovaOS", internal, "internal", onNova) }
            Column(Modifier.weight(1f)) { SanctuaryLaunchGroup("Android apps", external, "android", onAndroid) }
        }
    } else {
        SanctuaryLaunchGroup("Inside NovaOS", internal, "internal", onNova)
        SanctuaryLaunchGroup("Android apps", external, "android", onAndroid)
    }
}

@Composable
private fun SanctuaryLaunchGroup(label: String, apps: Map<String, String>, group: String, launch: (String) -> Unit) {
    Surface(shape = SanctuaryShape, color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            if (apps.isEmpty()) Text("No available apps in this list.", style = MaterialTheme.typography.bodySmall)
            apps.entries.sortedBy { it.value.lowercase(Locale.ROOT) }.forEach { (id, name) ->
                TextButton(
                    onClick = { launch(id) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("sanctuary-launch-$group-$id"),
                ) {
                    Icon(Icons.Default.Apps, null, Modifier.size(17.dp))
                    Text(name, Modifier.weight(1f).padding(horizontal = 8.dp))
                    Icon(Icons.Default.ChevronRight, null, Modifier.size(17.dp))
                }
            }
        }
    }
}

@Composable
private fun SanctuaryBreathing() {
    var running by remember { mutableStateOf(false) }
    var seconds by remember { mutableIntStateOf(0) }
    LaunchedEffect(running) {
        while (running) {
            delay(1000)
            seconds = (seconds + 1) % 14
        }
    }
    val prompt = when {
        !running -> "Take a breath"
        seconds < 4 -> "Breathe in · ${4 - seconds}"
        seconds < 8 -> "Hold gently · ${8 - seconds}"
        else -> "Breathe out · ${14 - seconds}"
    }
    Surface(shape = SanctuaryShape, color = MaterialTheme.colorScheme.secondaryContainer) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("A moment of calm", style = MaterialTheme.typography.titleMedium)
            Text(prompt, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.testTag("sanctuary-breathing-phase"))
            Text("Try naming five things you see, four you feel and three you hear.", style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = { running = !running; seconds = 0 }) { Text(if (running) "Stop breathing timer" else "Start breathing timer") }
        }
    }
}

@Composable
private fun SanctuaryCheckIn(remaining: Long, onExtend: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    var response by remember { mutableStateOf<String?>(null) }
    val endingSoon = remaining in 1L..(30 * 60 * 1000L)
    Surface(shape = SanctuaryShape, color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (endingSoon) "Your session ends soon" else "Wellbeing check-in", style = MaterialTheme.typography.titleMedium)
            Text("Take a moment if it feels useful. A response never changes your timer.", style = MaterialTheme.typography.bodySmall)
            if (open || endingSoon) {
                Text("How are you feeling?", style = MaterialTheme.typography.bodyLarge)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("Steady", "Unsure", "Overwhelmed").forEach { mood ->
                        TextButton(
                            onClick = { response = mood },
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("sanctuary-mood-${mood.lowercase(Locale.ROOT)}"),
                            contentPadding = PaddingValues(horizontal = 3.dp),
                        ) { Text(if (response == mood) "✓ $mood" else mood, textAlign = TextAlign.Center) }
                    }
                }
            } else {
                TextButton(onClick = { open = true }, modifier = Modifier.testTag("sanctuary-open-check-in")) { Text("Check in") }
            }
            Text("Extend voluntarily", style = MaterialTheme.typography.labelLarge)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(1, 3, 6, 12).forEach { hours ->
                    OutlinedButton(
                        onClick = { onExtend(hours) },
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("sanctuary-extend-$hours"),
                        contentPadding = PaddingValues(horizontal = 2.dp),
                    ) { Text("+${hours}h") }
                }
            }
        }
    }
}

@Composable
private fun SanctuaryHistory(store: SanctuaryStore, onBack: () -> Unit) {
    val entries = remember(store) { store.history() }
    val dates = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }
    SanctuaryScaffold("Session history", "Stored locally on this device", onBack, "sanctuary-history-screen") {
        if (entries.isEmpty()) Text("Completed sessions will appear here.")
        entries.forEach { entry ->
            Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                Column(Modifier.fillMaxWidth().padding(14.dp)) {
                    Text(entry.mode.label, style = MaterialTheme.typography.titleMedium)
                    Text(dates.format(Date(entry.startedAt)), style = MaterialTheme.typography.bodySmall)
                    Text("${entry.outcome.name.lowercase(Locale.ROOT).replaceFirstChar(Char::uppercaseChar)} · ${entry.extensions} extensions",
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun SanctuaryMenuRow(title: String, subtitle: String, onClick: () -> Unit, tag: String) {
    Surface(
        onClick = onClick, shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth().heightIn(min = 62.dp).testTag(tag),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall)
            }
            Icon(Icons.Default.ChevronRight, null)
        }
    }
}

@Composable
private fun SanctuaryRecoveryNote() {
    Text("Recovery and trusted contact", style = MaterialTheme.typography.titleMedium)
    Text(
        "Android Phone, emergency calling and device Settings remain available. In Android Settings you can revoke permissions or force stop NovaOS. Guardian availability depends on Android.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text(
        "WhatsApp Favourites cannot isolate conversations reliably. Use your phone or an approved trusted communication app.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
