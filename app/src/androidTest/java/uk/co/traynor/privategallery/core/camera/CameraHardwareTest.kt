package uk.co.traynor.privategallery.core.camera

import android.content.ContextWrapper
import androidx.camera.video.Quality
import androidx.camera.view.PreviewView
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*
import java.io.File
import java.util.UUID
import uk.co.traynor.privategallery.core.hidden.HiddenSession

/** Real CameraX on the emulator's synthetic camera, not physical Fold acceptance. */
class CameraHardwareTest {
    @get:Rule val compose=createComposeRule()
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private val base=instrumentation.targetContext
    private fun runCapture(hidden:Boolean,video:Boolean,black:Boolean,audio:Boolean,interrupt:Boolean=false) {
        android.os.ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand("pm grant ${base.packageName} android.permission.CAMERA")).use {it.readBytes()}
        if(audio)android.os.ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand("pm grant ${base.packageName} android.permission.RECORD_AUDIO")).use {it.readBytes()}
        val root=File(base.cacheDir,"camera-hardware-${UUID.randomUUID()}").apply {mkdirs()}
        val context=object:ContextWrapper(base){override fun getApplicationContext()=this;override fun getFilesDir()=File(root,"files").apply{mkdirs()};override fun getNoBackupFilesDir()=File(root,"private").apply{mkdirs()}}
        val session=HiddenSession{android.os.SystemClock.elapsedRealtime()}
        session.complete(session.beginAuthentication(),ByteArray(32){7})
        val access=VaultCaptureAccess(context,if(hidden)session else null,{ByteArray(32){2}},{true})
        lateinit var controller:VaultCameraController
        compose.setContent {
            val owner=LocalLifecycleOwner.current;val local=LocalContext.current
            val preview=remember {PreviewView(local)}
            controller=remember {VaultCameraController(context,access)}
            if(!black)AndroidView(factory={preview})
            LaunchedEffect(Unit){assertTrue(controller.enter());controller.bind(owner,if(black)null else preview,video,false,Quality.HD)}
        }
        try {
            compose.waitUntil(30_000){controller.bound}
            compose.runOnIdle {controller.input(video,audio)}
            if(video) {
                compose.waitUntil(15_000){controller.recordingNow}
                // The black recording intentionally exceeds the previously requested ten-second boundary.
                compose.waitUntil(30_000){controller.elapsed >= if(black)12 else 2}
                compose.runOnIdle {if(interrupt)controller.revoke() else controller.input(true,audio)}
            }
            compose.waitUntil(45_000){controller.completedRevision>0}
            if(interrupt) {
                assertTrue(access.journal.entries().isNotEmpty())
                val fresh=VaultCaptureAccess(context,if(hidden)session else null,{ByteArray(32){2}},{true})
                fresh.recover(fresh.journal.entries().single()).use {ticket ->
                    assertFalse(ticket.plain.exists());assertTrue(ticket.size>0)
                    if(ticket.complete)fresh.import(ticket)
                }
            } else {
                assertEquals("Saved encrypted",controller.status)
                val item=access.recordings().single();assertEquals(video,item.mimeType.startsWith("video/"))
                assertTrue(access.journal.entries().isEmpty())
                if(video) {
                    val bytes=if(hidden)uk.co.traynor.privategallery.core.hidden.HiddenRepository(context,session).read(item.id)
                        else uk.co.traynor.privategallery.core.vault.AndroidVaultRepository(context,ByteArray(32){2}).readForViewing(item)
                    val temp=File(root,"verify.mp4");temp.writeBytes(bytes)
                    try {val facts=uk.co.traynor.privategallery.core.browser.v2.VideoFileValidator.inspect(temp,"video/mp4",audio)
                        if(audio)assertTrue(facts.audioTracks>0) else assertEquals(0,facts.audioTracks)
                    } finally {bytes.fill(0);temp.delete()}
                }
            }
        } finally {compose.runOnIdle {controller.revoke()};session.lock();root.deleteRecursively()}
    }
    @Test fun primaryNormalPhoto()=runCapture(false,false,false,false)
    @Test fun hiddenBlackPhoto()=runCapture(true,false,true,false)
    @Test fun primaryNormalVideoWithAudio()=runCapture(false,true,false,true)
    @Test fun hiddenBlackSilentVideoBeyondTenSeconds()=runCapture(true,true,true,false)
    @Test fun hiddenRecordingRevocationPreservesEncryptedPending()=runCapture(true,true,true,false,true)
}
