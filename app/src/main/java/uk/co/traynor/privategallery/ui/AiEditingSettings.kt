package uk.co.traynor.privategallery.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.*
import uk.co.traynor.privategallery.core.editor.*

@Composable
fun AiEditingSettings(configuration: AiProviderConfiguration? = null) {
    val context = LocalContext.current
    val config = configuration ?: remember(context.applicationContext) { AiProviderRegistry.initialize(context) }
    val openAi = AiProviderRegistry.openAi
    val options = remember(context.applicationContext) { OpenAiImagePreferences(context) }
    var choice by remember { mutableStateOf(AiProviderRegistry.choice) }
    var model by remember { mutableStateOf(options.model) }
    var quality by remember { mutableStateOf(options.quality) }
    var moderation by remember { mutableStateOf(options.moderation) }
    var openAiSetup by remember { mutableStateOf(false) }
    val status by config.status.collectAsState()
    val consent = remember { AiConsentStore(context) }
    var relaxModeration by remember { mutableStateOf(consent.relaxSeedreamModeration()) }
    var keepInVault by remember { mutableStateOf(consent.keepEditsInVault()) }
    var cleared by remember { mutableStateOf(false) }
    var setup by remember { mutableStateOf(false) }
    GalleryCard {
        GalleryCardHeading("AI editing")
        RetiredModelCleanup()
        Text("Provider", style = MaterialTheme.typography.titleMedium)
        AiProviderChoice.entries.forEach { value ->
            GalleryChoiceRow(value.label, choice == value) { choice = value; AiProviderRegistry.select(value) }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(if (status == AiConnectionStatus.CONNECTED) Icons.Outlined.CheckCircle else Icons.Outlined.CloudQueue, contentDescription = null)
            Column {
                Text(if (status == AiConnectionStatus.NOT_CONFIGURED) "Provider · Not configured" else "Replicate · Seedream 4.5", style = MaterialTheme.typography.titleMedium)
                if (status != AiConnectionStatus.NOT_CONFIGURED) Text(if (status == AiConnectionStatus.CONNECTED) "Connected · token verified" else "Configured · connection not checked this session", style = MaterialTheme.typography.bodySmall)
            }
        }
        Text("AI editing sends the selected image and your edit instructions to the configured AI provider for processing.", style = MaterialTheme.typography.bodyMedium)
        Text(AiProviderRegistry.NETWORK_POLICY, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (status == AiConnectionStatus.NOT_CONFIGURED) Text("Set up Replicate to use cloud AI editing.", style = MaterialTheme.typography.bodyMedium)
        OutlinedButton(onClick = { setup = true }) { Text(if (status == AiConnectionStatus.NOT_CONFIGURED) "Set up provider" else "Manage provider") }
        if (openAi != null) {
            val openStatus by openAi.status.collectAsState()
            Text("OpenAI API · ${when (openStatus) { AiConnectionStatus.NOT_CONFIGURED -> "Not configured"; AiConnectionStatus.CONFIGURED -> "Configured"; AiConnectionStatus.CONNECTED -> "Connected" }}", style = MaterialTheme.typography.titleMedium)
            Text("Uses your separately funded OpenAI API key. An image and edit instruction are sent to OpenAI when you generate.", style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = { openAiSetup = true }) { Text(if (openStatus == AiConnectionStatus.NOT_CONFIGURED) "Set up OpenAI" else "Manage OpenAI") }
            Text("Model", style = MaterialTheme.typography.titleMedium)
            OpenAiImageModel.entries.forEach { value -> GalleryChoiceRow(value.label + if (value == OpenAiImageModel.FLARE) " · Fast everyday editing" else " · Precision editing", model == value) {
                model = value; options.model = value
            } }
            Text("Quality", style = MaterialTheme.typography.titleMedium)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                OpenAiImageQuality.entries.forEach { value -> FilterChip(quality == value, { quality = value; options.quality = value }, label = { Text(value.name.lowercase().replaceFirstChar { it.uppercase() }) }) }
            }
            Text("Output size: Auto · keeps source aspect ratio where the provider can", style = MaterialTheme.typography.bodySmall)
            Text("OpenAI moderation", style = MaterialTheme.typography.titleMedium)
            OpenAiImageModeration.entries.forEach { value -> GalleryChoiceRow(if (value == OpenAiImageModeration.STANDARD) "Standard" else "Lower restriction", moderation == value) {
                moderation = value; options.moderation = value
            } }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Keep AI edits inside Vault", style = MaterialTheme.typography.titleMedium)
                Text("New AI copies and their later edits cannot be restored, exported or shared. Turning this off applies only to future unrestricted copies.", style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = keepInVault, onCheckedChange = { keepInVault = it; consent.setKeepEditsInVault(it) })
        }
        Text("Vault containment controls this app's export routes. It is not DRM; cameras, rooted devices and compromised systems remain outside this protection.", style = MaterialTheme.typography.bodySmall)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Relax Seedream moderation", style = MaterialTheme.typography.titleMedium)
                Text("Off by default. Requests Replicate’s documented relaxed moderation option for new Seedream edits and creations. Replicate and model policies, including illegal-content restrictions, still apply. This does not guarantee a result.", style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = relaxModeration, onCheckedChange = { relaxModeration = it; consent.setRelaxSeedreamModeration(it) })
        }
        TextButton(onClick = { consent.clear(); cleared = true }) { Text(if (cleared) "Consent cleared" else "Clear remembered consent") }
    }
    if (setup) AiProviderSetup(config, { consent.clear(); cleared = true }, { setup = false })
    if (openAiSetup && openAi != null) AiProviderSetup(openAi, { consent.clearFor(OpenAiImageProvider.ID) }, { openAiSetup = false }, "OpenAI")
}

