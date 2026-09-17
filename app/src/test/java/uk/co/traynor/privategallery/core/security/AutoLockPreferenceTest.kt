package uk.co.traynor.privategallery.core.security

import org.junit.Assert.assertEquals
import org.junit.Test

class AutoLockPreferenceTest {
  @Test fun `unknown persisted value uses immediate lock`() {
    assertEquals(AutoLockTimeout.IMMEDIATELY, AutoLockPreference.decode("not-a-timeout"))
  }

  @Test fun `round trips every auto lock choice`() {
    AutoLockTimeout.entries.forEach { timeout ->
      assertEquals(timeout, AutoLockPreference.decode(AutoLockPreference.encode(timeout)))
    }
  }
}
