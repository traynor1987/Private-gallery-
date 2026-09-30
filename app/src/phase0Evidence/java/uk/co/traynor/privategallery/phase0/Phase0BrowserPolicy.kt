package uk.co.traynor.privategallery.phase0

internal object Phase0BrowserPolicy {
  fun allowsResource(url: String) = url in setOf("https://phase0.invalid/", "https://phase0.invalid/sw.js")
  fun canColdDelete(profilesTouched: Boolean) = !profilesTouched
  fun admit(correctProcess: Boolean, suffixApplied: Boolean, multiProfile: Boolean): String = when {
    !correctProcess -> "CLOSED_WRONG_PROCESS"
    !suffixApplied -> "NOT_SUPPORTED_STARTUP_SUFFIX"
    !multiProfile -> "NOT_SUPPORTED_MULTI_PROFILE"
    else -> "READY"
  }
}
