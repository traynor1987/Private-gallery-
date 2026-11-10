package uk.co.traynor.privategallery.core.guardian

import org.junit.Assert.*
import org.junit.Test

class GuardianCapturePolicyTest {
    @Test fun callsAreOnlyAttributedToTheDefaultDialerOrAnObservedWhatsAppProfile() {
        assertEquals(GuardianCallSource.PHONE, guardianCallSource("phone", "phone", true, ProbeAccount.MAIN))
        assertNull(guardianCallSource("phone", "phone", false, ProbeAccount.UNRESOLVED))
        assertNull(guardianCallSource("other", "phone", true, ProbeAccount.MAIN))
        assertEquals(GuardianCallSource.MAIN, guardianCallSource("com.whatsapp", "phone", true, ProbeAccount.MAIN))
        assertEquals(GuardianCallSource.FRIENDS, guardianCallSource("com.whatsapp", "phone", false, ProbeAccount.FRIENDS))
        assertEquals(GuardianCallSource.UNRESOLVED, guardianCallSource("com.whatsapp", "phone", false, ProbeAccount.UNRESOLVED))
    }

    @Test fun captureRequiresExplicitOptInAndPositivePersonalIdentity() {
        assertTrue(canCaptureGuardianNotification(true, "com.whatsapp", ProbeAccount.MAIN, false))
        assertFalse(canCaptureGuardianNotification(false, "com.whatsapp", ProbeAccount.MAIN, false))
        assertFalse(canCaptureGuardianNotification(true, "com.whatsapp", ProbeAccount.FRIENDS, false))
        assertFalse(canCaptureGuardianNotification(true, "com.whatsapp", ProbeAccount.UNRESOLVED, false))
        assertFalse(canCaptureGuardianNotification(true, "com.whatsapp", ProbeAccount.MAIN, true))
        assertFalse(canCaptureGuardianNotification(true, "com.whatsapp", ProbeAccount.MAIN, false, call = true))
        assertFalse(canCaptureGuardianNotification(true, "com.android.phone", ProbeAccount.MAIN, false))
    }
}
