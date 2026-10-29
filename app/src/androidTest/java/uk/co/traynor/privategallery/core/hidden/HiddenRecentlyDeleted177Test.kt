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

    @Test fun legacyLargeImageAndVideoRepairPreserveOriginalAndRejectCorruption() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val bitmap=Bitmap.createBitmap(8200,64,Bitmap.Config.ARGB_8888)
        val image=try {ByteArrayOutputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it);it.toByteArray()}}finally{bitmap.recycle()}
        val video=instrumentation.context.assets.open("browser-media/valid.mp4").use {it.readBytes()}
        for((mime,bytes) in listOf("image/png" to image,"video/mp4" to video)) {
            val root=File(instrumentation.targetContext.cacheDir,"legacy-preview177-${UUID.randomUUID()}")
            val context=object:ContextWrapper(instrumentation.targetContext){override fun getNoBackupFilesDir()=root}
            val key=ByteArray(32){29};val session=HiddenSession(android.os.SystemClock::elapsedRealtime)
            session.complete(session.beginAuthentication(),key.copyOf())
            try {
                val records=HiddenRecordStore(File(root,"hidden-media-v1"))
                val id=UUID.randomUUID().toString();val metadataId="00000000-0000-0000-0000-000000000000"
                val hash=java.security.MessageDigest.getInstance("SHA-256").digest(bytes)
                val item=org.json.JSONObject().put("id",id).put("mime",mime).put("name","legacy fixture").put("time",1000).put("size",bytes.size)
                    .put("hash",android.util.Base64.encodeToString(hash,android.util.Base64.NO_WRAP)).put("trash",false).put("deleted",0)
                val metadata=org.json.JSONObject().put("domain","hidden").put("version",1).put("items",org.json.JSONArray().put(item))
                    .put("collections",org.json.JSONArray()).put("memberships",org.json.JSONArray()).put("favourite","")
                records.promote(records.stage("media",id,bytes,key),"media",id)
                records.promote(records.stage("metadata",metadataId,metadata.toString().toByteArray(),key),"metadata",metadataId)
                val thumb=records.file("thumbnail",id)
                if(mime.startsWith("video/"))thumb.writeBytes(byteArrayOf(1,2,3))
                val original=records.file("media",id).readBytes();val originalMetadata=records.file("metadata",metadataId).readBytes()
                val repo=HiddenRepository(context,session)
                val preview=repo.preview(id)
                try {assertTrue("Bounded legacy $mime",preview.width in 1..512 && preview.height in 1..512)}finally{preview.recycle()}
                assertArrayEquals(original,records.file("media",id).readBytes())
                assertArrayEquals(originalMetadata,records.file("metadata",metadataId).readBytes())
                assertEquals(id,repo.items().single().id);assertArrayEquals(hash,repo.items().single().plaintextSha256)
                assertArrayEquals(bytes,repo.read(id))
                val thumbnailPlaintext=records.read("thumbnail",id,key)
                try {val decoded=checkNotNull(android.graphics.BitmapFactory.decodeByteArray(thumbnailPlaintext,0,thumbnailPlaintext.size));decoded.recycle()}finally{thumbnailPlaintext.fill(0)}
                val damaged=original.copyOf().also {it[it.lastIndex]=(it.last().toInt() xor 1).toByte()}
                records.file("media",id).writeBytes(damaged)
                val brokenThumbnail=byteArrayOf(7,8,9);thumb.writeBytes(brokenThumbnail)
                assertTrue("Corrupt original must not repair $mime",runCatching {repo.preview(id)}.isFailure)
                assertArrayEquals(damaged,records.file("media",id).readBytes());assertArrayEquals(brokenThumbnail,thumb.readBytes())
                assertEquals(id,repo.items().single().id)
            } finally {session.lock();key.fill(0);root.deleteRecursively()}
        }
        image.fill(0);video.fill(0)
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
