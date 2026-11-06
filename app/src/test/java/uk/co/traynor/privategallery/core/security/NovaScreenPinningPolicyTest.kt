package uk.co.traynor.privategallery.core.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import uk.co.traynor.privategallery.core.security.NovaScreenPinningPolicy.Decision
import uk.co.traynor.privategallery.core.security.NovaScreenPinningPolicy.Observation
import uk.co.traynor.privategallery.core.security.NovaScreenPinningPolicy.State

class NovaScreenPinningPolicyTest {
    private val ready = Observation(State.NONE, current = true, foreground = true,
        deviceLocked = false, managedLockTaskPermitted = false)

    @Test fun `explicit current foreground unlocked unmanaged action may request Android confirmation`() {
        assertEquals(Decision.REQUEST, NovaScreenPinningPolicy.decide(ready))
    }

    @Test fun `revoked authentication or changed Space cannot request even if Android is available`() {
        assertEquals(Decision.REVOKED, NovaScreenPinningPolicy.decide(ready.copy(current = false)))
    }

    @Test fun `pause or loss of foreground rejects an old click`() {
        assertEquals(Decision.NOT_FOREGROUND, NovaScreenPinningPolicy.decide(ready.copy(foreground = false)))
    }

    @Test fun `locked device and unreadable keyguard fail closed`() {
        assertEquals(Decision.DEVICE_LOCKED, NovaScreenPinningPolicy.decide(ready.copy(deviceLocked = true)))
        assertEquals(Decision.POLICY_UNKNOWN, NovaScreenPinningPolicy.decide(ready.copy(deviceLocked = null)))
    }

    @Test fun `allowlisting cannot enter managed kiosk through this action`() {
        assertEquals(Decision.MANAGED, NovaScreenPinningPolicy.decide(ready.copy(managedLockTaskPermitted = true)))
        assertEquals(Decision.POLICY_UNKNOWN, NovaScreenPinningPolicy.decide(ready.copy(managedLockTaskPermitted = null)))
    }

    @Test fun `active pinned locked and unknown states never request again`() {
        assertEquals(Decision.ALREADY_PINNED, NovaScreenPinningPolicy.decide(ready.copy(state = State.PINNED)))
        assertEquals(Decision.LOCKED, NovaScreenPinningPolicy.decide(ready.copy(state = State.LOCKED)))
        assertEquals(Decision.STATE_UNKNOWN, NovaScreenPinningPolicy.decide(ready.copy(state = State.UNKNOWN)))
    }

    @Test fun `revocation wins over an existing pinned state`() {
        assertEquals(Decision.REVOKED, NovaScreenPinningPolicy.decide(ready.copy(state = State.PINNED, current = false)))
    }

    @Test fun `a previous eligible observation cannot authorize a later paused or managed action`() {
        assertEquals(Decision.REQUEST, NovaScreenPinningPolicy.decide(ready))
        assertNotEquals(Decision.REQUEST, NovaScreenPinningPolicy.decide(ready.copy(foreground = false)))
        assertNotEquals(Decision.REQUEST, NovaScreenPinningPolicy.decide(ready.copy(managedLockTaskPermitted = true)))
    }
}
