@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
package uk.co.traynor.privategallery.core.hidden

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.provider.MediaStore
import androidx.test.platform.app.InstrumentationRegistry
import java.io.*
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test
import uk.co.traynor.privategallery.core.security.PinVaultKeyStore
import uk.co.traynor.privategallery.core.vault.*

class HiddenRestoreAccessTest {
    private val base=InstrumentationRegistry.getInstrumentation().targetContext
    private fun fixture(block:(Context,HiddenSession,HiddenRepository,AndroidVaultRepository,VaultItem)->Unit) {
        val id=UUID.randomUUID().toString();val root=File(base.noBackupFilesDir,"restore-test-$id").apply {mkdirs()}
        val prefs=mutableSetOf<String>()
        val ctx=object:ContextWrapper(base) {
            override fun getFilesDir()=File(root,"primary").apply {mkdirs()}
            override fun getNoBackupFilesDir()=File(root,"hidden").apply {mkdirs()}
            override fun getSharedPreferences(name:String,mode:Int):android.content.SharedPreferences {
                val scoped="restore-$id-$name";prefs+=scoped;return base.getSharedPreferences(scoped,mode)
            }
        }
        val key=PinVaultKeyStore(ctx).create("123456".toCharArray())
        val session=HiddenSession(android.os.SystemClock::elapsedRealtime)
        session.complete(session.beginAuthentication(),ByteArray(32){7})
        val bitmap=Bitmap.createBitmap(24,24,Bitmap.Config.ARGB_8888)
        val bytes=try {ByteArrayOutputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it);it.toByteArray()}}finally{bitmap.recycle()}
        try {
            val hidden=HiddenRepository(ctx,session)
            val item=hidden.importMedia("synthetic-$id.png","image/png",ByteArrayInputStream(bytes))
            block(ctx,session,hidden,AndroidVaultRepository(ctx,key),item)
        } finally {bytes.fill(0);session.lock();key.fill(0);prefs.forEach {base.getSharedPreferences(it,0).edit().clear().commit()};val removed=root.deleteRecursively();android.util.Log.i("PGStorage1751","test=restore stage=cleanup deleted=$removed rootExists=${root.exists()} usable=${base.noBackupFilesDir.usableSpace} total=${base.noBackupFilesDir.totalSpace}")}
    }
    private fun access(ctx:Context,s:HiddenSession)=HiddenRestoreAccess(ctx,s).apply {authenticate("123456".toCharArray())}
    @Test fun primaryCopyReencryptsAndRetainsHiddenAcrossRepositoryRestart()=fixture {ctx,s,h,p,item ->
        access(ctx,s).use {a ->assertEquals(1,a.transfer(listOf(item.id),HiddenRestoreAccess.Destination.PRIMARY,false){_,_->}.verified)}
        assertEquals(1,h.items().size);val copy=p.items().single()
        assertNotEquals(item.id,copy.id);assertArrayEquals(item.plaintextSha256,copy.plaintextSha256)
        assertEquals(item.id,HiddenRepository(ctx,s).items().single().id)
        val buffer=ByteArray(1024);p.withHiddenCopySource(copy.id,{false}){_,input ->while(input.read(buffer)>=0)Unit};buffer.fill(0)
        assertTrue(s.isUnlocked);assertTrue(p.collections().isEmpty())
        PrimaryCopyAccess(ctx,s).use {fresh ->
            fresh.authenticate("123456".toCharArray());val op=fresh.history().single()
            val payload=File(ctx.filesDir,"vault/payloads/${copy.id}.vault")
            for(missing in listOf(false,true)) {
                if(missing)assertTrue(payload.delete()) else RandomAccessFile(payload,"rw").use {it.setLength(it.length()-1)}
                val rejected=fresh.resume(op.id,{false}){_,_->}
                assertEquals(0,rejected.verified);assertEquals(1,rejected.failed);assertEquals(0,rejected.moved)
                val receipt=fresh.history().single()
                assertEquals("PARTIAL",receipt.status);assertEquals("FAILED",receipt.items.single().phase)
                assertEquals(copy.id,receipt.items.single().destination)
                h.verify(item.id);assertTrue(h.recentlyDeleted().isEmpty())
            }
        }
    }
    @Test fun transferredEligibleItemsAreImmediatelyVisibleToFreshPrimaryPickerQuery()=fixture {ctx,s,h,p,item ->
        assertTrue(p.items().isEmpty())
        access(ctx,s).use {a -> assertEquals(1,a.transfer(listOf(item.id),HiddenRestoreAccess.Destination.PRIMARY,false){_,_->}.verified) }
        val freshKey=PinVaultKeyStore(ctx).unlock("123456".toCharArray())
        try {
            val visible=AndroidVaultRepository(ctx,freshKey).items().filter {
                it.state==VaultItemState.COMPLETE && !it.vaultOnly &&
                    uk.co.traynor.privategallery.core.browser.v2.BrowserUploadPolicy.accepts(it.mimeType,arrayOf(".png"))
            }
            assertEquals(1,visible.size)
            assertEquals(item.displayName,visible.single().displayName)
            assertNotEquals(item.id,visible.single().id)
            assertTrue(h.items().any {it.id==item.id})
        } finally {freshKey.fill(0)}
    }
    @Test fun primaryMoveOnlyTrashesVerifiedSourceAndCanBeRecovered()=fixture {ctx,s,h,p,item ->
        access(ctx,s).use {a ->val r=a.transfer(listOf(item.id),HiddenRestoreAccess.Destination.PRIMARY,true){_,_->};assertEquals(1,r.verified);assertEquals(1,r.moved)}
        assertTrue(h.items().isEmpty());assertEquals(item.id,h.recentlyDeleted().single().id);assertEquals(1,p.items().size)
        h.restore(item.id);h.verify(item.id)
    }
    @Test fun missingAndWrongPrimaryAuthenticationNeverPublishesOrDeletes()=fixture {ctx,s,h,p,item ->
        HiddenRestoreAccess(ctx,s).use {a ->
            assertTrue(runCatching {a.transfer(listOf(item.id),HiddenRestoreAccess.Destination.PRIMARY,true){_,_->}}.isFailure)
            assertTrue(runCatching {a.authenticate("999999".toCharArray())}.isFailure)
            assertTrue(runCatching {a.transfer(listOf(item.id),HiddenRestoreAccess.Destination.PRIMARY,true){_,_->}}.isFailure)
        }
        assertTrue(p.items().isEmpty());assertEquals(item.id,h.items().single().id)
    }
    @Test fun cancellationAndRevocationPreserveSources()=fixture {ctx,s,h,p,item ->
        access(ctx,s).use {a ->a.close();assertTrue(runCatching {a.transfer(listOf(item.id),HiddenRestoreAccess.Destination.PRIMARY,true){_,_->}}.isFailure)}
        access(ctx,s).use {a ->a.transfer(listOf(item.id),HiddenRestoreAccess.Destination.PRIMARY,true){done,_->if(done==0)s.revokeOperations()}}
        assertTrue(p.items().isEmpty());assertEquals(item.id,HiddenRepository(ctx,s).items().single().id)
    }
    @Test fun restrictionsSurvivePrimaryTransferAndPreventPhonePublication()=fixture {ctx,s,h,p,item ->
        val bytes=h.read(item.id)
        val restricted=try {h.importMedia("edited.png","image/png",ByteArrayInputStream(bytes),true,MediaOrigin.LOCAL_EDIT,vaultOnly=true)}finally{bytes.fill(0)}
        access(ctx,s).use {a ->
            val failed=a.transfer(listOf(restricted.id),HiddenRestoreAccess.Destination.PHONE,true){_,_->};assertEquals(0,failed.verified);assertEquals(0,failed.moved)
            assertEquals(1,a.transfer(listOf(restricted.id),HiddenRestoreAccess.Destination.PRIMARY,false){_,_->}.verified)
        }
        assertTrue(p.items().single().vaultOnly);assertEquals(MediaOrigin.LOCAL_EDIT,p.items().single().origin);assertEquals(2,h.items().size)
    }
    @Test fun repeatedSelectionsNeverRemoveUnverifiedOrUnselectedSources()=fixture {ctx,s,h,p,item ->
        access(ctx,s).use {a ->
            val first=a.transfer(listOf(item.id,item.id,"missing"),HiddenRestoreAccess.Destination.PRIMARY,false){_,_->}
            assertEquals(2,first.total);assertEquals(1,first.verified);assertEquals(1,first.failed)
            assertEquals(1,a.transfer(listOf(item.id),HiddenRestoreAccess.Destination.PRIMARY,false){_,_->}.verified)
        }
        assertEquals(2,p.items().size);assertEquals(1,h.items().size)
    }
    @Test fun primaryRestrictedRoundTripCannotLaunderPhoneExportPermission()=fixture {ctx,s,h,p,item ->
        val bytes=h.read(item.id)
        val restricted=try {(p.importVerified(VaultImportSource("private-edit.png","image/png",{ByteArrayInputStream(bytes)},origin=MediaOrigin.REMOTE_AI_EDIT,vaultOnly=true)) as ImportResult.Imported).item}finally{bytes.fill(0)}
        PrimaryCopyAccess(ctx,s).use {a ->a.authenticate("123456".toCharArray());assertEquals(1,a.copy(listOf(restricted.id)){_,_->}.verified)}
        val copy=h.items().single {it.id!=item.id};assertTrue(copy.vaultOnly);assertEquals(MediaOrigin.REMOTE_AI_EDIT,copy.origin)
        HiddenRestoreAccess(ctx,s).use {a ->assertEquals(0,a.transfer(listOf(copy.id),HiddenRestoreAccess.Destination.PHONE,true){_,_->}.verified)}
        assertEquals(2,h.items().size)
    }
    @Test fun oppositeDirectionMovesCompleteWithoutLockOrderDeadlock()=fixture {ctx,s,h,p,item ->
        val bytes=h.read(item.id)+byteArrayOf(1)
        val primary=try {(p.importVerified(VaultImportSource("other.png","image/png",{ByteArrayInputStream(bytes)})) as ImportResult.Imported).item}finally{bytes.fill(0)}
        val start=java.util.concurrent.CountDownLatch(1)
        val pool=java.util.concurrent.Executors.newFixedThreadPool(2){r ->Thread(r,"synthetic-transfer").apply {isDaemon=true}}
        access(ctx,s).use {outbound ->PrimaryCopyAccess(ctx,s).use {inbound ->
            inbound.authenticate("123456".toCharArray())
            try {
                val out=pool.submit<HiddenRestoreAccess.Result>{start.await();outbound.transfer(listOf(item.id),HiddenRestoreAccess.Destination.PRIMARY,true){_,_->}}
                val into=pool.submit<PrimaryCopyAccess.Result>{start.await();inbound.transfer(listOf(primary.id),true){_,_->}}
                start.countDown()
                assertEquals(1,out.get(30,java.util.concurrent.TimeUnit.SECONDS).moved)
                assertEquals(1,into.get(30,java.util.concurrent.TimeUnit.SECONDS).moved)
            } finally {pool.shutdownNow()}
        }}
        assertEquals(1,h.items().size);assertEquals(1,p.items().size);assertEquals(item.id,h.recentlyDeleted().single().id);assertEquals(primary.id,p.recentlyDeleted().single().id)
    }
    @Test fun failedSourceRemovalReportsCopyOnlyAndNeverReplaysDeletion()=fixture {ctx,s,h,p,item ->
        HiddenRestoreAccess(ctx,s,beforeSourceRemoval={throw IOException("Synthetic source removal failure")}).use {a ->
            a.authenticate("123456".toCharArray())
            val result=a.transfer(listOf(item.id),HiddenRestoreAccess.Destination.PRIMARY,true){_,_->}
            assertEquals(1,result.verified);assertEquals(0,result.moved);assertEquals(0,result.failed)
        }
        assertEquals(1,p.items().size);assertEquals(item.id,h.items().single().id)
        val reopened=HiddenRepository(ctx,s);reopened.reconcile();reopened.verify(item.id);assertTrue(reopened.recentlyDeleted().isEmpty())
    }
    @Test fun lockAfterVerificationPreventsSourceRemoval()=fixture {ctx,s,_,p,item ->
        HiddenRestoreAccess(ctx,s,beforeSourceRemoval={s.revokeOperations()}).use {a ->
            a.authenticate("123456".toCharArray())
            val result=a.transfer(listOf(item.id),HiddenRestoreAccess.Destination.PRIMARY,true){_,_->}
            assertEquals(1,result.verified);assertEquals(0,result.moved)
        }
        assertEquals(1,p.items().size);HiddenRepository(ctx,s).verify(item.id)
    }
    @Test fun corruptSourceCannotPublishEitherDestinationOrAuthorizeMove()=fixture {ctx,s,h,p,item ->
        val file=File(ctx.noBackupFilesDir,"hidden-media-v1/media-${item.id}.enc")
        RandomAccessFile(file,"rw").use {it.setLength(it.length()-1)}
        access(ctx,s).use {a ->
            for(destination in HiddenRestoreAccess.Destination.entries) {
                val result=a.transfer(listOf(item.id),destination,true){_,_->}
                assertEquals(0,result.verified);assertEquals(0,result.moved);assertEquals(1,result.failed)
            }
        }
        HiddenPhoneExport(ctx).reconcile();assertTrue(exported(ctx,item.displayName).isEmpty());assertTrue(p.items().isEmpty())
        assertEquals(item.id,h.items().single().id);assertTrue(h.recentlyDeleted().isEmpty())
    }
    @Test fun largeVideoBothDestinationsPreserveCompleteBytesAndPlaybackSeek()=fixture {ctx,s,h,p,_ ->
        val video=InstrumentationRegistry.getInstrumentation().context.assets.open("browser-media/valid.mp4").use {it.readBytes()}
        val size=500L*1024*1024
        val freeSize=size-video.size
        val header=java.nio.ByteBuffer.allocate(8).putInt(freeSize.toInt()).put("free".toByteArray()).array()
        val zeros=object:InputStream(){var left=freeSize-8
            override fun read()=if(left-- >0)0 else -1
            override fun read(b:ByteArray,off:Int,len:Int):Int {if(left<=0)return -1;val n=minOf(left,len.toLong()).toInt();b.fill(0,off,off+n);left-=n;return n}}
        val name="restore-video-${UUID.randomUUID()}.mp4"
        android.util.Log.i("PGStorage1751","test=restore stage=before-import bytes=$size usable=${ctx.noBackupFilesDir.usableSpace} total=${ctx.noBackupFilesDir.totalSpace}")
        val item=SequenceInputStream(java.util.Collections.enumeration(listOf(video.inputStream(),header.inputStream(),zeros))).use {h.importMedia(name,"video/mp4",it)}
        fun phoneUris()=ctx.contentResolver.query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,arrayOf(MediaStore.MediaColumns._ID),
            "${MediaStore.MediaColumns.DISPLAY_NAME}=?",arrayOf(name),null)!!.use {c ->buildList {while(c.moveToNext())add(android.content.ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,c.getLong(0)))}}
        try {
            access(ctx,s).use {a ->for(destination in HiddenRestoreAccess.Destination.entries) {
                android.util.Log.i("PGStorage1751","test=restore stage=before-copy destination=$destination usable=${ctx.noBackupFilesDir.usableSpace}")
                assertEquals(1,a.transfer(listOf(item.id),destination,false){_,_->}.verified)
                android.util.Log.i("PGStorage1751","test=restore stage=after-copy destination=$destination usable=${ctx.noBackupFilesDir.usableSpace}")
            }}
            val uri=phoneUris().single();val digest=java.security.MessageDigest.getInstance("SHA-256");var count=0L
            val buffer=ByteArray(64*1024)
            try {ctx.contentResolver.openInputStream(uri)!!.use {input ->while(true){val n=input.read(buffer);if(n<0)break;count+=n;digest.update(buffer,0,n)}}}finally{buffer.fill(0)}
            assertEquals(size,count);assertArrayEquals(item.plaintextSha256,digest.digest())
            val copy=p.items().single();assertEquals(size,copy.plaintextSize);assertArrayEquals(item.plaintextSha256,copy.plaintextSha256)
            p.openVideoSession(copy,{true},{false}).use {protected ->playAndSeek(ctx,protected.sourceFactory,android.net.Uri.parse("memory://primary/video.mp4"))}
            playAndSeek(ctx,androidx.media3.datasource.DefaultDataSource.Factory(ctx),uri)
            HiddenRepository(ctx,s).verify(item.id)
        } finally {phoneUris().forEach {runCatching {ctx.contentResolver.delete(it,null,null)}}}
    }
    private fun playAndSeek(ctx:Context,factory:androidx.media3.datasource.DataSource.Factory,uri:android.net.Uri) {
        val ready=java.util.concurrent.CountDownLatch(1);val error=java.util.concurrent.atomic.AtomicReference<Throwable>()
        var player:androidx.media3.exoplayer.ExoPlayer?=null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            player=androidx.media3.exoplayer.ExoPlayer.Builder(ctx).setMediaSourceFactory(androidx.media3.exoplayer.source.DefaultMediaSourceFactory(factory)).build().also {p ->
                p.addListener(object:androidx.media3.common.Player.Listener {
                    override fun onPlaybackStateChanged(state:Int){if(state==androidx.media3.common.Player.STATE_READY)ready.countDown()}
                    override fun onPlayerError(failure:androidx.media3.common.PlaybackException){error.set(failure);ready.countDown()}
                });p.setMediaItem(androidx.media3.common.MediaItem.fromUri(uri));p.prepare();p.play()
            }
        }
        try {
            assertTrue(ready.await(30,java.util.concurrent.TimeUnit.SECONDS));assertNull(error.get())
            val advanced=java.util.concurrent.CountDownLatch(1)
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                player!!.seekTo(200)
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({if(player!!.currentPosition>=200 && player!!.playerError==null)advanced.countDown()},1500)
            }
            assertTrue(advanced.await(5,java.util.concurrent.TimeUnit.SECONDS))
        } finally {InstrumentationRegistry.getInstrumentation().runOnMainSync {player?.release()}}
    }
    private fun exported(ctx:Context,name:String):List<android.net.Uri> = ctx.contentResolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        arrayOf(MediaStore.MediaColumns._ID),"${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ? AND ${MediaStore.MediaColumns.OWNER_PACKAGE_NAME}=?",arrayOf(name.substringBeforeLast('.')+"%",ctx.applicationContext.packageName),null)!!.use {c ->buildList {while(c.moveToNext())add(android.content.ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,c.getLong(0)))}}
    @Test fun phoneCopyAndMoveProduceReadableOriginalsAndRecoverableSources()=fixture {ctx,s,h,_,item ->
        try {
            HiddenRestoreAccess(ctx,s).use {a ->
                assertEquals(1,a.transfer(listOf(item.id),HiddenRestoreAccess.Destination.PHONE,false){_,_->}.verified)
                assertEquals(1,h.items().size)
                assertEquals(1,a.transfer(listOf(item.id),HiddenRestoreAccess.Destination.PHONE,true){_,_->}.moved)
            }
            val copies=exported(ctx,item.displayName);assertEquals(2,copies.size)
            h.restore(item.id);val expected=h.read(item.id)
            try {copies.forEach {uri ->assertArrayEquals(expected,ctx.contentResolver.openInputStream(uri)!!.use {it.readBytes()})}}finally{expected.fill(0)}
            HiddenPhoneExport(ctx).reconcile();assertEquals(2,exported(ctx,item.displayName).size)
        } finally {exported(ctx,item.displayName).forEach {ctx.contentResolver.delete(it,null,null)}}
    }
    @Test fun phoneWriteAndPublicationFailuresKeepHiddenOriginal()=fixture {ctx,s,h,_,item ->
        val exporter=HiddenPhoneExport(ctx)
        assertTrue(runCatching {exporter.export(item,object:InputStream(){override fun read():Int=throw IOException("Synthetic disk failure")},{},{it()})}.isFailure)
        assertTrue(runCatching {h.withTransferSource(item.id,{false}) {source,input ->exporter.export(source,input,{}, {throw IOException("Synthetic publication failure")})}}.isFailure)
        exporter.reconcile();assertTrue(exported(ctx,item.displayName).isEmpty());h.verify(item.id)
    }
    @Test fun failedDestinationSyncCleansPendingEntryAndRetainsOriginal()=fixture {ctx,_,h,_,item ->
        val exporter=HiddenPhoneExport(ctx,syncOutput={throw IOException("Synthetic ENOSPC during destination sync")})
        assertTrue(runCatching {h.withTransferSource(item.id,{false}) {source,input ->exporter.export(source,input,{}, {it()})}}.isFailure)
        HiddenPhoneExport(ctx).reconcile();assertTrue(exported(ctx,item.displayName).isEmpty());h.verify(item.id);assertTrue(h.recentlyDeleted().isEmpty())
    }
    @Test fun processDeathRecoveryRemovesOnlyRecordedPendingRows()=fixture {ctx,_,_,_,item ->
        val name="pg-restore-${UUID.randomUUID()}"
        val prefs=ctx.getSharedPreferences("hidden-pending-exports-v1",0)
        assertTrue(prefs.edit().putBoolean(name,false).commit())
        val uri=ctx.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,android.content.ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME,name);put(MediaStore.MediaColumns.MIME_TYPE,"image/png");put(MediaStore.MediaColumns.IS_PENDING,1)
        })!!
        val unrelated=ctx.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,android.content.ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME,"unrelated-${UUID.randomUUID()}.png");put(MediaStore.MediaColumns.MIME_TYPE,"image/png");put(MediaStore.MediaColumns.IS_PENDING,1)
        })!!
        try {
            HiddenPhoneExport(ctx).reconcile();assertFalse(prefs.contains(name))
            val remains=try {ctx.contentResolver.query(uri,arrayOf(MediaStore.MediaColumns._ID),null,null,null)?.use {it.moveToFirst()} ?: false} catch(_:SecurityException){false}
            assertFalse("Recorded pending row must be removed even if MediaStore changed its name",remains)
            assertTrue(ctx.contentResolver.query(unrelated,arrayOf(MediaStore.MediaColumns._ID),null,null,null)!!.use {it.moveToFirst()})
        } finally {runCatching {ctx.contentResolver.delete(uri,null,null)};runCatching {ctx.contentResolver.delete(unrelated,null,null)}}
    }
    @Test fun largePhotoRestoreUsesOriginalBytesBeyondEditorAndLegacyHiddenLimits()=fixture {ctx,s,h,p,item ->
        val prefix=h.read(item.id)
        val size=100L*1024*1024
        val tail=object:InputStream(){var remaining=size-prefix.size
            override fun read():Int=if(remaining-->0)0 else -1
            override fun read(b:ByteArray,off:Int,len:Int):Int {if(remaining<=0)return -1;val n=minOf(len.toLong(),remaining).toInt();b.fill(0,off,off+n);remaining-=n;return n}}
        val large=try {h.importMedia("large.png","image/png",SequenceInputStream(ByteArrayInputStream(prefix),tail))}finally{prefix.fill(0)}
        access(ctx,s).use {a ->assertEquals(1,a.transfer(listOf(large.id),HiddenRestoreAccess.Destination.PRIMARY,false){_,_->}.verified)}
        assertEquals(size,p.items().single().plaintextSize);h.verify(large.id)
    }

    @Test fun hiddenMoveResumesVerifiedCopyWithFreshAuthenticationWithoutDuplicate()=fixture {ctx,s,h,p,item ->
        HiddenRestoreAccess(ctx,s,beforeSourceRemoval={throw AssertionError("synthetic process death")}).use {crashed ->
            crashed.authenticate("123456".toCharArray())
            assertTrue(runCatching {crashed.transfer(listOf(item.id),HiddenRestoreAccess.Destination.PRIMARY,true){_,_->}}.isFailure)
        }
        assertEquals(1,h.items().size);assertEquals(1,p.items().size)
        PrimaryCopyAccess(ctx,s).use {fresh ->
            fresh.authenticate("123456".toCharArray());val op=fresh.history().single()
            assertEquals("HIDDEN_TO_PRIMARY",op.direction)
            val result=fresh.resume(op.id,{false}){_,_->}
            assertEquals(1,result.verified);assertEquals(1,result.moved);assertEquals(0,result.failed)
            assertEquals(1,p.items().size);assertTrue(h.items().isEmpty())
            assertEquals(1,h.recentlyDeleted().size)
        }
    }

    @Test fun closingHistoryOwnerSynchronouslyRevokesReverseResume()=fixture {ctx,s,h,p,item ->
        HiddenRestoreAccess(ctx,s,beforeSourceRemoval={throw AssertionError("synthetic process death")}).use {crashed ->
            crashed.authenticate("123456".toCharArray())
            assertTrue(runCatching {crashed.transfer(listOf(item.id),HiddenRestoreAccess.Destination.PRIMARY,true){_,_->}}.isFailure)
        }
        val parent=PrimaryCopyAccess(ctx,s);parent.authenticate("123456".toCharArray())
        val id=parent.history().single().id
        runCatching {parent.resume(id,{false}) {done,_->if(done==0)parent.close()}}
        assertTrue(s.isUnlocked);assertEquals(1,h.items().size);assertTrue(h.recentlyDeleted().isEmpty());assertEquals(1,p.items().size)
    }

    @Test fun reverseCancellationAtRemovalBoundaryPreservesOriginal()=fixture {ctx,s,h,p,item ->
        // First suspend after verification, then resume with cancellation at the exact deletion boundary.
        HiddenRestoreAccess(ctx,s,beforeSourceRemoval={throw AssertionError("synthetic process death")}).use {crashed ->
            crashed.authenticate("123456".toCharArray())
            assertTrue(runCatching {crashed.transfer(listOf(item.id),HiddenRestoreAccess.Destination.PRIMARY,true){_,_->}}.isFailure)
        }
        var cancel=false
        HiddenRestoreAccess(ctx,s,beforeSourceRemoval={cancel=true}).use {resumed ->
            resumed.authenticate("123456".toCharArray())
            s.beginOperation().use {op ->
                val journal=TransferJournal(File(ctx.noBackupFilesDir,"hidden-media-v1/transfers-v1"),op.key()) {commit -> s.commit(op){commit.run()}}
                val result=resumed.resume(journal.list().single().id,{cancel}){_,_->}
                assertEquals(1,result.verified);assertEquals(0,result.moved)
            }
        }
        assertEquals(1,h.items().size);assertTrue(h.recentlyDeleted().isEmpty());assertEquals(1,p.items().size)
    }

    @Test fun collectionCoversPinsAndBulkMembershipRemainIndependentAcrossRestart()=fixture {ctx,s,h,p,item ->
        val one=h.createCollection("Hidden one");val two=h.createCollection("Hidden two")
        h.addItemsToCollection(one.id,listOf(item.id,item.id));h.addItemsToCollection(two.id,listOf(item.id))
        h.setCollectionCover(one.id,item.id);h.pinCollection(one.id,true)
        val reopened=HiddenRepository(ctx,s)
        assertEquals(item.id,reopened.collections().first {it.id==one.id}.coverVaultItemId)
        assertTrue(reopened.collections().first {it.id==one.id}.isPinned)
        assertTrue(p.collections().isEmpty())
        reopened.pinCollection(one.id,false);reopened.removeItemsFromCollection(one.id,listOf(item.id))
        assertFalse(reopened.collections().first {it.id==one.id}.isPinned)
        assertNull(reopened.collections().first {it.id==one.id}.coverVaultItemId)
        assertTrue(reopened.itemsInCollection(one.id).isEmpty());assertEquals(1,reopened.itemsInCollection(two.id).size)
        assertEquals(1,reopened.items().size);reopened.verify(item.id)
    }

    @Test fun externalEditedResultSaveAsNewAndReplaceStayInHiddenAndVerifyBytes()=fixture {ctx,s,h,p,item ->
        val bytes=h.read(item.id)
        val collection=h.createCollection("Edited collection");h.addItemsToCollection(collection.id,listOf(item.id));h.setCollectionCover(collection.id,item.id)
        try {
            val copy=uk.co.traynor.privategallery.core.media.ExternalEditImport.hidden(ctx,s,item.id,bytes,false,{s.isUnlocked})
            h.verify(copy.id);assertEquals(2,h.items().size);assertTrue(p.items().isEmpty())
            val replacement=uk.co.traynor.privategallery.core.media.ExternalEditImport.hidden(ctx,s,item.id,bytes,true,{s.isUnlocked})
            h.verify(replacement.id);assertTrue(h.items().none {it.id==item.id});assertEquals(item.id,h.recentlyDeleted().single().id)
            assertTrue(p.items().isEmpty());assertArrayEquals(item.plaintextSha256,replacement.plaintextSha256)
            assertEquals(replacement.id,h.itemsInCollection(collection.id).single().id)
            assertEquals(replacement.id,h.collections().first {it.id==collection.id}.coverVaultItemId)
            assertTrue(runCatching {uk.co.traynor.privategallery.core.media.ExternalEditImport.hidden(ctx,s,replacement.id,bytes,true,{false})}.isFailure)
            assertTrue(h.items().any {it.id==replacement.id})
        } finally {bytes.fill(0)}
    }
    @Test fun externalPrimaryResultReplacementPreservesIndependentHiddenOriginal()=fixture {ctx,s,h,p,item ->
        val bytes=h.read(item.id);val key=PinVaultKeyStore(ctx).unlock("123456".toCharArray())
        try {
            val original=(p.importVerified(VaultImportSource("synthetic.png","image/png",{ByteArrayInputStream(bytes)})) as ImportResult.Imported).item
            val collection=p.createCollection("Edited collection");p.addItemsToCollection(collection.id,listOf(original.id));p.setCollectionCover(collection.id,original.id)
            val copy=uk.co.traynor.privategallery.core.media.ExternalEditImport.primary(ctx,key,original.id,bytes,false,{true},{it()})
            p.verifyTransferDestination(copy){false};assertEquals(2,p.items().size)
            val replacement=uk.co.traynor.privategallery.core.media.ExternalEditImport.primary(ctx,key,original.id,bytes,true,{true},{it()})
            p.verifyTransferDestination(replacement){false};assertEquals(original.id,p.recentlyDeleted().single().id)
            h.verify(item.id);assertEquals(1,h.items().size);assertTrue(h.recentlyDeleted().isEmpty())
            assertEquals(replacement.id,p.itemsInCollection(collection.id).single().id)
            assertEquals(replacement.id,p.collections().first {it.id==collection.id}.coverVaultItemId)
            assertTrue(runCatching {uk.co.traynor.privategallery.core.media.ExternalEditImport.primary(ctx,key,replacement.id,bytes,true,{false},{it()})}.isFailure)
        } finally {bytes.fill(0);key.fill(0)}
    }
}
