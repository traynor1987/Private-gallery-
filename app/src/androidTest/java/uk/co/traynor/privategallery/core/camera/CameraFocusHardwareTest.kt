package uk.co.traynor.privategallery.core.camera

import android.content.ContextWrapper
import android.os.ParcelFileDescriptor
import androidx.camera.video.Quality
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID

/** Real CameraX request/callback tests; synthetic hardware may truthfully report no AF support. */
class CameraFocusHardwareTest {
    @get:Rule val compose=createComposeRule()

    @Test fun boundPreviewFocusReportsHardwareResultAndRetiresOldRequests() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val base=instrumentation.targetContext
        ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(
            "pm grant ${base.packageName} android.permission.CAMERA")).use {it.readBytes()}
        val root=File(base.cacheDir,"camera-focus-${UUID.randomUUID()}").apply {mkdirs()}
        val context=object:ContextWrapper(base) {
            override fun getApplicationContext()=this
            override fun getFilesDir()=File(root,"files").apply {mkdirs()}
            override fun getNoBackupFilesDir()=File(root,"private").apply {mkdirs()}
        }
        var authority=true
        val access=VaultCaptureAccess(context,null,{ByteArray(32){5}},{authority})
        lateinit var controller:VaultCameraController
        lateinit var preview:PreviewView
        lateinit var owner:LifecycleOwner
        val feedback=java.util.concurrent.CopyOnWriteArrayList<String>()
        val retired=java.util.concurrent.CopyOnWriteArrayList<String>()
        var retiredCount=0
        try {
            compose.setContent {
                owner=LocalLifecycleOwner.current
                val local=LocalContext.current
                preview=remember {PreviewView(local).apply {implementationMode=PreviewView.ImplementationMode.COMPATIBLE}}
                controller=remember {VaultCameraController(context,access)}
                AndroidView(factory={preview},modifier=Modifier.fillMaxSize())
                LaunchedEffect(Unit) {assertTrue(controller.enter());controller.bind(owner,preview,false,false,Quality.HD)}
            }
            compose.waitUntil(30_000){controller.bound&&preview.width>0&&preview.height>0}
            compose.runOnIdle {
                // Actual PreviewView coordinates travel through its CameraX metering factory.
                controller.focus(preview,preview.width*.75f,preview.height*.25f,feedback::add)
            }
            compose.waitUntil(15_000){feedback.isNotEmpty()&&feedback.last()!="Focusing…"}
            compose.runOnIdle {
                assertTrue(feedback.last() in setOf("Focus confirmed","Focus not confirmed",
                    "Focus unavailable","Tap focus unavailable on this camera"))
                if(feedback.last() in setOf("Focus confirmed","Focus not confirmed"))assertEquals("Focusing…",feedback.first())
                val count=feedback.size
                controller.focus(preview,-1f,10f,feedback::add)
                controller.focus(preview,preview.width.toFloat(),10f,feedback::add)
                assertEquals("Outside-preview requests cannot report focus",count,feedback.size)
                controller.focus(preview,preview.width*.5f,preview.height*.5f,retired::add)
                // Invalidate before the asynchronous CameraX listener can run on this main executor.
                assertTrue(controller.pauseForSettings())
                retiredCount=retired.size
                controller.focus(preview,10f,10f) {error("Unbound focus accepted")}
                assertEquals(retiredCount,retired.size)
                assertTrue(controller.enter())
                controller.bind(owner,preview,false,false,Quality.HD)
            }
            compose.waitUntil(30_000){controller.bound}
            val reboundFeedback=java.util.concurrent.CopyOnWriteArrayList<String>()
            compose.runOnIdle {controller.focus(preview,preview.width*.5f,preview.height*.5f,reboundFeedback::add)}
            compose.waitUntil(15_000){reboundFeedback.isNotEmpty()&&reboundFeedback.last()!="Focusing…"}
            compose.runOnIdle {
                assertTrue("The rebound camera must produce its own terminal focus result",reboundFeedback.last() in
                    setOf("Focus confirmed","Focus not confirmed","Focus unavailable","Tap focus unavailable on this camera"))
                assertEquals("Old-binding callbacks must be discarded",retiredCount,retired.size)
                authority=false
                controller.focus(preview,10f,10f) {error("Ownerless focus accepted")}
                controller.revoke()
                controller.focus(preview,10f,10f) {error("Revoked focus accepted")}
                assertTrue("Focus never creates capture bytes",access.journal.entries().isEmpty())
            }
        } finally {
            compose.runOnIdle {controller.revoke()}
            root.deleteRecursively()
        }
    }
}
