package uk.co.traynor.privategallery.intelligence

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.time.Instant

internal enum class OwnerPreviewTls(val label: String) {
    NOT_VERIFIED("Not verified"), VERIFIED("Verified"), FAILED("Verification failed"),
}

internal enum class OwnerPreviewExpiration(val label: String) {
    UNKNOWN("Unknown"), ACTIVE("Active"), EXPIRED("Expired"),
}

internal enum class OwnerPreviewFailure(val message: String) {
    OFFLINE("The Fold has no active internet connection."),
    HOST_LOOKUP("The Fold could not resolve the preview server address."),
    NETWORK_ROUTE("The Fold's network cannot reach the preview server."),
    SERVER_UNAVAILABLE("The preview server is unavailable. It may have stopped or the HTTPS port may be blocked."),
    TIMEOUT("The preview server did not respond in time."),
    TLS("TLS verification failed. Check the preview certificate and server address."),
    AUTHENTICATION("Preview access was rejected. Check the separate preview token and the Fold's network."),
    ROUTE_UNAVAILABLE("The HTTPS server does not provide the required Owner Preview routes."),
    RATE_LIMITED("The preview server is busy. Wait before checking again."),
    EXPIRED("The server reports that this preview has expired."),
    INVALID_RESPONSE("The response did not verify the Owner Preview identity and spending limits."),
}

/** Only fixed categories and verified, non-sensitive values may reach settings diagnostics. */
internal data class OwnerPreviewDiagnostics(
    val attemptedAt: Instant,
    val status: OwnerPreviewStatus? = null,
    val httpStatus: Int? = null,
    val tls: OwnerPreviewTls = OwnerPreviewTls.NOT_VERIFIED,
    val failure: OwnerPreviewFailure? = null,
    val expiration: OwnerPreviewExpiration = OwnerPreviewExpiration.UNKNOWN,
    val expiresAt: Instant? = null,
) {
    val authenticated: Boolean get() = status != null

    fun asText(): String = buildList {
        add("Endpoint: $OWNER_PREVIEW_ORIGIN")
        add("Connection mode: Owner Preview")
        add("Last connection attempt: $attemptedAt")
        add("HTTP: ${httpStatus ?: "No response"}")
        add("TLS: ${tls.label}")
        add("Authenticated: ${if (authenticated) "Yes" else "No"}")
        add("Preview state: ${when (status?.armed) {
            true -> "Funded"
            false -> "Disarmed"
            null -> "Unknown"
        }}")
        add("Paid analysis: ${when (status?.armed) {
            true -> "Enabled on the server"
            false -> "Disabled"
            null -> "Unknown"
        }}")
        add("Expiration at last check: ${expiration.label}${expiresAt?.let { " ($it)" }.orEmpty()}")
        failure?.let { add("Result: ${it.message}") }
    }.joinToString("\n")
}

internal fun ownerPreviewNetworkAvailable(context: Context): Boolean {
    val manager = context.getSystemService(ConnectivityManager::class.java) ?: return false
    val network = manager.activeNetwork ?: return false
    return manager.getNetworkCapabilities(network)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
}
