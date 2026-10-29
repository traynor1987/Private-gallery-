package uk.co.traynor.privategallery.core.hidden

import android.content.ContextWrapper
import android.graphics.Bitmap
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

class HiddenRecentlyDeleted177Test {
    @Test fun independentRetentionRestoreMembershipAndThumbnailRecovery() {
        val base=InstrumentationRegistry.getInstrumentation().targetContext
        val root=File(base.cacheDir,"trash-isolation177-${UUID.randomUUID()}")
        val context=object:ContextWrapper(base){override fun getNoBackupFilesDir()=File(root,"no-backup");override fun getFilesDir()=File(root,"files")}
        val session=HiddenSession(android.os.SystemClock::elapsedRealtime)
        session.complete(session.beginAuthentication(),ByteArray(32){29})
        val primaryKey=ByteArray(32){17}
        try {
            val hidden=HiddenRepository(context,session)
            val primary=uk.co.traynor.privategallery.core.vault.AndroidVaultRepository(context,primaryKey)
            hidden.retentionDays(0);primary.retentionDays(7)
            assertEquals(0,HiddenRepository(context,session).retentionDays());assertEquals(7,primary.retentionDays())
            val bitmap=Bitmap.createBitmap(8,8,Bitmap.Config.ARGB_8888)
            val bytes=ByteArrayOutputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it);it.toByteArray()};bitmap.recycle()
            val h=hidden.importMedia("private.png","image/png",bytes.inputStream(),capturedAtEpochMillis=1000)
            val collection=hidden.createCollection("Keep me");hidden.addItemsToCollection(collection.id,listOf(h.id));hidden.setFavouriteCollection(collection.id)
            val p=(primary.importVerified(uk.co.traynor.privategallery.core.vault.VaultImportSource("primary.png","image/png",{bytes.inputStream()})) as uk.co.traynor.privategallery.core.vault.ImportResult.Imported).item
            primary.moveToRecentlyDeleted(p.id,1000)
            hidden.trash(listOf(h.id));hidden.reconcile();primary.deleteExpiredRecentlyDeleted(1000+7*uk.co.traynor.privategallery.core.vault.RecentlyDeletedPolicy.DAY_MILLIS)
            assertTrue(primary.recentlyDeleted().isEmpty());assertEquals(h.id,hidden.recentlyDeleted().single().id)
            assertTrue(hidden.items().isEmpty());assertTrue(hidden.itemsInCollection(collection.id).isEmpty())
            assertTrue(runCatching {hidden.read(h.id)}.isFailure)
            hidden.preview(h.id,true).recycle()
            hidden.restore(h.id)
            assertEquals(h.id,hidden.itemsInCollection(collection.id).single().id)
            assertEquals(collection.id,hidden.favouriteCollection()!!.id)
            assertEquals(1000L,hidden.items().single().capturedAtEpochMillis)
            assertTrue(runCatching {hidden.restore(h.id)}.isFailure);assertEquals(1,hidden.items().size)
            val records=HiddenRecordStore(File(context.noBackupFilesDir,"hidden-media-v1"))
            assertTrue(records.file("thumbnail",h.id).delete())
            hidden.reconcile();hidden.preview(h.id).recycle();assertTrue(records.file("thumbnail",h.id).isFile)
            hidden.trash(listOf(h.id));hidden.deleteCollection(collection.id);hidden.restore(h.id)
            assertEquals(h.id,hidden.items().single().id);assertTrue(hidden.collections().isEmpty())
            hidden.trash(listOf(h.id));hidden.deletePermanently(h.id)
            assertFalse(records.file("media",h.id).exists());assertFalse(records.file("thumbnail",h.id).exists())
            hidden.reconcile();assertTrue(hidden.recentlyDeleted().isEmpty())
        } finally {session.lock();primaryKey.fill(0);root.deleteRecursively()}
    }

    @Test fun corruptHiddenRestoreRetainsTrashAndRejectsLockedAccess() {
        val base=InstrumentationRegistry.getInstrumentation().targetContext
        val root=File(base.cacheDir,"hidden-trash177-${UUID.randomUUID()}")
        val context=object:ContextWrapper(base){override fun getNoBackupFilesDir()=root}
        val session=HiddenSession(android.os.SystemClock::elapsedRealtime)
        session.complete(session.beginAuthentication(),ByteArray(32){29})
        try {
            val repo=HiddenRepository(context,session)
            val bitmap=Bitmap.createBitmap(8,8,Bitmap.Config.ARGB_8888)
            val bytes=ByteArrayOutputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it);it.toByteArray()};bitmap.recycle()
            val item=repo.importMedia("fixture.png","image/png",bytes.inputStream())
            repo.trash(listOf(item.id))
            val records=HiddenRecordStore(File(root,"hidden-media-v1"))
            val file=records.file("media",item.id)
            val damaged=file.readBytes().also {it[it.lastIndex]=(it.last().toInt() xor 1).toByte()};file.writeBytes(damaged)
            assertTrue(runCatching {repo.restore(item.id)}.isFailure)
            assertTrue(repo.items().isEmpty());assertEquals(item.id,repo.recentlyDeleted().single().id)
            assertArrayEquals(damaged,file.readBytes())
            session.lock();assertTrue(runCatching {repo.recentlyDeleted()}.isFailure)
            assertTrue(runCatching {repo.deletePermanently(item.id)}.isFailure)
            assertArrayEquals(damaged,file.readBytes())
        } finally {session.lock();root.deleteRecursively()}
    }
}
