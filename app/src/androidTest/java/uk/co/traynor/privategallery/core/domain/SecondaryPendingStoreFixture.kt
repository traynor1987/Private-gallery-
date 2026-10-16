package uk.co.traynor.privategallery.core.domain

import java.util.concurrent.TimeUnit
import uk.co.traynor.privategallery.core.security.ProcessReleaseCapacity

/** Direct store fixtures have no controller; retain a real exact-attempt owner instead.
 * Wait only outside production locks for prior fixture retirement, never reset capacity. */
internal fun fixturePendingResources(): PendingSetupResources {
  val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20)
  while (ProcessReleaseCapacity.hiddenIo.hasUnacknowledgedRetirement) {
    check(System.nanoTime() < deadline) { "Prior fixture retirement incomplete" }; Thread.yield()
  }
  val authority = SecondarySessionAuthority { 0L }
  return PendingSetupResources.forAttempt(authority, authority.beginAuthentication())
}
internal fun SecondaryStore.create(pin: CharArray, guard: () -> Unit, commit: (() -> Unit) -> Unit): PendingSetup {
  val resources = fixturePendingResources()
  return try { create(pin, guard, commit, resources) }
  catch (failure: Throwable) { resources.close(); resources.awaitRetirement(); throw failure }
}
internal fun SecondaryStore.resumePending(pin: CharArray, guard: () -> Unit, commit: (() -> Unit) -> Unit): PendingSetup {
  val resources = fixturePendingResources()
  return try { resumePending(pin, guard, commit, resources) }
  catch (failure: Throwable) { resources.close(); resources.awaitRetirement(); throw failure }
}

internal fun fixturePendingSetup(store: SecondaryStore, identity: DomainIdentity, token: String, master: ByteArray, secret: ByteArray): PendingSetup {
  val resources = fixturePendingResources()
  return try { resources.produceMaster({ master.copyOf() }) {
    resources.produceSecret({ secret.copyOf() }) { PendingSetup(store, identity, token, false, resources) }
  } } catch (failure: Throwable) { resources.close(); resources.awaitRetirement(); throw failure }
}

/** Synthetic fixtures only: no production copying getter or alternate cleanup path. */
internal val PendingSetup.recoverySecret: ByteArray
  get() {
    val field = PendingSetup::class.java.getDeclaredField("resources").apply { isAccessible = true }
    val resources = field.get(this) as PendingSetupResources
    return resources.withSecret { it.copyOf() }
  }

/** Synthetic direct callers retain the same real two-child confirmation original. */
internal fun SecondaryStore.confirmReplacement(operation: SecondaryOperation, pending: PendingSetup, secret: ByteArray) =
  OwnedRecoveryConfirmation.consume(operation, secret) { master, exactSecret ->
    confirmReplacement(operation, pending, master, exactSecret)
  }
