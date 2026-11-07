package uk.co.traynor.privategallery.core.sanctuary

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SanctuaryForegroundPolicyTest {
    private val whatsapp = "com.whatsapp"
    private val maps = "com.google.android.apps.maps"
    private val settings = "com.android.settings"
    private val policy = SanctuaryForegroundPolicy()

    @Test
    fun workAllowlistAllowsExactIdentityAndBlocksWhatsApp() {
        val approved = setOf(maps)
        val essential = setOf(settings)
        assertFalse(policy.shouldBlock(maps, approved, essential))
        assertTrue(policy.shouldBlock(whatsapp, approved, essential))
        assertTrue(policy.shouldBlock("$maps.imposter", approved, essential))
    }

    @Test
    fun androidRecoveryPackagesAndUnknownForegroundAreNeverBlocked() {
        assertFalse(policy.shouldBlock(settings, emptySet(), setOf(settings)))
        assertFalse(policy.shouldBlock(null, emptySet(), setOf(settings)))
    }

    @Test
    fun overlayWaitsUntilOldActivityPausesAndNeverCoversTwoForegroundPackages() {
        policy.resumed(whatsapp, "Main")
        assertEquals(whatsapp, policy.foreground())
        policy.resumed(settings, "Settings")
        assertNull(policy.foreground())
        policy.paused(whatsapp, "Main")
        assertEquals(settings, policy.foreground())
        policy.paused(settings, "Settings")
        assertNull(policy.foreground())
    }

    @Test
    fun clockOrDeviceStateResetDiscardsStaleForeground() {
        policy.resumed(whatsapp, "Main")
        policy.reset()
        assertNull(policy.foreground())
    }
}
