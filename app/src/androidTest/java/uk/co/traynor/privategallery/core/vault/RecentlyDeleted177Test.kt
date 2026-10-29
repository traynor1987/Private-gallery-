package uk.co.traynor.privategallery.core.vault

import android.content.ContextWrapper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

class RecentlyDeleted177Test {
    private fun fixture(block:(AndroidVaultRepository,android.content.Context,ByteArray,File)->Unit) {
        val base=InstrumentationRegistry.getInstrumentation().targetContext
        val root=File(base.cacheDir,"trash-fault177-${UUID.randomUUID()}")
        val context=object:ContextWrapper(base){override fun getFilesDir()=root}
        val key=ByteArray(32){17}
        try {block(AndroidVaultRepository(context,key),context,key,root)}finally{key.fill(0);root.deleteRecursively()}
    }
    @Test fun restoreStopsWhenAuthorityEndsDuringPayloadVerification() = fixture {repo,context,key,_ ->
        val item=(repo.importVerified(VaultImportSource("large.png","image/png",{ByteArray(200_000){it.toByte()}.inputStream()})) as ImportResult.Imported).item
        repo.moveToRecentlyDeleted(item.id)
        var checks=0
        val revoked=AndroidVaultRepository(context,key,authorized={++checks<4})
        assertTrue(runCatching {revoked.restoreRecentlyDeleted(item.id)}.isFailure)
        assertTrue("Authority checked during the payload read",checks>=4)
        assertEquals(item.id,repo.recentlyDeleted().single().id)
        assertTrue(repo.items().isEmpty())
    }
    @Test fun deletionInterruptedAfterIndexCommitReconcilesPayloadAndPreviewOnly() = fixture {repo,context,key,root ->
        val item=(repo.importVerified(VaultImportSource("first.png","image/png",{byteArrayOf(1,2,3).inputStream()})) as ImportResult.Imported).item
        val other=(repo.importVerified(VaultImportSource("other.png","image/png",{byteArrayOf(4,5,6).inputStream()})) as ImportResult.Imported).item
        val cache=uk.co.traynor.privategallery.core.media.EncryptedPreviewCache(File(root,"vault/previews"))
        cache.put(item.id,"revision",byteArrayOf(7,8),key);cache.put(other.id,"revision",byteArrayOf(9,10),key)
        repo.moveToRecentlyDeleted(item.id)
        var commits=0
        val failing=AndroidVaultRepository(context,key,publish={commit ->if(++commits==3)error("Simulated interruption after index commit");commit()})
        assertTrue(runCatching {failing.deleteFromVault(item)}.isFailure)
        assertTrue(repo.recentlyDeleted().isEmpty())
        assertNotNull(cache.get(item.id,"revision",key))
        AndroidVaultRepository(context,key).reconcile()
        assertNull(cache.get(item.id,"revision",key));assertNotNull(cache.get(other.id,"revision",key))
        assertFalse(File(root,"vault/payloads/${item.id}.deleting").exists())
        assertArrayEquals(byteArrayOf(4,5,6),repo.readForViewing(other))
    }
    @Test fun expiryDoesNotRemoveAnActiveImportStagingFile() = fixture {repo,_,_,root ->
        val old=(repo.importVerified(VaultImportSource("old.png","image/png",{byteArrayOf(9,8,7).inputStream()})) as ImportResult.Imported).item
        repo.moveToRecentlyDeleted(old.id,1000)
        val started=java.util.concurrent.CountDownLatch(1);val resume=java.util.concurrent.CountDownLatch(1)
        val executor=java.util.concurrent.Executors.newSingleThreadExecutor()
        val incoming=executor.submit<ImportResult> {repo.importVerified(VaultImportSource("new.png","image/png",{
            object:java.io.ByteArrayInputStream(byteArrayOf(1,2,3,4)) {
                override fun read(b:ByteArray,off:Int,len:Int):Int {
                    started.countDown();check(resume.await(20,java.util.concurrent.TimeUnit.SECONDS));return super.read(b,off,len)
                }
            }
        }))}
        try {
            assertTrue(started.await(10,java.util.concurrent.TimeUnit.SECONDS))
            val stage=File(root,"vault/staging").listFiles()!!.single {it.name.endsWith(".part")}
            repo.deleteExpiredRecentlyDeleted(Long.MAX_VALUE)
            assertTrue("Active ciphertext staging survives expiry",stage.exists())
            resume.countDown()
            val imported=(incoming.get(20,java.util.concurrent.TimeUnit.SECONDS) as ImportResult.Imported).item
            assertArrayEquals(byteArrayOf(1,2,3,4),repo.readForViewing(imported))
        }finally{resume.countDown();executor.shutdownNow()}
    }
    @Test fun interruptedPrimaryDeletionRestoresRetainedRecordAndRestoreIsIdempotent() {
        val base=InstrumentationRegistry.getInstrumentation().targetContext
        val root=File(base.cacheDir,"trash-restart177-${UUID.randomUUID()}")
        val context=object:ContextWrapper(base){override fun getFilesDir()=root}
        val key=ByteArray(32){17}
        try {
            val repo=AndroidVaultRepository(context,key)
            val item=(repo.importVerified(VaultImportSource("fixture.png","image/png",{byteArrayOf(1,2,3,4).inputStream()})) as ImportResult.Imported).item
            repo.retentionDays(0);repo.moveToRecentlyDeleted(item.id)
            val file=File(root,"vault/payloads/${item.id}.vault")
            val original=file.readBytes()
            assertTrue(file.renameTo(File(file.parentFile,"${item.id}.deleting")))
            val reopened=AndroidVaultRepository(context,key);reopened.reconcile()
            assertArrayEquals(original,file.readBytes());assertEquals(0,reopened.retentionDays())
            reopened.restoreRecentlyDeleted(item.id)
            assertEquals(item.id,reopened.items().single().id)
            assertTrue(runCatching {reopened.restoreRecentlyDeleted(item.id)}.isFailure)
            assertEquals(1,reopened.items().size)
            reopened.moveToRecentlyDeleted(item.id);reopened.deleteFromVault(reopened.recentlyDeleted().single())
            assertFalse(file.exists());assertTrue(reopened.recentlyDeleted().isEmpty())
            reopened.reconcile();assertTrue(reopened.items().isEmpty())
        } finally {key.fill(0);root.deleteRecursively()}
    }

