package uk.co.traynor.privategallery.core.guardian

import org.junit.Assert.*
import org.junit.Test

class GuardianCapturePolicyTest {
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
