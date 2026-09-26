package uk.co.traynor.privategallery.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import uk.co.traynor.privategallery.core.editor.*
import uk.co.traynor.privategallery.core.vault.VaultItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateImageSheet(onGenerate: (GenerationRequest, (String) -> Unit, (Result<VaultItem>) -> Unit) -> (() -> Unit),
    onSaved: (VaultItem) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val modelStore = remember { GenerationModelStore(context) }
    val consent = remember { AiConsentStore(context) }
    val configured = remember { AiCredentialStore(context).isConfigured(ReplicateSeedreamProvider.ID) }
    var model by remember { mutableStateOf(modelStore.selected()) }
    var aspect by remember { mutableStateOf(GenerationAspect.SQUARE) }
    var prompt by remember { mutableStateOf("") }
    var negative by remember { mutableStateOf("") }
    var seed by remember { mutableStateOf("") }
    var steps by remember { mutableStateOf("30") }
    var advanced by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var stage by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var consentPending by remember { mutableStateOf(false) }
    var cancelJob by remember { mutableStateOf<(() -> Unit)?>(null) }
    DisposableEffect(Unit) { onDispose { cancelJob?.invoke() } }

    fun start() {
        if (busy) return
        val request = runCatching { GenerationRequest(model, prompt.trim(), aspect,
            negativePrompt = negative.takeIf { it.isNotBlank() },
            seed = seed.takeIf { it.isNotBlank() }?.toIntOrNull(),
            steps = if (GenerationCapability.STEPS in model.capabilities) steps.toIntOrNull() else null) }.getOrNull()
        if (request == null || (seed.isNotBlank() && seed.toIntOrNull() == null) ||
            (GenerationCapability.STEPS in model.capabilities && steps.toIntOrNull() == null)) {
            error = "Check the prompt and advanced values."; return
        }
        busy = true; error = ""; stage = "Preparing request…"
        cancelJob = onGenerate(request, { stage = it }) { result ->
            busy = false; cancelJob = null
            result.onSuccess(onSaved).onFailure { error = it.message?.take(160) ?: "Could not create the image." }
        }
    }

    ModalBottomSheet(onDismissRequest = { cancelJob?.invoke(); onDismiss() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().heightIn(max = 700.dp).verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.AutoAwesome, null)
                Text("Create image", style = MaterialTheme.typography.headlineSmall)
            }
            Text("Provider · Replicate", style = MaterialTheme.typography.labelLarge)
            if (!configured) Text("Set up Replicate in Settings → AI editing first.", color = MaterialTheme.colorScheme.error)
            if (busy) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text(stage.ifBlank { "Generating…" })
                Text("Progress depends on the provider; no percentage is available.", style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { cancelJob?.invoke(); cancelJob = null; busy = false;
                    stage = ""; error = "Cancelled locally. A submitted Replicate prediction may still use credit." }) { Text("Cancel") }
            } else {
                Text("Model", style = MaterialTheme.typography.titleMedium)
                GenerationModel.entries.forEach { choice ->
                    GalleryChoiceRow("${choice.label} · ${choice.description}", model == choice) {
                        model = choice; modelStore.select(choice)
                        if (aspect !in choice.aspects) aspect = GenerationAspect.SQUARE
                        negative = ""; seed = ""; steps = "30"
                    }
                }
                OutlinedTextField(prompt, { prompt = it.take(4000) }, modifier = Modifier.fillMaxWidth(),
                    label = { Text("Describe the image") }, minLines = 3, maxLines = 6)
                Text("Aspect ratio", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    model.aspects.forEach { value -> FilterChip(selected = aspect == value,
                        onClick = { aspect = value }, label = { Text(value.label) }) }
                }
                if (model.capabilities.any { it in setOf(GenerationCapability.NEGATIVE_PROMPT, GenerationCapability.SEED, GenerationCapability.STEPS) }) {
                    TextButton(onClick = { advanced = !advanced }) { Text(if (advanced) "Hide advanced" else "Advanced") }
                    if (advanced) {
                        if (GenerationCapability.NEGATIVE_PROMPT in model.capabilities)
                            OutlinedTextField(negative, { negative = it.take(2000) }, Modifier.fillMaxWidth(), label = { Text("Negative prompt") })
                        if (GenerationCapability.SEED in model.capabilities)
                            OutlinedTextField(seed, { seed = it.take(11) }, Modifier.fillMaxWidth(),
                                label = { Text("Seed · blank for random") }, singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                        if (GenerationCapability.STEPS in model.capabilities)
                            OutlinedTextField(steps, { steps = it.take(3) }, Modifier.fillMaxWidth(),
                                label = { Text("Steps · 1–100") }, singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    }
                }
                Text("1 image · Uses Replicate API credit", style = MaterialTheme.typography.bodySmall)
                if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
                Button(onClick = { if (consent.hasConsent(ReplicateSeedreamProvider.ID)) start() else consentPending = true },
                    modifier = Modifier.fillMaxWidth(), enabled = configured && prompt.isNotBlank()) { Text("Generate") }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
    if (consentPending) AlertDialog(onDismissRequest = { consentPending = false },
        title = { Text("Remote image creation") },
        text = { Text("Your prompt is sent to Replicate to create an image. This uses your Replicate API credit. The result is saved to encrypted Vault storage.") },
        confirmButton = { TextButton(onClick = { consent.remember(ReplicateSeedreamProvider.ID); consentPending = false; start() }) { Text("Continue") } },
        dismissButton = { TextButton(onClick = { consentPending = false }) { Text("Cancel") } })
}
