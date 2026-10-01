package uk.co.traynor.privategallery

/** Activity-local one-time recovery display is never persisted in the retained session. */
internal fun restoredPrimaryRoute(
    sessionUnlocked: Boolean,
    epochAvailable: Boolean,
    previous: Route,
    configured: Boolean,
    recoveryDisplayAvailable: Boolean,
): Route = when {
    sessionUnlocked && epochAvailable && previous == Route.RECOVERY_KEY_SETUP && !recoveryDisplayAvailable -> Route.LOCK
    sessionUnlocked && epochAvailable -> previous
    configured -> Route.LOCK
    else -> Route.SETUP
}
