package uk.co.traynor.privategallery.core.browser.v2

import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import org.junit.Assert.*
import org.junit.Test
import uk.co.traynor.privategallery.core.security.*
import uk.co.traynor.privategallery.core.vault.*

/** Real encrypted import with production Browser admission and synthetic isolated native source. */
class Phase3BrowserImportIntegrationTest {
    @Test fun browserTransportAcknowledgesOutsideStorageBeforeItemSelection() = isolated { context, authority ->
        val operation = browserImportOperation(authority, true)!!
        try {
            val repository = AndroidVaultRepository(context, operation); var disconnected = 0
            val native = object : FakeConnection() {
                override fun disconnect() {
                    assertFalse(Thread.holdsLock(PrimaryVaultSetupGuard.storageLock))
                    synchronized(PrimaryVaultSetupGuard.storageLock) { assertTrue(repository.items().isEmpty()) }
                    disconnected++
                }
            }
            val source = browserV2ImageSource("https://invalid.example/image.png", "synthetic", null, { native })
            val result = VaultImportCoordinator(repository).acquire(source)
            assertTrue(result is ImportResult.Imported); assertEquals(1, disconnected)
            assertEquals(1, repository.items().size)
        } finally { operation.close() }
        authority.revoke(); assertTrue(authority.cleanupComplete)
    }
    @Test fun failedBrowserTransportCannotSelectAnEncryptedImportedItem() = isolated { context, authority ->
        val operation = browserImportOperation(authority, true)!!
        try {
            val repository = AndroidVaultRepository(context, operation)
            val native = object : FakeConnection() { override fun disconnect(): Unit = throw IOException("synthetic failure") }
            val source = browserV2DownloadSource("https://invalid.example/image.png", "synthetic", "download", "image/png", { native })
            assertThrows(IOException::class.java) { VaultImportCoordinator(repository).acquire(source) }
            assertTrue(repository.items().isEmpty())
        } finally { operation.close() }
        authority.revoke(); assertFalse(authority.cleanupComplete)
        val key = ByteArray(32) { 5 }; assertThrows(IllegalStateException::class.java) { authority.open(key) }
        assertArrayEquals(ByteArray(32), key)
    }
    private open class FakeConnection : HttpURLConnection(URL("https://invalid.example/image.png")) {
        override fun disconnect() = Unit
        override fun getResponseCode() = 200
        override fun getInputStream(): java.io.InputStream = ByteArrayInputStream(byteArrayOf(1,2,3))
        override fun connect() = Unit
        override fun usingProxy() = false
    }
    private fun isolated(test: (Context, PrimarySessionAuthority) -> Unit) {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val root = File(base.cacheDir, "phase3-browser-import-${java.util.UUID.randomUUID()}").apply { mkdirs() }
        val context = object : ContextWrapper(base) { override fun getFilesDir() = root }
        val authority = PrimarySessionAuthority { 0 }
        for (name in listOf("ioReleasePool", "presentationReleasePool")) {
            PrimarySessionAuthority::class.java.getDeclaredField(name).apply { isAccessible = true }.set(authority, ReleasePool(16))
        }
        authority.open(ByteArray(32) { 23 })
        try { test(context, authority) } finally { authority.revoke(); root.deleteRecursively() }
    }
}
