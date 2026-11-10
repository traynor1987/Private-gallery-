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

    @Test fun dismissalRequiresAnEnabledClearablePersonalMessageWithVerifiedIdentity() {
        fun allowed(enabled: Boolean = true, account: ProbeAccount = ProbeAccount.MAIN,
            summary: Boolean = false, call: Boolean = false, identity: Boolean = true,
            clearable: Boolean = true, category: String? = "msg", packageName: String = GUARDIAN_WHATSAPP,
        ) = guardianMayDismissMessage(enabled, packageName, account, summary, call, identity, clearable, category)
        assertTrue(allowed())
        assertFalse(allowed(enabled = false))
        assertFalse(allowed(account = ProbeAccount.FRIENDS))
        assertFalse(allowed(account = ProbeAccount.UNRESOLVED))
        assertFalse(allowed(summary = true))
        assertFalse(allowed(call = true))
        assertFalse(allowed(identity = false))
        assertFalse(allowed(clearable = false))
        assertFalse(allowed(category = null))
        assertFalse(allowed(category = "alarm"))
        assertFalse(allowed(packageName = "android"))
    }

    @Test fun senderDisplayNamesRejectEmptyAndFormattingOnlyValues() {
        assertNull(guardianSenderDisplayName(null))
        assertNull(guardianSenderDisplayName("\u202E\n  "))
        assertEquals("Alice", guardianSenderDisplayName(" Alice\u202E "))
    }
    @Test fun conversationSelectionRequiresStableIdentityAndSmsRejectsLikelyCodes() {
        assertEquals("shortcut:123", guardianConversationId("123", "person", null, true))
        assertEquals("person-key:abc", guardianConversationId(null, "abc", null, false))
        assertNull(guardianConversationId(null, "abc", null, true))
        assertNull(guardianConversationId(null, null, null, false))
        assertTrue(guardianMayKeepSms("See you at the shop this evening"))
        assertFalse(guardianMayKeepSms("Your verification code is 351 249"))
        assertFalse(guardianMayKeepSms("Use 482731 to continue"))
    }
    @Test fun mainCaptureRecoversWithoutSilentlyExpandingConsent() {
        val main = GuardianConversationKey(GUARDIAN_WHATSAPP, 0, "shortcut:incoming")
        val rule = GuardianContact(main, "Sample sender")
        assertFalse(guardianCaptureAllowed(false, main, null))
        assertTrue(guardianCaptureAllowed(true, main, null))
        assertTrue(guardianCaptureAllowed(false, main, rule))
        assertFalse(guardianCaptureAllowed(true, main, rule.copy(enabled = false)))
        assertFalse(guardianCaptureAllowed(true, main, rule.copy(retain = false)))
        assertFalse(guardianCaptureAllowed(true, main.copy(app = "example.sms"), null))
        assertFalse(guardianCaptureAllowed(true, main, rule.copy(key = main.copy(profile = 10))))
    }
}
