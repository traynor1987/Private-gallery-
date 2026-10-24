package uk.co.traynor.privategallery.core.hidden

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import androidx.test.platform.app.InstrumentationRegistry
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test
import uk.co.traynor.privategallery.core.security.PinVaultKeyStore
import uk.co.traynor.privategallery.core.vault.*

class PrimaryCopyAccessTest {
    private val base = InstrumentationRegistry.getInstrumentation().targetContext
    private fun photo(): ByteArray = Bitmap.createBitmap(24,24,Bitmap.Config.ARGB_8888).let {
        try { ByteArrayOutputStream().use { out -> it.compress(Bitmap.CompressFormat.PNG,100,out); out.toByteArray() } }
        finally { it.recycle() }
    }
    private fun fixture(block: (Context, HiddenSession, AndroidVaultRepository, List<VaultItem>) -> Unit) {
        val id = UUID.randomUUID().toString()
        val root = File(base.noBackupFilesDir,"copy-test-$id").apply { mkdirs() }
        val preferences = mutableSetOf<String>()
        val ctx = object: ContextWrapper(base) {
            override fun getFilesDir() = File(root,"primary").apply { mkdirs() }
            override fun getNoBackupFilesDir() = File(root,"hidden").apply { mkdirs() }
            override fun getPackageName() = "${base.packageName}.copy.$id"
            override fun getSharedPreferences(name: String, mode: Int): android.content.SharedPreferences {
                val scoped = "copy-$id-$name"; preferences += scoped
                return base.getSharedPreferences(scoped,mode)
            }
        }
        val primaryKey = PinVaultKeyStore(ctx).create("123456".toCharArray())
        val session = HiddenSession(android.os.SystemClock::elapsedRealtime)
        session.complete(session.beginAuthentication(),ByteArray(32){7})
        try {
            val primary = AndroidVaultRepository(ctx,primaryKey)
            val video = InstrumentationRegistry.getInstrumentation().context.assets.open("browser-media/valid.mp4").use { it.readBytes() }
            val inputs = listOf(Triple("photo.png","image/png",photo()), Triple("video.mp4","video/mp4",video))
            val items = inputs.map { (name,mime,bytes) ->
                try { (primary.importVerified(VaultImportSource(name,mime,{ByteArrayInputStream(bytes)})) as ImportResult.Imported).item }
                finally { bytes.fill(0) }
            }
            val collection = primary.createCollection("Primary only")
            primary.addItemsToCollection(collection.id,items.map { it.id });primary.setFavouriteCollection(collection.id)
            block(ctx,session,primary,items)
        } finally {
            session.lock();primaryKey.fill(0)
            preferences.forEach { base.getSharedPreferences(it,Context.MODE_PRIVATE).edit().clear().commit() }
            root.deleteRecursively()
        }
    }
    @Test fun copyAndHiddenEditKeepCaptureDateAndAssignDestinationAddition() = fixture { ctx, hidden, primary, _ ->
        val bytes=photo()
        try {
            val source=(primary.importVerified(VaultImportSource("old-capture.png","image/png",{ByteArrayInputStream(bytes)},createDistinctCopy=true,capturedAtEpochMillis=1000L)) as ImportResult.Imported).item
            PrimaryCopyAccess(ctx,hidden).use {access ->
                access.authenticate("123456".toCharArray())
                assertEquals(1,access.transfer(listOf(source.id),false,{false}) {_,_->}.verified)
            }
            val repository=HiddenRepository(ctx,hidden)
            val copied=repository.items().single {it.displayName=="old-capture.png"}
            assertEquals(1000L,copied.capturedAtEpochMillis)
            assertTrue(copied.addedAtEpochMillis!!>1000L)
            val edited=repository.importEditedCopy(copied.id,bytes,null)
            assertEquals(1000L,edited.capturedAtEpochMillis)
            repository.withTransferSource(copied.id,{false}) {metadata,input ->
                assertEquals(1000L,metadata.capturedAtEpochMillis)
                while(input.read(ByteArray(4096))>=0) { }
            }
        } finally {bytes.fill(0)}
    }
    @Test fun copiesMoreThanOneHundredRealPhotosWithoutLosingOriginals() = fixture { ctx, hidden, primary, _ ->
        val bytes = photo()
        val ids = (0 until 120).map { n ->
            val distinct = bytes + byteArrayOf(n.toByte())
            (primary.importVerified(VaultImportSource("batch-$n.png", "image/png", { ByteArrayInputStream(distinct) })) as ImportResult.Imported).item.id
        }
        val originalIds = primary.items().map { it.id }.toSet()
        PrimaryCopyAccess(ctx, hidden).use { access ->
            access.authenticate("123456".toCharArray())
            var completed = 0
            val result = access.transfer(ids, false, { false }) { done, total ->
                assertEquals(120, total); assertTrue(done >= completed); completed = done
            }
            assertEquals(120, completed)
            assertEquals(120, result.verified)
            assertEquals(0, result.failed)
            assertEquals(0, result.moved)
            assertFalse(result.interrupted)
            assertEquals(originalIds, primary.items().map { it.id }.toSet())
            assertEquals(120, HiddenRepository(ctx, hidden).items().size)
        }
        bytes.fill(0)
    }
    @Test fun hundredsOfSelectionsReportActualProgressAndSafeCancellation() = fixture { ctx, hidden, primary, items ->
        // Missing IDs are disposable failures; 250 selections reproduce the old 100-item rejection.
        val ids = listOf(items.first().id) + (1..249).map { "missing-$it" }
        var completed = 0
        var cancel = false
        PrimaryCopyAccess(ctx, hidden).use { access ->
            access.authenticate("123456".toCharArray())
            val result = access.transfer(ids, true, { cancel }) { done, total ->
                assertEquals(250, total)
                assertTrue(done >= completed)
                completed = done
                if (done == 120) cancel = true
            }
            assertEquals(120, completed)
            assertEquals(1, result.verified)
            assertEquals(1, result.moved)
            assertEquals(119, result.failed)
            assertTrue(result.interrupted)
            assertEquals(130, result.total - result.verified - result.failed)
            assertTrue(primary.items().any { it.id == items.last().id })
            assertTrue(primary.items().none { it.id == items.first().id })
            assertEquals(1, HiddenRepository(ctx, hidden).items().size)
        }
    }
    private fun hashes(root: File) = root.walkTopDown().filter { it.isFile }.associate {
        it.relativeTo(root).path to MessageDigest.getInstance("SHA-256").digest(it.readBytes()).toList()
    }
    @Test fun phoneCopyAndMoveReceiptsRequireVerifiedDestinationAndUnchangedSource() = fixture {ctx,hidden,primary,_ ->
        val resolver=ctx.contentResolver
        val uri=checkNotNull(resolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,android.content.ContentValues().apply {
            put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME,"disposable-transfer-${UUID.randomUUID()}.png")
            put(android.provider.MediaStore.MediaColumns.MIME_TYPE,"image/png")
        }))
        val bytes=photo()
        try {
            resolver.openOutputStream(uri,"w")!!.use {it.write(bytes)}
            val key=PinVaultKeyStore(ctx).unlock("123456".toCharArray())
            try {
                for(destination in listOf<HiddenSession?>(null,hidden)) {
                    var authorized=true
                    val transfer=PhoneVaultTransfer(ctx,if(destination==null)key else null,destination) {authorized}
                    val copied=transfer.copy(uri)
                    assertEquals(uri,transfer.deletionSource(uri,copied))
                    assertArrayEquals(bytes,resolver.openInputStream(uri)!!.use {it.readBytes()}) // Copy never deletes.
                    assertEquals(copied.id,transfer.copy(uri).id) // Duplicate copy remains verified.
                    val unverified=PhoneVaultTransfer(ctx,if(destination==null)key else null,destination) {true}
                    assertTrue(runCatching {unverified.deletionSource(uri,copied)}.isFailure)
                    authorized=false
                    assertTrue(runCatching {transfer.deletionSource(uri,copied)}.isFailure)
                }
                assertTrue(runCatching {PhoneVaultTransfer(ctx,null,null) {true}}.isFailure)
                // A distinct payload exercises publication instead of the duplicate path.
                val distinct=bytes+byteArrayOf(0)
                resolver.openOutputStream(uri,"wt")!!.use {it.write(distinct)}
                val before=primary.items().map {it.id}.toSet()
                var permitted=true
                val denied=PhoneVaultTransfer(ctx,key,null,publish={ commit -> permitted=false;check(permitted);commit() }) {permitted}
                assertTrue(runCatching {denied.copy(uri)}.isFailure)
                assertEquals(before,primary.items().map {it.id}.toSet())
                assertArrayEquals(distinct,resolver.openInputStream(uri)!!.use {it.readBytes()})
                resolver.openOutputStream(uri,"wt")!!.use {it.write(bytes)}
                val transfer=PhoneVaultTransfer(ctx,key,null) {true}
                val copied=transfer.copy(uri)
                resolver.openOutputStream(uri,"wt")!!.use {it.write(byteArrayOf(1,2,3))}
                assertTrue(runCatching {transfer.deletionSource(uri,copied)}.isFailure)
                assertTrue(primary.items().any {it.id==copied.id})
            } finally {key.fill(0)}
        } finally {bytes.fill(0);resolver.delete(uri,null,null)} // Only disposable test media.
    }

    @Test fun moveVerifiesHiddenBeforeRecoverablePrimaryRemovalAndRestart() = fixture {ctx,hidden,primary,items ->
        PrimaryCopyAccess(ctx,hidden).use {access ->
            access.authenticate("123456".toCharArray())
            val result=access.transfer(items.map {it.id}+items.first().id,true) {_,_->}
            assertEquals(2,result.verified);assertEquals(2,result.moved);assertEquals(0,result.failed)
        }
        assertTrue(primary.items().isEmpty());assertEquals(2,primary.recentlyDeleted().size)
        val target=HiddenRepository(ctx,hidden);target.items().forEach {target.verify(it.id)}
        assertEquals(2,target.items().size);assertTrue(target.collections().isEmpty());assertNull(target.favouriteCollection())
        primary.recentlyDeleted().forEach {primary.restoreRecentlyDeleted(it.id)}
        assertEquals(items.map {it.id}.toSet(),primary.items().map {it.id}.toSet())
        assertEquals(2,primary.itemsInCollection(primary.favouriteCollection()!!.id).size)
    }
    @Test fun moveStorageFailureNeverRemovesOriginal() = fixture {ctx,hidden,primary,items ->
        PrimaryCopyAccess(ctx,hidden) {c,h,guard ->HiddenRepository(c,h,directorySync={throw IOException("ENOSPC")},publishIfAllowed=guard)}.use {access ->
            access.authenticate("123456".toCharArray())
            val result=access.transfer(items.map {it.id},true) {_,_->}
            assertEquals(0,result.moved);assertEquals(0,result.verified);assertEquals(2,result.failed)
        }
        assertEquals(2,primary.items().size);assertTrue(primary.recentlyDeleted().isEmpty())
    }
    @Test fun interruptedMoveDoesNotResumeSourceRemoval() = fixture {ctx,hidden,primary,items ->
        PrimaryCopyAccess(ctx,hidden).use {access ->
            access.authenticate("123456".toCharArray())
            assertTrue(runCatching {access.transfer(items.map {it.id},true) {done,_->if(done==1)access.close()}}.isFailure)
        }
        assertEquals(1,primary.items().size);assertEquals(1,primary.recentlyDeleted().size)
        HiddenRepository(ctx,hidden).reconcile();primary.reconcile()
        assertEquals(1,primary.items().size);assertEquals(1,HiddenRepository(ctx,hidden).items().size)
    }
    @Test fun changedSourceVersionCannotAuthorizeRemoval() = fixture {_,_,primary,items ->
        val item=items.first()
        assertTrue(runCatching {primary.trashVerifiedSource(item.copy(plaintextSha256=ByteArray(32)))}.isFailure)
        assertEquals(2,primary.items().size);assertTrue(primary.recentlyDeleted().isEmpty())
    }

    @Test fun freshPinMultiPhotoVideoDuplicateAndRestartPreservePrimaryExactly() = fixture { ctx,hidden,primary,items ->
        val before = hashes(ctx.filesDir)
        PrimaryCopyAccess(ctx,hidden).use { access ->
            assertTrue(runCatching {access.items()}.isFailure)
            assertTrue(runCatching {access.authenticate("999999".toCharArray())}.isFailure)
            assertTrue(runCatching {access.items()}.isFailure)
            access.authenticate("123456".toCharArray())
            assertEquals(items.map {it.id}.toSet(),access.items().map {it.id}.toSet())
            access.preview(items[0].id).recycle();access.preview(items[1].id).recycle()
            val result = access.copy(items.map {it.id} + items.first().id) {_,_->}
            assertEquals(2,result.verified);assertEquals(0,result.failed);assertEquals(2,result.total)
            assertEquals(2,access.copy(items.map {it.id}) {_,_->}.verified)
        }
        hidden.lock()
        val restarted = HiddenSession(android.os.SystemClock::elapsedRealtime)
        restarted.complete(restarted.beginAuthentication(),ByteArray(32){7})
        try {
            val target = HiddenRepository(ctx,restarted);target.reconcile()
            assertEquals(2,target.items().size);assertTrue(target.collections().isEmpty());assertNull(target.favouriteCollection())
            target.items().forEach { copied ->
                assertFalse(items.any {it.id==copied.id})
                val source=items.single {it.displayName==copied.displayName}
                val bytes=primary.readForViewing(source);val actual=target.read(copied.id)
                try {assertArrayEquals(bytes,actual)} finally {bytes.fill(0);actual.fill(0)}
                target.preview(copied.id).recycle()
            }
            assertEquals(before,hashes(ctx.filesDir))
            assertTrue(File(ctx.noBackupFilesDir,"hidden-media-v1").listFiles()!!.all {it.extension=="enc"})
        } finally {restarted.lock()}
    }
    @Test fun sourceDigestMismatchIsRejectedBeforeHiddenPublication() = fixture {ctx,hidden,_,items ->
        val key=PinVaultKeyStore(ctx).unlock("123456".toCharArray())
        try {
            val index=EncryptedIndexStore(File(ctx.filesDir,"vault"))
            val snapshot=index.loadSnapshot(key)
            index.saveSnapshot(snapshot.copy(items=snapshot.items.map {if(it.id==items.first().id)it.copy(plaintextSha256=ByteArray(32)) else it}),key)
        } finally {key.fill(0)}
        val before=hashes(ctx.filesDir)
        PrimaryCopyAccess(ctx,hidden).use {access ->
            access.authenticate("123456".toCharArray())
            val result=access.copy(listOf(items.first().id)) {_,_->}
            assertEquals(0,result.verified);assertEquals(1,result.failed)
        }
        assertTrue(HiddenRepository(ctx,hidden).items().isEmpty());assertEquals(before,hashes(ctx.filesDir))
    }
    @Test fun hiddenRevokedBeforeMetadataPublicationLeavesOnlyRecoverableCiphertext() = fixture {ctx,hidden,_,items ->
        var publications=0
        PrimaryCopyAccess(ctx,hidden) {c,s,guard ->HiddenRepository(c,s,publishIfAllowed={publish ->
            publications++
            if(publications==3)hidden.lock()
            guard(publish)
        })}.use {access ->
            access.authenticate("123456".toCharArray())
            assertTrue(runCatching {access.copy(listOf(items.first().id)) {_,_->}}.isFailure)
        }
        hidden.complete(hidden.beginAuthentication(),ByteArray(32){7})
        val target=HiddenRepository(ctx,hidden);target.reconcile();assertTrue(target.items().isEmpty())
    }
    @Test fun primaryRevocationBetweenItemsStopsBatchAndPreservesCompletedCopy() = fixture {ctx,hidden,_,items ->
        val before=hashes(ctx.filesDir)
        val access=PrimaryCopyAccess(ctx,hidden)
        access.authenticate("123456".toCharArray())
        assertTrue(runCatching {access.copy(items.map {it.id}) {done,_->if(done==1)access.close()}}.isFailure)
        assertEquals(1,HiddenRepository(ctx,hidden).items().size)
        assertEquals(before,hashes(ctx.filesDir));assertFalse(access.isActive())
    }
    @Test fun hiddenLockStopsBatchAndOldSourceCannotInheritNewUnlock() = fixture {ctx,hidden,_,items ->
        PrimaryCopyAccess(ctx,hidden).use {access ->
            access.authenticate("123456".toCharArray())
            assertTrue(runCatching {access.copy(items.map {it.id}) {done,_->if(done==1)hidden.lock()}}.isFailure)
            hidden.complete(hidden.beginAuthentication(),ByteArray(32){7})
            assertFalse(access.isActive());assertTrue(runCatching {access.items()}.isFailure)
            val reopened=HiddenRepository(ctx,hidden);reopened.reconcile();assertEquals(1,reopened.items().size)
        }
    }
    @Test fun backgroundRevokesTransferEvenWithinHiddenGrace() = fixture {ctx,hidden,_,items ->
        PrimaryCopyAccess(ctx,hidden).use {access ->
            access.authenticate("123456".toCharArray())
            hidden.onBackground(300_000);hidden.onForeground()
            assertTrue(hidden.isUnlocked);assertFalse(access.isActive())
            assertTrue(runCatching {access.copy(items.map {it.id}) {_,_->}}.isFailure)
        }
    }
    @Test fun storageExhaustionFailsWithoutSuccessOrPrimaryMutation() = fixture {ctx,hidden,_,items ->
        val before=hashes(ctx.filesDir)
        PrimaryCopyAccess(ctx,hidden) {c,s,guard ->
            HiddenRepository(c,s,directorySync={throw IOException("ENOSPC: injected full storage")},publishIfAllowed=guard)
        }.use {access ->
            access.authenticate("123456".toCharArray())
            val result=access.copy(items.map {it.id}) {_,_->}
            assertEquals(0,result.verified);assertEquals(2,result.failed)
        }
        val reopened=HiddenRepository(ctx,hidden);reopened.reconcile()
        assertTrue(reopened.items().isEmpty());assertEquals(before,hashes(ctx.filesDir))
    }
    @Test fun primaryRevokedImmediatelyBeforePublicationCannotPublishItem() = fixture {ctx,hidden,_,items ->
        lateinit var access: PrimaryCopyAccess
        var publications=0
        access=PrimaryCopyAccess(ctx,hidden) {c,s,guard -> HiddenRepository(c,s,publishIfAllowed={publish ->
            publications++
            if(publications==2)access.close() // After empty index, before payload promotion.
            guard(publish)
        })}
        access.use {
            it.authenticate("123456".toCharArray())
            assertTrue(runCatching {it.copy(listOf(items.first().id)) {_,_->}}.isFailure)
        }
        val target=HiddenRepository(ctx,hidden);target.reconcile();assertTrue(target.items().isEmpty())
        assertTrue(File(ctx.noBackupFilesDir,"hidden-media-v1").listFiles()!!.none {it.name.startsWith("media-")})
    }
    @Test fun interruptedPromotionIsReconciledAfterNewSession() = fixture {ctx,hidden,_,items ->
        var syncs=0
        PrimaryCopyAccess(ctx,hidden) {c,s,guard -> HiddenRepository(c,s,directorySync={directory ->
            if(directory.name=="hidden-media-v1")syncs++
            if(syncs>=2){hidden.lock();throw IOException("Synthetic process interruption after promotion")}
        },publishIfAllowed=guard)}.use {access ->
            access.authenticate("123456".toCharArray())
            assertTrue(runCatching {access.copy(listOf(items.first().id)) {_,_->}}.isFailure)
        }
        assertEquals(3,File(ctx.noBackupFilesDir,"hidden-media-v1").listFiles()!!.count {it.extension=="enc"})
        val fresh=HiddenSession(android.os.SystemClock::elapsedRealtime)
        fresh.complete(fresh.beginAuthentication(),ByteArray(32){7})
        try {
            val target=HiddenRepository(ctx,fresh);target.reconcile();assertTrue(target.items().isEmpty())
            assertEquals(1,File(ctx.noBackupFilesDir,"hidden-media-v1").listFiles()!!.size)
        } finally {fresh.lock()}
    }
}
