package uk.co.traynor.privategallery.core.vpn

import java.io.File

/** Private profile metadata/configuration repository. Callers must never log returned configs. */
class VpnProfileRepository(root: File, private val key: ByteArray) {
    private val store = EncryptedVpnProfileStore(root)

    fun snapshot(): VpnProfileSnapshot = store.load(key)

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
