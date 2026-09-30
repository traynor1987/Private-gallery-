package uk.co.traynor.privategallery.core.vault

import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.util.concurrent.CancellationException
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Real ciphertext stores; simulated restart reconstructs stores from disk, never an in-memory snapshot. */
class PrimaryWriteFaultsTest {
    @get:Rule val temporary = TemporaryFolder()
    private val key=ByteArray(32) { it.toByte() }
    private val plain=ByteArray(4099) { (it%251).toByte() }
    private val old=VaultIndexSnapshot(emptyList(),listOf(VaultCollection("collection-1","Before",1)))
    private val next=old.copy(collections=listOf(VaultCollection("collection-1","After",1)))

    @Test fun indexCheckpointsPreserveOneAuthenticatedCommittedGenerationAcrossFailures() {
        WriteCheckpoint.entries.filter { it.name.startsWith("INDEX_") }.forEach { point ->
            val root=temporary.newFolder(point.name)
            EncryptedIndexStore(root).saveSnapshot(old,key)
            val before=File(root,"vault-index.enc").readBytes()
            var reached=false
            val faults=PrimaryWriteFaults { checkpoint,_ -> if(checkpoint==point) { reached=true; throw IOException("synthetic ENOSPC") } }
            assertThrows(IOException::class.java) { EncryptedIndexStore(root,faults=faults).saveSnapshot(next,key) }
            assertTrue("checkpoint $point was not exercised",reached)
            val fresh=EncryptedIndexStore(root).loadSnapshot(key)
            assertEquals(if(point==WriteCheckpoint.INDEX_AFTER_PROMOTION) "After" else "Before",fresh.collections.single().name)
            if(point!=WriteCheckpoint.INDEX_AFTER_PROMOTION) assertArrayEquals(before,File(root,"vault-index.enc").readBytes())
            assertFalse(File(root,"vault-index.new").exists())
            EncryptedIndexStore(root).saveSnapshot(next,key)
            assertEquals("After",EncryptedIndexStore(root).loadSnapshot(key).collections.single().name)
        }
    }

    @Test fun revokedCommitAndCancellationLeaveLastIndexByteIdentical() {
        val root=temporary.newFolder("denied-index")
        EncryptedIndexStore(root).saveSnapshot(old,key)
        val before=File(root,"vault-index.enc").readBytes()
        assertThrows(CancellationException::class.java) { EncryptedIndexStore(root).saveSnapshot(next,key,commit={ throw CancellationException("revoked") }) }
        assertArrayEquals(before,File(root,"vault-index.enc").readBytes())
        assertFalse(File(root,"vault-index.new").exists())
        assertEquals("Before",EncryptedIndexStore(root).loadSnapshot(key).collections.single().name)
    }

    @Test fun indexCorruptOrTruncatedStagingNeverReplacesLastVerifiedIndex() {
        for(truncate in listOf(true,false)) {
            val root=temporary.newFolder("index-corrupt-$truncate")
            EncryptedIndexStore(root).saveSnapshot(old,key)
            val before=File(root,"vault-index.enc").readBytes()
            val faults=PrimaryWriteFaults { point,file -> if(point==WriteCheckpoint.INDEX_AFTER_SYNC) damage(file,truncate) }
            assertThrows(Exception::class.java) { EncryptedIndexStore(root,faults=faults).saveSnapshot(next,key) }
            assertArrayEquals(before,File(root,"vault-index.enc").readBytes())
            assertEquals("Before",EncryptedIndexStore(root).loadSnapshot(key).collections.single().name)
        }
    }

    @Test fun payloadFaultsCannotRemoveExistingVerifiedCopyAndRetryIsSafe() {
        for(chunked in listOf(false,true)) {
            WriteCheckpoint.entries.filter { it.name.startsWith("PAYLOAD_") }.forEach { point ->
                val root=temporary.newFolder("$chunked-${point.name}")
                val existing=EncryptedPayloadStore(root).writeAndVerify("existing",plain.inputStream(),key,chunked)
                val original=existing.file.readBytes()
                var reached=false
                val faults=PrimaryWriteFaults { checkpoint,_ -> if(checkpoint==point) { reached=true; throw CancellationException("synthetic cancellation") } }
                assertThrows(CancellationException::class.java) { EncryptedPayloadStore(root,faults=faults).writeAndVerify("new",plain.inputStream(),key,chunked) }
                assertTrue("$point $chunked was not exercised",reached)
                assertArrayEquals(original,existing.file.readBytes())
                val restarted=EncryptedPayloadStore(root)
                restarted.reconcileInterruptedWrites()
                assertTrue(restarted.verify(existing,key))
                val promoted=File(root,"payloads/new.vault")
                if(point==WriteCheckpoint.PAYLOAD_AFTER_PROMOTION) {
                    assertTrue(promoted.exists()) // safe orphan/duplicate after crash, no fabricated index record
                    assertThrows(IllegalStateException::class.java) { restarted.writeAndVerify("new",plain.inputStream(),key,chunked) }
                } else {
                    assertFalse(promoted.exists())
                    assertTrue(restarted.verify(restarted.writeAndVerify("new",plain.inputStream(),key,chunked),key))
                }
            }
        }
    }

