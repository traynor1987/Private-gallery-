package uk.co.traynor.privategallery.core.domain

import org.junit.Assert.*
import org.junit.Test

class SecondaryAuthPolicyTest {
    @Test fun `independent auto lock defaults to immediate and supports only approved options`() {
        val policy = SecondaryAuthPolicy { 0 }
        assertEquals(SecondaryAutoLock.IMMEDIATE, policy.autoLock)
        assertEquals(listOf(0L, 30_000L, 60_000L, 300_000L), SecondaryAutoLock.entries.map { it.timeoutMillis })
        policy.setAutoLock(SecondaryAutoLock.FIVE_MINUTES)
        assertEquals(SecondaryAutoLock.FIVE_MINUTES, policy.autoLock)
    }
    @Test fun `cold start requires PIN default expires at a day and biometric never refreshes it`() {
        var now = 0L
        val policy = SecondaryAuthPolicy { now }
        assertEquals(StrongAuthInterval.DAY, policy.strongAuthInterval)
        assertFalse(policy.canUseBiometric())
        policy.recordPinSuccess(); assertTrue(policy.canUseBiometric())
        now = 86_399_999; assertTrue(policy.canUseBiometric())
        policy.recordBiometricSuccess(); now = 86_400_000; assertFalse(policy.canUseBiometric())
        assertFalse(SecondaryAuthPolicy { now }.canUseBiometric())
    }
    @Test fun `every time three day seven day policies and security change reset`() {
        var now = 0L
        val policy = SecondaryAuthPolicy { now }
        for (interval in StrongAuthInterval.entries) {
            policy.setStrongAuthInterval(interval); policy.recordPinSuccess()
            assertEquals(interval != StrongAuthInterval.EVERY_TIME, policy.canUseBiometric())
            now += interval.durationMillis
            assertFalse(policy.canUseBiometric())
        }
        policy.setStrongAuthInterval(StrongAuthInterval.DAY); policy.recordPinSuccess()
        policy.onSecurityChanged(); assertFalse(policy.canUseBiometric())
        policy.recordPinSuccess(); now--
        assertFalse(policy.canUseBiometric())
    }
    @Test fun `repeated failures exponentially back off cap and eventually permit retry`() {
        var now = 0L
        val policy = SecondaryAuthPolicy { now }
        repeat(4) { policy.recordFailure(); assertEquals(0L, policy.retryAfterMillis()) }
        policy.recordFailure(); assertEquals(1000L, policy.retryAfterMillis())
        assertFalse(policy.canAttempt())
        now = 999; assertEquals(1L, policy.retryAfterMillis()); now = 1000; assertTrue(policy.canAttempt())
        policy.recordFailure(); assertEquals(2000L, policy.retryAfterMillis())
        repeat(1000) { policy.recordFailure() }
        assertEquals(60_000L, policy.retryAfterMillis())
        now += 60_000; assertTrue(policy.canAttempt())
        policy.recordPinSuccess(); policy.recordFailure(); assertEquals(0L, policy.retryAfterMillis())
    }
    @Test fun `biometric success resets failure count but cannot bypass strong auth or backoff`() {
        var now = 0L
        val policy = SecondaryAuthPolicy { now }
        assertThrows(IllegalStateException::class.java) { policy.recordBiometricSuccess() }
        policy.recordPinSuccess(); repeat(5) { policy.recordFailure() }
        assertFalse(policy.canUseBiometric())
        assertThrows(IllegalStateException::class.java) { policy.recordBiometricSuccess() }
        now = 1000; policy.recordBiometricSuccess()
        policy.recordFailure(); assertEquals(0L, policy.retryAfterMillis())
    }
}
