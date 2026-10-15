package uk.co.traynor.privategallery.core.vault

import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.CopyOnWriteArrayList
import uk.co.traynor.privategallery.core.security.PrimarySessionAuthority
import uk.co.traynor.privategallery.core.security.ReleasePool
import uk.co.traynor.privategallery.core.security.ReleaseInvocation
import org.junit.Assert.*
import org.junit.Test
import uk.co.traynor.privategallery.core.security.PrimaryScope
import uk.co.traynor.privategallery.core.security.ScopedIoGuard
import uk.co.traynor.privategallery.core.security.testPrimaryAuthority

class ChunkedVaultVideoStoreTest {
    @Test fun legacyVideoMigratesWithoutChangingIndexNonceOrPlaintextDigest() {
        val root = Files.createTempDirectory("private-gallery-legacy-video").toFile()
        val key = ByteArray(32) { (it + 3).toByte() }
        val plain = ByteArray(1024 * 1024 + 19) { (it % 197).toByte() }
        try {
            val store = EncryptedPayloadStore(root, syncOutput = {})
            val legacy = store.writeAndVerify("06b9676c-c671-4a50-a6d2-6919e33df624", ByteArrayInputStream(plain), key)
            assertFalse(ChunkedVaultVideoStore.isChunked(legacy.file))
            val migrated = store.migrateLegacyVideo(legacy, key) { false }
            assertArrayEquals(legacy.nonce, migrated.nonce)
            assertTrue(ChunkedVaultVideoStore.isChunked(migrated.file))
            assertTrue(store.verify(legacy, key))
            assertArrayEquals(plain, store.decryptToBoundedBytes(migrated, key, plain.size + 1) { false })
            assertFalse(root.walkTopDown().any { it.isFile && it.name.endsWith(".legacy") })
        } finally { root.deleteRecursively(); plain.fill(0); key.fill(0) }
    }

    @Test fun boundedReaderAuthenticatesChunksAcrossSeekAndEndOfFile() {
        val root = Files.createTempDirectory("private-gallery-video-chunks").toFile()
        val key = ByteArray(32) { (it + 1).toByte() }
        val plain = ByteArray(2 * 1024 * 1024 + 73) { (it % 251).toByte() }
        try {
            val stored = ChunkedVaultVideoStore.writeAndVerify("b34677f4-81cd-409c-b825-608d8f4309ab", ByteArrayInputStream(plain), key, root)
            assertTrue(ChunkedVaultVideoStore.isChunked(stored.file))
            ChunkedVaultVideoStore.open(stored, key).use { reader ->
                assertTrue(reader.verifyAll())
                val target = ByteArray(170)
                assertEquals(170, reader.readAt(1024L * 1024 - 60, target, 0, target.size))
                assertArrayEquals(plain.copyOfRange(1024 * 1024 - 60, 1024 * 1024 + 110), target)
                assertEquals(73, reader.readAt(2L * 1024 * 1024, target, 0, target.size))
                assertEquals(-1, reader.readAt(plain.size.toLong(), target, 0, target.size))
            }
            java.io.RandomAccessFile(stored.file, "rw").use { file ->
                val offset = ChunkedVaultVideoStore.HEADER_BYTES.toLong() + 30
                file.seek(offset)
                val original = file.readUnsignedByte()
                val neighbor = file.readUnsignedByte()
                file.seek(offset)
                file.writeByte(original xor 1)
                file.seek(offset)
                val changed = file.readUnsignedByte()
                assertEquals(original xor 1, changed)
                assertNotEquals("the authentication negative must actually alter ciphertext", original, changed)
                assertEquals("the neighboring ciphertext byte remains unchanged", neighbor, file.readUnsignedByte())
            }
            assertFalse(ChunkedVaultVideoStore.open(stored, key).use { it.verifyAll() })
        } finally { root.deleteRecursively(); plain.fill(0); key.fill(0) }
    }

