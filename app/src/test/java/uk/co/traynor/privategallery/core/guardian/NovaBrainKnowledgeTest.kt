package uk.co.traynor.privategallery.core.guardian

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NovaBrainKnowledgeTest {
    private val benchmark = NovaBrainValidation(10, 20, 20, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0)

    @Test fun insufficientOrInvalidEvidenceIsUnmeasured() {
        assertNull(benchmark.copy(conversations = 9).knowledgePercent())
        assertNull(benchmark.copy(upsetting = 19).knowledgePercent())
        assertNull(benchmark.copy(ordinary = 19).knowledgePercent())
        assertNull(benchmark.copy(calibration = Double.NaN).knowledgePercent())
    }

    @Test fun missedUpsettingMessagesReduceTheScore() {
        assertEquals(100, benchmark.knowledgePercent())
        assertEquals(75, benchmark.copy(recall = 0.0).knowledgePercent())
    }
}
