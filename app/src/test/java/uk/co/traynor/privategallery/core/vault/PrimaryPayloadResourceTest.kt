package uk.co.traynor.privategallery.core.vault

import java.io.File
import java.io.IOException
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PrimaryPayloadResourceTest {
    @get:Rule val temporary=TemporaryFolder()
    private val key=ByteArray(32) { it.toByte() }
    private val plain=ByteArray(128*1024+7) { (it%251).toByte() }

    @Test fun allDecryptPathsRegisterReaderOrInputBeforeUsingIt() {
        for(chunked in listOf(false,true)) {
            val root=temporary.newFolder("resource-$chunked")
            val stored=EncryptedPayloadStore(root).writeAndVerify("media",plain.inputStream(),key,chunked)
            for(action in listOf("verify","bytes","bounded","progress","file")) {
                var count=0
                val store=EncryptedPayloadStore(root,registerResource={ resource -> count++; resource.close() })
                when(action) {
                    "verify" -> assertFalse("revoked verify cannot succeed",store.verify(stored,key))
                    "bytes" -> assertThrows(Exception::class.java) { store.decryptToBytes(stored,key) }
                    "bounded" -> assertThrows(Exception::class.java) { store.decryptToBoundedBytes(stored,key,plain.size,{false}) }
                    "progress" -> assertThrows(Exception::class.java) { store.decryptWithProgress(stored,key,{false},{}) }
                    "file" -> {
                        val output=File(root,"output-$action")
                        assertThrows(Exception::class.java) { store.decryptToVerifiedFile(stored,key,output,plain.size.toLong(),{false}) }
                        assertFalse(output.exists())
                    }
                }
                assertTrue("$action chunked=$chunked failed to register a decryption resource",count>0)
            }
        }
    }

    @Test fun registrationFailureClosesCopiedKeyReaderBeforeItCanEscape() {
        val root=temporary.newFolder("rejected-register")
        val stored=EncryptedPayloadStore(root).writeAndVerify("video",plain.inputStream(),key,true)
        var captured:AutoCloseable?=null
        val store=EncryptedPayloadStore(root,registerResource={ resource -> captured=resource; throw IOException("revoked") })
        assertFalse(store.verify(stored,key))
        val reader=checkNotNull(captured) as ChunkedVaultVideoStore.Reader
        assertThrows(IOException::class.java) { reader.readAt(0,ByteArray(1),0,1) }
    }

    @Test fun chunkedWriteVerificationAlsoRegistersItsCopiedKeyReader() {
        val root=temporary.newFolder("write-register")
        var seen=false
        val store=EncryptedPayloadStore(root,registerResource={ resource -> if(resource is ChunkedVaultVideoStore.Reader) { seen=true; resource.close() } })
        assertThrows(Exception::class.java) { store.writeAndVerify("video",plain.inputStream(),key,true) }
        assertTrue(seen); assertFalse(File(root,"payloads/video.vault").exists())
    }

    @Test fun preexistingRetiredCopyCannotTriggerRollbackOverTheLiveVerifiedCopy() {
        val root=temporary.newFolder("retirement-collision")
        val stored=EncryptedPayloadStore(root).writeAndVerify("video",plain.inputStream(),key)
        val original=stored.file.readBytes()
        val retired=File(root,"payloads/video.legacy").apply { writeBytes(byteArrayOf(11,12,13)) }
        val other=retired.readBytes()
        assertThrows(IllegalStateException::class.java) { EncryptedPayloadStore(root).migrateLegacyVideo(stored,key,{false}) }
        assertArrayEquals(original,stored.file.readBytes())
        assertArrayEquals(other,retired.readBytes())
        assertTrue(EncryptedPayloadStore(root).verify(stored,key))
    }

    @Test fun legacyMigrationDenialRetainsOriginalCiphertextAndUnindexedRetiredCopySurvivesReconcile() {
        val root=temporary.newFolder("migration-denied")
        val stored=EncryptedPayloadStore(root).writeAndVerify("video",plain.inputStream(),key)
        val original=stored.file.readBytes()
        assertThrows(IOException::class.java) { EncryptedPayloadStore(root).migrateLegacyVideo(stored,key,{false},commit={ throw IOException("revoked") }) }
        assertArrayEquals(original,stored.file.readBytes())
        val retired=File(root,"payloads/unknown.legacy").apply { writeBytes(original) }
        EncryptedPayloadStore(root).reconcileVideoMigrations(emptyList(),key)
        assertArrayEquals(original,retired.readBytes())
    }
    @Test fun deniedDeletionReconciliationLeavesRetiredAndLiveCiphertextUnchanged() {
        for(indexed in listOf(false,true)) {
            val root=temporary.newFolder("deletion-revoke-$indexed")
            val store=EncryptedPayloadStore(root)
            val stored=store.writeAndVerify("media",plain.inputStream(),key)
            val retired=store.retireForDeletion(stored.id)
            val original=retired.readBytes()
            val live=File(root,"payloads/other.vault").apply { writeBytes(byteArrayOf(7,8,9)) }
            val liveBytes=live.readBytes()
            var checked=false
            assertThrows(IOException::class.java) {
                store.reconcileInterruptedDeletes(if(indexed) setOf(stored.id) else emptySet(),commit={ checked=true; throw IOException("revoked") })
            }
            assertTrue(checked)
            assertArrayEquals(original,retired.readBytes())
            assertArrayEquals(liveBytes,live.readBytes())
            assertFalse(stored.file.exists())
        }
    }

    @Test fun missingCommittedIndexCannotAuthorizeDeletingUnindexedSoleCiphertext() {
        val root=temporary.newFolder("missing-delete-index")
        val store=EncryptedPayloadStore(root)
        val stored=store.writeAndVerify("unindexed",plain.inputStream(),key)
        val retired=store.retireForDeletion(stored.id)
        val original=retired.readBytes()
        store.reconcileInterruptedDeletes(emptySet())
        assertTrue(retired.exists())
        assertArrayEquals(original,retired.readBytes())
        assertTrue(store.verify(stored.copy(file=retired),key))
    }

}