    @Test fun guardedReaderPreclaimsReaderKeyAndDescriptorBeforeOpening() {
        val root = Files.createTempDirectory("private-gallery-owned-video-reader").toFile()
        val key = ByteArray(32) { (it + 1).toByte() }
        val plain = ByteArray(257) { (it % 127).toByte() }
        val authority = testPrimaryAuthority()
        try {
            val stored = ChunkedVaultVideoStore.writeAndVerify("34452994-8f02-4d87-b3f0-0fbd64c5b345", ByteArrayInputStream(plain), key, root)
            authority.open(key.copyOf())
            val operation = checkNotNull(authority.operationOrNull(setOf(PrimaryScope.READ)))
            val owned = ChunkedVaultVideoStore.openOwned(ScopedIoGuard(operation, PrimaryScope.READ), stored, key)
            try { assertTrue(owned.value.verifyAll()) } finally { owned.close() }
            assertTrue(owned.retirement.await(5, TimeUnit.SECONDS))
            authority.revoke()
            assertTrue(authority.cleanupComplete)
        } finally { root.deleteRecursively(); plain.fill(0); key.fill(0) }
    }
    @Test fun ownedReaderKeyWipeWaitsForReaderMutexWhileDescriptorClosesIndependently() {
        val root = Files.createTempDirectory("private-gallery-reader-quiescence").toFile()
        val key = ByteArray(32) { (it + 1).toByte() }
        val plain = ByteArray(257) { (it % 127).toByte() }
        val entered = CountDownLatch(3)
        val workers = CopyOnWriteArrayList<Thread>()
        val authority = PrimarySessionAuthority().also { value ->
            value.javaClass.getDeclaredField("ioReleasePool").apply { isAccessible = true }.set(value,
                ReleasePool(16, allocateInvocation = { action, returned ->
                    ReleaseInvocation({ workers.add(Thread.currentThread()); entered.countDown(); action() }, returned)
                }))
            value.javaClass.getDeclaredField("presentationReleasePool").apply { isAccessible = true }
                .set(value, ReleasePool(16))
        }
        try {
            val stored = ChunkedVaultVideoStore.writeAndVerify("61cae41c-91e9-49f0-9a88-624139561252", ByteArrayInputStream(plain), key, root)
            authority.open(key.copyOf())
            val operation = checkNotNull(authority.operationOrNull(setOf(PrimaryScope.READ)))
            val owned = ChunkedVaultVideoStore.openOwned(ScopedIoGuard(operation, PrimaryScope.READ), stored, key)
            val reader = owned.value
            fun field(name: String): Any = reader.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(reader)
            val mutex = field("readerMutex")
            val copiedKey = field("ownedKey") as ByteArray
            val descriptor = field("file") as java.io.RandomAccessFile
            try {
                synchronized(mutex) {
                    authority.revoke()
                    assertTrue(entered.await(5, TimeUnit.SECONDS))
                    // Observe both dependent release workers actually waiting on this exact
                    // monitor, rather than assuming asynchronous dispatch finished after a delay.
                    // Android's compile bootclasspath omits java.management; these
                    // host-JVM concurrency observations use its public interfaces reflectively.
                    val threads = Class.forName("java.lang.management.ManagementFactory")
                        .getMethod("getThreadMXBean").invoke(null)
                    val getThreadInfo = Class.forName("java.lang.management.ThreadMXBean")
                        .getMethod("getThreadInfo", java.lang.Long.TYPE)
                    val threadInfoClass = Class.forName("java.lang.management.ThreadInfo")
                    val getThreadState = threadInfoClass.getMethod("getThreadState")
                    val getLockInfo = threadInfoClass.getMethod("getLockInfo")
                    val getIdentityHashCode = Class.forName("java.lang.management.LockInfo")
                        .getMethod("getIdentityHashCode")
                    val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
                    var blocked = false
                    while (System.nanoTime() < deadline) {
                        blocked = workers.count { worker ->
                            val info = getThreadInfo.invoke(threads, worker.id)
                            val lock = info?.let { getLockInfo.invoke(it) }
                            info != null && getThreadState.invoke(info) == Thread.State.BLOCKED &&
                                lock != null && getIdentityHashCode.invoke(lock) == System.identityHashCode(mutex)
                        } == 2
                        if (blocked && !descriptor.channel.isOpen) break
                        Thread.yield()
                    }
                    assertTrue("reader and key retirement must await reader quiescence", blocked)
                    assertFalse("descriptor close must independently unblock active IO", descriptor.channel.isOpen)
                    assertArrayEquals(key, copiedKey)
                    assertFalse(owned.retirement.isComplete)
                    assertFalse(authority.cleanupComplete)
                }
            } finally {
                owned.close()
                assertTrue(owned.retirement.await(5, TimeUnit.SECONDS))
            }
            assertArrayEquals(ByteArray(32), copiedKey)
            assertTrue(authority.cleanupComplete)
        } finally { authority.revoke(); root.deleteRecursively(); plain.fill(0); key.fill(0) }
    }

}
