package uk.co.traynor.privategallery.home2

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private const val PeopleProfile = 1
private const val PeopleConversation = 2
private const val PeopleStudio = 3

internal val visualPeople = VisualModule(
    id = "people",
    title = "Nova People",
    subtitle = "Fictional companions and mentors",
    icon = Icons.Default.Groups,
    sections = listOf(
        VisualSection(
            "Library", "Character library",
            "Explore fictional examples of how a future companion or mentor could be presented. These characters use prewritten content.",
        ),
        VisualSection(
            "Profile", "Character profile",
            "Review the example role, tone and limits before opening a scripted conversation.",
        ),
        VisualSection(
            "Conversation", "Example conversation",
            "All conversation bubbles are prewritten. No message is sent and nothing is generated.",
        ),
        VisualSection(
            "Studio", "Character creation studio",
            "Combine a fictional template and a visual style to see a sample profile. No character is saved.",
        ),
        VisualSection(
            "Personality", "Personality preferences",
            "Try sample settings for tone and response detail. They change only the example summary on this screen.",
        ),
        VisualSection(
            "Voice", "Voice preferences",
            "Review sample voice profiles and playback pace. This demo plays no audio, records nothing and requests no microphone access.",
        ),
        VisualSection(
            "Memory", "Memory and privacy",
            "Review how a future character could explain what it remembers. Only bundled fictional examples can be shown here.",
        ),
        VisualSection(
            "Consent", "Consent and deletion",
            "Practice the controls for a future integration. No consent is recorded and no encrypted records are changed.",
        ),
    ),
)

private data class PeopleCharacter(
    val id: String,
    val name: String,
    val role: String,
    val approach: String,
    val icon: ImageVector,
    val opening: String,
    val samplePrompt: String,
    val sampleReply: String,
)

private val peopleCharacters = listOf(
    PeopleCharacter(
        "lyra", "Lyra", "Companion", "Gentle check-ins and practical reflection",
        Icons.Default.Spa, "What would make this example afternoon feel a little calmer?",
        "I have twenty minutes and would like a quiet reset.",
        "One fictional plan: take a short walk, then leave a few minutes free before the next task.",
    ),
    PeopleCharacter(
        "orbit", "Orbit", "Mentor", "Structured prompts for learning and planning",
        Icons.Default.School, "What skill would you like the example lesson to cover?",
        "Show me how to practise reading a simple time chart.",
        "Start with the labels, compare two columns, then explain the difference in one sentence.",
    ),
)

private val peopleTemplates = listOf("Companion", "Mentor")
private val peopleStyles = listOf("Quiet", "Bright")
private val peopleTones = listOf("Warm", "Direct", "Curious")
private val peopleLengths = listOf("Concise", "Balanced", "Detailed")
private val peopleVoices = listOf("Soft", "Clear", "Lively")
private val peoplePaces = listOf("Slow", "Standard", "Quick")

/** Ephemeral presentation settings. The demo has no account, network or persistence. */
internal class VisualPeopleState {
    var characterIndex by mutableIntStateOf(0)
    var replyVisible by mutableStateOf(false)
    var template by mutableStateOf(peopleTemplates.first())
    var studioStyle by mutableStateOf(peopleStyles.first())
    var studioPreview by mutableStateOf(false)
    var tone by mutableStateOf(peopleTones.first())
    var length by mutableStateOf(peopleLengths[1])
    var voice by mutableStateOf(peopleVoices.first())
    var pace by mutableStateOf(peoplePaces[1])
    var showMemory by mutableStateOf(false)
    var consentPreview by mutableStateOf(false)
    var confirmDeletion by mutableStateOf(false)
    var deleted by mutableStateOf(false)
        private set

    fun chooseCharacter(index: Int) {
        characterIndex = index
        replyVisible = false
    }

    fun clearExample() {
        chooseCharacter(0)
        template = peopleTemplates.first()
        studioStyle = peopleStyles.first()
        studioPreview = false
        tone = peopleTones.first()
        length = peopleLengths[1]
        voice = peopleVoices.first()
        pace = peoplePaces[1]
        showMemory = false
        consentPreview = false
        confirmDeletion = false
        deleted = true
    }

    fun restoreExample() {
        deleted = false
    }
}

