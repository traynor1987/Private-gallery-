package uk.co.traynor.privategallery.core.vault

import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import org.junit.Assert.*
import org.junit.Test
import uk.co.traynor.privategallery.core.security.*

/** Real legacy encrypted import, isolated synthetic root/pools; never owner data. */
class Phase3ImportDisposalTest {
    @Test fun firstImportSelectsAuthenticatedMetadataBeforeProviderClose() = isolated { context, root, authority ->
        val repository = AndroidVaultRepository(context, authority.operationOrNull(PrimaryScope.entries.toSet())!!)
        val selected = root.resolve("vault/vault-index.enc")
        assertFalse(selected.exists())
        val result = repository.importVerified(VaultImportSource("synthetic.png", "image/png", {
            object : ByteArrayInputStream(byteArrayOf(1,2,3)) {
                override fun close() { synchronized(PrimaryVaultSetupGuard.storageLock) {
                    assertTrue(selected.exists()); assertTrue(repository.items().isEmpty())
                } }
            }
        }))
        assertTrue(result is ImportResult.Imported); assertEquals(1, repository.items().size)
    }

    @Test fun selectedIndexDisappearanceDuringCloseCannotRecreateStaleMetadata() = isolated { context, root, authority ->
        val repository = AndroidVaultRepository(context, authority.operationOrNull(PrimaryScope.entries.toSet())!!)
        repository.createCollection("retained")
        val selected = root.resolve("vault/vault-index.enc")
        assertThrows(IllegalStateException::class.java) {
            repository.importVerified(VaultImportSource("synthetic.png", "image/png", {
                object : ByteArrayInputStream(byteArrayOf(1,2,3)) {
                    override fun close() { synchronized(PrimaryVaultSetupGuard.storageLock) { assertTrue(selected.delete()) } }
                }
            }))
        }
        assertFalse(selected.exists())
        assertEquals(1, root.resolve("vault/payloads").listFiles().orEmpty().count { it.name.endsWith(".vault") })
        assertThrows(IllegalStateException::class.java) { repository.items() }
    }

    @Test fun firstImportCloseFailureRetainsAuthenticatedEmptyIndexWithoutSelectingItem() = isolated { context, root, authority ->
        val repository = AndroidVaultRepository(context, authority.operationOrNull(PrimaryScope.entries.toSet())!!)
        val selected = root.resolve("vault/vault-index.enc")
        val beforeClose = AtomicReference<ByteArray>()
        assertThrows(IOException::class.java) {
            repository.importVerified(VaultImportSource("synthetic.png", "image/png", {
                object : ByteArrayInputStream(byteArrayOf(1,2,3)) {
                    override fun close(): Unit {
                        beforeClose.set(selected.readBytes())
                        throw IOException("synthetic first import close failure")
                    }
                }
            }))
        }
        assertArrayEquals(beforeClose.get(), selected.readBytes())
        assertTrue(repository.items().isEmpty())
    }

    @Test fun metadataMutationDuringProviderCloseIsRetainedByFreshSelection() = isolated { context, _, authority ->
        val repository = AndroidVaultRepository(context, authority.operationOrNull(PrimaryScope.entries.toSet())!!)
        repository.createCollection("before")
        val result = repository.importVerified(VaultImportSource("synthetic.png", "image/png", {
            object : ByteArrayInputStream(byteArrayOf(1,2,3)) {
                override fun close() { synchronized(PrimaryVaultSetupGuard.storageLock) { repository.createCollection("during close") } }
            }
        }))
        assertTrue(result is ImportResult.Imported)
        assertEquals(setOf("before", "during close"), repository.collections().map { it.name }.toSet())
    }

