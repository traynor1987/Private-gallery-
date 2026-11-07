package uk.co.traynor.privategallery.home2

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private const val GuardianSession = 1
private const val GuardianAndroid = 2
private const val GuardianNova = 3
private const val GuardianBlocked = 4
private const val GuardianCountdown = 5
private const val GuardianExit = 6

private val guardianAndroidExamples = listOf("Phone", "Maps", "Music player")
private val guardianNovaExamples = listOf("Nova Mind", "Nova Calculator", "Nova Arcade")
private val guardianDurations = listOf(15, 30, 45, 60)
private const val GuardianExitPhrase = "END DEMO"

internal val visualSanctuary = VisualModule(
    id = "sanctuary",
    title = "Nova Sanctuary",
    subtitle = "App Guardian · protection is not active",
    icon = Icons.Default.Security,
    sections = listOf(
        VisualSection(
            "Dashboard",
            "App Guardian preview",
            "Protection is not active. Explore how a future Sanctuary session might look using a sample schedule and fictional app choices.",
            rows = listOf(
                VisualRow("Protection status", "Monitoring, overlays and blocking are all off. No background service is running.", "Inactive"),
                VisualRow("Existing Sanctuary", "The calm experience in Nova Home remains separate from this App Guardian demonstration.", "Existing app"),
            ),
        ),
        VisualSection(
            "Duration",
            "Choose a sample duration",
            "Pick a duration for the on-screen simulation. The sample countdown advances only when you press its demo control.",
            rows = listOf(VisualRow("Session example", "A future session could use a start time and end time. This demo does not schedule either.", "Simulated")),
        ),
        VisualSection(
            "Android apps",
            "Android allowlist preview",
            "These sample app names are bundled with NovaOS. The demo does not inspect installed apps, grant access or block launches.",
            rows = listOf(VisualRow("Emergency access", "Emergency calls and essential device functions remain available. Sample allowlist choices have no effect on Android.", "Always available")),
        ),
        VisualSection(
            "NovaOS apps",
            "NovaOS allowlist preview",
            "Choose from fictional allowlist entries for internal apps. NovaOS continues to open its real applications normally.",
            rows = listOf(VisualRow("App policy", "Any future restrictions would require a separate implementation and explicit approval.", "Planned")),
        ),
        VisualSection(
            "Blocked example",
            "Example interruption screen",
            "This is an illustrated blocked-app state inside NovaOS. No other app is covered or blocked.",
            rows = listOf(VisualRow("What would appear", "A future screen could show the remaining time, a way back to NovaOS, and access to emergency information.", "Example")),
        ),
        VisualSection(
            "Countdown",
            "Sample session countdown",
            "Watch a fixed countdown value change as you advance the sample. The demo makes no changes to Android navigation or your apps.",
            rows = listOf(VisualRow("Session end", "At zero, the demonstration shows that the example is complete. Nothing is unblocked because no apps were blocked.", "Simulated")),
        ),
        VisualSection(
            "Early exit",
            "Practice the exit confirmation",
            "The typed phrase confirms the end of the on-screen sample only. Android navigation remains available throughout.",
            rows = listOf(VisualRow("Exit scope", "Leaving the example does not affect any app, settings or protection.", "Demo only")),
        ),
        VisualSection(
            "Emergency",
            "Emergency access stays available",
            "Phone and emergency controls remain under Android's control. This demo never intercepts calls or opens the dialler.",
            rows = listOf(
                VisualRow("Emergency calling", "The design reserves a visible explanation of how to reach Android emergency controls during a future session.", "Design only"),
                VisualRow("Leave immediately", "You can leave this demo at any time using NovaOS Back or Home. No typed phrase is required to use Android.", "Available"),
            ),
        ),
        VisualSection(
            "Setup",
            "Preview the setup walkthrough",
            "These are explanatory steps. The demo does not open Android Settings or request any permissions.",
            rows = listOf(
                VisualRow("1 · Review the design", "Read what a future App Guardian implementation would do and decide whether to continue.", "Walkthrough"),
                VisualRow("2 · Usage Access", "A future opt-in implementation may require access to app-usage information. NovaOS has not requested it here.", "Unavailable"),
                VisualRow("3 · Overlay permission", "A future opt-in implementation may require a supported display-over-apps permission. No overlay is requested or drawn by this demo.", "Unavailable"),
            ),
        ),
        VisualSection(
            "Diagnostics",
            "App Guardian diagnostics preview",
            "These are fixed demo indicators. NovaOS has not inspected permission state, usage events or other apps.",
            metrics = listOf(VisualMetric("Sample monitor", "Off"), VisualMetric("Sample overlay", "Off")),
            rows = listOf(
                VisualRow("Usage events", "No events are collected or read by the demonstration.", "Not checked"),
                VisualRow("Overlay service", "No overlay or foreground monitoring service runs.", "Not running"),
                VisualRow("Block decisions", "No app launches were intercepted. Any blocked screen you view is synthetic.", "None"),
            ),
        ),
    ),
)

/** The screen owns this state. It has no clock, persistence or device capability. */
internal class VisualGuardianState {
    var duration by mutableIntStateOf(30)
        private set
    var remaining by mutableStateOf<Int?>(null)
        private set
    var confirmation by mutableStateOf("")
        private set
    var androidChoices by mutableStateOf(setOf("Phone"))
        private set
    var novaChoices by mutableStateOf(setOf("Nova Mind"))
        private set

