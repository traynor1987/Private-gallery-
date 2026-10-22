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
        } finally {bytes.fill(0);session.lock();key.fill(0);prefs.forEach {base.getSharedPreferences(it,0).edit().clear().commit()};root.deleteRecursively()}
    }
    private fun access(ctx:Context,s:HiddenSession)=HiddenRestoreAccess(ctx,s).apply {authenticate("123456".toCharArray())}
    @Test fun primaryCopyReencryptsAndRetainsHiddenAcrossRepositoryRestart()=fixture {ctx,s,h,p,item ->
        access(ctx,s).use {a ->assertEquals(1,a.transfer(listOf(item.id),HiddenRestoreAccess.Destination.PRIMARY,false){_,_->}.verified)}
        assertEquals(1,h.items().size);val copy=p.items().single()
        assertNotEquals(item.id,copy.id);assertArrayEquals(item.plaintextSha256,copy.plaintextSha256)
        assertEquals(item.id,HiddenRepository(ctx,s).items().single().id)
        val buffer=ByteArray(1024);p.withHiddenCopySource(copy.id,{false}){_,input ->while(input.read(buffer)>=0)Unit};buffer.fill(0)
        assertTrue(s.isUnlocked);assertTrue(p.collections().isEmpty())
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
}
