package uk.co.traynor.privategallery.launcher

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LauncherDefaultHomePromptSessionTest {
    @Test fun unavailableOrAlreadyDefaultDoesNotConsumeTheOffer() {
        val session = LauncherDefaultHomePromptSession()
        assertFalse(session.claim(isDefault = null, ask = true, safeHome = true))
        assertFalse(session.claim(isDefault = true, ask = true, safeHome = true))
        assertTrue(session.claim(isDefault = false, ask = true, safeHome = true))
    }

    @Test fun optOutAndUnsafeSurfacesDoNotConsumeTheOffer() {
        val session = LauncherDefaultHomePromptSession()
        assertFalse(session.claim(isDefault = false, ask = false, safeHome = true))
        assertFalse(session.claim(isDefault = false, ask = true, safeHome = false))
        assertTrue(session.claim(isDefault = false, ask = true, safeHome = true))
    }

    @Test fun anOfferIsShownOncePerOpeningIncludingAfterRecreation() {
        val session = LauncherDefaultHomePromptSession()
        assertTrue(session.claim(isDefault = false, ask = true, safeHome = true))
        assertFalse(session.claim(isDefault = false, ask = true, safeHome = true))
        val recreated = LauncherDefaultHomePromptSession(session.offered)
        assertFalse(recreated.claim(isDefault = false, ask = true, safeHome = true))
        val anotherOpening = LauncherDefaultHomePromptSession()
        assertTrue(anotherOpening.claim(isDefault = false, ask = true, safeHome = true))
    }
}