    val canEnd: Boolean get() = remaining != null && remaining != 0 && confirmation == GuardianExitPhrase

    fun chooseDuration(value: Int) {
        duration = value
    }

    fun start() {
        remaining = duration
        confirmation = ""
    }

    fun advance() {
        remaining = remaining?.let { (it - 5).coerceAtLeast(0) }
    }

    fun enterConfirmation(value: String) {
        confirmation = value.take(24)
    }

    fun end() {
        if (canEnd) {
            remaining = 0
            confirmation = ""
        }
    }

    fun toggleAndroid(name: String) {
        androidChoices = androidChoices.toggle(name)
    }

    fun toggleNova(name: String) {
        novaChoices = novaChoices.toggle(name)
    }

    private fun Set<String>.toggle(name: String): Set<String> =
        if (name in this) this - name else this + name
}

@Composable
internal fun VisualGuardianControls(section: Int, state: VisualGuardianState, navigate: (Int) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        Modifier.fillMaxWidth().testTag("guardian-inactive"),
        shape = RoundedCornerShape(16.dp),
        color = colors.tertiaryContainer,
        contentColor = colors.onTertiaryContainer,
    ) {
        Text(
            "DEMO · Protection is not active",
            Modifier.padding(14.dp),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
    }
    when (section) {
        0 -> OutlinedButton(onClick = { navigate(GuardianSession) }) { Text("Choose a demo duration") }
        GuardianSession -> GuardianDurationPicker(state) {
            state.start()
            navigate(GuardianCountdown)
        }
        GuardianAndroid -> GuardianAllowlist(guardianAndroidExamples, state.androidChoices, "guardian-android", state::toggleAndroid)
        GuardianNova -> GuardianAllowlist(guardianNovaExamples, state.novaChoices, "guardian-nova", state::toggleNova)
        GuardianBlocked -> {
            Surface(shape = RoundedCornerShape(22.dp), color = colors.surfaceContainerHigh) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Security, contentDescription = null)
                    Text("WhatsApp · sample app", style = MaterialTheme.typography.titleMedium)
                    Text("This is what a block could look like. WhatsApp remains available.")
                    OutlinedButton(onClick = { navigate(GuardianCountdown) }) { Text("Return to demo countdown") }
                }
            }
        }
        GuardianCountdown -> GuardianCountdownCard(state, navigate)
        GuardianExit -> GuardianExitCard(state) {
            state.end()
            navigate(GuardianCountdown)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GuardianDurationPicker(state: VisualGuardianState, onStart: () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        guardianDurations.forEach { minutes ->
            FilterChip(
                selected = state.duration == minutes,
                onClick = { state.chooseDuration(minutes) },
                label = { Text("$minutes minutes") },
                modifier = Modifier.heightIn(min = 48.dp).testTag("guardian-duration-$minutes"),
            )
        }
    }
    Button(onClick = onStart, modifier = Modifier.testTag("guardian-start")) { Text("Start demo countdown") }
}

@Composable
private fun GuardianAllowlist(apps: List<String>, selected: Set<String>, tag: String, onToggle: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(20.dp), color = colors.surfaceContainer) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp)) {
            apps.forEachIndexed { index, name ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp)
                        .toggleable(value = name in selected, role = Role.Switch, onValueChange = { onToggle(name) })
                        .testTag("$tag-$index"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    Switch(checked = name in selected, onCheckedChange = null)
                }
                if (index < apps.lastIndex) HorizontalDivider()
            }
        }
    }
}

@Composable
private fun GuardianCountdownCard(state: VisualGuardianState, navigate: (Int) -> Unit) {
    val remaining = state.remaining
    val colors = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(22.dp), color = colors.surfaceContainerHigh) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                when (remaining) {
                    null -> "No demo session"
                    0 -> "Demo session ended"
                    else -> "${remaining.toString().padStart(2, '0')}:00"
                },
                modifier = Modifier.testTag("guardian-remaining"),
                style = if (remaining != null && remaining > 0) MaterialTheme.typography.displaySmall else MaterialTheme.typography.titleLarge,
            )
            if (remaining == null || remaining == 0) {
                OutlinedButton(onClick = { navigate(GuardianSession) }) { Text("Choose a demo duration") }
            } else {
                Button(onClick = state::advance, modifier = Modifier.testTag("guardian-advance")) { Text("Advance sample by 5 minutes") }
                TextButton(onClick = { navigate(GuardianBlocked) }) { Text("See example blocked screen") }
                TextButton(onClick = { navigate(GuardianExit) }, modifier = Modifier.testTag("guardian-open-exit")) { Text("Preview early exit") }
            }
        }
    }
}

@Composable
private fun GuardianExitCard(state: VisualGuardianState, onEnd: () -> Unit) {
    val activeSample = (state.remaining ?: 0) > 0
    OutlinedTextField(
        value = state.confirmation,
        onValueChange = state::enterConfirmation,
        modifier = Modifier.fillMaxWidth().testTag("guardian-exit-phrase"),
        enabled = activeSample,
        label = { Text("Type $GuardianExitPhrase") },
        singleLine = true,
        supportingText = { Text(if (activeSample) "Only the example session will end." else "Start a demo session to try this control.") },
    )
    Button(
        onClick = onEnd,
        modifier = Modifier.testTag("guardian-confirm-exit"),
        enabled = state.canEnd,
    ) {
        Text("End demo session")
    }
}
