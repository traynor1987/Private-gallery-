package uk.co.traynor.privategallery.phase0

import org.junit.Assert.*
import org.junit.Test

class Phase0BrowserPolicyTest {
  @Test fun missingStartupSupportClosesInsteadOfFallingBack() {
    assertEquals("NOT_SUPPORTED_STARTUP_SUFFIX", Phase0BrowserPolicy.admit(true, false, true))
  }
  @Test fun missingMultiProfileClosesInsteadOfFallingBack() {
    assertEquals("NOT_SUPPORTED_MULTI_PROFILE", Phase0BrowserPolicy.admit(true, true, false))
  }
  @Test fun wrongProcessClosesBeforeProviderAccess() {
    assertEquals("CLOSED_WRONG_PROCESS", Phase0BrowserPolicy.admit(false, true, true))
  }
  @Test fun allCapabilitiesAdmitOnlyEvidenceHost() {
    assertEquals("READY", Phase0BrowserPolicy.admit(true, true, true))
  }
  @Test fun externalSchemesHostsPortsAndUnknownPathsAreDenied() {
    for (url in listOf("https://example.com/", "http://phase0.invalid/", "file:///synthetic",
      "content://phase0/", "https://phase0.invalid.evil/", "https://user@phase0.invalid/",
      "https://phase0.invalid:443/", "https://phase0.invalid/unknown", "https://phase0.invalid/?redirect=1")) {
      assertFalse(url, Phase0BrowserPolicy.allowsResource(url))
    }
  }
  @Test fun onlyTheTwoFixedLocalFixtureResourcesAreAllowed() {
    assertTrue(Phase0BrowserPolicy.allowsResource("https://phase0.invalid/"))
    assertTrue(Phase0BrowserPolicy.allowsResource("https://phase0.invalid/sw.js"))
  }
  @Test fun loadedProfileCannotBeColdDeletedEvenAfterViewDestruction() {
    assertFalse(Phase0BrowserPolicy.canColdDelete(profilesTouched = true))
    assertTrue(Phase0BrowserPolicy.canColdDelete(profilesTouched = false))
  }
}
