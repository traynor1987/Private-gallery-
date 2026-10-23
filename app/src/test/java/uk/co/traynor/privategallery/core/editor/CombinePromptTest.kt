package uk.co.traynor.privategallery.core.editor

import org.junit.Assert.*
import org.junit.Test

class CombinePromptTest {
    @Test fun disabledPreservesWhitespaceAndUnicodeExactly() {
        val prompt = "  Place 👩🏽‍🚀 beside me.\nKeep this spacing.  "
        assertEquals(prompt, CombinePrompt.compose(prompt, false))
    }
    @Test fun enabledRetainsExplicitUnusualScaleAndConditionsGuidanceOnUserPriority() {
        val prompt = "Make me a giant and the second person tiny; surreal proportions."
        val result = CombinePrompt.compose(prompt, true)
        assertTrue(result.startsWith(prompt + "\n\n"))
        assertTrue(result.contains("unusual scaling or surreal proportions take priority"))
        assertTrue(result.endsWith(CombinePrompt.GUIDANCE))
    }
    @Test fun regenerationToggleDoesNotMutateCapturedRequest() {
        val prompt = "Stand beside each other on the beach"
        val firstRequest = CombinePrompt.compose(prompt, true)
        val regeneratedRequest = CombinePrompt.compose(prompt, false)
        assertTrue(firstRequest.endsWith(CombinePrompt.GUIDANCE))
        assertEquals(prompt, regeneratedRequest)
    }
}
