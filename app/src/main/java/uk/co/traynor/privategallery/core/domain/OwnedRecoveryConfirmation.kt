package uk.co.traynor.privategallery.core.domain

import java.util.concurrent.TimeUnit
import uk.co.traynor.privategallery.core.security.OwnedResource
import uk.co.traynor.privategallery.core.security.OwnedResourceManifest
import uk.co.traynor.privategallery.core.security.PendingSetupBuffer

/** Exact replacement inputs, independently funded before copying or consuming either array.
 * The trusted synchronous consumer must return all use; it cannot retain or dispatch the arrays.
 * Revocation seals ingress but preserves admitted use through actual consumer return. */
internal object OwnedRecoveryConfirmation {
  fun <T> consume(operation: SecondaryOperation, secret: ByteArray, consumer: (ByteArray, ByteArray) -> T): T {
    var owned: OwnedResource<PendingSetupBuffer>? = null
    var primaryFailure: Throwable? = null
    var restoreInterrupt = false
    try {
      // An inherited authority gate must deny before ownership, Native work or any wait.
      operation.requireNativeOutsideAuthorityGate()
      require(secret.size == 32) { "Invalid confirmation secret size" }
      operation.requireRecoveryConfirmationAdmission()
      lateinit var secretCell: PendingSetupBuffer
      val original = operation.createOwned(OwnedResourceManifest.io("confirmation-master", "confirmation-secret")) {
        val master = attach("confirmation-master", PendingSetupBuffer())
        secretCell = attach("confirmation-secret", PendingSetupBuffer())
        master
      }
      owned = original
      original.original.beginPendingBuffer(0, original.value, true)
      val result = original.value.produce({ operation.commit { operation.requireRecoveryConfirmationAdmission(); operation.key.copyOf() } }) { master ->
        operation.requireRecoveryConfirmationAdmission()
        original.original.beginPendingBuffer(1, secretCell, true)
        secretCell.produce({ secret }) { exactSecret ->
          operation.requireRecoveryConfirmationAdmission()
          original.original.requireNativeUse(0, original.value)
          original.original.requireNativeUse(1, secretCell)
          consumer(master, exactSecret)
        }
      }
      return result
    } catch (failure: Throwable) {
      primaryFailure = failure
      restoreInterrupt = failure is InterruptedException
      throw failure
    } finally {
      // A consumer's InterruptedException must not pre-interrupt and skip the mandatory
      // release wait. Restore its status after retirement; a new wait interruption still
      // denies success and leaves the original charged until actual cleanup returns.
      val interruptedBeforeRetirement = Thread.interrupted()
      try {
        // Every synchronous consumer has returned, including on exceptions/cancellation.
        secret.fill(0)
        val original = owned
        if (original != null) {
          try {
            retire(original)
            // Same exact lease/epoch after actual release; ownership never grants authority.
            operation.requireRecoveryConfirmationAdmission()
          } catch (retirementFailure: Throwable) {
            if (primaryFailure != null) primaryFailure.addSuppressed(retirementFailure)
            else throw retirementFailure
          }
        }
      } finally {
        if (restoreInterrupt || interruptedBeforeRetirement) Thread.currentThread().interrupt()
      }
    }
  }

  private fun retire(owned: OwnedResource<PendingSetupBuffer>) {
    var closeFailure: Throwable? = null
    try { owned.close() } catch (failure: Throwable) { closeFailure = failure }
    try {
      val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20)
      do {
        check(!owned.releaseFailed) { "Confirmation input retirement failed" }
        check(System.nanoTime() < deadline) { "Confirmation input retirement incomplete" }
      } while (!owned.retirement.await(1, TimeUnit.SECONDS))
      check(!owned.releaseFailed) { "Confirmation input retirement failed" }
    } catch (failure: Throwable) {
      if (failure is InterruptedException) Thread.currentThread().interrupt()
      if (closeFailure != null) closeFailure.addSuppressed(failure) else throw failure
    }
    closeFailure?.let { throw it }
  }
}
