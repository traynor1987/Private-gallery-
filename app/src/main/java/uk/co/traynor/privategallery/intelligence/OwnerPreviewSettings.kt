package uk.co.traynor.privategallery.intelligence

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uk.co.traynor.privategallery.core.brain.CloudTeacherException

@Composable
internal fun OwnerPreviewSettings() {
    val context = LocalContext.current.applicationContext
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val store = remember(context) { OwnerPreviewStore(context) }
    val client = remember { OwnerPreviewClient() }
    val scope = rememberCoroutineScope()
    var enabled by remember { mutableStateOf(false) }
    var configuring by remember { mutableStateOf(false) }
    var token by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<OwnerPreviewTest?>(null) }
    var status by remember { mutableStateOf<OwnerPreviewStatus?>(null) }
    var replies by remember { mutableStateOf(emptyMap<OwnerPreviewTest, OwnerPreviewReply>()) }
    var message by remember { mutableStateOf<String?>(null) }

    fun foreground() = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
    fun allowed() = foreground() && intelligencePrivateAllowed(context)

    LaunchedEffect(lifecycle, store) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            enabled = withContext(Dispatchers.IO) { store.isEnabled() }
        }
    }
    DisposableEffect(lifecycle, scope) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                scope.coroutineContext.cancelChildren()
                configuring = false
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

    fun act(failure: String, action: suspend () -> Unit) {
        if (!foreground() || busy) return
        busy = true
        message = null
        scope.launch {
            try { action() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: CloudTeacherException) { message = error.failure.userMessage }
            catch (_: Exception) { message = failure }
            finally { busy = false }
        }
    }

    fun checkConnection() {
        if (!allowed()) return
        status = null
        act("Owner Preview check failed. Verify the current preview session and its token.") {
            status = withContext(Dispatchers.IO) {
                val connection = checkNotNull(store.read())
                client.check(connection) { isActive && allowed() && store.isCurrent(connection) }
            }
        }
    }

    fun send(test: OwnerPreviewTest) {
        if (!allowed() || status?.allowed != true) return
        act("Owner Preview could not complete the request. Check the remaining allowance before continuing.") {
            status = status?.let { it.copy(remaining = (it.remaining - 1).coerceAtLeast(0), allowed = it.remaining > 1) }
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
            Text("Two synthetic tests share a US$0.25 server ceiling. Each send needs your confirmation.")
            if (!enabled) {
                TextButton(enabled = !busy, onClick = {
                    if (allowed()) configuring = true else message = "Unlock NovaOS and end Sanctuary to set up the preview."
                }) { Text("Set up Owner Preview") }
            } else {
                Text(OWNER_PREVIEW_ORIGIN)
                TextButton(enabled = !busy, onClick = {
                    if (allowed()) checkConnection() else message = "Unlock NovaOS and end Sanctuary to check the preview."
                }) { Text("Check preview connection") }
                status?.let {
                    Text(if (!it.armed) "Authenticated · paid analysis off"
                        else "Authenticated · ${it.remaining} approved requests left")
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
                TextButton(enabled = !busy, onClick = {
                    act("Could not remove the preview configuration.") {
                        withContext(Dispatchers.IO) { store.disable() }
                        enabled = false
                        pending = null
                        status = null
                        replies = emptyMap()
                    }
                }) { Text("Disable Owner Preview") }
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            message?.let { Text(it) }
        }
    }

    if (configuring) AlertDialog(
        onDismissRequest = { configuring = false; token = "" },
        title = { Text("Enable Owner Preview?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Enter the separate token from the temporary Nova Brain session. The preview sends only the two fixed synthetic texts you approve.")
                OutlinedTextField(token, { token = it.take(80) }, label = { Text("Preview token") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
            }
        },
        confirmButton = {
            TextButton(enabled = token.trim().matches(Regex("[a-f0-9]{64}")), onClick = {
                if (!allowed()) return@TextButton
                val previewToken = token.trim()
                configuring = false
                token = ""
                act("Could not save the preview. Use its separate 64-character token.") {
                    withContext(Dispatchers.IO) { store.enable(previewToken) }
                    enabled = true
                }
            }) { Text("Save and enable") }
        },
        dismissButton = { TextButton(onClick = { configuring = false; token = "" }) { Text("Cancel") } },
        properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn),
    )

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
