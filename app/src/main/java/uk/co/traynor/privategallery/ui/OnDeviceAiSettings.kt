package uk.co.traynor.privategallery.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import uk.co.traynor.privategallery.core.editor.*
import uk.co.traynor.privategallery.core.editor.local.*
import java.util.Locale

@Composable
internal fun AiProviderChoices(value: AiProviderChoice, enabled: Boolean = true, changed: (AiProviderChoice) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }, enabled = enabled) { Text("Provider · ${value.label}") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            AiProviderChoice.entries.forEach { choice ->
                val status = when (choice) {
                    AiProviderChoice.AUTO -> "Local first · confirms cloud use"
                    AiProviderChoice.REPLICATE -> if (AiProviderRegistry.configured != null) "Configured" else "Not configured"
                    else -> AiProviderRegistry.provider(choice)?.availabilityLabel ?: "Unavailable"
                }
                DropdownMenuItem(text = { Column { Text(choice.label); Text(status, style = MaterialTheme.typography.bodySmall) } }, onClick = { changed(choice); open = false })
            }
        }
    }
}

@Composable
internal fun OnDeviceAiSettings() {
    val context = LocalContext.current
    val environment = AiProviderRegistry.local ?: return
    val downloads by environment.downloads.collectAsState()
    var choice by remember { mutableStateOf(AiProviderRegistry.choice) }
    var showModels by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<ModelSpec?>(null) }
    var remove by remember { mutableStateOf<ModelSpec?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var revision by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    AiProviderChoices(choice) { choice = it; AiProviderRegistry.choice = it }
    Text("Auto prefers an installed, compatible on-device model. Every cloud fallback asks before uploading an image or using paid credit.", style = MaterialTheme.typography.bodySmall)
    Text("On-device: processed on this device. No image upload, API account or per-generation payment. CPU generation can be slow; advanced model performance needs device acceptance.", style = MaterialTheme.typography.bodySmall)
    TextButton(onClick = { showModels = !showModels }) { Text("On-device models") }
    // Read revision/downloads to refresh disk usage after install/removal.
    val used = remember(downloads, revision) { environment.store.usedBytes() }
    Text("AI model storage · ${decimalGb(used)} GB used", style = MaterialTheme.typography.bodyMedium)
    if (showModels) ModelCatalog.all.forEach { model ->
        val download = downloads[model.id]
        val installed = remember(downloads, revision) { environment.installed(model) }
        val availability = environment.availability(model)
        HorizontalDivider()
        Text(model.name, style = MaterialTheme.typography.titleMedium)
        Text("Image-to-image · Restyle · Masked fill · Object removal", style = MaterialTheme.typography.bodySmall)
        Text("Download: ${decimalGb(model.bytes)} GB (${model.bytes} bytes)\nInstalled: ${decimalGb(model.bytes)} GB (${model.bytes} bytes)", style = MaterialTheme.typography.bodySmall)
        Text(if (installed) "Installed · ${availability.label}" else availability.label, style = MaterialTheme.typography.bodySmall)
        Text(model.licence, style = MaterialTheme.typography.bodySmall)
        download?.let {
            Text(it.message, style = MaterialTheme.typography.bodySmall)
            if (it.busy) {
                LinearProgressIndicator(progress = { (it.bytes.toDouble() / model.bytes).toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                Text("${it.bytes} / ${model.bytes} bytes", style = MaterialTheme.typography.bodySmall)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (download?.busy == true) TextButton(onClick = { environment.cancel(model) }) { Text("Cancel download") }
            else if (!installed) TextButton(enabled = downloads.values.none { it.busy } && availability !in setOf(LocalAvailability.UNSUPPORTED_ANDROID, LocalAvailability.UNSUPPORTED_CHIPSET, LocalAvailability.RUNTIME_NOT_AVAILABLE, LocalAvailability.INSUFFICIENT_RAM), onClick = { confirm = model }) { Text(if (download == null) "Download" else "Retry download") }
            TextButton(enabled = download?.busy != true, onClick = { remove = model }) { Text("Remove model") }
            TextButton(onClick = { confirm = model }) { Text("Licence & download details") }
        }
    }
    message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
    confirm?.let { model ->
        val licence = remember(model) { context.assets.open(model.licenceAsset).bufferedReader().use { it.readText() } }
        var accepted by remember(model) { mutableStateOf(false) }
        val enoughStorage = environment.availableSpace() >= model.bytes + ModelStore.STORAGE_RESERVE
        AlertDialog(onDismissRequest = { confirm = null }, title = { Text(model.name) }, text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Download ${decimalGb(model.bytes)} GB (${model.bytes} bytes). Installed size: ${decimalGb(model.bytes)} GB. Models stay in app-private storage and are not included in the APK.")
                Text("Source: ${java.net.URI(model.url).path.substringBefore("/resolve/").removePrefix("/")} on Hugging Face. Integrity is checked against a pinned SHA-256 before activation.")
                if (!enoughStorage) Text("Insufficient free storage for a fresh download plus the 512 MiB safety reserve. A partial download may need less; Retry checks the exact remaining bytes.")
                Text("CPU processing may take several minutes. SDXL usability is subject to physical-device acceptance.")
                Text(licence, style = MaterialTheme.typography.bodySmall)
                Row { Checkbox(accepted, { accepted = it }); Text("I agree to this model licence, including its use restrictions. These terms govern my use of the model.") }
            }
        }, confirmButton = { TextButton(enabled = accepted && environment.canDownload(model) && downloads.values.none { it.busy }, onClick = {
            environment.accept(model); environment.download(model); confirm = null
        }) { Text("Agree and download") } }, dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancel") } })
    }
    remove?.let { model -> AlertDialog(onDismissRequest = { remove = null }, title = { Text("Remove ${model.name}?") }, text = { Text("Deletes this model and any partial download. Saved Vault images and provider settings are kept.") }, confirmButton = {
        TextButton(onClick = { remove = null; scope.launch { try { environment.remove(model); revision++ } catch (_: Exception) { message = "Could not remove model. Try again." } } }) { Text("Remove model") }
    }, dismissButton = { TextButton(onClick = { remove = null }) { Text("Cancel") } }) }
}
private fun decimalGb(bytes: Long) = String.format(Locale.UK, "%.2f", bytes / 1_000_000_000.0)
