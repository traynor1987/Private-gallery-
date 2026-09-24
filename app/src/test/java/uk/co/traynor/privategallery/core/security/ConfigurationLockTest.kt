package uk.co.traynor.privategallery.core.security
import org.junit.Assert.*
import org.junit.Test
class ConfigurationLockTest {
    @Test fun configurationStopPreservesUnlock() {
        val session = LockSession(AutoLockTimeout.IMMEDIATELY)
        session.unlock(); session.onActivityStopped(1, changingConfigurations = true, screenInteractive = true)
        session.onForegrounded(100)
        assertTrue(session.isUnlocked)
    }
    @Test fun homeStillLocksImmediately() {
        val session = LockSession(AutoLockTimeout.IMMEDIATELY)
        session.unlock(); session.onActivityStopped(1, false, true)
        assertFalse(session.isUnlocked)
    }
    @Test fun screenOffWinsOverConfigurationAndTimeout() {
        val session = LockSession(AutoLockTimeout.MINUTES_5)
        session.unlock(); session.onActivityStopped(1, true, false)
        assertFalse(session.isUnlocked)
    }
    @Test fun realBackgroundRetainsTimeoutSemantics() {
        val session = LockSession(AutoLockTimeout.SECONDS_30)
        session.unlock(); session.onActivityStopped(1, false, true); session.onForegrounded(30001)
        assertFalse(session.isUnlocked)
    }
}