@Composable
internal fun VisualPeopleControls(section: Int, state: VisualPeopleState, navigate: (Int) -> Unit) {
    val character = peopleCharacters[state.characterIndex]
    when (section) {
        0 -> {
            peopleCharacters.forEachIndexed { index, example ->
                Surface(
                    onClick = { state.chooseCharacter(index); navigate(PeopleProfile) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 88.dp).testTag("people-character-${example.id}"),
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                ) {
                    PeopleCharacterSummary(example, Modifier.padding(14.dp))
                }
            }
            OutlinedButton(onClick = { navigate(PeopleStudio) }) { Text("Open character studio") }
        }

        PeopleProfile -> PeoplePanel("Fictional ${character.role.lowercase()}", Modifier.testTag("people-profile")) {
            PeopleCharacterSummary(character)
            Text("Example only · offers no professional advice and has no access to NovaOS records.")
            Button(onClick = { navigate(PeopleConversation) }, modifier = Modifier.testTag("people-open-conversation")) {
                Text("Open demo conversation")
            }
        }

        PeopleConversation -> PeoplePanel("Scripted chat · ${character.name}", Modifier.testTag("people-conversation")) {
            if (state.deleted) {
                Text("The sample conversation is cleared.")
                OutlinedButton(onClick = state::restoreExample) { Text("Restore scripted example") }
            } else {
                PeopleBubble(character.name, character.opening, incoming = true)
                PeopleBubble("Example user", character.samplePrompt, incoming = false)
                if (state.replyVisible) {
                    PeopleBubble(character.name, character.sampleReply, incoming = true, modifier = Modifier.testTag("people-demo-reply"))
                } else {
                    OutlinedButton(onClick = { state.replyVisible = true }, modifier = Modifier.testTag("people-show-reply")) {
                        Text("Reveal prewritten reply")
                    }
                }
            }
        }

        PeopleStudio -> PeoplePanel("Build an example profile") {
            PeopleChoices("Role template", peopleTemplates, state.template, "people-studio-template") {
                state.template = it
                state.studioPreview = false
            }
            PeopleChoices("Portrait style", peopleStyles, state.studioStyle, "people-studio-style") {
                state.studioStyle = it
                state.studioPreview = false
            }
            Button(onClick = { state.studioPreview = true }, modifier = Modifier.testTag("people-preview-character")) {
                Text("Preview character")
            }
            if (state.studioPreview) {
                PeoplePanel("Sample profile · ${state.template}") {
                    PeoplePortrait(
                        if (state.template == "Mentor") Icons.Default.Explore else Icons.Default.AutoAwesome,
                        bright = state.studioStyle == "Bright",
                    )
                    Text("A fictional ${state.template.lowercase()} with the ${state.studioStyle.lowercase()} portrait style.")
                }
            }
        }

        4 -> PeoplePanel("Sample conversation style") {
            PeopleChoices("Tone", peopleTones, state.tone, "people-tone") { state.tone = it }
            PeopleChoices("Response detail", peopleLengths, state.length, "people-length") { state.length = it }
            Text("Example profile · ${state.tone} tone · ${state.length} responses")
        }

        5 -> PeoplePanel("Sample voice profile") {
            PeopleChoices("Voice style", peopleVoices, state.voice, "people-voice") { state.voice = it }
            PeopleChoices("Pace", peoplePaces, state.pace, "people-pace") { state.pace = it }
            Text("Voice example · ${state.voice} · ${state.pace} pace")
            OutlinedButton(onClick = {}, enabled = false) { Text("Audio unavailable in demo") }
        }

        6 -> PeoplePanel("Example memory controls") {
            PeopleSwitch("Show fictional memory card", state.showMemory, Modifier.testTag("people-memory-switch")) {
                state.showMemory = it
            }
            if (state.showMemory) {
                Text("Bundled example · The fictional learner prefers short exercises.")
            }
            Text("A future integration would need permission before retaining information.")
        }

        7 -> PeoplePanel("Example consent and deletion controls") {
            PeopleSwitch("Preview opt-in setting", state.consentPreview, Modifier.testTag("people-consent-switch")) {
                state.consentPreview = it
            }
            Text(if (state.consentPreview) "The example switch is on. No permission was granted." else "No consent has been recorded.")
            if (state.deleted) {
                Text("Example cleared. No records were deleted.", Modifier.testTag("people-deleted"))
            } else if (state.confirmDeletion) {
                Text("Clear the temporary sample chat and preferences?")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = state::clearExample, modifier = Modifier.testTag("people-confirm-delete")) {
                        Text("Clear example")
                    }
                    TextButton(onClick = { state.confirmDeletion = false }) { Text("Cancel") }
                }
            } else {
                OutlinedButton(onClick = { state.confirmDeletion = true }, modifier = Modifier.testTag("people-delete-demo")) {
                    Text("Preview deletion")
                }
            }
        }
    }
}

@Composable
private fun PeopleCharacterSummary(character: PeopleCharacter, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PeoplePortrait(character.icon)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(character.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text("${character.role} · Fictional", style = MaterialTheme.typography.labelMedium)
            Text(character.approach, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun PeoplePortrait(icon: ImageVector, bright: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    Box(
        Modifier.size(64.dp).clip(RoundedCornerShape(22.dp)).background(
            Brush.linearGradient(
                if (bright) listOf(colors.tertiaryContainer, colors.primaryContainer)
                else listOf(colors.primaryContainer, colors.secondaryContainer),
            ),
        ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(32.dp),
            tint = if (bright) colors.onTertiaryContainer else colors.onPrimaryContainer)
    }
}

@Composable
private fun PeoplePanel(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp),
        color = colors.surfaceContainer, contentColor = colors.onSurface,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

@Composable
private fun ColumnScope.PeopleBubble(sender: String, message: String, incoming: Boolean, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier.fillMaxWidth(.9f).align(if (incoming) Alignment.Start else Alignment.End),
        shape = RoundedCornerShape(16.dp),
        color = if (incoming) colors.surfaceContainerHigh else colors.secondaryContainer,
        contentColor = if (incoming) colors.onSurface else colors.onSecondaryContainer,
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(sender, style = MaterialTheme.typography.labelMedium)
            Text(message, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PeopleChoices(label: String, choices: List<String>, selected: String, tag: String, onChoice: (String) -> Unit) {
    Text(label, style = MaterialTheme.typography.labelLarge)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        choices.forEach { choice ->
            FilterChip(
                selected = choice == selected,
                onClick = { onChoice(choice) },
                label = { Text(choice) },
                modifier = Modifier.heightIn(min = 48.dp).testTag("$tag-${choice.lowercase()}"),
            )
        }
    }
}

@Composable
private fun PeopleSwitch(label: String, checked: Boolean, modifier: Modifier, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked, onChange, modifier.semantics { contentDescription = label })
    }
}
