package uk.co.traynor.privategallery.core.guardian

import org.junit.Assert.*
import org.junit.Test

class NovaBrainEvidenceReferenceTest {
    @Test fun parsesOnlyCanonicalGuardianTimelineReferences() {
        val id = "ab".repeat(32)
        assertEquals(id, guardianMessageFromEvidenceEvent(guardianEvidenceEventId(id)))
        assertNull(guardianMessageFromEvidenceEvent(id))
        assertNull(guardianMessageFromEvidenceEvent("guardian-example"))
        assertNull(guardianMessageFromEvidenceEvent("guardian-$id-extra"))
    }
}
