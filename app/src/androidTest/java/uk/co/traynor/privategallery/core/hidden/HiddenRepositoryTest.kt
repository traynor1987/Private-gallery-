package uk.co.traynor.privategallery.core.hidden

import android.content.ContextWrapper
import android.graphics.Bitmap
import androidx.test.platform.app.InstrumentationRegistry
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import org.junit.Test
import org.junit.Assert.*

class HiddenRepositoryTest {
    private val base = InstrumentationRegistry.getInstrumentation().targetContext
    private fun fixture(): ByteArray = Bitmap.createBitmap(16,16,Bitmap.Config.ARGB_8888).let { bitmap ->
        try { ByteArrayOutputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it);it.toByteArray() } }
        finally { bitmap.recycle() }
    }
    private fun runTest(block:(HiddenRepository,HiddenSession,java.io.File,android.content.Context)->Unit) {
        val root=File(base.noBackupFilesDir,"hidden-repository-test-${UUID.randomUUID()}")
        val context=object:ContextWrapper(base){override fun getNoBackupFilesDir()=root}
        val session=HiddenSession(android.os.SystemClock::elapsedRealtime)
        session.complete(session.beginAuthentication(),ByteArray(32){7})
        try {block(HiddenRepository(context,session),session,root,context)} finally {session.lock();root.deleteRecursively()}
    }
    @Test fun photosThumbnailsCollectionsAndFavouritesPersistAfterNewRepository() = runTest { repo,session,root,context ->
        val bytes=fixture()
        val item=repo.importMedia("synthetic-private-filename.png","image/png",ByteArrayInputStream(bytes))
        val collection=repo.createCollection("Hidden collection")
        repo.addItemsToCollection(collection.id,listOf(item.id));repo.setFavouriteCollection(collection.id)
        val reopened=HiddenRepository(context,session)
        assertEquals(item.id,reopened.items().single().id)
        assertEquals(collection.id,reopened.favouriteCollection()?.id)
        assertEquals(item.id,reopened.itemsInCollection(collection.id).single().id)
        assertArrayEquals(bytes,reopened.read(item.id))
        reopened.preview(item.id).recycle()
        root.walkTopDown().filter {it.isFile}.forEach {
            assertFalse(it.readBytes().toString(Charsets.ISO_8859_1).contains("synthetic-private-filename"))
            assertFalse(it.readBytes().toString(Charsets.ISO_8859_1).contains("Hidden collection"))
            assertFalse(it.readBytes().take(8).toByteArray().contentEquals(bytes.take(8).toByteArray()))
        }
        assertTrue(File(root,"hidden-media-v1").listFiles()!!.all {it.extension=="enc"})
    }
    @Test fun lockRejectsReadsWritesAndOldPrimaryCannotDecrypt() = runTest {repo,session,_,context ->
        val item=repo.importMedia("test.png","image/png",ByteArrayInputStream(fixture()))
        session.lock()
        assertTrue(runCatching {repo.read(item.id)}.isFailure)
        assertTrue(runCatching {repo.createCollection("rejected")}.isFailure)
        session.complete(session.beginAuthentication(),ByteArray(32){9})
        assertTrue(runCatching {HiddenRepository(context,session).items()}.isFailure)
    }
    @Test fun corruptMetadataFailsClosedAndDoesNotReplaceRecords() = runTest {repo,_,root,_ ->
        repo.importMedia("test.png","image/png",ByteArrayInputStream(fixture()))
        val metadata=File(root,"hidden-media-v1/metadata-00000000-0000-0000-0000-000000000000.enc")
        val corrupt=metadata.readBytes().also {it[it.lastIndex]=(it.last().toInt() xor 1).toByte()};metadata.writeBytes(corrupt)
        assertTrue(runCatching {repo.items()}.isFailure)
        assertTrue(runCatching {repo.createCollection("must not overwrite")}.isFailure)
        assertArrayEquals(corrupt,metadata.readBytes())
    }
    @Test fun failedSourceAndDuplicateKeepOriginalAndCommittedMetadata() = runTest {repo,_,_,_ ->
        val bytes=fixture();val first=repo.importMedia("test.png","image/png",ByteArrayInputStream(bytes))
        val second=repo.importMedia("duplicate.png","image/png",ByteArrayInputStream(bytes))
        assertEquals(first.id,second.id)
        val failed=object:java.io.InputStream(){override fun read():Int=throw java.io.IOException("Synthetic interruption")}
        assertTrue(runCatching {repo.importMedia("failure.png","image/png",failed)}.isFailure)
        assertEquals(1,repo.items().size);assertArrayEquals(bytes,repo.read(first.id))
    }
    @Test fun trashRestoreAndDeleteStayInsideHidden() = runTest {repo,_,_,_ ->
        val primary=File(base.filesDir,"vault");val before=primary.listFiles()?.map {it.name}?.sorted()
        val item=repo.importMedia("test.png","image/png",ByteArrayInputStream(fixture()))
        repo.trash(listOf(item.id));assertTrue(repo.items().isEmpty());assertEquals(1,repo.recentlyDeleted().size)
        repo.restore(item.id);assertEquals(1,repo.items().size)
        repo.trash(listOf(item.id));repo.deletePermanently(item.id);assertTrue(repo.recentlyDeleted().isEmpty())
        assertEquals(before,primary.listFiles()?.map {it.name}?.sorted())
    }
    @Test fun syntheticVideoPersistsAndThumbnailAuthenticates() = runTest {repo,_,_,_ ->
        val bytes=InstrumentationRegistry.getInstrumentation().context.assets.open("browser-media/valid.mp4").use {it.readBytes()}
        val item=repo.importMedia("synthetic.mp4","video/mp4",ByteArrayInputStream(bytes))
        assertArrayEquals(bytes,repo.read(item.id));repo.preview(item.id).recycle()
    }
    @Test fun repositoryFromPreviousUnlockCannotInheritNewSessionAuthority() = runTest {repo,session,_,context ->
        val item=repo.importMedia("test.png","image/png",ByteArrayInputStream(fixture()))
        session.lock()
        session.complete(session.beginAuthentication(),ByteArray(32){7})
        assertTrue(runCatching {repo.read(item.id)}.isFailure)
        assertTrue(runCatching {repo.createCollection("queued old operation")}.isFailure)
        assertArrayEquals(fixture(),HiddenRepository(context,session).read(item.id))
    }
    @Test fun interruptedPromotedOrphanRecoversWithoutLosingCommittedMedia() = runTest {repo,session,root,context ->
        val item=repo.importMedia("test.png","image/png",ByteArrayInputStream(fixture()))
        val records=HiddenRecordStore(File(root,"hidden-media-v1")) {}
        val orphan=UUID.randomUUID().toString()
        records.promote(records.stage("media",orphan,fixture(),ByteArray(32){7}),"media",orphan)
        val reopened=HiddenRepository(context,session)
        reopened.reconcile()
        assertFalse(records.file("media",orphan).exists())
        assertEquals(item.id,reopened.items().single().id)
        assertArrayEquals(fixture(),reopened.read(item.id))
    }
    @Test fun collectionLimitRejectsMutationWithoutMakingExistingVaultUnreadable() = runTest {repo,session,root,context ->
        val json=org.json.JSONObject().put("domain","hidden").put("version",1)
            .put("items",org.json.JSONArray()).put("memberships",org.json.JSONArray()).put("favourite","")
            .put("collections",org.json.JSONArray((0 until 1000).map {n ->org.json.JSONObject().put("id",UUID.randomUUID().toString()).put("name","Fixture $n").put("time",1)}))
        val records=HiddenRecordStore(File(root,"hidden-media-v1")) {}
        val id="00000000-0000-0000-0000-000000000000"
        records.promote(records.stage("metadata",id,json.toString().toByteArray(),ByteArray(32){7}),"metadata",id)
        val before=records.file("metadata",id).readBytes()
        assertTrue(runCatching {repo.createCollection("one too many")}.isFailure)
        assertArrayEquals(before,records.file("metadata",id).readBytes())
        assertEquals(1000,HiddenRepository(context,session).collections().size)
    }
    @Test fun corruptionOfThumbnailDoesNotDeleteAuthenticatedMedia() = runTest {repo,_,root,_ ->
        val item=repo.importMedia("test.png","image/png",ByteArrayInputStream(fixture()))
        val thumbnail=File(root,"hidden-media-v1/thumbnail-${item.id}.enc")
        val corrupt=thumbnail.readBytes().also {it[it.lastIndex]=(it.last().toInt() xor 1).toByte()};thumbnail.writeBytes(corrupt)
        assertTrue(runCatching {repo.preview(item.id)}.isFailure)
        assertArrayEquals(fixture(),repo.read(item.id))
        assertArrayEquals(corrupt,thumbnail.readBytes())
    }
    @Test fun lockDuringSourceReadCannotCommitImportedMedia() = runTest {repo,session,_,context ->
        val bytes=fixture()
        val source=object:ByteArrayInputStream(bytes) {
            override fun read(buffer:ByteArray,offset:Int,length:Int):Int {
                val count=super.read(buffer,offset,length)
                session.lock()
                return count
            }
        }
        assertTrue(runCatching {repo.importMedia("interrupted.png","image/png",source)}.isFailure)
        session.complete(session.beginAuthentication(),ByteArray(32){7})
        val reopened=HiddenRepository(context,session)
        reopened.reconcile();assertTrue(reopened.items().isEmpty())
    }

}
