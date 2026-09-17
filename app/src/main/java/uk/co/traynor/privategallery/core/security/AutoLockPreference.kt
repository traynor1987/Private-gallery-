package uk.co.traynor.privategallery.core.security

/** Stable, non-sensitive representation for persisting the chosen session timeout. */
object AutoLockPreference {
  fun encode(timeout: AutoLockTimeout): String = timeout.name

  fun decode(value: String?): AutoLockTimeout =
    AutoLockTimeout.entries.firstOrNull { it.name == value } ?: AutoLockTimeout.IMMEDIATELY
}