@Composable
private fun AiProviderSetup(config: AiProviderConfiguration, clearConsent: () -> Unit, dismiss: () -> Unit, providerName: String = "Replicate") {
    val status by config.status.collectAsState()
    val scope = rememberCoroutineScope()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var token by remember { mutableStateOf("") } // Never saved-instance state or prefilled from storage.
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf(false) }
    var removal by remember { mutableStateOf(false) }
    var operation by remember { mutableStateOf<Job?>(null) }
    val close = { token = ""; operation?.cancel(); dismiss() }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) close() }
        lifecycle.addObserver(observer)
        onDispose { token = ""; operation?.cancel(); lifecycle.removeObserver(observer) }
    }
    Dialog(onDismissRequest = close, properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn, usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.padding(20.dp).widthIn(max = 560.dp).fillMaxWidth().imePadding(), shape = MaterialTheme.shapes.extraLarge, tonalElevation = 6.dp) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Outlined.Key, contentDescription = null)
                    Text(if (providerName == "OpenAI") "OpenAI API" else "Replicate · Seedream 4.5", style = MaterialTheme.typography.titleLarge)
                }
                Text(when {
                    busy -> "Checking connection…"
                    failed -> "Connection not verified"
                    status == AiConnectionStatus.CONNECTED -> "Connected"
                    status == AiConnectionStatus.CONFIGURED -> "Configured"
                    else -> "Not configured"
                }, style = MaterialTheme.typography.titleMedium)
                Text("Use your own $providerName API ${if (providerName == "OpenAI") "key" else "token"}. It is encrypted on this device and never shown again after saving.", style = MaterialTheme.typography.bodyMedium)
                if (status != AiConnectionStatus.NOT_CONFIGURED) Text("API token saved securely. Leave the field empty to test it, or enter a replacement.", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(value = token, onValueChange = { if (it.length <= 8192) token = it },
                    label = { Text("API token") }, singleLine = true, enabled = !busy,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                    modifier = Modifier.fillMaxWidth())
                Text("Test connection reads model access without sending a photo or generating an image. Generation uses your $providerName account credit.", style = MaterialTheme.typography.bodySmall)
                Text(if (providerName == "OpenAI") "Prompt-based editing is supported. Source and results are sanitized and previewed before Save copy." else "Prompt-based editing is supported. Remote input is resized and compressed; transparent areas use white. Results are previewed before Save copy.", style = MaterialTheme.typography.bodySmall)
                message?.let { Text(it, color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium) }
                Button(modifier = Modifier.fillMaxWidth(), enabled = !busy && (token.isNotBlank() || status != AiConnectionStatus.NOT_CONFIGURED), onClick = {
                    val candidate = token.trim().takeIf { it.isNotEmpty() }?.toByteArray(Charsets.UTF_8)
                    token = ""; busy = true; message = null; failed = false
                    operation = scope.launch(start = CoroutineStart.UNDISPATCHED) {
                        try { config.connect(candidate); message = if (providerName == "OpenAI") "API key and model are available. Image editing may require account credit." else "Connection verified. AI Edit is ready." }
                        catch (_: TimeoutCancellationException) { failed = true; message = "Connection timed out. Check your network and try again." }
                        catch (cancelled: CancellationException) { throw cancelled }
                        catch (failure: AiEditFailure) { failed = true; message = failure.message }
                        finally { candidate?.fill(0); busy = false }
                    }
                }) {
                    if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Test connection")
                }
                if (status != AiConnectionStatus.NOT_CONFIGURED) {
                    if (removal) {
                        Text("Remove the saved token and remembered AI consent? Local editing and Vault media are kept.", style = MaterialTheme.typography.bodyMedium)
                        TextButton(enabled = !busy, onClick = {
                            busy = true
                            operation = scope.launch(start = CoroutineStart.UNDISPATCHED) {
                                try {
                                    withContext(Dispatchers.IO) { config.remove(clearConsent) }
                                    message = "Configuration removed."; failed = false; removal = false
                                } catch (_: Exception) { failed = true; message = "Could not remove configuration. Try again." }
                                finally { busy = false }
                            }
                        }) { Text("Confirm removal", color = MaterialTheme.colorScheme.error) }
                    } else TextButton(enabled = !busy, onClick = { removal = true }) { Text("Remove configuration", color = MaterialTheme.colorScheme.error) }
                }
                TextButton(onClick = close, modifier = Modifier.align(Alignment.End)) { Text(if (busy) "Cancel" else "Done") }
            }
        }
    }
}
