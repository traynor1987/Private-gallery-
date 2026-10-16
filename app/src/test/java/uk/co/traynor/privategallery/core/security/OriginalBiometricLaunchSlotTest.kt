package uk.co.traynor.privategallery.core.security

import org.junit.Assert.*
import org.junit.Test

class OriginalBiometricLaunchSlotTest {
    @Test fun staleAndDuplicateLocatorsCannotClaimAnotherOriginal() {
        val slot = OriginalBiometricLaunchSlot<Any>()
        val first = Any()
        val replacement = Any()
        assertTrue(slot.reserve(first, "first"))
        assertNull(slot.claim(null))
        assertNull(slot.claim("untrusted"))
        assertFalse(slot.reserve(replacement, "second"))
        assertSame(first, slot.claim("first"))
        assertNull(slot.claim("first")) // Recreation cannot attach a second Activity/model.
        assertFalse(slot.retire(replacement))
        assertFalse(slot.reserve(replacement, "second"))
        assertTrue(slot.retire(first))
        assertTrue(slot.reserve(replacement, "second"))
        assertFalse(slot.retire(first)) // Saved old destruction cannot clear the replacement.
        assertNull(slot.claim("first"))
        assertSame(replacement, slot.claim("second"))
    }

    @Test fun pendingOriginalStaysStronglyClaimableUntilActualRetirement() {
        val slot = OriginalBiometricLaunchSlot<Any>()
        val original = Any()
        assertTrue(slot.reserve(original, "pending"))
        // Cancelling a pending platform launch must not retire this slot: its late
        // onCreate still needs the exact handle in order to finish and acknowledge.
        assertFalse(slot.reserve(Any(), "replacement"))
        assertSame(original, slot.claim("pending"))
        assertTrue(slot.retire(original))
        assertNull(slot.claim("pending"))
    }
}
