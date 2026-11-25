package uk.co.traynor.privategallery.intelligence

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.inputmethod.EditorInfo
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.PlatformTextInputInterceptor
import androidx.compose.ui.platform.PlatformTextInputMethodRequest
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import java.math.BigDecimal
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import uk.co.traynor.privategallery.core.brain.CloudTeacherException

@Composable
internal fun OwnerPreviewSettings(
    canUsePreview: (Context) -> Boolean = ::intelligencePrivateAllowed,
    checkPreview: ((OwnerPreviewConnection, () -> Boolean) -> OwnerPreviewDiagnostics)? = null,
) {
    val context = LocalContext.current.applicationContext
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val store = remember(context) { OwnerPreviewStore(context) }
    val client = remember(context) { OwnerPreviewClient(networkAvailable = { ownerPreviewNetworkAvailable(context) }) }
    val scope = rememberCoroutineScope()
    var enabled by remember { mutableStateOf(false) }
    var configuring by rememberSaveable { mutableStateOf(false) }
    var token by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var operation by remember { mutableStateOf<Job?>(null) }
    var generation by remember { mutableStateOf(0) }
    var pending by remember { mutableStateOf<OwnerPreviewTest?>(null) }
    var status by remember { mutableStateOf<OwnerPreviewStatus?>(null) }
    var diagnostics by remember { mutableStateOf<OwnerPreviewDiagnostics?>(null) }
    var diagnosticsOpen by rememberSaveable { mutableStateOf(false) }
    var replies by remember { mutableStateOf(emptyMap<OwnerPreviewTest, OwnerPreviewReply>()) }
    var message by remember { mutableStateOf<String?>(null) }
    val endpointError = remember { ownerPreviewEndpointError(OWNER_PREVIEW_ORIGIN) }

    fun foreground() = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
    fun allowed() = foreground() && canUsePreview(context)

    LaunchedEffect(lifecycle, store) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            OwnerPreviewStore.changes.collect {
                enabled = withContext(Dispatchers.IO) { store.isEnabled() }
            }
        }
    }
    DisposableEffect(lifecycle, scope) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                scope.coroutineContext.cancelChildren()
                token = ""
                pending = null
                status = null
                replies = emptyMap()
                message = null
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    fun act(failure: String, replace: Boolean = false, action: suspend () -> Unit) {
        if (!foreground() || busy && !replace) return
        val ticket = ++generation
        if (replace) operation?.cancel()
        busy = true
        message = null
        operation = scope.launch {
            try { action() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: CloudTeacherException) { message = error.failure.userMessage }
            catch (_: Exception) { message = failure }
            finally { if (ticket == generation) busy = false }
        }
    }

    suspend fun verifyConnection() {
        val job = currentCoroutineContext().job
        val outcome = runInterruptible(Dispatchers.IO) {
            val connection = checkNotNull(store.read())
            val current = { job.isActive && allowed() && store.isCurrent(connection) }
            (checkPreview ?: client::diagnose)(connection, current)
        }
        diagnostics = outcome
        status = outcome.status
    }

    fun checkConnection() {
        if (!allowed()) return
        if (endpointError != null) {
            message = endpointError
            return
        }
        status = null
        act("Owner Preview check failed. Verify the current preview session and its token.") {
            verifyConnection()
        }
    }

    fun send(test: OwnerPreviewTest) {
        if (!allowed() || status?.allowed != true) return
        status = null
        act("Owner Preview could not complete the request. Check the remaining allowance before continuing.") {
            replies = replies + (test to withContext(Dispatchers.IO) {
                val connection = checkNotNull(store.read())
                client.send(connection, test, approved = true) { isActive && allowed() && store.isCurrent(connection) }
            })
        }
    }

    ElevatedCard(Modifier.fillMaxWidth().testTag("nova-owner-preview")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Owner Preview", style = MaterialTheme.typography.titleLarge)
            Text(if (enabled) "Enabled · temporary connection" else "Off",
                Modifier.testTag("nova-owner-preview-status"))
            Text(OWNER_PREVIEW_ORIGIN, style = MaterialTheme.typography.bodyMedium)
            endpointError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Text("Two synthetic tests share a US$0.25 server ceiling. Each send needs your confirmation.")
            if (!enabled) {
                TextButton(enabled = !busy, onClick = {
                    if (allowed()) configuring = true else message = "Unlock NovaOS and end Sanctuary to set up the preview."
                }) { Text("Set up Owner Preview") }
            } else {
                if (status == null) Text("Connection and shared allowance need a fresh check.")
                TextButton(enabled = !busy, onClick = {
                    if (allowed()) checkConnection() else message = "Unlock NovaOS and end Sanctuary to check the preview."
                }) { Text("Check preview connection") }
                status?.let { verified ->
                    Text(if (verified.armed) "Authenticated · funded preview"
                        else "Authenticated · read-only preview, paid analysis off")
                    Text("${verified.remaining} approved requests left")
                    Text("Available budget US$ ${previewDollars(verified.availableMicroUsd)} of US$ ${previewDollars(verified.ceilingMicroUsd)}")
                    if (verified.armed && !verified.allowed) Text("The shared ledger does not currently allow another paid request.")
                }
                diagnostics?.failure?.let { failure ->
                    Text(failure.message, color = MaterialTheme.colorScheme.error)
                }
                OwnerPreviewTest.entries.forEach { test ->
                    TextButton(enabled = !busy && status?.allowed == true && test !in replies, onClick = {
                        if (allowed()) pending = test
                    }) { Text("Test ${test.title}") }
                    replies[test]?.let { reply ->
                        reply.assessment?.let { Text(it) }
                        Text(reply.text)
                    }
                }
                TextButton(onClick = {
                    pending = null
                    status = null
                    diagnostics = null
                    replies = emptyMap()
                    act("Could not remove the preview configuration.", replace = true) {
                        withContext(Dispatchers.IO) { store.disable() }
                        enabled = false
                    }
                }) { Text("Disable Owner Preview") }
            }
            TextButton(onClick = { diagnosticsOpen = !diagnosticsOpen }) {
                Text(if (diagnosticsOpen) "Hide diagnostics" else "Connection diagnostics")
            }
            if (diagnosticsOpen) {
                val report = previewDiagnosticsReport(enabled, endpointError, diagnostics)
                Text(report, Modifier.testTag("nova-owner-preview-diagnostics"), style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = {
                    context.getSystemService(ClipboardManager::class.java)
                        .setPrimaryClip(ClipData.newPlainText("Nova Brain connection diagnostics", report))
                    message = "Diagnostics copied."
                }) { Text("Copy diagnostics") }
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            message?.let { Text(it) }
        }
    }

    if (configuring) {
        OwnerPreviewSetupDialog(token, { token = it }, endpointError, onDismiss = {
            configuring = false
            token = ""
        }, onSave = { previewToken ->
            if (!allowed()) {
                configuring = false
                token = ""
                message = "Unlock NovaOS and end Sanctuary to enable the preview."
            } else {
                configuring = false
                token = ""
                diagnostics = null
                act("Could not save the preview. Use its separate 64-character token.") {
                    withContext(Dispatchers.IO) { store.enable(previewToken) }
                    enabled = true
                    verifyConnection()
                }
            }
        })
    }

    pending?.let { test ->
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text("Approve one ${test.title} test?") },
            text = { Text("OpenAI will receive this synthetic text through your preview. A send uses one slot and reserves up to US$0.102.\n\n${test.fixture}") },
            confirmButton = { TextButton(onClick = { pending = null; send(test) }) { Text("Approve and send") } },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("Cancel") } },
            properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn),
        )
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun OwnerPreviewSetupDialog(
    token: String,
    onTokenChange: (String) -> Unit,
    endpointError: String?,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    val context = LocalContext.current
    val input = remember(token) { OwnerPreviewTokenInput.parse(token) }
    val noLearning = remember {
        PlatformTextInputInterceptor { request, next ->
            next.startInputMethod(PlatformTextInputMethodRequest { attrs ->
                request.createInputConnection(attrs).also {
                    attrs.imeOptions = attrs.imeOptions or EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
                }
            })
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Enable Owner Preview?") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Preview address")
                Text(OWNER_PREVIEW_ORIGIN, style = MaterialTheme.typography.bodySmall)
                Text("Use the token shown in your temporary Nova Brain session.")
                InterceptPlatformTextInput(interceptor = noLearning) {
                    OutlinedTextField(
                        value = token,
                        onValueChange = onTokenChange,
                        modifier = Modifier.fillMaxWidth().testTag("owner-preview-token"),
                        label = { Text("Preview token") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        isError = token.isNotBlank() && input.canonical == null,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.None,
                            autoCorrectEnabled = false,
                            keyboardType = KeyboardType.Password,
                        ),
                    )
                }
                TextButton(onClick = {
                    val text = context.getSystemService(ClipboardManager::class.java)
                        .primaryClip?.takeIf { it.itemCount == 1 }?.getItemAt(0)?.text
                    onTokenChange(text?.toString().orEmpty())
                }) { Text("Paste token") }
                Text(endpointError ?: input.validation.message, Modifier.testTag("owner-preview-validation"))
            }
        },
        confirmButton = {
            TextButton(enabled = endpointError == null && input.canonical != null, onClick = {
                input.canonical?.let(onSave)
            }) { Text("Save and enable") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOff),
    )
}

private fun previewDiagnosticsReport(
    enabled: Boolean,
    endpointError: String?,
    diagnostics: OwnerPreviewDiagnostics?,
): String = buildString {
    appendLine("Local validation: ${endpointError ?: if (enabled) "Preview address and token format verified" else "Preview token not configured"}")
    if (diagnostics != null) {
        append(diagnostics.asText())
    } else {
        appendLine("Endpoint: $OWNER_PREVIEW_ORIGIN")
        appendLine("Connection mode: ${if (enabled) "Owner Preview" else "Owner Preview off"}")
        appendLine("Last connection attempt: none since opening settings")
        appendLine("HTTP and TLS: not checked")
        appendLine("Authentication: not checked")
        append("Staging, paid analysis and expiration: not checked")
    }
}

private fun previewDollars(microUsd: Long): String =
    BigDecimal.valueOf(microUsd, 6).stripTrailingZeros().toPlainString()
