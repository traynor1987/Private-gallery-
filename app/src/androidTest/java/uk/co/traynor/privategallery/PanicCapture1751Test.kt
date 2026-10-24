package uk.co.traynor.privategallery

import android.content.ContextWrapper
import androidx.activity.compose.setContent
import androidx.camera.video.Quality
import androidx.camera.view.PreviewView
import androidx.compose.runtime.*
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID
import uk.co.traynor.privategallery.core.camera.*

/** Real CameraX on disposable emulator cameras; isolated synthetic journals only. */
class PanicCapture1751Test {
 @get:Rule val compose=createAndroidComposeRule<MainActivity>()
 @Test fun primaryRecordingPanicRetainsRecoverableEncryptedCapture()=capture(false)
 @Test fun hiddenRecordingPanicRetainsOriginalDestinationAndRequiresFreshAuthentication()=capture(true)
 private fun capture(hidden:Boolean) {
  val base=compose.activity.applicationContext
  android.os.ParcelFileDescriptor.AutoCloseInputStream(InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("pm grant ${base.packageName} android.permission.CAMERA")).use {it.readBytes()}
  val root=File(base.cacheDir,"panic-capture-${UUID.randomUUID()}").apply {mkdirs()}
  val context=object:ContextWrapper(base) {
   override fun getApplicationContext()=this
   override fun getFilesDir()=File(root,"files").apply {mkdirs()}
   override fun getNoBackupFilesDir()=File(root,"private").apply {mkdirs()}
  }
  val owner=ViewModelProvider(compose.activity)[ProtectedSessionState::class.java]
  var cleanup:VaultCameraController?=null
  lateinit var controller:VaultCameraController
  val access=VaultCaptureAccess(context,null,{ByteArray(32){2}},{true})
  try {
   // Hidden authority must exist before constructing its capture access.
   compose.runOnIdle {
    owner.key=ByteArray(32){2};owner.session.unlock()
    if(hidden) {owner.hidden.session.complete(owner.hidden.session.beginAuthentication(),ByteArray(32){7});owner.hidden.select()}
   }
   val actualAccess=if(hidden)VaultCaptureAccess(context,owner.hidden.session,{null},{true}) else access
   controller=VaultCameraController(context,actualAccess);cleanup=controller
   @Suppress("UNCHECKED_CAST")
   val camera=MainActivity::class.java.getDeclaredField("vaultCamera\$delegate").apply {isAccessible=true}.get(compose.activity) as MutableState<VaultCameraController?>
   compose.runOnIdle {camera.value=controller;compose.activity.setContent {
    val lifecycleOwner=LocalLifecycleOwner.current
    AndroidView(factory={PreviewView(it).also {preview->check(controller.enter());controller.bind(lifecycleOwner,preview,true,false,Quality.HD)}})
   }}
   compose.waitUntil(30000){controller.bound}
   compose.runOnIdle {controller.input(true,false)}
   compose.waitUntil(30000){controller.recordingNow && controller.elapsed>=2}
   compose.runOnIdle {
    compose.activity.panicExit()
    assertFalse(controller.recordingNow);assertFalse(controller.bound);assertFalse(actualAccess.allowed())
    assertNull(camera.value);assertNull(owner.key);assertFalse(owner.session.isUnlocked);assertFalse(owner.hidden.selected);assertFalse(owner.hidden.session.isUnlocked)
    assertTrue(owner.automaticBiometricPromptAttempted)
   }
   compose.waitUntil(45000){controller.completedRevision>0}
   assertEquals(1,actualAccess.journal.entries().size)
   if(hidden) owner.hidden.session.complete(owner.hidden.session.beginAuthentication(),ByteArray(32){7})
   val fresh=VaultCaptureAccess(context,if(hidden)owner.hidden.session else null,{ByteArray(32){2}},{true})
   assertEquals(if(hidden)"hidden" else "primary",fresh.domain)
   fresh.recover(fresh.journal.entries().single()).use {ticket->assertTrue(ticket.size>0);assertFalse(ticket.plain.exists())}
   assertEquals(1,fresh.journal.entries().size)
  } finally {
   compose.runOnIdle {cleanup?.revoke();owner.hidden.leave()}
   root.deleteRecursively()
  }
 }
}
