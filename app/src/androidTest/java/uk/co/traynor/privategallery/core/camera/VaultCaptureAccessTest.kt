package uk.co.traynor.privategallery.core.camera

import android.content.ContextWrapper
import android.graphics.Bitmap
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.Assert.*
import java.io.*
import java.util.UUID
import uk.co.traynor.privategallery.core.hidden.HiddenSession
import uk.co.traynor.privategallery.core.hidden.HiddenRepository
import uk.co.traynor.privategallery.core.vault.AndroidVaultRepository

class VaultCaptureAccessTest {
    private val base=InstrumentationRegistry.getInstrumentation().targetContext
    private fun fixture(block:(android.content.Context)->Unit) {
        val root=File(base.cacheDir,"camera-test-${UUID.randomUUID()}").apply{mkdirs()}
        val context=object:ContextWrapper(base){override fun getApplicationContext()=this;override fun getFilesDir()=File(root,"files").apply{mkdirs()};override fun getNoBackupFilesDir()=File(root,"no-backup").apply{mkdirs()}}
        try{block(context)}finally{root.deleteRecursively()}
    }
    private fun jpeg()=Bitmap.createBitmap(16,16,Bitmap.Config.ARGB_8888).let {bitmap->try{ByteArrayOutputStream().use {bitmap.compress(Bitmap.CompressFormat.JPEG,95,it);it.toByteArray()}}finally{bitmap.recycle()}}
    @Test fun bothVaultsImportPhotoAndVideoWithHashTimestampAndNoPublicCopy()=fixture {context->
        val primaryKey=ByteArray(32){2};val hiddenKey=ByteArray(32){7};val hidden=HiddenSession{android.os.SystemClock.elapsedRealtime()}
        hidden.complete(hidden.beginAuthentication(),hiddenKey.copyOf())
        val video=InstrumentationRegistry.getInstrumentation().context.assets.open("browser-media/valid.mp4").use{it.readBytes()}
        for(isHidden in listOf(false,true)) {
            val access=VaultCaptureAccess(context,if(isHidden)hidden else null,{primaryKey.copyOf()},{true})
            assertFalse(access.audio());access.audio(true);assertTrue(access.audio());access.audio(false);assertFalse(access.audio())
            for((mime,bytes) in listOf("image/jpeg" to jpeg(),"video/mp4" to video)) {
                val ticket=access.create(mime,false);ticket.plain.writeBytes(bytes)
                access.journal.seal(ticket,true,if(mime.startsWith("video"))1000 else 0)
                val item=access.import(ticket)
                assertEquals(ticket.timestamp,item.importedAtEpochMillis)
                assertArrayEquals(java.security.MessageDigest.getInstance("SHA-256").digest(bytes),item.plaintextSha256)
                assertFalse(ticket.plain.exists());assertTrue(access.journal.entries().isEmpty())
                assertTrue(item.sourceUri!!.startsWith("capture:v1|"));ticket.close()
            }
        }
        assertEquals(2,AndroidVaultRepository(context,primaryKey).items().size)
        assertEquals(2,HiddenRepository(context,hidden).items().size)
        assertEquals(2,VaultCaptureAccess(context,hidden,{null},{true}).recordings().size)
        hidden.lock()
    }
    @Test fun revokedHiddenCaptureSealsButCannotImportUntilIndependentAuthentication()=fixture {context->
        val key=ByteArray(32){8};val session=HiddenSession{android.os.SystemClock.elapsedRealtime()};session.complete(session.beginAuthentication(),key.copyOf())
        var authorized=true
        val access=VaultCaptureAccess(context,session,{error("Primary must never be consulted")},{authorized})
        val ticket=access.create("image/jpeg",false);ticket.plain.writeBytes(jpeg())
        authorized=false;session.lock()
        access.journal.seal(ticket,true,0);assertFalse(ticket.plain.exists())
        assertTrue(runCatching {access.import(ticket)}.isFailure);ticket.close()
        session.complete(session.beginAuthentication(),key.copyOf());authorized=true
        assertTrue(runCatching {access.open(access.journal.entries().single())}.isFailure) // stale authority never resumes
        val fresh=VaultCaptureAccess(context,session,{null},{authorized})
        fresh.open(fresh.journal.entries().single()).use {fresh.import(it)}
        assertEquals(1,HiddenRepository(context,session).items().size);session.lock()
    }
    @Test fun processRecoveryKeepsIncompleteBytesAndAudioPreferencesAreVaultSpecific()=fixture {context->
        val key=ByteArray(32){9};val access=VaultCaptureAccess(context,null,{key.copyOf()},{true})
        val ticket=access.create("video/mp4",false);ticket.plain.writeBytes(byteArrayOf(1,2,3));val id=ticket.id;ticket.close()
        access.recover(id).use {access.journal.seal(it,false,0);assertFalse(it.complete);assertTrue(runCatching {access.import(it)}.isFailure);assertFalse(it.plain.exists())}
        assertEquals(listOf(id),access.journal.entries())
        assertTrue(runCatching {VaultCaptureAccess(context,null,{ByteArray(32)},{true}).open(id)}.isFailure)
    }
    @Test fun equalBytesKeepDistinctOriginalIdentities()=fixture {context->
        val primary=ByteArray(32){3};val hidden=HiddenSession{android.os.SystemClock.elapsedRealtime()}
        hidden.complete(hidden.beginAuthentication(),ByteArray(32){4})
        for(session in listOf(null,hidden)) {
            val access=VaultCaptureAccess(context,session,{primary.copyOf()},{true})
            val ids=mutableSetOf<String>();val refs=mutableSetOf<String>()
            repeat(2) {
                access.create("image/jpeg",false).use {ticket->ticket.plain.writeBytes(jpeg());access.journal.seal(ticket,true,0)
                    val item=access.import(ticket);ids+=item.id;refs+=item.sourceUri!!}
            }
            assertEquals(2,ids.size);assertEquals(2,refs.size);assertEquals(2,access.recordings().size)
        }
        hidden.lock()
    }
    @Test fun revokedDeliveryClosesTicketLease()=fixture {context->
        val key=ByteArray(32){8};var calls=0
        val access=VaultCaptureAccess(context,null,{key.copyOf()},{++calls==1})
        assertTrue(runCatching {access.create("image/jpeg",false)}.isFailure)
        val fresh=VaultCaptureAccess(context,null,{key.copyOf()},{true})
        fresh.recover(fresh.journal.entries().single()).use {assertFalse(it.complete)}
    }
    @Test fun revokedAccessCannotResumeAfterFreshAuthentication()=fixture {context->
        val key=ByteArray(32){8};val session=HiddenSession{android.os.SystemClock.elapsedRealtime()}
        session.complete(session.beginAuthentication(),key.copyOf())
        val access=VaultCaptureAccess(context,session,{null},{true})
        val ticket=access.create("image/jpeg",false);ticket.plain.writeBytes(jpeg())
        access.revoke();session.lock();session.complete(session.beginAuthentication(),key.copyOf())
        access.journal.seal(ticket,true,0)
        assertTrue(runCatching {access.import(ticket)}.isFailure);ticket.close();session.lock()
    }
    @Test fun cameraVideoValidationRejectsTruncation()=fixture {context->
        val bytes=InstrumentationRegistry.getInstrumentation().context.assets.open("browser-media/valid.mp4").use{it.readBytes()}
        val file=File(context.noBackupFilesDir,"truncated.mp4");file.writeBytes(bytes.copyOf(bytes.size/2))
        assertTrue(runCatching {uk.co.traynor.privategallery.core.browser.v2.VideoFileValidator.inspect(file,"video/mp4",false)}.isFailure)
    }

}
