package uk.co.traynor.privategallery.core.evidence

import android.net.Uri
import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.*
import org.junit.Test

/** Actual EvidenceAccess/leases, with unused Android media sources replaced by a dependency fixture. */
class EvidenceChildAuthority62Test {
 private class Origin:AutoCloseable {
  val directory=Files.createTempDirectory("child-authority62").toFile()
  val gate=Any();val identity=Any()
  private val sources:(EvidenceAccess)->EvidenceSources={object:EvidenceSources {
   override fun list()=emptyList<EvidenceOriginal>()
   override fun inspect(sourceId:String):EvidenceOriginal=error("No media fixture")
   override fun <T> readVerified(sourceId:String,cancelled:()->Boolean,consume:(EvidenceOriginal,InputStream)->T):T=error("No media fixture")
   override fun import(uri:Uri):EvidenceOriginal=error("No media fixture")
  }}
  val access:EvidenceAccess
  init {
   val leases=EvidenceAuthorities.primary(identity,{identity},{true},{ByteArray(32){41}},gate)
   val guard:()->Boolean={true};val boundary:((()->Unit)->Unit)={action->synchronized(gate){action()}}
   val constructor=EvidenceAccess::class.java.declaredConstructors.single{it.parameterTypes.size==7}.apply{isAccessible=true}
   access=constructor.newInstance(EvidenceCatalog.Owner.PRIMARY,directory,leases,guard,boundary,sources,null) as EvidenceAccess
  }
  override fun close(){access.close();directory.deleteRecursively()}
 }
 @Test fun originRetiredBeforeCommitCannotPromoteOrPublishVerifiedHistory()=Origin().use {origin->
  origin.access.primaryChild{true}.use {child->
   val before=File(origin.directory,"history").apply{writeText("Unverified")};val staged=File(origin.directory,"staged").apply{writeText("Verified")}
   val ready=CountDownLatch(1);val proceed=CountDownLatch(1);val worker=Executors.newSingleThreadExecutor();val published=AtomicBoolean()
   try {
    child.leases.acquire().use {lease->
     val result=worker.submit<Boolean>{ready.countDown();check(proceed.await(5,TimeUnit.SECONDS));runCatching{lease.commit{published.set(true);Files.move(staged.toPath(),before.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING)}}.isSuccess}
     assertTrue(ready.await(5,TimeUnit.SECONDS));origin.access.close();proceed.countDown()
     assertFalse(result.get(5,TimeUnit.SECONDS));assertFalse(published.get());assertEquals("Unverified",before.readText());assertTrue(staged.exists())
    }
   }finally{proceed.countDown();worker.shutdownNow()}
  }
 }
 @Test fun linearizedChildCommitHoldsOriginBoundaryUntilPublicationCompletes()=Origin().use {origin->
  origin.access.primaryChild{true}.use {child->
   val entered=CountDownLatch(1);val release=CountDownLatch(1);val attempted=CountDownLatch(1);val retired=CountDownLatch(1);val published=AtomicBoolean();val worker=Executors.newSingleThreadExecutor()
   val retirement=Thread{attempted.countDown();origin.access.close();assertTrue(published.get());retired.countDown()}
   try {
    child.leases.acquire().use {lease->
     val result=worker.submit<Boolean>{lease.commit{assertTrue("Child commit uses exact originating monitor",Thread.holdsLock(origin.gate));entered.countDown();check(release.await(5,TimeUnit.SECONDS));published.set(true)};true}
     assertTrue(entered.await(5,TimeUnit.SECONDS));retirement.start();assertTrue(attempted.await(5,TimeUnit.SECONDS))
     val until=System.nanoTime()+TimeUnit.SECONDS.toNanos(5)
     while(retirement.state!=Thread.State.BLOCKED&&retired.count>0&&System.nanoTime()<until)Thread.yield()
     assertEquals(Thread.State.BLOCKED,retirement.state);assertEquals(1L,retired.count)
     release.countDown();assertTrue(result.get(5,TimeUnit.SECONDS));assertTrue(retired.await(5,TimeUnit.SECONDS));assertFalse(origin.access.current())
    }
   }finally{release.countDown();retirement.join(5000);worker.shutdownNow()}
  }
 }
 @Test fun childCancellationWipesItsLeaseAndResourcesWithoutRetiringOrigin()=Origin().use {origin->
  val child=origin.access.primaryChild{true};val lease=child.leases.acquire();val bytes=lease.key();val closed=CountDownLatch(1)
  EvidenceAccess::class.java.getDeclaredMethod("own",AutoCloseable::class.java).apply{isAccessible=true}.invoke(child,AutoCloseable{closed.countDown()});child.close()
  assertFalse(child.current());assertFalse(lease.current());assertTrue(closed.await(5,TimeUnit.SECONDS))
  lease.close()
  // Retirement cleanup is asynchronous; its resource callback precedes lease erasure.
  fun wiped()=synchronized(origin.gate){bytes.all{it==0.toByte()}}
  val until=System.nanoTime()+TimeUnit.SECONDS.toNanos(5)
  while(!wiped()&&System.nanoTime()<until)Thread.yield()
  assertTrue("Child-owned key erased after retirement cleanup",wiped());assertTrue("Origin still usable",origin.access.current())
  origin.access.leases.acquire().use{var published=false;it.commit{published=true};assertTrue(published)}
 }
}
