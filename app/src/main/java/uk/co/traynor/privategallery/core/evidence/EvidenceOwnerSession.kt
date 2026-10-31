package uk.co.traynor.privategallery.core.evidence

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/** An opaque original-authentication ticket owner. Factories capture epochs, never a copied vault key. */
internal class EvidenceOwnerSession(
 val owner:EvidenceCatalog.Owner,private val authenticationCurrent:()->Boolean,
 private val accessFactory:()->EvidenceAccess?,private val routeFactory:(EvidenceAccess)->EvidenceRouteConnection,
 private val primarySystemAllowed:()->Boolean,val ciphertextStaging:File,
 private val lockOriginating:()->Unit,private val panic:()->Unit,private val hide:()->Unit,
):AutoCloseable {
 private val closed=AtomicBoolean()
 private val main=Handler(Looper.getMainLooper())
 private val controllers=LinkedHashSet<EvidenceController>()
 private val stages=LinkedHashSet<CipherStage>()
 fun sameAuthentication():Boolean {
  if(closed.get())return false
  if(!authenticationCurrent()){close();return false}
  return true
 }
 fun newController(context:Context):EvidenceController? {
  check(Looper.myLooper()===Looper.getMainLooper())
  if(!sameAuthentication())return null
  val access=accessFactory()?:return null
  try {
   check(access.owner==owner&&sameAuthentication()&&access.current())
   return EvidenceController(context,access).also{controllers.add(it)}
  }catch(failure:Throwable){access.close();throw failure}
 }
 fun retire(controller:EvidenceController){
  check(Looper.myLooper()===Looper.getMainLooper());controllers.remove(controller);controller.close()
 }
 fun routes(controller:EvidenceController):EvidenceRouteConnection {
  check(Looper.myLooper()===Looper.getMainLooper())
  check(sameAuthentication()&&controller in controllers&&controller.access.owner==owner&&controller.current())
  return routeFactory(controller.access)
 }
 fun lockVault(){if(sameAuthentication()){close();lockOriginating()}}
 fun panicExit(){if(sameAuthentication()){close();panic()}}
 fun hideContent(){if(sameAuthentication()){close();hide()}}
 fun newCipherStage():CipherStage {
  check(Looper.myLooper()===Looper.getMainLooper());check(sameAuthentication());require(stages.size<2)
  check(ciphertextStaging.mkdirs()||ciphertextStaging.isDirectory);android.system.Os.chmod(ciphertextStaging.absolutePath,448)
  val file=File(ciphertextStaging,java.util.UUID.randomUUID().toString()+".cipher")
  check(file.createNewFile());android.system.Os.chmod(file.absolutePath,384)
  return try{CipherStage(file,java.io.FileOutputStream(file)).also{stages.add(it)}}catch(failure:Throwable){file.delete();throw failure}
 }
 fun discard(stage:CipherStage){check(Looper.myLooper()===Looper.getMainLooper());if(stages.remove(stage))stage.close();ciphertextStaging.delete()}
 class CipherStage internal constructor(val file:File,val output:java.io.FileOutputStream):AutoCloseable {
  private val discarded=AtomicBoolean()
  @Volatile var sealed=false;private set
  fun seal(){check(!discarded.get());output.flush();output.fd.sync();output.close();check(!discarded.get());android.system.Os.chmod(file.absolutePath,256);sealed=true}
  fun current()=sealed&&!discarded.get()&&file.isFile
  override fun close(){if(discarded.compareAndSet(false,true)){runCatching{output.close()};file.delete()}}
 }
 fun retirePresentations(){check(Looper.myLooper()===Looper.getMainLooper());controllers.toList().forEach{it.close()};controllers.clear()}
 fun allowsSystem()=owner==EvidenceCatalog.Owner.PRIMARY&&sameAuthentication()&&primarySystemAllowed()
 override fun close(){
  if(!closed.compareAndSet(false,true))return
  val clear=Runnable{controllers.toList().forEach{it.close()};controllers.clear();stages.toList().forEach{it.close()};stages.clear();ciphertextStaging.delete()}
  // A guard can retire this owner while an authentication monitor is held.
  // Resource cleanup must always run after that short boundary, never inline.
  main.post(clear)
 }
}
