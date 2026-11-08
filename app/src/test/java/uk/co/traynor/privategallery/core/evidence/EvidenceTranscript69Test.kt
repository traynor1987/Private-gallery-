package uk.co.traynor.privategallery.core.evidence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EvidenceTranscript69Test {
    @Test fun suggestsOnlyLocalSpacingWithoutChangingWordsOrLineProvenance() {
        val raw = "7 November 2026\nCan  you call?Please,thanks!See you\n21:46\nMissed voice call\n21:55"
        val suggestion = requireNotNull(EvidenceTranscript69.suggest("record", "extraction", raw))
        assertEquals("7 November 2026\nCan you call? Please, thanks! See you\n21:46\nMissed voice call\n21:55", suggestion.text)
        assertNotEquals(suggestion.id, EvidenceTranscript69.suggest("record", "new extraction", raw)?.id)
    }

    @Test fun keepsTechnicalTokensAndPossibleGeometryIntact() {
        val raw = "       21:46\n£1,500.50 v2.0\nhttps://example.test/a,b?raw=text!here\nSome-\nthing"
        assertNull(EvidenceTranscript69.suggest("record", "extraction", raw))
    }

    @Test fun noSuggestionForUnchangedTextOrTextAlreadyAtQuota() {
        assertNull(EvidenceTranscript69.suggest("record", "extraction", "A readable transcript."))
        assertNull(EvidenceTranscript69.suggest("record", "extraction", "a".repeat(99_998) + "a!B"))
    }
}
