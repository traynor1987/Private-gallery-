package uk.co.traynor.privategallery.core.security

import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

/** Complete original producer; phase closure never transfers authority or awaits below a gate. */
class OwnedPhaseRetirementTest {
 @Test fun phaseReturnsItsSlotButRootFactoryRemainsChargedThroughRevocation() {
  val authority=testPrimaryAuthority();authority.open(ByteArray(32));val operation=authority.operationOrNull(setOf(PrimaryScope.READ))!!
  val phase=CountDownLatch(1);val allow=CountDownLatch(1);val done=CountDownLatch(1)
  val rootClosed=CountDownLatch(1);val outputClosed=AtomicInteger();val failure=AtomicReference<Throwable>()
  val worker=thread(isDaemon=true) {
   try { operation.createOwned(OwnedResourceManifest.io("lifetime","output")) {
    val root=create("lifetime", { _: Any -> rootClosed.countDown() }) { Any() }
    create("output", { _: Any -> outputClosed.incrementAndGet() }) { Any() }
    retireChildren("output");phase.countDown();allow.await();root
   };fail("revoked factory cannot publish") } catch(p: Throwable) { failure.set(p) } finally { done.countDown() }
  }
  try {
   assertTrue(phase.await(5,TimeUnit.SECONDS));assertEquals(1,outputClosed.get())
   assertEquals(1,ioPool(authority).occupied);authority.revoke();assertTrue(rootClosed.await(5,TimeUnit.SECONDS))
   assertFalse(authority.cleanupComplete);assertEquals(1,ioPool(authority).occupied)
   val fresh=ByteArray(32) { 9 };assertThrows(IllegalStateException::class.java) { authority.open(fresh) };assertArrayEquals(ByteArray(32),fresh)
   allow.countDown();assertTrue(done.await(5,TimeUnit.SECONDS));awaitCleanup(authority);assertTrue(failure.get() is IllegalStateException)
   assertEquals(1,outputClosed.get());assertEquals(0,ioPool(authority).occupied)
  } finally { allow.countDown();authority.revoke();worker.interrupt();assertTrue(done.await(5,TimeUnit.SECONDS)) }
 }
 @Test fun phaseDispatchesEveryIndependentUnblockerBeforeAwaitingAnyChild() = isolated { authority,op ->
  val disconnected=CountDownLatch(1);val inputClosed=AtomicInteger();val connectionClosed=AtomicInteger()
  val root=op.createOwned(OwnedResourceManifest.io("lifetime","input","connection")) {
   val lifetime=create("lifetime", { _: Any -> }) { Any() }
   attach("input",AutoCloseable { check(disconnected.await(5,TimeUnit.SECONDS));inputClosed.incrementAndGet() })
   attach("connection",AutoCloseable { connectionClosed.incrementAndGet();disconnected.countDown() })
   retireChildren("input","connection")
   assertEquals(1,inputClosed.get());assertEquals(1,connectionClosed.get());assertEquals(1,ioPool(authority).occupied)
   lifetime
  }
  root.close();assertTrue(root.retirement.await(5,TimeUnit.SECONDS));assertEquals(0,ioPool(authority).occupied)
  assertEquals(1,inputClosed.get());assertEquals(1,connectionClosed.get())
 }
 @Test fun unusedPhaseCannotLaterCreateANativeChildOrBeReused() = isolated { authority,op ->
  var calls=0
  val root=op.createOwned(OwnedResourceManifest.io("lifetime","unused1","unused2")) {
   val lifetime=create("lifetime", { _: Any -> }) { Any() }
   discardChildren("unused1","unused2");assertEquals(1,ioPool(authority).occupied)
   assertThrows(IllegalStateException::class.java) { create("unused1", { _: Any -> }) { calls++;Any() } }
   assertThrows(IllegalStateException::class.java) { discardChildren("unused2") }
   lifetime
  }
  assertEquals(0,calls);root.close();assertTrue(root.retirement.await(5,TimeUnit.SECONDS))
 }
 @Test fun malformedPhaseAndLifetimeRetirementDenyBeforeAnyChildChanges() = isolated { authority,op ->
  val closed=AtomicInteger()
  val root=op.createOwned(OwnedResourceManifest.io("lifetime","output","unused")) {
   val lifetime=create("lifetime", { _: Any -> closed.incrementAndGet() }) { Any() }
   create("output", { _: Any -> closed.incrementAndGet() }) { Any() }
   assertThrows(IllegalStateException::class.java) { retireChildren("output","lifetime") }
   assertThrows(IllegalArgumentException::class.java) { retireChildren("output","output") }
   assertThrows(IllegalArgumentException::class.java) { retireChildren("missing") }
   assertThrows(IllegalStateException::class.java) { retireChildren("unused") }
   assertThrows(IllegalStateException::class.java) { discardChildren("output") }
   assertEquals(0,closed.get());assertEquals(3,ioPool(authority).occupied)
   retireChildren("output");discardChildren("unused");assertEquals(1,closed.get());assertEquals(1,ioPool(authority).occupied)
   lifetime
  }
  root.close();assertTrue(root.retirement.await(5,TimeUnit.SECONDS));assertEquals(2,closed.get())
 }
 @Test fun failedPhaseDispatchesOtherChildrenAndDeniesEveryLaterFactory() {
  val authority=testPrimaryAuthority();authority.open(ByteArray(32));val op=authority.operationOrNull(setOf(PrimaryScope.READ))!!
  val otherClosed=CountDownLatch(1);var later=0
  try {
   assertThrows(IOException::class.java) { op.createOwned(OwnedResourceManifest.io("lifetime","bad","other","later")) {
    val lifetime=create("lifetime", { _: Any -> }) { Any() }
    attach("bad",AutoCloseable { throw IOException("synthetic phase close failure") })
    attach("other",AutoCloseable { otherClosed.countDown() })
    try { retireChildren("bad","other") } catch(p: IOException) {
     assertThrows(IllegalStateException::class.java) { create("later", { _: Any -> }) { later++;Any() } };throw p
    }
    lifetime
   } }
   assertTrue(otherClosed.await(5,TimeUnit.SECONDS));assertEquals(0,later);authority.revoke();assertFalse(authority.cleanupComplete)
   val fresh=ByteArray(32) { 7 };assertThrows(IllegalStateException::class.java) { authority.open(fresh) };assertArrayEquals(ByteArray(32),fresh)
  } finally { op.close();authority.revoke() }
 }
 @Test fun capturedFactoryCannotRetireChildrenAfterEnclosingFactoryReturns() = isolated { _,op ->
  lateinit var scope: OwnedFactoryScope;val closed=AtomicInteger()
  val root=op.createOwned(OwnedResourceManifest.io("lifetime","output")) {
   scope=this;val lifetime=create("lifetime", { _: Any -> closed.incrementAndGet() }) { Any() }
   create("output", { _: Any -> closed.incrementAndGet() }) { Any() };lifetime
  }
  assertThrows(IllegalStateException::class.java) { scope.retireChildren("output") };assertEquals(0,closed.get())
  root.close();assertTrue(root.retirement.await(5,TimeUnit.SECONDS));assertEquals(2,closed.get())
 }
 @Test fun earlyNativeSignalCannotCompletePhaseBeforeActualCloseReturns() {
  val authority=testPrimaryAuthority();authority.open(ByteArray(32));val op=authority.operationOrNull(setOf(PrimaryScope.READ))!!
  val entered=CountDownLatch(1);val allow=CountDownLatch(1);val phase=CountDownLatch(1);val done=CountDownLatch(1)
  val signal=CompletableFuture.completedFuture(Unit);val result=AtomicReference<OwnedResource<*>>();val failure=AtomicReference<Throwable>()
  val worker=thread(isDaemon=true) {
   try { result.set(op.createOwned(OwnedResourceManifest.io("lifetime","native")) {
    val lifetime=create("lifetime", { _: Any -> }) { Any() }
    attach("native",object : AcknowledgedCloseable { override fun closeAcknowledged(): java.util.concurrent.CompletionStage<Unit> { entered.countDown();allow.await();return signal } })
    retireChildren("native");phase.countDown();lifetime
   }) } catch(p: Throwable) { failure.set(p) } finally { done.countDown() }
  }
  try {
   assertTrue(entered.await(5,TimeUnit.SECONDS));assertEquals(1L,phase.count);assertEquals(2,ioPool(authority).occupied)
   allow.countDown();assertTrue(done.await(5,TimeUnit.SECONDS));assertNull(failure.get());assertEquals(0L,phase.count)
   assertEquals(1,ioPool(authority).occupied);result.get().close();assertTrue(result.get().retirement.await(5,TimeUnit.SECONDS))
  } finally { allow.countDown();op.close();authority.revoke();worker.interrupt();assertTrue(done.await(5,TimeUnit.SECONDS)) }
 }
 @Test fun retiredActualCannotBeAcquiredByAnotherNativeChild() = isolated { _,op ->
  val closes=AtomicInteger();val actual=AutoCloseable { closes.incrementAndGet() }
  assertThrows(IllegalStateException::class.java) { op.createOwned(OwnedResourceManifest.io("lifetime","first","second")) {
   val lifetime=create("lifetime", { _: Any -> }) { Any() }
   create("first", { child: AutoCloseable -> child.close() }) { actual };retireChildren("first")
   assertEquals(1,closes.get());create("second", { child: AutoCloseable -> child.close() }) { actual };lifetime
  } }
  assertEquals(1,closes.get())
 }
 @Test fun retiredDirectChildCannotBeAttachedAgain() = isolated { _,op ->
  val closes=AtomicInteger();val actual=AutoCloseable { closes.incrementAndGet() }
  assertThrows(IllegalStateException::class.java) { op.createOwned(OwnedResourceManifest.io("lifetime","first","second")) {
   val lifetime=create("lifetime", { _: Any -> }) { Any() }
   attach("first",actual);retireChildren("first");assertEquals(1,closes.get());attach("second",actual);lifetime
  } }
  assertEquals(1,closes.get())
 }
 @Test fun retiredActualCannotBecomeMalformedRootResult() = isolated { authority,op ->
  val closes=AtomicInteger();val actual=AutoCloseable { closes.incrementAndGet() }
  assertThrows(IllegalStateException::class.java) { op.createOwned(OwnedResourceManifest.io("lifetime","first")) {
   create("first", { child: AutoCloseable -> child.close() }) { actual };retireChildren("first");actual
  } }
  awaitCleanup(authority);assertEquals(1,closes.get())
 }
 @Test fun retiredJobCannotBeReboundToAnotherOriginalChild() = isolated { _,op ->
  val cancels=AtomicInteger();val native=kotlinx.coroutines.Job()
  val actual=object : kotlinx.coroutines.Job by native {
   override fun cancel(cause: java.util.concurrent.CancellationException?) { cancels.incrementAndGet();native.cancel(cause) }
  }
  assertThrows(IllegalStateException::class.java) { op.createOwned(OwnedResourceManifest.io("lifetime","first","second")) {
   val lifetime=create("lifetime", { _: Any -> }) { Any() }
   original.createJob(1) { attach -> actual.also(attach) };retireChildren("first")
   assertEquals(1,cancels.get());original.createJob(2) { attach -> actual.also(attach) };lifetime
  } }
  assertEquals(1,cancels.get())
 }
 @Test fun nativeFactoryCannotRetireItsOwnUnattachedResult() = isolated { authority,op ->
  val closes=AtomicInteger()
  val root=op.createOwned(OwnedResourceManifest.io("lifetime","late")) {
   val lifetime=create("lifetime", { _: Any -> }) { Any() }
   create("late", { _: Any -> closes.incrementAndGet() }) {
    assertThrows(IllegalStateException::class.java) { retireChildren("late") }
    assertEquals(2,ioPool(authority).occupied);Any()
   }
   retireChildren("late");assertEquals(1,closes.get());lifetime
  }
  root.close();assertTrue(root.retirement.await(5,TimeUnit.SECONDS));assertEquals(1,closes.get())
 }
 @Test fun jobFactoryCannotSealBeforeHookAndFactoryReturn() = isolated { authority,op ->
  val native=kotlinx.coroutines.Job()
  val root=op.createOwned(OwnedResourceManifest.io("lifetime","job")) {
   val lifetime=create("lifetime", { _: Any -> }) { Any() }
   original.createJob(1) { attach ->
    attach(native)
    assertThrows(IllegalStateException::class.java) { retireChildren("job") }
    assertEquals(2,ioPool(authority).occupied);native
   }
   retireChildren("job");assertTrue(native.isCompleted);lifetime
  }
  root.close();assertTrue(root.retirement.await(5,TimeUnit.SECONDS))
 }
 @Test fun arrayCopierCannotRetireBeforeActualCopyAttaches() {
  val pool=ReleasePool(2);lateinit var original: ReleaseReservation;val actual=AtomicReference<ByteArray>()
  original=ReleaseReservation(pool.reserveAll(Any(),2),copyArray={ source ->
   assertThrows(IllegalStateException::class.java) { original.retirePhase(intArrayOf(1),absent=false) }
   assertEquals(2,pool.occupied);source.copyOf().also(actual::set)
  })
  original.onRetirementAccounting {}
  try {
   original.construct {
    attach(0,AutoCloseable {});val bytes=copyBytes(1,byteArrayOf(9))
    assertArrayEquals(byteArrayOf(9),bytes);retirePhase(intArrayOf(1),absent=false)
    assertTrue(actual.get().all { it==0.toByte() });assertEquals(1,pool.occupied)
   }
  } finally { original.release() }
  assertTrue(original.terminalRetirement.await(5,TimeUnit.SECONDS));assertEquals(0,pool.occupied)
 }
 @Test fun failedUnblockerIsObservedBehindFirstBlockedChild() {
  val authority=testPrimaryAuthority();authority.open(ByteArray(32));val op=authority.operationOrNull(setOf(PrimaryScope.READ))!!
  val rootClosed=CountDownLatch(1);val done=CountDownLatch(1);val failure=AtomicReference<Throwable>()
  val worker=thread(isDaemon=true) {
   try { op.createOwned(OwnedResourceManifest.io("lifetime","input","connection")) {
    val lifetime=create("lifetime", { _: Any -> rootClosed.countDown() }) { Any() }
    attach("input",AutoCloseable { rootClosed.await() })
    attach("connection",AutoCloseable { throw IOException("synthetic failed unblocker") })
    retireChildren("input","connection");lifetime
   } } catch(p: Throwable) { failure.set(p) } finally { done.countDown() }
  }
  try { assertTrue(done.await(5,TimeUnit.SECONDS));assertTrue(failure.get() is IOException);assertTrue(rootClosed.await(5,TimeUnit.SECONDS));assertEquals(0L,rootClosed.count);authority.revoke();assertFalse(authority.cleanupComplete) }
  finally { op.close();authority.revoke();worker.interrupt();assertTrue(done.await(5,TimeUnit.SECONDS)) }
 }
 @Test fun retiredAdapterCannotBeAttachedAsAnotherOriginalChild() = isolated { _,op ->
  val closes=AtomicInteger()
  assertThrows(IllegalStateException::class.java) { op.createOwned(OwnedResourceManifest.io("lifetime","first","second")) {
   val lifetime=create("lifetime", { _: Any -> }) { Any() }
   val first=create("first", { _: Any -> closes.incrementAndGet() }) { Any() }
   retireChildren("first");assertEquals(1,closes.get());attach("second",first);lifetime
  } }
  assertEquals(1,closes.get())
 }
 @Test fun retiredCopiedArrayCannotAcquireAnotherOriginalToken() {
  val pool=ReleasePool(3);lateinit var actual: ByteArray;var copies=0
  val original=ReleaseReservation(pool.reserveAll(Any(),3),copyArray={ source ->
   if(copies++==0) source.copyOf().also { actual=it } else actual
  })
  original.onRetirementAccounting {}
  try {
   assertThrows(IllegalStateException::class.java) { original.construct {
    attach(0,AutoCloseable {});copyBytes(1,byteArrayOf(9));retirePhase(intArrayOf(1),absent=false)
    assertArrayEquals(byteArrayOf(0),actual);copyBytes(2,byteArrayOf(7))
   } }
  } finally { original.release() }
  assertTrue(original.terminalRetirement.await(5,TimeUnit.SECONDS));assertEquals(0,pool.occupied)
 }
 @Test fun retiredAdapterCannotBecomeMalformedRootResult() = isolated { authority,op ->
  val closes=AtomicInteger()
  assertThrows(IllegalStateException::class.java) { op.createOwned(OwnedResourceManifest.io("lifetime","first")) {
   val first=create("first", { _: Any -> closes.incrementAndGet() }) { Any() }
   retireChildren("first");first
  } }
  awaitCleanup(authority);assertEquals(1,closes.get())
 }
 @Test fun retiredCopiedArrayCannotBecomeAnotherNativeChild() = isolated { authority,op ->
  val secondCloses=AtomicInteger()
  assertThrows(IllegalStateException::class.java) { op.createOwned(OwnedResourceManifest.io("lifetime","first","second")) {
   val lifetime=create("lifetime", { _: Any -> }) { Any() }
   val bytes=copyBytes("first",byteArrayOf(9));retireChildren("first");assertArrayEquals(byteArrayOf(0),bytes)
   create("second", { _: ByteArray -> secondCloses.incrementAndGet() }) { bytes };lifetime
  } }
  awaitCleanup(authority);assertEquals(0,secondCloses.get())
 }
 @Test fun retiredNativeArrayCannotBecomeAnotherCopiedArray() {
  val pool=ReleasePool(3);val actual=byteArrayOf(9);val closes=AtomicInteger()
  val original=ReleaseReservation(pool.reserveAll(Any(),3),copyArray={ actual })
  original.onRetirementAccounting {}
  try {
   assertThrows(IllegalStateException::class.java) { original.construct {
    attach(0,AutoCloseable {});createValue(1, { bytes: ByteArray -> bytes.fill(0);closes.incrementAndGet() }) { actual }
    retirePhase(intArrayOf(1),absent=false);assertArrayEquals(byteArrayOf(0),actual);copyBytes(2,byteArrayOf(7))
   } }
  } finally { original.release() }
  assertTrue(original.terminalRetirement.await(5,TimeUnit.SECONDS));assertEquals(0,pool.occupied);assertEquals(1,closes.get())
 }
 private fun ioPool(authority: PrimarySessionAuthority)=PrimarySessionAuthority::class.java.getDeclaredField("ioReleasePool").apply { isAccessible=true }.get(authority) as ReleasePool
 private fun isolated(test: (PrimarySessionAuthority,PrimaryOperation)->Unit) {
  val authority=testPrimaryAuthority();authority.open(ByteArray(32));val op=authority.operationOrNull(setOf(PrimaryScope.READ))!!
  try { test(authority,op) } finally { op.close();authority.revoke() }
 }
 private fun awaitCleanup(authority: PrimarySessionAuthority) {
  val end=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);while(!authority.cleanupComplete && System.nanoTime()<end) Thread.yield();assertTrue(authority.cleanupComplete)
 }
}
