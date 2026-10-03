package uk.co.traynor.privategallery.core.security
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread
class ReleaseSlotReturnTest {
 @Test fun publicTerminalWaitsForSlotMarkerAndFinalPoolBookkeeping() {
  val actualWorker=AtomicReference<Thread>();val pool=ReleasePool(1);val ticket=pool.reserve(Any())
  val original=ReleaseReservation(listOf(ticket));original.onRetirementAccounting {}
  original.construct { attach(0,AutoCloseable { actualWorker.set(Thread.currentThread()) }) }
  val poolGate=ReleasePool::class.java.getDeclaredField("gate").apply { isAccessible=true }.get(pool)!!
  val ticketGate=ReleaseTicket::class.java.getDeclaredField("gate").apply { isAccessible=true }.get(ticket)!!
  val holding=CountDownLatch(1);val allow=CountDownLatch(1);val holderDone=CountDownLatch(1)
  fun blockedAt(method: String)=actualWorker.get()?.let { t -> t.state==Thread.State.BLOCKED && if (method=="terminalPublication") t.stackTrace.firstOrNull()?.let { frame -> frame.className==ReleaseTicket::class.java.name && (frame.methodName=="finishIfReady" || frame.methodName.startsWith("publishSlotReturn")) } == true else t.stackTrace.any { it.methodName.startsWith(method) } } == true
  fun awaitBlocked(method: String) { val end=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);while(!blockedAt(method) && System.nanoTime()<end) Thread.yield();assertTrue("exact worker blocked at $method",blockedAt(method)) }
  try {
   synchronized(poolGate) {
    original.release();awaitBlocked("recycle")
    thread(isDaemon=true) { try { synchronized(ticketGate) { holding.countDown();allow.await() } } finally { holderDone.countDown() } }
    assertTrue(holding.await(5,TimeUnit.SECONDS))
   }
   awaitBlocked("terminalPublication")
   assertFalse("public terminal cannot precede original slot marker",original.terminalRetirement.isComplete)
   allow.countDown();assertTrue(holderDone.await(5,TimeUnit.SECONDS))
   assertTrue(original.terminalRetirement.await(5,TimeUnit.SECONDS))
   assertTrue(original.successful);assertFalse(pool.hasUnacknowledgedRetirement);assertEquals(0,pool.occupied)
  } finally { allow.countDown() }
 }
 @Test fun successWaitsForActualPoolSlotReturn() {
  val actualWorker=AtomicReference<Thread>();val pool=ReleasePool(1);val ticket=pool.reserve(Any())
  ticket.construct { attach -> AutoCloseable { actualWorker.set(Thread.currentThread()) }.also(attach) }
  val gate=ReleasePool::class.java.getDeclaredField("gate").apply { isAccessible=true }.get(pool)!!
  synchronized(gate) {
   ticket.release()
   val deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5)
   fun blocked()=actualWorker.get()?.let { t -> t.state==Thread.State.BLOCKED && t.stackTrace.any { it.className.endsWith("ReleasePool\$Slot") && it.methodName=="recycle" } } == true
   while(!blocked() && System.nanoTime()<deadline) Thread.yield()
   assertTrue("actual worker must reach blocked final slot-return phase",blocked())
   assertFalse("success cannot precede actual slot return",ticket.successful)
   assertFalse(ticket.finished)
   assertTrue("process retirement barrier must retain original",ticket.hasUnacknowledgedRetirement)
   assertEquals(1,pool.occupied)
  }
  val deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5)
  while(!ticket.successful && System.nanoTime()<deadline) Thread.yield()
  assertTrue(ticket.successful);assertFalse(ticket.hasUnacknowledgedRetirement);assertEquals(0,pool.occupied)
 }
}
