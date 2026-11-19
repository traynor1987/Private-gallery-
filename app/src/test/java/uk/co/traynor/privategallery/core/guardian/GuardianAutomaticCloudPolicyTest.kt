package uk.co.traynor.privategallery.core.guardian

import org.junit.Assert.*
import org.junit.Test

class GuardianAutomaticCloudPolicyTest {
    @Test fun redactsKnownNamesAndLeavesStructuredOrSensitiveContentLocal() {
        val names = listOf("Taylor Smith")
        assertEquals("[redacted] please give me some space", GuardianAutomaticCloudPolicy.excerpt(
            "Taylor please give me some space", names))
        for (text in listOf("My OTP is six four two", "Call me at 07123456789", "Open https://example.test",
            "This is a\nconversation transcript", "Email me at name@example.test", "Please visit " + "a".repeat(320))) {
            assertNull(GuardianAutomaticCloudPolicy.excerpt(text, names))
        }
    }

    @Test fun stableNetworkNeedsTwoChecksTenSecondsApartAndFiveMinuteWindow() {
        val policy = GuardianAutomaticCloudPolicy
        assertFalse(policy.ready(100, 10_099, true))
        assertFalse(policy.ready(100, 10_100, false))
        assertTrue(policy.ready(100, 10_100, true))
        assertTrue(policy.validWindow(500, 300_499))
        assertFalse(policy.validWindow(500, 300_500))
        assertFalse(policy.validWindow(500, 499))
        assertEquals(listOf(10_000L, 20_000L, 40_000L, 80_000L), (1..4).map(policy::backoff))
    }
}
