package uk.co.traynor.privategallery.core.browser.hidden

import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test
import uk.co.traynor.privategallery.core.vpn.*

class HiddenVpnProfileLoaderTest {
    @Test fun unreadableProfileReturnsFailureAndWipesCopiedKey() {
        val root=File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,"vpn-load-${UUID.randomUUID()}")
        root.mkdirs()
        File(root,"vpn-profiles.enc").writeBytes(byteArrayOf(1,2,3)) // Truncated encrypted nonce.

        val key=ByteArray(32){7}
        try {assertTrue(runCatching {VpnProfileRepository(root,key).snapshot()}.isFailure);assertNull(HiddenVpnProfileLoader.load(root,key));assertArrayEquals(ByteArray(32),key)} finally {root.deleteRecursively()}
    }
    @Test fun successfulProfileLoadStillWipesCopiedKey() {
        val root=File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,"vpn-load-${UUID.randomUUID()}")
        val key=ByteArray(32){7}
        try {
            val profile=VpnProfile("fixture","Test",VpnProtocol.WIREGUARD,"synthetic; never connected")
            EncryptedVpnProfileStore(root).save(VpnProfileSnapshot(listOf(profile),profile.id),key)
            assertEquals(profile,HiddenVpnProfileLoader.load(root,key));assertArrayEquals(ByteArray(32),key)
        } finally {key.fill(0);root.deleteRecursively()}
    }
}
