package uk.co.traynor.privategallery.core.update

/** Keeps Android package-install handoff defensive: a missing installer must not crash the app. */
object UpdateInstallPolicy {
    fun acceptsAndroid17(minimum: Int, target: Int): Boolean = minimum == 37 && target == 37
    fun confirmedInstalled(requestedCode: Long, actualCode: Long): Boolean = requestedCode > 0 && actualCode >= requestedCode
    fun canHandOffToAndroid(unknownSourcesAllowed: Boolean, installerAvailable: Boolean): Boolean =
        unknownSourcesAllowed && installerAvailable
}
