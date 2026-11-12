package uk.co.traynor.privategallery.core.guardian

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class NovaBrainEvidenceSourcesTest {
    @Test fun removingAnApprovalPreservesOtherSources() {
        val row = JSONObject("""{"label":"LOW_RISK","evidenceSources":["first","second"]}""")
        assertFalse(removeNovaBrainEvidenceSource(row, "unrelated").found)
        val revised = checkNotNull(removeNovaBrainEvidenceSource(row, "first").retained)
        assertEquals("second", revised.getJSONArray("evidenceSources").getString(0))
        assertEquals(2, row.getJSONArray("evidenceSources").length())
        assertNull(removeNovaBrainEvidenceSource(revised, "second").retained)
    }

    @Test fun removingLastEvidenceApprovalPreservesAnIndependentApproval() {
        val row = JSONObject("""{"label":"UNCERTAIN","evidenceSources":["first"],"unlinkedApproval":true}""")
        val revised = checkNotNull(removeNovaBrainEvidenceSource(row, "first").retained)
        assertTrue(revised.getBoolean("unlinkedApproval"))
        assertEquals(0, revised.getJSONArray("evidenceSources").length())
    }
}
