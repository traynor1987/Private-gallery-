@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
package uk.co.traynor.privategallery.core.hidden

import android.content.ContextWrapper
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.media.MediaDataSource
import androidx.test.platform.app.InstrumentationRegistry
import java.io.*
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject
import org.json.JSONArray

class HiddenLargeMediaTest {
    private fun fixture(block:(android.content.Context,HiddenSession,File)->Unit) {
        val base=InstrumentationRegistry.getInstrumentation().targetContext
        val root=File(base.noBackupFilesDir,"large-hidden-${UUID.randomUUID()}").apply {mkdirs()}
        val context=object:ContextWrapper(base){override fun getNoBackupFilesDir()=root}
        val session=HiddenSession(android.os.SystemClock::elapsedRealtime)
        session.complete(session.beginAuthentication(),ByteArray(32){9})
        try{block(context,session,root)}finally{session.lock();root.deleteRecursively()}
    }
    @Test fun largeVideoImportSeekThumbnailRestartAndRevocation()=fixture {context,session,root ->
        val video=InstrumentationRegistry.getInstrumentation().context.assets.open("browser-media/valid.mp4").use {it.readBytes()}
        for(mib in listOf(20,100,500)) {
            val size=mib*1024L*1024
            val freeSize=size-video.size
            val header=ByteBuffer.allocate(8).putInt(freeSize.toInt()).put("free".toByteArray()).array()
            val zeros=object:InputStream(){var left=freeSize-8
                override fun read()=if(left-- >0)0 else -1
                override fun read(b:ByteArray,off:Int,len:Int):Int {if(left<=0)return -1;val n=minOf(left,len.toLong()).toInt();b.fill(0,off,off+n);left-=n;return n}}
            val input=SequenceInputStream(java.util.Collections.enumeration(listOf(video.inputStream(),header.inputStream(),zeros)))
            val repository=HiddenRepository(context,session)
            val item=input.use {repository.importMedia("large-$mib.mp4","video/mp4",it)}
            assertEquals(size,item.plaintextSize);repository.verify(item.id)
            repository.preview(item.id).recycle()
            val tail=ByteArray(4096){42};assertEquals(tail.size,repository.readRange(item.id,size-tail.size,tail,0,tail.size));assertTrue(tail.all {it==0.toByte()})
            val retriever=MediaMetadataRetriever()
            try {retriever.setDataSource(object:MediaDataSource(){override fun getSize()=size;override fun close()=Unit
                override fun readAt(position:Long,buffer:ByteArray,offset:Int,count:Int)=repository.readRange(item.id,position,buffer,offset,count)})
                assertNotNull(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION))
                retriever.getScaledFrameAtTime(0,MediaMetadataRetriever.OPTION_CLOSEST_SYNC,128,128)!!.recycle()
            }finally{retriever.release()}
            val protectedSession=HiddenVideoSession(repository,item.id){false}
            val ready=java.util.concurrent.CountDownLatch(1)
            val error=java.util.concurrent.atomic.AtomicReference<Throwable>()
            var player:androidx.media3.exoplayer.ExoPlayer?=null
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                player=androidx.media3.exoplayer.ExoPlayer.Builder(context)
                    .setMediaSourceFactory(androidx.media3.exoplayer.source.DefaultMediaSourceFactory(protectedSession.sourceFactory)).build().also {p ->
                        p.addListener(object:androidx.media3.common.Player.Listener {
                            override fun onPlaybackStateChanged(state:Int){if(state==androidx.media3.common.Player.STATE_READY)ready.countDown()}
                            override fun onPlayerError(failure:androidx.media3.common.PlaybackException){error.set(failure);ready.countDown()}
                        })
                        p.setMediaItem(androidx.media3.common.MediaItem.fromUri("memory://hidden/video.mp4"));p.prepare();p.play()
                    }
            }
            try {assertTrue("Media3 ready for $mib MiB",ready.await(30,java.util.concurrent.TimeUnit.SECONDS));assertNull(error.get())
                val advanced=java.util.concurrent.CountDownLatch(1)
                InstrumentationRegistry.getInstrumentation().runOnMainSync {
                    player!!.seekTo(200)
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({if(player!!.currentPosition>=200 && player!!.playerError==null)advanced.countDown()},1500)
                }
                assertTrue("Media3 seek/playback for $mib MiB",advanced.await(5,java.util.concurrent.TimeUnit.SECONDS))
            }finally{InstrumentationRegistry.getInstrumentation().runOnMainSync {player?.release()};protectedSession.close()}
            session.lock();session.complete(session.beginAuthentication(),ByteArray(32){9})
            val reopened=HiddenRepository(context,session);assertEquals(size,reopened.items().single {it.id==item.id}.plaintextSize);reopened.verify(item.id)
            try{repository.readRange(item.id,0,tail,0,tail.size);fail("Old repository authority survived") }catch(expected:IllegalStateException){assertTrue(tail.all {it==0.toByte()})}
            reopened.trash(listOf(item.id));reopened.deletePermanently(item.id)
        }
    }
    @Test fun largePhotoIsPreservedButDisplayIsBounded()=fixture {context,session,root ->
        val synthetic=File(root,"synthetic-large-photo.png")
        val bitmap=Bitmap.createBitmap(4096,4096,Bitmap.Config.ARGB_8888)
        try {
            val row=IntArray(4096);val random=java.util.Random(42)
            for(y in 0 until 4096){for(x in row.indices)row[x]=random.nextInt() or (0xff shl 24);bitmap.setPixels(row,0,4096,0,y,4096,1)}
            synthetic.outputStream().use {assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,it))}
        }finally{bitmap.recycle()}
        assertTrue(synthetic.length()>16*1024*1024)
        val digest=MessageDigest.getInstance("SHA-256")
        synthetic.inputStream().use {input ->val buffer=ByteArray(65536);while(true){val n=input.read(buffer);if(n<0)break;digest.update(buffer,0,n)}}
        val repository=HiddenRepository(context,session)
        val item=synthetic.inputStream().use {repository.importMedia("large.png","image/png",it)}
        assertArrayEquals(digest.digest(),item.plaintextSha256);repository.verify(item.id)
        val display=repository.readForDisplay(item.id)
        try {val decoded=android.graphics.BitmapFactory.decodeByteArray(display,0,display.size)!!
            try{assertTrue(decoded.width<=2048 && decoded.height<=2048)}finally{decoded.recycle()}
        }finally{display.fill(0);synthetic.delete()}
    }
    @Test fun interruptedPublicationRetainsExistingMedia()=fixture {context,session,root ->
        val bitmap=Bitmap.createBitmap(32,32,Bitmap.Config.ARGB_8888)
        val png=try{ByteArrayOutputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it);it.toByteArray()}}finally{bitmap.recycle()}
        val repository=HiddenRepository(context,session)
        val original=repository.importMedia("original.png","image/png",png.inputStream())
        val broken=HiddenRepository(context,session,directorySync={
            if(File(root,"hidden-media-v1").listFiles().orEmpty().count {it.name.startsWith("media-")}>1)throw IOException("Synthetic fsync interruption")
        })
        val different=png+byteArrayOf(1)
        assertTrue(runCatching {broken.importMedia("interrupted.png","image/png",different.inputStream())}.isFailure)
        repository.reconcile();assertEquals(listOf(original.id),repository.items().map {it.id});assertArrayEquals(png,repository.read(original.id))
    }
    @Test fun legacyHiddenRecordSurvivesMetadataMutation()=fixture {context,session,root ->
        val id=UUID.randomUUID().toString();val metadataId="00000000-0000-0000-0000-000000000000"
        val store=HiddenRecordStore(File(root,"hidden-media-v1"))
        val bytes=byteArrayOf(1,2,3,4);val key=ByteArray(32){9}
        val item=JSONObject().put("id",id).put("mime","image/png").put("name","legacy").put("time",1).put("size",4)
            .put("hash",android.util.Base64.encodeToString(MessageDigest.getInstance("SHA-256").digest(bytes),android.util.Base64.NO_WRAP)).put("trash",false).put("deleted",0)
        val metadata=JSONObject().put("domain","hidden").put("version",1).put("items",JSONArray().put(item)).put("collections",JSONArray()).put("memberships",JSONArray()).put("favourite","")
        for((kind,value) in listOf("media" to bytes,"thumbnail" to bytes,"metadata" to metadata.toString().toByteArray())) {
            val recordId=if(kind=="metadata")metadataId else id
            store.promote(store.stage(kind,recordId,value,key),kind,recordId)
        }
        val repository=HiddenRepository(context,session);assertArrayEquals(bytes,repository.read(id));repository.createCollection("Preserved")
        assertArrayEquals(bytes,HiddenRepository(context,session).read(id));key.fill(0)
    }
}
