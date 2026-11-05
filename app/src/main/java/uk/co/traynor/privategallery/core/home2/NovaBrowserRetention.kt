package uk.co.traynor.privategallery.core.home2

/** Destination changes inside Nova are not Android background events. */
internal object NovaBrowserRetention {
    fun retainPrimary(
        resumed: Boolean,
        started: Boolean,
        interactive: Boolean,
        concealed: Boolean,
        hidden: Boolean,
        authenticated: Boolean,
        profileClearing: Boolean,
    ): Boolean = resumed && started && interactive && !concealed && !hidden && authenticated && !profileClearing
}
