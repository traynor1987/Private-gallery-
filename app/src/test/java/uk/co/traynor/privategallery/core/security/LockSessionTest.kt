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
  @Test fun `late foreground gap expires only the authentication that owned the loss`() {
    val session=LockSession(AutoLockTimeout.SECONDS_30)
    session.unlock();val owner=session.captureAuthenticationEpoch()
    assertFalse(session.expireForegroundGap(owner,100,30_099,false));assertTrue(session.isUnlocked)
    assertTrue(session.expireForegroundGap(owner,100,30_100,false));assertFalse(session.isUnlocked)
    session.unlock()
    assertFalse(session.expireForegroundGap(owner,100,50_000,true));assertTrue(session.isUnlocked)
  }
  @Test fun `duplicate background loss cannot extend original deadline`() {
    val session=LockSession(AutoLockTimeout.SECONDS_30)
    session.unlock();session.onAppBackgrounded(100);session.onAppBackgrounded(20_000)
    session.onForegrounded(30_100);assertFalse(session.isUnlocked)
  }
}
