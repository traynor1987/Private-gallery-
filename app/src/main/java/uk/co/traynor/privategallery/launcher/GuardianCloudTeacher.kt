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
import javax.net.ssl.HttpsURLConnection

internal fun cloudTeacherEndpoint(value: String): URI {
    val uri = URI(value.trim())
    require(uri.scheme == "https" && uri.host != null && uri.rawUserInfo == null &&
        uri.rawQuery == null && uri.rawFragment == null && uri.rawPath == "/guardian/analyze")
    return uri
}

private fun requestCloudAdvice(endpoint: URI, token: String, message: String): String {
    val connection = endpoint.toURL().openConnection() as HttpsURLConnection
    try {
        connection.instanceFollowRedirects = false
        connection.connectTimeout = 8_000
        connection.readTimeout = 20_000
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.setRequestProperty("Authorization", "Bearer $token")
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        val bytes = JSONObject().put("message", message).put("consent", true).toString().toByteArray()
        connection.setFixedLengthStreamingMode(bytes.size)
        try {
            connection.outputStream.use { it.write(bytes) }
        } finally {
            bytes.fill(0)
        }
        check(connection.responseCode == 200)
        val response = connection.inputStream.use { it.readNBytes(32769) }
        require(response.size <= 32768)
        return JSONObject(response.toString(Charsets.UTF_8)).getString("advice")
            .trim().also { require(it.length in 1..4000) }
    } finally {
        connection.disconnect()
    }
}

@Composable
internal fun GuardianCloudTeacher(message: String, canSend: () -> Boolean) {
    var open by remember(message) { mutableStateOf(false) }
    TextButton(onClick = { if (canSend()) open = true }) { Text("Ask Cloud Teacher") }
    if (!open) return
    var endpoint by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
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
                Text("Sending shares only this reviewed message with your configured backend and OpenAI. Cloud advice does not change local classification or learning.")
                OutlinedTextField(endpoint, { endpoint = it.take(512) }, enabled = !busy,
                    label = { Text("HTTPS backend /guardian/analyze") }, singleLine = true)
                OutlinedTextField(token, { token = it.take(256) }, enabled = !busy,
                    label = { Text("Backend access token") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
                if (busy) Text("Waiting for cloud advice…")
                status?.let { Text(it) }
            }
        },
        confirmButton = {
            TextButton(enabled = !busy && url != null && validToken, onClick = {
                if (!canSend()) return@TextButton
                val approvedEndpoint = url ?: return@TextButton
                val approvedToken = token
                token = ""
                status = null
                busy = true
                scope.launch {
                    try {
                        val advice = withContext(Dispatchers.IO) { requestCloudAdvice(approvedEndpoint, approvedToken, message) }
                        if (canSend()) status = "Cloud advisory · $advice"
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: java.io.IOException) {
                        status = "Cloud Teacher is offline or the backend is unreachable. Local review is still available."
                    } catch (_: Exception) {
                        status = "The backend rejected the request or returned an invalid response."
                    } finally {
                        busy = false
                    }
                }
            }) { Text("Send this message to OpenAI") }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = { open = false }) { Text("Close") } },
        properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn),
    )
}
