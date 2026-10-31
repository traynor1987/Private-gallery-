package uk.co.traynor.privategallery.core.evidence

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class EvidenceRouteCoordinator179Test {
 @Test fun hiddenCannotChooseSystemAndRevokedStartupHasNoTransportSideEffects()=runBlocking {
  val app=InstrumentationRegistry.getInstrumentation().targetContext
  val root=java.io.File(app.cacheDir,"evidence-route179-${java.util.UUID.randomUUID()}")
  val context=object:android.content.ContextWrapper(app){
   override fun getFilesDir()=java.io.File(root,"files").apply{mkdirs()}
   override fun getNoBackupFilesDir()=java.io.File(root,"no-backup").apply{mkdirs()}
   override fun getApplicationContext():android.content.Context=this
  }
  val hidden=uk.co.traynor.privategallery.core.hidden.HiddenSession{android.os.SystemClock.elapsedRealtime()};check(hidden.complete(hidden.beginAuthentication(),ByteArray(32){3}))
  val valid=AtomicBoolean(true);val access=EvidenceAccess.hidden(context,hidden){valid.get()}
  val prepared=AtomicInteger();val released=AtomicInteger()
  val coordinator=withContext(Dispatchers.Main.immediate){EvidenceRouteCoordinator(context,access,{null},{true},{prepared.incrementAndGet();Unit},{released.incrementAndGet();Unit},{false})}
  try {
   try{coordinator.connect("System");fail("Hidden direct route allowed")}catch(expected:IllegalArgumentException){}
   assertEquals(0,prepared.get());assertEquals(0,released.get())
   valid.set(false);access.close()
   try{coordinator.connect("Tor");fail("revoked transport started")}catch(expected:Exception){}
   assertEquals(0,prepared.get());assertEquals(0,released.get())
   assertTrue(hidden.isUnlocked)
  }finally{coordinator.close();access.close();hidden.lock();root.deleteRecursively()}
 }
}