    @Test fun deniedPayloadCommitAndDamagedStagingNeverPublish() {
        for(chunked in listOf(false,true)) {
            val deniedRoot=temporary.newFolder("denied-$chunked")
            assertThrows(CancellationException::class.java) { EncryptedPayloadStore(deniedRoot).writeAndVerify("denied",plain.inputStream(),key,chunked,commit={ throw CancellationException("revoked") }) }
            assertFalse(File(deniedRoot,"payloads/denied.vault").exists())
            for(truncate in listOf(true,false)) {
                val root=temporary.newFolder("bad-$chunked-$truncate")
                val faults=PrimaryWriteFaults { point,file -> if(point==WriteCheckpoint.PAYLOAD_AFTER_SYNC) damage(file,truncate) }
                assertThrows(Exception::class.java) { EncryptedPayloadStore(root,faults=faults).writeAndVerify("damaged",plain.inputStream(),key,chunked) }
                assertFalse(File(root,"payloads/damaged.vault").exists())
                assertFalse(File(root,"staging/damaged.part").exists())
            }
        }
    }

    @Test fun callbackThatSkipsPromotionCannotClaimACommittedIndexOrPayload() {
        val root=temporary.newFolder("skipped-index")
        EncryptedIndexStore(root).saveSnapshot(old,key)
        val before=File(root,"vault-index.enc").readBytes()
        assertThrows(IllegalStateException::class.java) { EncryptedIndexStore(root).saveSnapshot(next,key,commit={}) }
        assertArrayEquals(before,File(root,"vault-index.enc").readBytes())
        for(chunked in listOf(false,true)) {
            val payloadRoot=temporary.newFolder("skipped-payload-$chunked")
            assertThrows(IllegalStateException::class.java) { EncryptedPayloadStore(payloadRoot).writeAndVerify("skipped",plain.inputStream(),key,chunked,commit={}) }
            assertFalse(File(payloadRoot,"payloads/skipped.vault").exists())
            assertFalse(File(payloadRoot,"staging/skipped.part").exists())
        }
    }

    @Test fun destinationCreatedDuringPreparationCannotBeOverwrittenAtFinalPromotion() {
        for(chunked in listOf(false,true)) {
            val root=temporary.newFolder("promotion-race-$chunked")
            val competing=temporary.newFolder("competing-$chunked")
            val copy=EncryptedPayloadStore(competing).writeAndVerify("same-id",byteArrayOf(7,8,9).inputStream(),key,chunked)
            var winner:StoredPayload?=null
            assertThrows(IllegalStateException::class.java) {
                EncryptedPayloadStore(root).writeAndVerify("same-id",plain.inputStream(),key,chunked,commit={ action ->
                    val destination=File(root,"payloads/same-id.vault")
                    copy.file.copyTo(destination)
                    winner=copy.copy(file=destination)
                    action()
                })
            }
            val verified=checkNotNull(winner)
            assertArrayEquals(copy.file.readBytes(),verified.file.readBytes())
            assertTrue(EncryptedPayloadStore(root).verify(verified,key))
        }
    }

    @Test fun restartEquivalentLeftoverStagingAndRetiredPayloadRetainIndexedCopy() {
        val root=temporary.newFolder("restart")
        val store=EncryptedPayloadStore(root)
        val stored=store.writeAndVerify("sole-copy",plain.inputStream(),key)
        val original=stored.file.readBytes()
        File(root,"staging/interrupted.part").writeBytes(byteArrayOf(1,2,3))
        store.retireForDeletion(stored.id)
        val restarted=EncryptedPayloadStore(root)
        restarted.reconcileInterruptedWrites(); restarted.reconcileInterruptedDeletes(setOf(stored.id))
        assertArrayEquals(original,stored.file.readBytes()); assertTrue(restarted.verify(stored,key))
        assertFalse(File(root,"staging/interrupted.part").exists())
        assertThrows(IllegalStateException::class.java) { restarted.writeAndVerify(stored.id,plain.inputStream(),key) }
        assertArrayEquals(original,stored.file.readBytes())
    }

    private fun damage(file:File,truncate:Boolean)=RandomAccessFile(file,"rw").use {
        if(truncate) it.setLength(5) else { it.seek(it.length()-1); val last=it.read(); it.seek(it.length()-1); it.write(last xor 1) }
    }
}
