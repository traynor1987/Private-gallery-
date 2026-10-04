package uk.co.traynor.privategallery.core.browser.v2

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Job
import org.junit.Assert.*
import org.junit.Test
import uk.co.traynor.privategallery.core.security.*
import java.util.concurrent.TimeUnit

class Phase3BrowserSessionJobOwnershipTest {
    @Test fun fullOriginalCapacityDoesNotConstructNativeProbeRoot() = isolated { _,op ->
        val holds = (1..16).map { op.createOwned { attach -> AutoCloseable {}.also(attach) } }; var created = 0
        try {
            assertThrows(IllegalStateException::class.java) { session(op) { created++; Job() } }
            assertEquals(0,created)
        } finally { holds.forEach { it.close() } }
    }
    @Test fun closedOriginalLeaseDoesNotConstructNativeProbeRoot() = isolated { _,op ->
        op.close(); var created = 0
        assertThrows(IllegalStateException::class.java) { session(op) { created++; Job() } }; assertEquals(0,created)
    }
    @Test fun neutralSessionWithoutPrimaryOwnerCreatesNoProbeRoot() {
        var created = 0; session(null) { created++; Job() }; assertEquals(0,created)
    }
    @Test fun actualSessionRootRetiresUnderExactPrimaryOriginal() = isolated { authority,op ->
        lateinit var root: Job; var created = 0
        session(op) { created++; Job().also { root = it } }; assertEquals(1,created); assertFalse(root.isCancelled)
        authority.revoke()
        val deadline = System.nanoTime()+TimeUnit.SECONDS.toNanos(5)
        while (!authority.cleanupComplete && System.nanoTime()<deadline) Thread.yield()
        assertTrue(root.isCancelled); assertTrue(authority.cleanupComplete)
    }
    @Test fun actualCallerAdmissionReturnsNeutralOnCapacityFailureAndClosesExactAttemptedLease() = isolated { _,op ->
        val holds = (1..16).map { op.createOwned { attach -> AutoCloseable {}.also(attach) } }; val key = op.key; var created = 0
        try {
            val admitted = browserSessionAdmission(op) { original -> session(original) { created++;Job() } }
            assertNull(admitted.owner); assertEquals(0,created); assertArrayEquals(ByteArray(32),key)
            assertThrows(IllegalStateException::class.java) { op.checkValid() }
        } finally { holds.forEach { it.close() } }
    }
    @Test fun actualCallerAdmissionPreservesExactSuccessfulOwner() = isolated { _,op ->
        var created = 0; val admitted = browserSessionAdmission(op) { original -> session(original) { created++;Job() } }
        assertSame(op,admitted.owner); assertEquals(1,created); op.checkValid()
    }
    @Test fun actualCallerNeutralAdmissionDoesNotRetryFactory() {
        var calls = 0
        val admitted = browserSessionAdmission(null) { original -> calls++;session(original) { error("neutral native root") } }
        assertNull(admitted.owner); assertEquals(1,calls)
    }
    @Test fun fatalCallerFactoryFailureClosesExactLeaseWithoutNeutralRetry() = isolated { _,op ->
        val key = op.key;assertTrue(key.any { it != 0.toByte() });var calls = 0
        assertThrows(AssertionError::class.java) { browserSessionAdmission(op) { _ -> calls++;throw AssertionError("synthetic fatal allocation") } }
        assertEquals(1,calls);assertArrayEquals(ByteArray(32),key)
    }
    private fun session(op: PrimaryOperation?,factory: () -> Job) = BrowserV2Session(
        ApplicationProvider.getApplicationContext<Context>(),BrowserVpnGate { false },Listener,primaryOwner=op,mediaProbeRootFactory=factory)
    private object Listener : BrowserV2Session.Listener {
        override fun onSessionChanged() = Unit
        override fun onMessage(message: BrowserMessage) = Unit
        override fun onDownload(url: String,userAgent: String,contentDisposition: String,mimeType: String) = Unit
        override fun onImageLongPress(resourceUrl: String?) = Unit
        override fun onExternalNavigation(value: String) = Unit
        override fun onHistoryVisit(title: String,url: String) = Unit
        override fun onFullscreen(view: android.view.View,callback: android.webkit.WebChromeClient.CustomViewCallback) = Unit
        override fun onExitFullscreen() = Unit
        override fun onPermissionRequest(request: android.webkit.PermissionRequest) = Unit
        override fun onGeolocationRequest(origin: String,callback: android.webkit.GeolocationPermissions.Callback) = Unit
        override fun onShowFileChooser(callback: android.webkit.ValueCallback<Array<android.net.Uri>>,params: android.webkit.WebChromeClient.FileChooserParams) = false
    }
    private fun isolated(test: (PrimarySessionAuthority,PrimaryOperation) -> Unit) {
        val authority = PrimarySessionAuthority { 0 }
        for (name in listOf("ioReleasePool","presentationReleasePool")) {
            PrimarySessionAuthority::class.java.getDeclaredField(name).apply { isAccessible=true }.set(authority,ReleasePool(16))
        }
        authority.open(ByteArray(32) { 0x5a });val op=authority.operationOrNull(setOf(PrimaryScope.READ,PrimaryScope.BROWSER_UPLOAD_EGRESS))!!
        try { test(authority,op) } finally { op.close();authority.revoke() }
    }
}
