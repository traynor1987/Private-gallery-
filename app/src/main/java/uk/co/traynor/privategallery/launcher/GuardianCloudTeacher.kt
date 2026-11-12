package uk.co.traynor.privategallery.launcher

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URI
import java.time.Instant
import javax.net.ssl.HttpsURLConnection

private fun requestCloud(endpoint: URI, token: String, approvedPayload: ByteArray? = null): JSONObject {
    val connection = endpoint.toURL().openConnection() as HttpsURLConnection
    try {
        connection.instanceFollowRedirects = false
        connection.connectTimeout = 8_000
        connection.readTimeout = 20_000
        connection.setRequestProperty("Authorization", "Bearer $token")
        if (approvedPayload != null) {
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.setFixedLengthStreamingMode(approvedPayload.size)
            connection.outputStream.use { it.write(approvedPayload) }
        }
        check(connection.responseCode == 200)
        val response = connection.inputStream.use { it.readNBytes(32769) }
        require(response.size <= 32768)
        return JSONObject(response.toString(Charsets.UTF_8))
    } finally {
        connection.disconnect()
    }
}

@Composable
internal fun GuardianCloudTeacher(
    message: String? = null,
    historicalEvidence: Boolean = false,
    canSend: () -> Boolean,
) {
    var open by remember(message) { mutableStateOf(false) }
    TextButton(onClick = { if (canSend()) open = true }) { Text(when {
        historicalEvidence -> "Review OCR excerpt for cloud analysis"
        message == null -> "Cloud Teacher status"
        else -> "Ask Cloud Teacher"
    }) }
    if (!open) return
    var endpoint by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var approved by remember { mutableStateOf(false) }
    var upload by remember(message) { mutableStateOf(message.orEmpty()) }
    var connectionLines by remember { mutableStateOf<List<String>>(emptyList()) }
    var lastConnection by remember { mutableStateOf<Instant?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val url = remember(endpoint) { runCatching { cloudTeacherEndpoint(endpoint) }.getOrNull() }
    val validToken = token.length in 32..256 && !token.startsWith("sk-") &&
        token.all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it in "-_." }
    GuardianAlertDialog(
        onDismissRequest = { if (!busy) open = false },
        title = { Text("Cloud Teacher") },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                Text("Local Nova Brain is available without cloud access.")
                Text("Check an authenticated HTTPS gateway. Checking status sends only your access token.")
                lastConnection?.let { Text("Last authenticated check: $it") }
                connectionLines.forEach { Text(it) }
                TextButton(enabled = !busy && url != null && validToken, onClick = {
                    if (!canSend()) return@TextButton
                    val target = cloudTeacherStatusEndpoint(url ?: return@TextButton)
                    val credential = token
                    status = null
                    connectionLines = emptyList()
                    busy = true
                    scope.launch {
                        try {
                            val lines = withContext(Dispatchers.IO) {
                                cloudTeacherStatusLines(requestCloud(target, credential))
                            }
                            if (canSend()) {
                                connectionLines = lines
                                lastConnection = Instant.now()
                            }
                        } catch (error: CancellationException) {
                            throw error
                        } catch (_: java.io.IOException) {
                            status = "Gateway unreachable. Local review is available."
                        } catch (_: Exception) {
                            status = "Gateway rejected the request or returned an invalid status."
                        } finally {
                            busy = false
                        }
                    }
                }) { Text("Check connection and budget") }
                if (message != null) {
                    Text("Only the text below is sent to your gateway and OpenAI. Edit it to remove private details.")
                    OutlinedTextField(upload, { upload = it.take(4096); approved = false }, enabled = !busy,
                        label = { Text("Text to send") })
                    Row {
                        Checkbox(approved, { approved = it }, enabled = !busy)
                        Text(if (historicalEvidence) "Allow cloud analysis of this historical OCR excerpt"
                        else "Allow cloud analysis for this request")
                    }
                }
                OutlinedTextField(endpoint, { endpoint = it.take(512); approved = false; connectionLines = emptyList(); lastConnection = null }, enabled = !busy,
                    label = { Text("HTTPS backend /guardian/analyze") }, singleLine = true)
                OutlinedTextField(token, { token = it.take(256); approved = false; connectionLines = emptyList(); lastConnection = null }, enabled = !busy,
                    label = { Text("Backend access token") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
                if (busy) Text("Contacting Cloud Teacher…")
                status?.let { Text(it) }
            }
        },
        confirmButton = {
            if (message != null) TextButton(enabled = !busy && url != null && validToken && approved && upload.length in 1..4096 && upload.isNotBlank(), onClick = {
                if (!canSend()) return@TextButton
                val approvedEndpoint = url ?: return@TextButton
                val approvedToken = token
                val payload = cloudTeacherApprovedPayload(upload, approved)
                approved = false
                token = ""
                status = null
                busy = true
                scope.launch {
                    try {
                        val advice = withContext(Dispatchers.IO) {
                            requestCloud(approvedEndpoint, approvedToken, payload).getString("advice")
                                .trim().also { require(it.length in 1..4000) }
                        }
                        if (canSend()) status = "Cloud advisory · $advice"
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: java.io.IOException) {
                        status = "Cloud Teacher is offline or the backend is unreachable. Local review is still available."
                    } catch (_: Exception) {
                        status = "The backend rejected the request or returned an invalid response."
                    } finally {
                        payload.fill(0)
                        busy = false
                    }
                }
            }) { Text("Send this message to OpenAI") }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = { open = false }) { Text("Close") } },
        properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn),
    )
}
