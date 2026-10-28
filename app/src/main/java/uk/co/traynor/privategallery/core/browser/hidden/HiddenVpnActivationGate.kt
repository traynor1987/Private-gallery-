package uk.co.traynor.privategallery.core.browser.hidden

/** Flip only in an independently reviewed source change with exact-source real VPN/WebView,
 * DNS/packet and revocation evidence. Neither preference nor launch intent can enable it. */
internal object HiddenVpnActivationGate {
    const val INTERACTIVE_VERIFIED = false
}
