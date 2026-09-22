package uk.co.traynor.privategallery.core.browser.v2

/** Narrow adapter over the existing engine-confirmed BrowserVpnController. */
fun interface BrowserVpnGate {
    /** True only when this Browser is currently permitted to make remote HTTP(S) requests. */
    fun permitsRemoteNetworking(): Boolean
}
