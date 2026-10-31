package uk.co.traynor.privategallery.core.evidence

import android.content.Context
import android.content.ContextWrapper
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import uk.co.traynor.privategallery.core.hidden.HiddenSession

class EvidenceRouteCoordinator179Test {
 private class Fixture {
  val app=InstrumentationRegistry.getInstrumentation().targetContext
  val root=File(app.cacheDir,"evidence-route179-${UUID.randomUUID()}")
  val context=object:ContextWrapper(app){
   override fun getFilesDir()=File(root,"files").apply{mkdirs()}
   override fun getNoBackupFilesDir()=File(root,"no-backup").apply{mkdirs()}
   override fun getApplicationContext():Context=this
  }
  val hidden=HiddenSession{android.os.SystemClock.elapsedRealtime()}.also{check(it.complete(it.beginAuthentication(),ByteArray(32){3}))}
  val valid=AtomicBoolean(true);val access=EvidenceAccess.hidden(context,hidden){valid.get()}
  val prepared=AtomicInteger();val released=AtomicInteger();val networks=AtomicInteger()
  suspend fun coordinator(admission:()->Boolean={true},process:()->Boolean={false},prepare:suspend ()->Unit={}):EvidenceRouteCoordinator = withContext(Dispatchers.Main.immediate){
   EvidenceRouteCoordinator(context,access,{networks.incrementAndGet();null},{true},{prepared.incrementAndGet();prepare()},{released.incrementAndGet();Unit},{false},
    browserAdmissionRetired=admission,dedicatedBrowserPresent=process,permissionGranted={true})
  }
  fun close(){access.close();hidden.lock();root.deleteRecursively()}
 }
 @Test fun hiddenCannotChooseSystemWithoutAnyTransportSideEffects()=runBlocking {
  val f=Fixture();val coordinator=f.coordinator()
  try {
   assertTrue(f.access.current())
   try{coordinator.connect("System");fail("Hidden direct route allowed")}catch(expected:IllegalArgumentException){}
   assertEquals(0,f.prepared.get());assertEquals(0,f.released.get());assertEquals(0,f.networks.get())
  }finally{coordinator.close();f.close()}
 }
 @Test fun independentlyLiveRevokedStartupHasNoTransportSideEffects()=runBlocking {
  val f=Fixture();val coordinator=f.coordinator()
  try {
   // This coordinator has never attempted another mode or entered its catch/close path.
   assertTrue(f.access.current());assertTrue(f.hidden.isUnlocked)
   f.valid.set(false);f.access.close()
   try{coordinator.connect("Tor");fail("revoked transport started")}catch(expected:Exception){}
   assertEquals(0,f.prepared.get());assertEquals(0,f.released.get());assertEquals(0,f.networks.get())
  }finally{coordinator.close();f.close()}
 }
 @Test fun vpnAdmissionAndDedicatedProcessFenceBothPrecedePreparationAndReadiness()=runBlocking {
  for(blockedAdmission in listOf(true,false)) {
   val f=Fixture();val coordinator=f.coordinator(admission={!blockedAdmission},process={!blockedAdmission})
   try {
    assertTrue(f.access.current())
    try{coordinator.connect("VPN");fail("browser boundary was bypassed")}catch(expected:IllegalStateException){assertTrue(expected.message!!.contains("browser process"))}
    assertEquals(0,f.prepared.get());assertEquals(0,f.networks.get());assertEquals(0,f.released.get())
   }finally{coordinator.close();f.close()}
  }
 }
 @Test fun revokedDuringPreparationCannotProbeReadinessOrPublishRoute()=runBlocking {
  val f=Fixture();val entered=CompletableDeferred<Unit>();val finish=CompletableDeferred<Unit>()
  val coordinator=f.coordinator(prepare={entered.complete(Unit);finish.await()})
  val operation=async{runCatching{coordinator.connect("VPN")}}
  try {
   withTimeout(10000){entered.await()};assertEquals(1,f.prepared.get());assertTrue(f.access.current())
   assertEquals(0,f.networks.get())
   f.valid.set(false);f.access.close();finish.complete(Unit)
   assertTrue(withTimeout(10000){operation.await()}.isFailure)
   assertEquals(0,f.networks.get());assertEquals(0,f.released.get())
  }finally{finish.complete(Unit);operation.cancel();coordinator.close();f.close()}
 }
 @Test fun startupCancellationDuringPreparationCannotProbeOrPublishRoute()=runBlocking {
  val f=Fixture();val entered=CompletableDeferred<Unit>()
  val coordinator=f.coordinator(prepare={entered.complete(Unit);awaitCancellation()})
  val operation=launch{coordinator.connect("VPN")}
  try {
   withTimeout(10000){entered.await()};assertEquals(1,f.prepared.get())
   operation.cancelAndJoin();assertEquals(0,f.networks.get());assertEquals(0,f.released.get())
  }finally{operation.cancel();coordinator.close();f.close()}
 }
}
