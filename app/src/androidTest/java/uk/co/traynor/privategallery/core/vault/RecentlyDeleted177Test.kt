package uk.co.traynor.privategallery.core.vault

import android.content.ContextWrapper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

class RecentlyDeleted177Test {
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