    @Test fun nativeProviderCloseCanAcquireStorageBeforeMetadataSelection() = isolated { context, root, authority ->
        val op = authority.operationOrNull(PrimaryScope.entries.toSet())!!
        val repository = AndroidVaultRepository(context, op)
        repository.createCollection("retained")
        val finished = CountDownLatch(1); val closed = CountDownLatch(1)
        val openedUnderStorage = AtomicBoolean(); val failure = AtomicReference<Throwable>()
        val worker = thread(isDaemon = true) {
            try {
                val result = repository.importVerified(VaultImportSource("synthetic.png", "image/png", {
                    openedUnderStorage.set(Thread.holdsLock(PrimaryVaultSetupGuard.storageLock))
                    object : ByteArrayInputStream(byteArrayOf(1,2,3)) {
                        override fun close() { synchronized(PrimaryVaultSetupGuard.storageLock) { closed.countDown() } }
                    }
                }))
                assertTrue(result is ImportResult.Imported)
                assertEquals(0L, closed.count)
            } catch (problem: Throwable) { failure.set(problem) } finally { finished.countDown() }
        }
        try {
            assertTrue("no storage-await/native-close cycle", finished.await(10, TimeUnit.SECONDS))
            failure.get()?.let { throw AssertionError("synthetic import failed", it) }
            assertFalse(openedUnderStorage.get())
            assertEquals(1, repository.items().size)
            assertEquals(1, root.resolve("vault/payloads").listFiles().orEmpty().count { it.name.endsWith(".vault") })
        } finally { worker.interrupt(); assertTrue(finished.await(5, TimeUnit.SECONDS)) }
    }

    @Test fun finalNativeCloseFailurePreservesExistingSelectedIndex() = isolated { context, root, authority ->
        val op = authority.operationOrNull(PrimaryScope.entries.toSet())!!
        val repository = AndroidVaultRepository(context, op)
        repository.createCollection("retained")
        val selected = root.resolve("vault/vault-index.enc"); val before = selected.readBytes()
        assertThrows(IOException::class.java) {
            repository.importVerified(VaultImportSource("synthetic.png", "image/png", {
                object : ByteArrayInputStream(byteArrayOf(1,2,3)) { override fun close(): Unit = throw IOException("synthetic final close failure") }
            }))
        }
        assertArrayEquals(before, selected.readBytes())
        assertTrue(repository.items().isEmpty())
        authority.revoke(); assertFalse(authority.cleanupComplete)
        val key = ByteArray(32) { 9 }
        assertThrows(IllegalStateException::class.java) { authority.open(key) }
        assertArrayEquals(ByteArray(32), key)
    }

    @Test fun corruptSelectedMetadataDeniesBeforeOpeningProvider() = isolated { context, root, authority ->
        val op = authority.operationOrNull(PrimaryScope.entries.toSet())!!
        val repository = AndroidVaultRepository(context, op)
        repository.createCollection("retained")
        val selected = root.resolve("vault/vault-index.enc")
        val corrupted = selected.readBytes().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }
        selected.writeBytes(corrupted); var opened = false
        assertThrows(Exception::class.java) {
            repository.importVerified(VaultImportSource("synthetic.png", "image/png", { opened = true; byteArrayOf(1,2,3).inputStream() }))
        }
        assertFalse(opened); assertArrayEquals(corrupted, selected.readBytes())
    }

    private fun isolated(test: (Context, File, PrimarySessionAuthority) -> Unit) {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val root = File(base.cacheDir, "phase3-disposal-${java.util.UUID.randomUUID()}").apply { mkdirs() }
        val context = object : ContextWrapper(base) { override fun getFilesDir() = root }
        val authority = PrimarySessionAuthority { 0 }
        // Synthetic failed cleanup retains its own obligation; do not poison/reset process pools.
        for (field in listOf("ioReleasePool", "presentationReleasePool")) {
            PrimarySessionAuthority::class.java.getDeclaredField(field).apply { isAccessible = true }.set(authority, ReleasePool(16))
        }
        authority.open(ByteArray(32) { 23 })
        try { test(context, root, authority) } finally { authority.revoke(); root.deleteRecursively() }
    }
}
