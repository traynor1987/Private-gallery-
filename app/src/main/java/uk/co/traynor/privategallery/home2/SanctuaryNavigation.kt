package uk.co.traynor.privategallery.home2

import uk.co.traynor.privategallery.core.sanctuary.SanctuarySession

internal fun sanctuaryAllowsNovaRoute(
    session: SanctuarySession,
    destination: String,
    destinationUrl: String? = null,
): Boolean = session.allowsNovaApp(destination, destinationUrl)
