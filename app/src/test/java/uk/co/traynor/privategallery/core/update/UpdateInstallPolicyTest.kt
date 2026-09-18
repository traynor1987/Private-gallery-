package uk.co.traynor.privategallery.core.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateInstallPolicyTest {
    @Test
    fun `installer is launched only when Android permits installs and can resolve the handoff`() {
        assertFalse(UpdateInstallPolicy.canHandOffToAndroid(unknownSourcesAllowed = false, installerAvailable = true))
        assertFalse(UpdateInstallPolicy.canHandOffToAndroid(unknownSourcesAllowed = true, installerAvailable = false))
        assertTrue(UpdateInstallPolicy.canHandOffToAndroid(unknownSourcesAllowed = true, installerAvailable = true))
    }
}
