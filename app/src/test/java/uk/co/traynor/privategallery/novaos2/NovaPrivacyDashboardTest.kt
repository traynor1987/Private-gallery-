package uk.co.traynor.privategallery.novaos2

import org.junit.Assert.assertEquals
import org.junit.Test

class NovaPrivacyDashboardTest {
    @Test fun guardianAccessRevocationOverridesAnOldConnectedFlag() {
        assertEquals(NovaPrivacyGuardian.ACCESS_OFF, NovaPrivacyGuardian.from(false, true, true))
        assertEquals(NovaPrivacyGuardian.DISCONNECTED, NovaPrivacyGuardian.from(true, false, true))
        assertEquals(NovaPrivacyGuardian.CAPTURE_OFF, NovaPrivacyGuardian.from(true, true, false))
        assertEquals(NovaPrivacyGuardian.CAPTURE_ACTIVE, NovaPrivacyGuardian.from(true, true, true))
    }

    @Test fun approximateLocationIsNotReportedAsPrecise() {
        assertEquals(NovaPrivacyLocation.OFF, NovaPrivacyLocation.from(false, false))
        assertEquals(NovaPrivacyLocation.APPROXIMATE, NovaPrivacyLocation.from(false, true))
        assertEquals(NovaPrivacyLocation.PRECISE, NovaPrivacyLocation.from(true, true))
    }

    @Test fun missingSystemReportsRemainUnavailable() {
        assertEquals(NovaPrivacyAccess.UNAVAILABLE, NovaPrivacyAccess.from(null))
        assertEquals(NovaPrivacyVpn.UNAVAILABLE, NovaPrivacyVpn.from(null))
        assertEquals(NovaPrivacyVpn.OFF, NovaPrivacyVpn.from(false))
        assertEquals(NovaPrivacyVpn.ACTIVE, NovaPrivacyVpn.from(true))
    }
}
