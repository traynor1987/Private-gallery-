package uk.co.traynor.privategallery.core.domain

import java.util.concurrent.TimeUnit
import uk.co.traynor.privategallery.core.security.PendingSetupBuffer
import uk.co.traynor.privategallery.core.security.OwnedResource
import uk.co.traynor.privategallery.core.security.OwnedResourceManifest

/** Two independently charged buffers, funded before any pending secret is generated.
 * Every callback is synchronous and trusted: it must not retain an array or start unjoined use.
 * This original is preparatory ownership, never an authentication admission capability. */
internal class PendingSetupResources private constructor(
  private val owned: OwnedResource<PendingSetupBuffer>,
  private val master: PendingSetupBuffer?,
  private val secret: PendingSetupBuffer,
  private val checkCurrent: () -> Unit,
) : AutoCloseable {
  val retirementComplete: Boolean get() = owned.retirement.isComplete
  fun <T> produceMaster(factory: () -> ByteArray, action: (ByteArray) -> T): T {
    checkCurrent(); val buffer = checkNotNull(master)
    owned.original.beginPendingBuffer(0, buffer, true)
    return buffer.produce(factory) { bytes -> checkCurrent(); owned.original.requireNativeUse(0, buffer); action(bytes) }
  }
  fun <T> produceSecret(factory: () -> ByteArray, action: (ByteArray) -> T): T {
    checkCurrent(); owned.original.beginPendingBuffer(if (master == null) 0 else 1, secret, true)
    return secret.produce(factory) { bytes -> checkCurrent(); owned.original.requireNativeUse(if (master == null) 0 else 1, secret); action(bytes) }
  }
  fun <T> withMaster(action: (ByteArray) -> T): T {
    checkCurrent(); val buffer = checkNotNull(master); owned.original.beginPendingBuffer(0, buffer, false)
    return buffer.borrow { bytes -> checkCurrent(); owned.original.requireNativeUse(0, buffer); action(bytes) }
  }
  fun <T> withSecret(action: (ByteArray) -> T): T {
    checkCurrent(); owned.original.beginPendingBuffer(if (master == null) 0 else 1, secret, false)
    return secret.borrow { bytes -> checkCurrent(); owned.original.requireNativeUse(if (master == null) 0 else 1, secret); action(bytes) }
  }
  fun discardSecret() { secret.closeAcknowledged() }
  override fun close() = owned.close()
  fun awaitRetirement() {
    try { check(owned.retirement.await(20, TimeUnit.SECONDS) && !owned.releaseFailed) { "Pending secret retirement incomplete" } }
    catch (interrupted: InterruptedException) { Thread.currentThread().interrupt(); throw interrupted }
  }
  companion object {
    fun forAttempt(authority: SecondarySessionAuthority, attempt: SecondaryAuthAttempt): PendingSetupResources {
      var secret: PendingSetupBuffer? = null
      val owned = authority.createAttemptOwned(attempt, OwnedResourceManifest.io("pending-master", "pending-secret")) {
        val master = attach("pending-master", PendingSetupBuffer())
        secret = attach("pending-secret", PendingSetupBuffer())
        master
      }
      return try { PendingSetupResources(owned, owned.value, checkNotNull(secret)) { authority.checkPreparation(attempt) } }
      catch (failure: Throwable) { owned.close(); throw failure }
    }
    fun forReplacement(operation: SecondaryOperation): PendingSetupResources {
      operation.requireScope(SecondaryScope.RECOVERY)
      val owned = operation.createSessionOwned(OwnedResourceManifest.io("pending-secret")) {
        attach("pending-secret", PendingSetupBuffer())
      }
      return try { PendingSetupResources(owned, null, owned.value) { operation.publish { Unit } } }
      catch (failure: Throwable) { owned.close(); throw failure }
    }
  }
}
