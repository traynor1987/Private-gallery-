package uk.co.traynor.privategallery.core.home2

import android.content.ContextWrapper
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import uk.co.traynor.privategallery.core.evidence.EvidenceAccess
import uk.co.traynor.privategallery.home2.IntegratedHome

class MindForegroundLifecycleTest {
 @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
 @Test fun real_mind_controller_cannot_resume_after_pause_or_authority_revocation(){
  val activity=compose.activity
  val root=File(activity.cacheDir,"mind-foreground-${UUID.randomUUID()}")
  val context=object:ContextWrapper(activity){override fun getFilesDir()=File(root,"files").apply{mkdirs()};override fun getNoBackupFilesDir()=File(root,"private").apply{mkdirs()}}
  val key=ByteArray(32){58};val identity=Any();val alive=java.util.concurrent.atomic.AtomicBoolean(true)
  val access=EvidenceAccess.primary(context,identity,{identity},{alive.get()},{key.copyOf()},Any())
  val attached=mutableStateOf(true)
  fun controllers()=(activity.getSystemService(android.content.Context.MEDIA_SESSION_SERVICE) as MediaSessionManager).getActiveSessionsForPackage(activity.packageName).map { MediaController(activity,it) }
  try{
   compose.setContent{if(attached.value)CompositionLocalProvider(LocalContext provides context){IntegratedHome(access,true,{},{},{},null,null)}}
   compose.onNodeWithContentDescription("Home page 2").performClick()
   compose.onNodeWithTag("app-mind").performClick()
   compose.onNodeWithTag("mind-sounds").performScrollTo().performClick()
   compose.onNodeWithText("Rain").performClick()
   lateinit var controller:MediaController
   compose.waitUntil(10000){controllers().firstOrNull{it.tag=="Nova calm sounds"&&it.playbackState?.state==PlaybackState.STATE_PLAYING}?.let{controller=it;true}?:false}
   compose.activityRule.scenario.moveToState(Lifecycle.State.STARTED)
   controller.transportControls.play();controller.transportControls.skipToNext()
   val drained=CountDownLatch(1);android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({drained.countDown()},250)
   assertTrue(drained.await(5,TimeUnit.SECONDS))
   assertNotEquals("Retained controller cannot restart audio while Activity is paused",PlaybackState.STATE_PLAYING,controller.playbackState?.state)
   compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
   compose.onNodeWithTag("calm-sound-toggle").performScrollTo().performClick()
   compose.waitUntil(10000){controller.playbackState?.state==PlaybackState.STATE_PLAYING}
   compose.runOnIdle{alive.set(false);access.close()}
   controller.transportControls.play()
   val retired=CountDownLatch(1);android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({retired.countDown()},250)
   assertTrue(retired.await(5,TimeUnit.SECONDS))
   assertNotEquals(PlaybackState.STATE_PLAYING,controller.playbackState?.state)
  }finally{
   compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
   compose.runOnIdle{attached.value=false};access.close();key.fill(0);root.deleteRecursively()
  }
 }
}
