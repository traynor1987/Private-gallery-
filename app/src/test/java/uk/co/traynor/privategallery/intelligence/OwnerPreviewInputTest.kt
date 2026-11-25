package uk.co.traynor.privategallery.intelligence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class OwnerPreviewInputTest {
    private val token = "01abcdef".repeat(8)

    @Test fun acceptsRawLauncherLineAndWhitespaceBeforeLengthValidation() {
        val launcherLine = "Preview token for the app only: ${token.uppercase()}"
        for (input in listOf(token, launcherLine, " ".repeat(20) + launcherLine + "\r\n", "\n$token\t")) {
            assertEquals(OwnerPreviewTokenValidation.VALID, OwnerPreviewTokenInput.parse(input).validation)
            assertEquals(token, OwnerPreviewTokenInput.parse(input).canonical)
        }
    }

    @Test fun rejectsMissingTruncatedAndAmbiguousValues() {
        assertInvalid(" \n", OwnerPreviewTokenValidation.MISSING)
        assertInvalid(token.dropLast(1), OwnerPreviewTokenValidation.INVALID_LENGTH)
        assertInvalid(token + "a", OwnerPreviewTokenValidation.INVALID_LENGTH)
        assertInvalid("Bearer $token", OwnerPreviewTokenValidation.INVALID_CHARACTERS)
        assertInvalid(token.take(32) + " " + token.drop(32), OwnerPreviewTokenValidation.INVALID_CHARACTERS)
        assertInvalid(token.dropLast(1) + "０", OwnerPreviewTokenValidation.INVALID_CHARACTERS)
        assertInvalid("$token\n$token", OwnerPreviewTokenValidation.INVALID_CHARACTERS)
    }

    @Test fun onlyFixedPreviewHttpsOriginMayReceiveTheToken() {
        assertNull(ownerPreviewEndpointError(OWNER_PREVIEW_ORIGIN))
        for (origin in listOf("", "http://brain.jamestraynor.co.uk:18443", "https://brain.jamestraynor.co.uk",
            "$OWNER_PREVIEW_ORIGIN/?mode=preview", "https://brain.jamestraynor.co.uk@elsewhere.example:18443")) {
            assertNotNull(ownerPreviewEndpointError(origin))
        }
    }

    private fun assertInvalid(text: String, validation: OwnerPreviewTokenValidation) {
        val input = OwnerPreviewTokenInput.parse(text)
        assertEquals(validation, input.validation)
        assertNull(input.canonical)
    }
}
