package uk.co.traynor.privategallery.core.ui

import org.junit.Assert.*
import org.junit.Test

class NovaBlackScreenPolicyTest {
    @Test fun `authorized foreground focused request activates`() {
        val policy = NovaBlackScreenPolicy()
        assertTrue(policy.request(authorized = true, foreground = true, focused = true))
        assertTrue(policy.active)
        assertTrue(policy.canInteract(authorized = true))
    }

    @Test fun `request waits for closing menu focus and rechecks current authority`() {
        val policy = NovaBlackScreenPolicy()
        assertTrue(policy.request(true, true, false))
        assertEquals(NovaBlackScreenPolicy.State.PENDING, policy.state)
        assertFalse(policy.active)
        assertFalse(policy.canInteract(true))
        policy.windowFocusChanged(true, true, true)
        assertTrue(policy.active)
    }

    @Test fun `stale menu focus cannot activate revoked or background owner`() {
        for ((authorized, foreground) in listOf(false to true, true to false)) {
            val policy = NovaBlackScreenPolicy()
            policy.request(true, true, false)
            policy.windowFocusChanged(true, authorized, foreground)
            assertEquals(NovaBlackScreenPolicy.State.IDLE, policy.state)
            policy.windowFocusChanged(true, true, true)
            assertFalse(policy.active)
        }
    }

    @Test fun `request fails closed without authority or foreground`() {
        for ((authorized, foreground) in listOf(false to true, true to false, false to false)) {
            val policy = NovaBlackScreenPolicy()
            assertFalse(policy.request(authorized, foreground, true))
            assertEquals(NovaBlackScreenPolicy.State.IDLE, policy.state)
        }
    }

    @Test fun `active focus loss dismisses and focus return does not reactivate`() {
        val policy = NovaBlackScreenPolicy()
        policy.request(true, true, true)
        policy.windowFocusChanged(false, true, true)
        assertEquals(NovaBlackScreenPolicy.State.IDLE, policy.state)
        policy.windowFocusChanged(true, true, true)
        assertFalse(policy.active)
    }

    @Test fun `pause cancels both visible and pending requests`() {
        for (focused in listOf(false, true)) {
            val policy = NovaBlackScreenPolicy()
            policy.request(true, true, focused)
            policy.pause()
            assertEquals(NovaBlackScreenPolicy.State.IDLE, policy.state)
            policy.windowFocusChanged(true, true, true)
            assertFalse(policy.active)
        }
    }

    @Test fun `input after revocation cancels authority and cannot wake protected content`() {
        val policy = NovaBlackScreenPolicy()
        policy.request(true, true, true)
        assertFalse(policy.canInteract(false))
        assertEquals(NovaBlackScreenPolicy.State.IDLE, policy.state)
        assertFalse(policy.canInteract(true))
    }

    @Test fun `dismiss permits fresh explicit activation but close is terminal`() {
        val policy = NovaBlackScreenPolicy()
        policy.request(true, true, true)
        policy.dismiss()
        assertFalse(policy.active)
        assertTrue(policy.request(true, true, true))
        policy.close()
        assertFalse(policy.request(true, true, true))
        policy.windowFocusChanged(true, true, true)
        policy.dismiss()
        policy.pause()
        assertEquals(NovaBlackScreenPolicy.State.CLOSED, policy.state)
        assertFalse(policy.canInteract(true))
        assertFalse(NovaBlackScreenPolicy().active)
    }
}
