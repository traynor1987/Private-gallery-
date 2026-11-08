package uk.co.traynor.privategallery.core.evidence

import java.security.MessageDigest
import java.util.HexFormat

/** Generates an optional formatting edit. A proposal never becomes the effective transcript by itself. */
internal object EvidenceTranscript69 {
    data class Suggestion(val id: String, val text: String)

    private const val MAX_TRANSCRIPT = 100_000
    private val horizontalGaps = Regex("(?<=\\S)[ \\t]{2,}(?=\\S)")
    private val tokens = Regex("[^\\s]+")
    private val missingSpace = Regex("(?<=\\p{L})([!?;,])(?=\\p{L})")

    fun suggest(evidenceId: String, extractionId: String, original: String): Suggestion? {
        val text = original.splitToSequence('\n').joinToString("\n") { line ->
            tokens.replace(line.replace(horizontalGaps, " ")) { token ->
                val value = token.value
                if (value.contains("://") || value.contains('@') || value.startsWith("www.", ignoreCase = true)
                    || value.contains('=') || value.contains('/')) value
                else missingSpace.replace(value, "$1 ")
            }
        }
        if (text == original || text.length > MAX_TRANSCRIPT) return null
        val id = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
            .digest("ocr-format-69\u0000$evidenceId\u0000$extractionId\u0000$text".encodeToByteArray()))
        return Suggestion(id, text)
    }
}
