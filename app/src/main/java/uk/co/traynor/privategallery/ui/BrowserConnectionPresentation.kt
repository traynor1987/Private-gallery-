package uk.co.traynor.privategallery.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import uk.co.traynor.privategallery.core.vpn.VpnConnectionState

/** Presentation only. BrowserVpnGate remains the authority for every network operation. */
internal data class BrowserConnectionPresentation(
    val blocked: Boolean,
    val title: String = "",
    val detail: String = "",
    val busy: Boolean = false,
    val action: String? = null,
    val configure: Boolean = false,
) {
    companion object {
        fun from(required: Boolean, state: VpnConnectionState, permissionRequired: Boolean = false, preparing: Boolean = false): BrowserConnectionPresentation {
            if (!required || state == VpnConnectionState.CONNECTED) return BrowserConnectionPresentation(false)
            if (preparing) return BrowserConnectionPresentation(true, "Preparing secure browsing…", "Checking your selected VPN profile.", busy = true)
            if (permissionRequired) return BrowserConnectionPresentation(true, "VPN permission required", "Allow Android’s VPN connection request to browse securely.", action = "Allow VPN connection")
            return when (state) {
                VpnConnectionState.UNCONFIGURED -> BrowserConnectionPresentation(true, "Set up a VPN", "Select or import a VPN profile in Settings before browsing.", action = "Set up VPN", configure = true)
                VpnConnectionState.CONNECTING, VpnConnectionState.RECONNECTING -> BrowserConnectionPresentation(true, "Connecting securely…", "Verifying the WireGuard peer handshake. Browsing stays blocked until verification succeeds.", busy = true)
                VpnConnectionState.DISCONNECTING -> BrowserConnectionPresentation(true, "Disconnecting VPN…", "Browsing is paused until a secure connection is available.", busy = true)
                VpnConnectionState.FAILED -> BrowserConnectionPresentation(true, "Couldn't connect to VPN", "No verified VPN connection. Check the handshake details in VPN settings, your imported profile and Android’s active VPN, then retry.", action = "Try again")
                VpnConnectionState.DISCONNECTED -> BrowserConnectionPresentation(true, "VPN not connected", "Connect your VPN to continue browsing securely.", action = "Connect securely")
                VpnConnectionState.CONNECTED -> BrowserConnectionPresentation(false)
            }
        }
    }
}

@Composable
internal fun BrowserConnectionState(presentation: BrowserConnectionPresentation, onConnect: () -> Unit, onConfigure: () -> Unit) {
    NovaConnectionPanel(false,presentation.title,presentation.detail,presentation.busy,presentation.action,
        if(presentation.configure)onConfigure else onConnect,
        if(!presentation.configure && !presentation.busy)"VPN settings" else null,onConfigure,
        progressDescription=if(presentation.title=="Connecting securely…")"Connecting to VPN"else presentation.title,
        modifier=Modifier.semantics {testTag="browser-vpn-state";liveRegion=LiveRegionMode.Polite})
}
