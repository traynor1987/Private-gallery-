package uk.co.traynor.privategallery.core.guardian

import org.junit.Assert.*
import org.junit.Test

class GuardianAlertPrivacyTest {
    @Test fun legacySecondsDoNotControlShadeRetention() {
        assertNull(GuardianAlertRetention.read(null).timeoutMillis)
        assertNull(GuardianAlertRetention.read("5").timeoutMillis)
        assertEquals(3_600_000L, GuardianAlertRetention.read("HOUR").timeoutMillis)
        assertEquals(86_400_000L, GuardianAlertRetention.DAY.timeoutMillis)
    }

    @Test fun senderModesFailClosedAndLockScreenHasItsOwnRules() {
        assertNull(guardianAlertSender(GuardianAlertSender.HIDDEN, "Example", "Private"))
        assertEquals("Example", guardianAlertSender(GuardianAlertSender.NAME, "Example", "Private"))
        assertEquals("Private", guardianAlertSender(GuardianAlertSender.ALIAS, "Example", "Private"))
        assertNull(guardianAlertSender(GuardianAlertSender.ALIAS, "Example", null))
        assertFalse(GuardianAlertLockScreen.HIDDEN.showSender)
        assertTrue(GuardianAlertLockScreen.SENDER.showSender)
        assertFalse(GuardianAlertLockScreen.SENDER.showPreview)
        assertTrue(GuardianAlertLockScreen.FOLLOW.showPreview)
    }

    @Test fun previewRequiresOptInCurrentValidationAndEligibleMessage() {
        val validation = NovaBrainValidation(10, 20, 20, 0.99, 1.0, 0.99, 0.99, 0.99, 0.1,
            validatedAtMillis = 1)
        assertTrue(guardianAlertMayPreview(true, true, GuardianNoticeKind.LOW, validation))
        assertFalse(guardianAlertMayPreview(false, true, GuardianNoticeKind.LOW, validation))
        assertFalse(guardianAlertMayPreview(true, false, GuardianNoticeKind.LOW, validation))
        assertFalse(guardianAlertMayPreview(true, true, GuardianNoticeKind.UNCERTAIN, validation))
        assertFalse(guardianAlertMayPreview(true, true, GuardianNoticeKind.LOW, null))
        assertFalse(guardianAlertMayPreview(true, true, GuardianNoticeKind.LOW, validation.copy(falseNegatives = 1)))
    }
}
