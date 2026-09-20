package uk.co.traynor.privategallery.core.vpn

import java.io.File

/** UI-safe metadata; deliberately excludes the encrypted profile configuration. */
data class VpnProfileSummary(
    val id: String,
    val displayName: String,
    val protocol: VpnProtocol,
    val active: Boolean,
)

/** Compact UI state that keeps profile selection separate from tunnel lifecycle. */
data class VpnProfilePresentation(
    val selectedProfileName: String,
    val connectionLabel: String,
) {
    companion object {
        fun from(profiles: List<VpnProfileSummary>, connection: VpnConnectionState): VpnProfilePresentation =
            VpnProfilePresentation(
                selectedProfileName = profiles.singleOrNull { it.active }?.displayName ?: "No WireGuard profile selected",
                connectionLabel = connection.name.lowercase().replaceFirstChar { it.titlecase() },
            )
    }
}

/** Private profile metadata/configuration repository. Callers must never log returned configs. */
class VpnProfileRepository(root: File, private val key: ByteArray) {
    private val store = EncryptedVpnProfileStore(root)

    fun snapshot(): VpnProfileSnapshot = store.load(key)

    fun summaries(): List<VpnProfileSummary> {
        val current = snapshot()
        return current.profiles.map { profile ->
            VpnProfileSummary(profile.id, profile.displayName, profile.protocol, profile.id == current.activeProfileId)
        }
    }

    fun import(displayName: String, configuration: String): VpnProfileImportResult {
        val result = VpnProfileParser.import(displayName, configuration)
        if (result is VpnProfileImportResult.Accepted) {
            val current = snapshot()
            store.save(current.copy(profiles = current.profiles + result.profile), key)
        }
        return result
    }

    fun select(profileId: String?) {
        val current = snapshot()
        require(profileId == null || current.profiles.any { it.id == profileId }) { "Unknown VPN profile" }
        store.save(current.copy(activeProfileId = profileId), key)
    }

    fun remove(profileId: String) {
        val current = snapshot()
        require(current.activeProfileId != profileId) { "Select another profile before removing the active profile" }
        val profiles = current.profiles.filterNot { it.id == profileId }
        store.save(VpnProfileSnapshot(profiles, current.activeProfileId), key)
    }

    /** Atomic active-profile switch used before removing a formerly active profile. */
    fun replaceActiveWith(profileId: String) = select(profileId)
}