    @Test fun corruptPrimaryRestoreRetainsTrashAndCiphertext() {
        val base=InstrumentationRegistry.getInstrumentation().targetContext
        val root=File(base.cacheDir,"trash177-${UUID.randomUUID()}")
        val context=object:ContextWrapper(base){override fun getFilesDir()=root}
        val key=ByteArray(32){17}
        try {
            val repo=AndroidVaultRepository(context,key)
            val item=(repo.importVerified(VaultImportSource("fixture.png","image/png",{byteArrayOf(1,2,3,4).inputStream()})) as ImportResult.Imported).item
            repo.moveToRecentlyDeleted(item.id)
            assertTrue(runCatching {repo.readForViewing(item)}.isFailure)
            assertTrue(runCatching {repo.readForEditing(item){false}}.isFailure)
            assertTrue(runCatching {repo.readVideoForViewing(item,{false},{})}.isFailure)
            assertTrue(runCatching {repo.importAiEditedCopy(item.id,byteArrayOf(1,2),null,false,cancelled={false})}.isFailure)
            val file=File(root,"vault/payloads/${item.id}.vault")
            val damaged=file.readBytes().also {it[it.lastIndex]=(it.last().toInt() xor 1).toByte()}
            file.writeBytes(damaged)
            assertTrue("Corrupt media must not be published",runCatching {repo.restoreRecentlyDeleted(item.id)}.isFailure)
            assertTrue(repo.items().isEmpty())
            assertEquals(item.id,repo.recentlyDeleted().single().id)
            assertArrayEquals(damaged,file.readBytes())
        } finally {key.fill(0);root.deleteRecursively()}
    }
}
