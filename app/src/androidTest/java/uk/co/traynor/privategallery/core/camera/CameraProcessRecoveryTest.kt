package uk.co.traynor.privategallery.core.camera

import android.content.ContextWrapper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import uk.co.traynor.privategallery.core.hidden.HiddenSession
import java.io.File

class CameraProcessRecoveryTest {
    @Test fun pendingCapturesSurviveIndependentProcessRestart() {
        val base=InstrumentationRegistry.getInstrumentation().targetContext
        check(base.packageName=="uk.co.traynor.privategallery.hiddenprototype")
        val root=File(base.cacheDir,"camera-process-fixture-v17")
        val context=object:ContextWrapper(base){override fun getApplicationContext()=this;override fun getFilesDir()=File(root,"files").apply{mkdirs()};override fun getNoBackupFilesDir()=File(root,"private").apply{mkdirs()}}
        val phase=InstrumentationRegistry.getArguments().getString("cameraJournalPhase")
        val hidden=HiddenSession{android.os.SystemClock.elapsedRealtime()}
        hidden.complete(hidden.beginAuthentication(),ByteArray(32){7})
        val bytes=InstrumentationRegistry.getInstrumentation().context.assets.open("browser-media/valid.mp4").use{it.readBytes()}
        fun accesses()=listOf(VaultCaptureAccess(context,null,{ByteArray(32){2}},{true}),VaultCaptureAccess(context,hidden,{null},{true}))
        try {
            if(phase==null||phase=="seed") {
                root.deleteRecursively();root.mkdirs()
                File(root,"pid").writeText(android.os.Process.myPid().toString())
                for(access in accesses()) {
                    for(complete in listOf(false,true)) {
                        val ticket=access.create("video/mp4",false)
                        ticket.plain.outputStream().use {it.write(bytes)}
                        if(complete)access.journal.seal(ticket,true,1000)
                        // Phase seed deliberately leaves LIVE leases/key owners until OS process death.
                        if(phase==null)ticket.close()
                    }
                    assertEquals(2,access.journal.entries().size)
                }
            }
            if(phase==null||phase=="verify") {
                if(phase!=null)assertNotEquals(File(root,"pid").readText().toInt(),android.os.Process.myPid())
                for(access in accesses()) {
                    var completed=0;var incomplete=0
                    for(id in access.journal.entries())access.recover(id).use {ticket->
                        if(ticket.hash==null)access.journal.seal(ticket,false,0)
                        assertFalse(ticket.plain.exists())
                        access.journal.reader(ticket).use {assertArrayEquals(bytes,it.stream().readBytes())}
                        if(ticket.complete){access.import(ticket);completed++}else{assertTrue(runCatching{access.import(ticket)}.isFailure);incomplete++}
                    }
                    assertEquals(1,completed);assertEquals(1,incomplete)
                    assertEquals(1,access.recordings().size);assertEquals(1,access.journal.entries().size)
                }
            }
        } finally {hidden.lock();if(phase!="seed")root.deleteRecursively()}
    }
}
