package uk.co.traynor.privategallery.intelligence

import java.net.URI
import java.util.Locale

internal enum class OwnerPreviewTokenValidation(val message: String) {
    MISSING("Enter or paste the preview token."),
    INVALID_CHARACTERS("Use only hexadecimal characters (0–9 and A–F), with no spaces inside the token."),
    INVALID_LENGTH("The preview token must contain exactly 64 hexadecimal characters."),
    VALID("Token format verified."),
}

internal class OwnerPreviewTokenInput private constructor(
    val validation: OwnerPreviewTokenValidation,
    val canonical: String? = null,
) {
    companion object {
        private const val LAUNCHER_LABEL = "Preview token for the app only:"

        fun parse(text: String): OwnerPreviewTokenInput {
            val token = text.trim().removePrefix(LAUNCHER_LABEL).trim()
            return when {
                token.isEmpty() -> OwnerPreviewTokenInput(OwnerPreviewTokenValidation.MISSING)
                token.any { it !in '0'..'9' && it !in 'a'..'f' && it !in 'A'..'F' } ->
                    OwnerPreviewTokenInput(OwnerPreviewTokenValidation.INVALID_CHARACTERS)
                token.length != 64 -> OwnerPreviewTokenInput(OwnerPreviewTokenValidation.INVALID_LENGTH)
                else -> OwnerPreviewTokenInput(OwnerPreviewTokenValidation.VALID, token.lowercase(Locale.ROOT))
            }
        }
    }
}

internal fun ownerPreviewEndpointError(origin: String): String? {
    if (origin.isBlank()) return "The Owner Preview HTTPS address is missing."
    val uri = runCatching { URI(origin) }.getOrNull()
    return if (uri?.scheme == "https" && uri.host == "brain.jamestraynor.co.uk" && uri.port == 18443 &&
        uri.rawUserInfo == null && uri.rawPath == "" && uri.rawQuery == null && uri.rawFragment == null
    ) null else "The configured Owner Preview HTTPS address is invalid. Update NovaOS before connecting."
}
