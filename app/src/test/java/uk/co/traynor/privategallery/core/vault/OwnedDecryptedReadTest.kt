package uk.co.traynor.privategallery.core.vault

import java.nio.file.Files
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test
import uk.co.traynor.privategallery.core.security.*

class OwnedDecryptedReadTest {
    @Test fun scopedSniffDeniesBeforeOpeningWhenIoCapacityIsExhausted() {
        val root=Files.createTempDirectory("owned-sniff").toFile()
        val file=java.io.File(root,"payload").apply { writeBytes("PGVIDEO1".toByteArray()+ByteArray(60)) }
        val authority=testPrimaryAuthority();authority.open(ByteArray(32){4})
        val operation=checkNotNull(authority.operationOrNull(setOf(PrimaryScope.READ)))
        val guard=ScopedIoGuard(operation,PrimaryScope.READ)
        val holds=List(16) { operation.createOwned(OwnedResourceManifest.io("hold")) { attach("hold",AutoCloseable {}) } }
        try {
            assertThrows(IllegalStateException::class.java) { ChunkedVaultVideoStore.isChunked(file,guard) }
            holds.forEach { it.close();assertTrue(it.retirement.await(5,TimeUnit.SECONDS)) }
            assertTrue(ChunkedVaultVideoStore.isChunked(file,guard))
            val deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5)
            while (!authority.cleanupComplete && System.nanoTime()<deadline) Thread.yield()
            assertTrue(authority.cleanupComplete)
        } finally { holds.forEach { it.close() };operation.close();authority.revoke();root.deleteRecursively() }
    }

    @Test fun authenticatedReadsRetireNormallyWithoutLegacyRegistrationOrCiphertextChanges() {
        val root=Files.createTempDirectory("owned-decrypt").toFile()
        val authority=testPrimaryAuthority();val key=ByteArray(32){5};authority.open(key.copyOf())
        val operation=checkNotNull(authority.operationOrNull(setOf(PrimaryScope.READ)))
        try {
            val body=ByteArray(4096){(it%251).toByte()}
            val stored=EncryptedPayloadStore(root, syncOutput={}).writeAndVerify("owned",body.inputStream(),key)
            val encrypted=stored.file.readBytes();var registrations=0
            val store=EncryptedPayloadStore(root, syncOutput={}, registerResource={registrations++})
            repeat(40) {
                val source=operation.createOwnedPlaintext { store.decryptToBoundedBytes(stored,key,8192,{false},guard=ScopedIoGuard(operation,PrimaryScope.READ)) }
                source.useBytes { assertArrayEquals(body,it) };source.close();assertTrue(source.retirement.await(5,TimeUnit.SECONDS))
                val deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5)
                while (!authority.cleanupComplete && System.nanoTime()<deadline) Thread.yield()
                assertTrue(authority.cleanupComplete)
            }
            val chunked=ChunkedVaultVideoStore.writeAndVerify("chunked-owned",body.inputStream(),key,root,ByteArray(12){2})
            val chunkedCiphertext=chunked.file.readBytes()
            repeat(20) {
                val source=operation.createOwnedPlaintext { store.decryptToBoundedBytes(chunked,key,8192,{false},guard=ScopedIoGuard(operation,PrimaryScope.READ)) }
                source.useBytes { assertArrayEquals(body,it) };source.close();assertTrue(source.retirement.await(5,TimeUnit.SECONDS))
                val deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5)
                while (!authority.cleanupComplete && System.nanoTime()<deadline) Thread.yield()
                assertTrue(authority.cleanupComplete)
            }
            assertArrayEquals(chunkedCiphertext,chunked.file.readBytes())
            repeat(20) {
                assertThrows(Exception::class.java) { operation.createOwnedPlaintext {
                    store.decryptToBoundedBytes(stored,ByteArray(32){8},8192,{false},guard=ScopedIoGuard(operation,PrimaryScope.READ))
                } }
                val deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5)
                while (!authority.cleanupComplete && System.nanoTime()<deadline) Thread.yield()
                assertTrue(authority.cleanupComplete)
            }
            var checks=0
            assertThrows(java.io.IOException::class.java) { operation.createOwnedPlaintext {
                store.decryptToBoundedBytes(stored,key,8192,{ ++checks > 1 },guard=ScopedIoGuard(operation,PrimaryScope.READ))
            } }
            assertEquals(0,registrations);assertArrayEquals(encrypted,stored.file.readBytes())
            assertThrows(java.io.IOException::class.java) { operation.createOwnedPlaintext { store.decryptToBoundedBytes(stored,key,8192,{true},guard=ScopedIoGuard(operation,PrimaryScope.READ)) } }
            assertArrayEquals(encrypted,stored.file.readBytes())
        } finally { operation.close();authority.revoke();root.deleteRecursively();key.fill(0) }
    }
}
