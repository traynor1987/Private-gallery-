package uk.co.traynor.privategallery.core.update

/** Keeps Android package-install handoff defensive: a missing installer must not crash the app. */
object UpdateInstallPolicy {
    fun canHandOffToAndroid(unknownSourcesAllowed: Boolean, installerAvailable: Boolean): Boolean =
        unknownSourcesAllowed && installerAvailable
}
