package uk.co.traynor.privategallery.core.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LockSessionTest {
  @Test fun `immediate policy locks when app backgrounds`() {
    val session = LockSession(AutoLockTimeout.IMMEDIATELY)
    session.unlock()
    session.onAppBackgrounded(0)
    assertFalse(session.isUnlocked)
  }

  @Test fun `thirty second policy waits before locking`() {
    val session = LockSession(AutoLockTimeout.SECONDS_30)
    session.unlock(); session.onAppBackgrounded(100)
    session.onForegrounded(30_099); assertTrue(session.isUnlocked)
    val expired = LockSession(AutoLockTimeout.SECONDS_30)
    expired.unlock(); expired.onAppBackgrounded(100)
    expired.onForegrounded(30_100); assertFalse(expired.isUnlocked)
  }
}
