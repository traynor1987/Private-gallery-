package uk.co.traynor.privategallery.core.security

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

/** Inert before original attachment. Closing invalidates synchronously; only actual
 * producer/borrower return can acknowledge wiping. No waiting, Native work or joins in close. */
internal class PendingSetupBuffer : AcknowledgedCloseable {
  private val gate = Any()
  private val done = CompletableFuture<Unit>()
  private var value: ByteArray? = null
  private var produced = false
  private var active = false
  private var retired = false
  internal fun begin(production: Boolean) = synchronized(gate) {
    check(!retired && !active && (!production || !produced)) { "Pending buffer unavailable" }
    if (production) produced = true else checkNotNull(value)
    active = true
  }
  fun <T> produce(factory: () -> ByteArray, action: (ByteArray) -> T): T {
    try {
      val bytes = factory()
      synchronized(gate) { value = bytes }
      check(bytes.size == 32) { "Invalid pending secret size" }
      // Cancellation during production denies consumption; finally still owns late bytes.
      synchronized(gate) { check(!retired) }
      return action(bytes)
    } finally { returned() }
  }
  fun <T> borrow(action: (ByteArray) -> T): T {
    try {
      val bytes = synchronized(gate) { check(active); checkNotNull(value) }
      return action(bytes)
    } finally { returned() }
  }
  private fun returned() {
    val complete = synchronized(gate) { active = false; wipeIfReady() }
    if (complete) done.complete(Unit)
  }
  private fun wipeIfReady(): Boolean {
    if (!retired || active) return false
    value?.fill(0); value = null; return true
  }
  override fun closeAcknowledged(): CompletionStage<Unit> {
    val complete = synchronized(gate) { retired = true; wipeIfReady() }
    if (complete) done.complete(Unit)
    return done
  }
}
