package uk.co.traynor.privategallery.core.brain

import java.io.IOException
import java.net.URI
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLException
import org.json.JSONObject

internal data class NovaBrainConfiguration(val origin: String, val token: String) {
    init {
        val uri = URI(origin)
        require(uri.scheme == "https" && uri.host != null && uri.rawUserInfo == null &&
            uri.rawQuery == null && uri.rawFragment == null && uri.rawPath in listOf("", "/") &&
            (uri.port == -1 || uri.port in 1..65535)) { "Use an HTTPS server address without a path or credentials." }
        require(token.length in 32..256 && !token.startsWith("sk-") &&
            token.all { it.isLetterOrDigit() && it.code < 128 || it in "-_." }) { "Use the backend owner token, not an OpenAI key." }
    }
    fun endpoint(path: String): URI {
        require(path in setOf("/health", "/guardian/status"))
        return URI(origin).resolve(path)
    }
    // Never include the credential in diagnostics or data-class logging.
    override fun toString() = "NovaBrainConfiguration(redacted)"
}

internal enum class BrainConnectionPhase { DISCONNECTED, CHECKING, CONNECTED, AUTHENTICATION_REQUIRED, OFFLINE, ERROR }
internal data class BrainConnectionStatus(
    val phase: BrainConnectionPhase = BrainConnectionPhase.DISCONNECTED,
    val message: String = "Disconnected · local Nova Brain remains available",
    val analysisEnabledOnServer: Boolean = false,
    val staging: Boolean = false,
)
internal data class BrainResponse(val code: Int, val body: ByteArray)
internal fun interface BrainStatusTransport { fun get(endpoint: URI, token: String?): BrainResponse }

/** Read-only status contract inspected in scripts/cloud_teacher/server.py. Never calls /analyze. */
internal class NovaBrainConnection(private val transport: BrainStatusTransport = BrainStatusTransport(::getBrainStatus)) {
    fun check(configuration: NovaBrainConfiguration, cancelled: () -> Boolean = { false }): BrainConnectionStatus {
        repeat(2) { attempt ->
            if (cancelled()) return BrainConnectionStatus()
            try {
                val health = transport.get(configuration.endpoint("/health"), null)
                if (health.code in 500..599 && attempt == 0) return@repeat
                check(health.code == 200 && JSONObject(health.body.decodeToString()).getString("status") == "ok")
                if (cancelled()) return BrainConnectionStatus()
                val response = transport.get(configuration.endpoint("/guardian/status"), configuration.token)
                if (response.code in setOf(401, 403)) return BrainConnectionStatus(BrainConnectionPhase.AUTHENTICATION_REQUIRED,
                    "Authentication rejected · check your backend owner token. Local processing is available.")
                if (response.code in 500..599 && attempt == 0) return@repeat
                if (response.code == 429) return BrainConnectionStatus(BrainConnectionPhase.ERROR,
                    "Server is busy · try again later. Local processing is available.")
                check(response.code == 200)
                val status = JSONObject(response.body.decodeToString())
                check(status.getString("status") == "authenticated")
                val model = status.getString("model")
                val staging = model == "staging"
                val enabled = status.getBoolean("analysis_enabled") && !staging
                return BrainConnectionStatus(BrainConnectionPhase.CONNECTED,
                    if (staging) "Connected · authenticated · staging, analysis disabled"
                    else "Connected · authenticated · ${if (enabled) "server analysis available" else "analysis disabled"}", enabled, staging)
            } catch (_: SSLException) {
                return BrainConnectionStatus(BrainConnectionPhase.ERROR, "Secure connection failed · certificate validation was not bypassed. Local processing is available.")
            } catch (_: IOException) {
                if (attempt == 1) return BrainConnectionStatus(BrainConnectionPhase.OFFLINE, "Server unreachable · local Nova Brain remains available")
            } catch (_: Exception) {
                return BrainConnectionStatus(BrainConnectionPhase.ERROR, "Invalid server response · local Nova Brain remains available")
            }
        }
        return BrainConnectionStatus(BrainConnectionPhase.OFFLINE, "Server unavailable · local Nova Brain remains available")
    }
}

private fun getBrainStatus(endpoint: URI, token: String?): BrainResponse {
    require(endpoint.scheme == "https" && endpoint.rawUserInfo == null && endpoint.rawQuery == null &&
        endpoint.rawFragment == null && endpoint.rawPath in setOf("/health", "/guardian/status"))
    val connection = endpoint.toURL().openConnection() as HttpsURLConnection
    try {
        connection.instanceFollowRedirects = false
        connection.connectTimeout = 8_000
        connection.readTimeout = 8_000
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("Cache-Control", "no-store")
        connection.useCaches = false
        if (token != null) connection.setRequestProperty("Authorization", "Bearer $token")
        val code = connection.responseCode
        // Reject every redirect; never forward the credential to a different origin.
        if (code != 200) return BrainResponse(code, byteArrayOf())
        require(connection.contentLengthLong <= 32768)
        val bytes = connection.inputStream.use { it.readNBytes(32769) }
        require(bytes.size <= 32768)
        return BrainResponse(code, bytes)
    } finally { connection.disconnect() }
}
