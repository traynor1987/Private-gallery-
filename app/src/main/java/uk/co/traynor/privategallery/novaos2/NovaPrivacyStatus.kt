package uk.co.traynor.privategallery.novaos2

internal enum class NovaPrivacyAccess(val label: String) {
    ALLOWED("Allowed"), OFF("Off"), UNAVAILABLE("Unavailable");

    companion object {
        fun from(allowed: Boolean?) = when (allowed) {
            true -> ALLOWED
            false -> OFF
            null -> UNAVAILABLE
        }
    }
}

internal enum class NovaPrivacyLocation(val label: String) {
    PRECISE("Precise permission granted"), APPROXIMATE("Approximate permission granted"), OFF("Permission off");

    companion object {
        fun from(fine: Boolean, coarse: Boolean) = when {
            fine -> PRECISE
            coarse -> APPROXIMATE
            else -> OFF
        }
    }
}

internal enum class NovaPrivacyGuardian(val label: String) {
    ACCESS_OFF("Notification access is off"), DISCONNECTED("Notification listener disconnected"),
    CAPTURE_OFF("Connected · capture off"), CAPTURE_ACTIVE("Connected · encrypted capture enabled");

    companion object {
        fun from(access: Boolean, connected: Boolean, capture: Boolean) = when {
            !access -> ACCESS_OFF
            !connected -> DISCONNECTED
            !capture -> CAPTURE_OFF
            else -> CAPTURE_ACTIVE
        }
    }
}

internal enum class NovaPrivacyVpn(val label: String) {
    ACTIVE("Active network reports VPN transport"),
    OFF("No VPN transport reported for NovaOS"),
    UNAVAILABLE("No active network status available");

    companion object {
        fun from(vpnTransport: Boolean?) = when (vpnTransport) {
            true -> ACTIVE
            false -> OFF
            null -> UNAVAILABLE
        }
    }
}

internal data class NovaPrivacySnapshot(
    val camera: NovaPrivacyAccess,
    val microphone: NovaPrivacyAccess,
    val contacts: NovaPrivacyAccess,
    val location: NovaPrivacyLocation,
    val notifications: NovaPrivacyAccess,
    val guardian: NovaPrivacyGuardian,
    val accessibility: NovaPrivacyAccess,
    val usageAccess: NovaPrivacyAccess,
    val overlay: NovaPrivacyAccess,
    val vpn: NovaPrivacyVpn,
    val sanctuary: String,
    val manualCloudConsent: NovaPrivacyAccess,
    val backupSchedule: String,
    val storage: NovaPrivacyStorage?,
)

internal data class NovaPrivacyStorage(val appDataBytes: Long, val cacheBytes: Long)

/** Export only enum-backed status. Workspace descriptions and storage details stay on device. */
internal fun NovaPrivacySnapshot.diagnostics(): String = listOf(
    "NovaOS privacy diagnostics",
    "Camera permission: ${camera.label}",
    "Microphone permission: ${microphone.label}",
    "Contacts permission: ${contacts.label}",
    "Location permission: ${location.label}",
    "Post notifications: ${notifications.label}",
    "Guardian: ${guardian.label}",
    "Sanctuary accessibility: ${accessibility.label}",
    "Usage access: ${usageAccess.label}",
    "Overlay access: ${overlay.label}",
    "Network: ${vpn.label}",
    "Manual Cloud Teacher consent: ${manualCloudConsent.label}",
).joinToString("\n")
