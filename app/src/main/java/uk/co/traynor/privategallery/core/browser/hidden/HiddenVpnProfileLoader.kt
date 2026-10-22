package uk.co.traynor.privategallery.core.browser.hidden

import java.io.File
import uk.co.traynor.privategallery.core.vpn.VpnProfile
import uk.co.traynor.privategallery.core.vpn.VpnProfileRepository

/** Consumes a copied Primary key; unreadable/missing transport configuration never escapes as a crash. */
internal object HiddenVpnProfileLoader {
    fun load(root:File,key:ByteArray):VpnProfile? = try {
        VpnProfileRepository(root,key).snapshot().let {snapshot -> snapshot.profiles.singleOrNull {it.id==snapshot.activeProfileId}}
    } catch(_:Exception) { null }
    finally {key.fill(0)}
}
