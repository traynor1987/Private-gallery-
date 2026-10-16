package uk.co.traynor.privategallery.core.domain

import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.junit.Assert.*
import org.junit.Test

/** Real cryptographic unwrap/store promotion with synthetic device keys, no biometric identity claim. */
class SecondaryControllerBiometricTest {
  private class Backend : SecondaryBiometricKeyBackend {
    val keys = java.util.concurrent.ConcurrentHashMap<String, SecretKey>()
    val deleted = CountDownLatch(1)
    var beforeDelete: () -> Unit = {}
    var beforeDecrypt: () -> Unit = {}
    override fun create(alias: String): Cipher {
      check(!keys.containsKey(alias))
      val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
      keys[alias] = key
      return Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key) }
    }
    override fun decrypt(alias: String, nonce: ByteArray) = Cipher.getInstance("AES/GCM/NoPadding").apply {
      beforeDecrypt()
      init(Cipher.DECRYPT_MODE, checkNotNull(keys[alias]), GCMParameterSpec(128, nonce))
    }
    override fun deleteOwned(alias: String) { beforeDelete(); keys.remove(alias); deleted.countDown() }
  }
  private class Fixture : AutoCloseable {
    val files = Files.createTempDirectory("biometric-controller").toFile()
    val reached = CountDownLatch(1)
    val release = CountDownLatch(1)
    @Volatile var pause = false
    @Volatile var pauseSelection = false
    @Volatile var failAfterSelection = false
    val selectionReached = CountDownLatch(1)
    val selectionRelease = CountDownLatch(1)
    val backend = Backend()
    var now = 10L
    val authority = SecondarySessionAuthority { now }
    val store = SecondaryStore(files, object : SecondaryStorageIo by DurableSecondaryIo {
      override fun writeNew(path: Path, bytes: ByteArray) {
        if (pause && path.parent.fileName.toString() == "usage" && path.fileName.toString().startsWith("q")) {
          pause = false; reached.countDown(); check(release.await(20, TimeUnit.SECONDS))
        }
        DurableSecondaryIo.writeNew(path, bytes)
      }
      override fun atomicReplace(source: Path, target: Path) {
        if (pauseSelection && target.fileName.toString() == "selected") {
          selectionReached.countDown(); check(selectionRelease.await(20, TimeUnit.SECONDS))
        }
        DurableSecondaryIo.atomicReplace(source, target)
        if (failAfterSelection && target.fileName.toString() == "selected") throw java.io.IOException("failure after rename")
      }
    }, object : SecondaryRetirementKeys by SyntheticRetirementKeys {
      override fun deleteBiometric(identity: DomainIdentity,slotId: ByteArray) {
        backend.deleteOwned(SecondaryBiometricEnvelope.alias(identity,slotId))
      }
    }, Unit)
    val controller = SecondaryController(store, authority, SecondaryAuthPolicy { now }, SecondaryBiometricSlot(backend), Executor { it.run() }) { false }
    fun discover() = controller.discover(DiscoveryChallenge())
    fun create() {
      discover(); controller.setup("222222222222".toCharArray())
      val display = checkNotNull(controller.takeRecoveryDisplay())
      val secret = display.concatToString().chunked(2).map { it.toInt(16).toByte() }.toByteArray()
      display.fill('\u0000'); controller.acknowledgeRecoveryDisplay(); controller.confirmRecovery(secret)
      assertEquals(SecondaryRoute.READY, controller.state.value.route)
    }
    fun enroll() {
      controller.prepareBiometricEnrollment()
      val value = checkNotNull(controller.takeBiometricRequest())
      controller.completeBiometric(value, value.cipher)
      assertTrue(controller.state.value.biometricEnabled)
    }
    fun eligibleDiscovery(): SecondaryBiometricRequest {
      controller.exit(); discover(); controller.unlock("222222222222".toCharArray())
      controller.exit(); discover()
      // Compatible with both explicit preparation and automatic eligible discovery.
      return controller.takeBiometricRequest() ?: run {
        controller.prepareBiometricUnlock(); checkNotNull(controller.takeBiometricRequest())
      }
    }
    override fun close() { release.countDown(); selectionRelease.countDown(); controller.close(); files.deleteRecursively() }
  }
  @Test fun cancelledEnrollmentRetiresOutsideControllerGateAndBlocksFreshAuthenticationUntilReturn() = Fixture().use { f ->
    f.create(); f.controller.prepareBiometricEnrollment()
    val request = checkNotNull(f.controller.takeBiometricRequest())
    val gate = f.controller.javaClass.getDeclaredField("gate").apply { isAccessible = true }.get(f.controller)
    val entered = CountDownLatch(1); val release = CountDownLatch(1); val cancelReturned = CountDownLatch(1)
    val inheritedGate = java.util.concurrent.atomic.AtomicBoolean(false)
    f.backend.beforeDelete = {
      inheritedGate.set(Thread.holdsLock(gate)); entered.countDown()
      check(release.await(20, TimeUnit.SECONDS))
    }
    val cancel = Thread { try { f.controller.cancelBiometric(request) } finally { cancelReturned.countDown() } }
    cancel.start()
    try {
      assertTrue(entered.await(20, TimeUnit.SECONDS))
      assertFalse("native deletion cannot inherit controller rank6", inheritedGate.get())
      assertTrue("cancellation dispatch must not wait for native deletion", cancelReturned.await(2, TimeUnit.SECONDS))
      assertFalse(f.authority.cleanupComplete)
      val fresh = f.authority.beginAuthentication()
      assertThrows(IllegalStateException::class.java) { f.authority.completeAuthentication(fresh, ByteArray(32)) }
    } finally { release.countDown(); cancel.join(20_000); request.awaitRetirement() }
    assertFalse(cancel.isAlive)
  }
  @Test fun automaticUnlockProviderPreparationDoesNotInheritControllerGate() = Fixture().use { f ->
    f.create(); f.enroll()
    val gate = f.controller.javaClass.getDeclaredField("gate").apply { isAccessible = true }.get(f.controller)
    val calls = java.util.concurrent.atomic.AtomicInteger()
    f.backend.beforeDecrypt = {
      assertFalse("provider construction cannot inherit controller rank6", Thread.holdsLock(gate))
      calls.incrementAndGet()
    }
    val request = f.eligibleDiscovery()
    assertTrue(calls.get() > 0)
    f.controller.cancelBiometric(request)
    request.awaitRetirement()
  }
  @Test fun successfulEnrollmentWaitsForActualOriginalProviderRetirementBeforePublication() = enrollmentRetirementBarrier(false)
  @Test fun cancellationDuringActualEnrollmentRetirementCannotPublishLateSuccess() = enrollmentRetirementBarrier(true)

  private fun enrollmentRetirementBarrier(cancelDuringRetirement: Boolean) = Fixture().use { f ->
    f.create(); f.controller.prepareBiometricEnrollment()
    val request = checkNotNull(f.controller.takeBiometricRequest())
    val queue = java.util.concurrent.LinkedBlockingQueue<Runnable>()
    lateinit var events: uk.co.traynor.privategallery.core.security.OriginalBiometricBackend.Events
    var destroyRequested = false
    val backend = object : uk.co.traynor.privategallery.core.security.OriginalBiometricBackend {
      override val supportsAcknowledgedRetirement = true
      override fun acquire(value: uk.co.traynor.privategallery.core.security.OriginalBiometricBackend.Events,
        attach: (uk.co.traynor.privategallery.core.security.OriginalBiometricBackend.Host) -> Unit) {
        events = value
        attach(object : uk.co.traynor.privategallery.core.security.OriginalBiometricBackend.Host {
          override fun cancel() {}
          override fun destroy() { destroyRequested = true }
        })
      }
    }
    val cipher = request.cipher
    val lifecycle = uk.co.traynor.privategallery.core.security.OriginalBiometricCoordinator({ queue.add(it) }) { true }
      .begin(f.controller.biometricOwner(request), cipher, backend)
    f.controller.attachBiometricLifecycle(request, lifecycle)
    fun drainUntil(done: () -> Boolean) {
      val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
      while (!done()) {
        check(System.nanoTime() < deadline)
        queue.poll(10, TimeUnit.MILLISECONDS)?.run()
      }
    }
    events.success(cipher)
    drainUntil { lifecycle.readyForAuthenticatedUse }
    f.pauseSelection = true
    val worker = Thread { f.controller.completeBiometric(request, lifecycle) }
    worker.start()
    try {
      drainUntil { destroyRequested }
      events.destroyed()
      drainUntil { lifecycle.retirement.isComplete }
      assertTrue(f.selectionReached.await(20, TimeUnit.SECONDS))
      val ownership = request.javaClass.getDeclaredField("ownership").apply { isAccessible = true }.get(request)
        as uk.co.traynor.privategallery.core.security.OwnedResource<*>
      // Selection's markInstalled has returned. Holding the real pending provider's
      // monitor prevents its close from returning after selection is released.
      synchronized(checkNotNull(request.enrollment)) {
        f.selectionRelease.countDown()
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        while (!ownership.original.retiring) {
          check(System.nanoTime() < deadline)
          Thread.yield()
        }
        assertFalse(ownership.retirement.isComplete)
        assertFalse("success cannot precede original provider close return", f.controller.state.value.biometricEnabled)
        assertTrue(f.controller.state.value.busy)
        if (cancelDuringRetirement) {
          f.controller.cancelBiometric(request)
          assertFalse(f.controller.state.value.biometricEnabled)
        }
      }
    } finally { f.selectionRelease.countDown(); worker.join(20_000); lifecycle.close() }
    assertFalse(worker.isAlive)
    assertEquals("only the still-current original may publish enabled", !cancelDuringRetirement,
      f.controller.state.value.biometricEnabled)
    assertFalse(f.controller.state.value.busy)
    request.awaitRetirement()
    // Durable selection survives cancellation after installation; it must not delete
    // the selected alias even though its obsolete UI result is suppressed.
    assertNotNull(f.store.biometricRecord())
  }

  private fun awaitCleanup(authority: SecondarySessionAuthority) {
    val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
    while (!authority.cleanupComplete) {
      check(System.nanoTime() < deadline) { "Actual original cleanup did not complete" }
      Thread.yield()
    }
  }
  @Test fun verifiedOldPinCannotAdmitAfterSelectedCredentialRotation() = Fixture().use { f ->
    f.create()
    // The successful handoff's original still has its bounded retirement epilogue.
    // Observe real shared-pool/owning cleanup before funding the independent writer.
    awaitCleanup(f.authority)
    val independent = SecondarySessionAuthority { f.now }
    val copied = checkNotNull(f.authority.operationOrNull(setOf(SecondaryScope.CREDENTIALS))).use { it.key.copyOf() }
    check(independent.completeAuthentication(independent.beginAuthentication(), copied))
    try {
      f.controller.exit(); f.discover()
      val auth = f.controller.javaClass.getDeclaredField("attempt").apply { isAccessible = true }.get(f.controller) as SecondaryAuthAttempt
      val token = f.controller.javaClass.getDeclaredField("sequence").apply { isAccessible = true }.getLong(f.controller)
      val input = "222222222222".toCharArray()
      val prepare = f.controller.javaClass.declaredMethods.single { it.name == "prepareVerified" }.apply { isAccessible = true }
      val factory: () -> AuthenticatedDomain = {
        f.store.authenticatePin(input, { f.authority.checkPreparation(auth) })
      }
      // Use the real branded worker handoff, otherwise missing ownership itself would
      // reject this result and hide a broken selected-predecessor admission check.
      val ownedVerified = prepare.invoke(f.controller, auth, input, null, factory) as AuthenticatedDomain
      ownedVerified.use { oldVerified ->
        checkNotNull(independent.operationOrNull(setOf(SecondaryScope.CREDENTIALS))).use {
          f.store.changePin(it, "333333333333".toCharArray())
        }
        val promote = f.controller.javaClass.declaredMethods.single { it.name == "promote" }.apply { isAccessible = true }
        assertThrows(java.lang.reflect.InvocationTargetException::class.java) {
          promote.invoke(f.controller, token, auth, oldVerified, true, false, SecondaryRoute.READY)
        }
        assertNull("rotated selected credentials must deny the old verified result", f.authority.operationOrNull())
      }
      awaitCleanup(f.authority)
      f.controller.unlock("333333333333".toCharArray())
      assertEquals(SecondaryRoute.READY, f.controller.state.value.route)
    } finally { independent.revoke() }
  }
  @Test fun outerCancelledRequestCannotExposeItsCipherOrAffectReplacement() = Fixture().use { f ->
    f.create(); f.enroll(); val old = f.eligibleDiscovery(); val oldCipher = old.cipher
    f.controller.cancelBiometric(old)
    assertThrows(F1Exception::class.java) { old.cipher }
    val replacement = f.eligibleDiscovery()
    f.controller.completeBiometric(old, oldCipher)
    assertNull(f.authority.operationOrNull())
    f.controller.completeBiometric(replacement, replacement.cipher)
    assertEquals(SecondaryRoute.READY, f.controller.state.value.route)
  }
  @Test fun cancellationAfterUnwrapCannotPromoteAuthorityAndPinFallbackStillWorks() = Fixture().use { f ->
    f.create(); f.enroll(); val request = f.eligibleDiscovery()
    f.pause = true
    val worker = Thread { f.controller.completeBiometric(request, request.cipher) }
    worker.start()
    try {
      assertTrue("validation seam reached after unwrap", f.reached.await(20, TimeUnit.SECONDS))
      f.controller.cancelBiometric(request)
    } finally { f.release.countDown(); worker.join(20_000) }
    assertFalse(worker.isAlive)
    assertNull("cancelled biometric must not create authority", f.authority.operationOrNull())
    assertEquals(SecondaryRoute.PIN, f.controller.state.value.route)
    f.controller.unlock("222222222222".toCharArray())
    assertEquals(SecondaryRoute.READY, f.controller.state.value.route)
  }
  @Test fun discoveryWithoutSlotDoesNotOfferBiometrics() = Fixture().use { f ->
    f.create(); f.controller.exit(); f.discover()
    assertFalse(f.controller.state.value.biometricAvailable)
    assertNull(f.controller.takeBiometricRequest())
    assertNull(f.authority.operationOrNull())
  }
  @Test fun enrollmentCanceledAfterWrappingCannotSelectADeletedAlias() = Fixture().use { f ->
    f.create()
    f.controller.prepareBiometricEnrollment()
    val request = checkNotNull(f.controller.takeBiometricRequest())
    f.pause = true
    val worker = Thread { f.controller.completeBiometric(request, request.cipher) }
    worker.start()
    try {
      assertTrue("store verification reached after wrapping", f.reached.await(20, TimeUnit.SECONDS))
      f.controller.cancelBiometric(request)
    } finally { f.release.countDown(); worker.join(20_000) }
    assertFalse(worker.isAlive)
    assertNull("canceled enrollment must not select the deleted alias", f.store.biometricRecord())
    assertFalse(f.controller.state.value.biometricEnabled)
    checkNotNull(f.authority.operationOrNull()).close()
  }
  @Test fun cancellationDuringPointerPromotionCannotDeleteTheSelectedBiometricAlias() = Fixture().use { f ->
    f.create(); f.controller.prepareBiometricEnrollment()
    val request = checkNotNull(f.controller.takeBiometricRequest())
    f.pauseSelection = true
    val worker = Thread { f.controller.completeBiometric(request, request.cipher) }
    val cancelStarted = CountDownLatch(1)
    val cancel = Thread { cancelStarted.countDown(); f.controller.cancelBiometric(request) }
    worker.start()
    try {
      assertTrue(f.selectionReached.await(20, TimeUnit.SECONDS))
      cancel.start(); assertTrue(cancelStarted.await(20, TimeUnit.SECONDS))
      // Before the fix, close deletes the alias then blocks on the worker's authority
      // gate. After the fix, cancellation waits for the whole protected promotion.
      f.backend.deleted.await(250, TimeUnit.MILLISECONDS)
    } finally { f.selectionRelease.countDown(); worker.join(20_000); cancel.join(20_000) }
    assertFalse(worker.isAlive); assertFalse(cancel.isAlive)
    val record = f.store.biometricRecord()
    if (record != null) {
      val unlock = SecondaryBiometricSlot(f.backend).prepareUnlock(record.identity, record.slotId, record.generation, record.envelope)
      unlock.close() // Requires the selected alias to survive the cancellation race.
    }
    assertEquals(SecondaryPreflight.READY, f.store.preflight())
  }
  @Test fun uncertainSelectionOutcomeCannotDeleteAnAliasAlreadySelected() = Fixture().use { f ->
    f.create(); f.controller.prepareBiometricEnrollment()
    val request = checkNotNull(f.controller.takeBiometricRequest())
    f.failAfterSelection = true
    f.controller.completeBiometric(request, request.cipher)
    val record = checkNotNull(f.store.biometricRecord())
    SecondaryBiometricSlot(f.backend).prepareUnlock(record.identity, record.slotId, record.generation, record.envelope).close()
    assertEquals(SecondaryPreflight.READY, f.store.preflight())
  }
  @Test fun eligibleInstalledSlotAutomaticallyChallengesAndGraceRetainsControls() = Fixture().use { f ->
    f.create(); f.enroll()
    f.controller.updateSettings(StrongAuthInterval.DAY, SecondaryAutoLock.THIRTY_SECONDS)
    f.controller.exit(); f.discover()
    assertNull("security change requires PIN", f.controller.takeBiometricRequest())
    f.controller.unlock("222222222222".toCharArray())
    f.controller.onBackgrounded(); f.now++
    assertEquals(SecondaryRoute.CLOSED, f.controller.state.value.route)
    f.controller.onForegrounded()
    assertTrue(f.controller.state.value.biometricEnabled)
    f.controller.exit(); f.discover()
    val request = checkNotNull(f.controller.takeBiometricRequest())
    assertNull(f.authority.operationOrNull())
    f.controller.completeBiometric(request, request.cipher)
    assertEquals(SecondaryRoute.READY, f.controller.state.value.route)
    assertTrue(f.controller.state.value.biometricEnabled)
  }

  @Test fun disablingBiometricRetiresOnlyItsIndependentOwnedAlias() = Fixture().use { f ->
    f.create(); f.enroll()
    val record = checkNotNull(f.store.biometricRecord())
    val owned = SecondaryBiometricEnvelope.alias(record.identity,record.slotId)
    val primaryCanary = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    f.backend.keys["primary-biometric-canary"] = primaryCanary
    assertTrue(f.backend.keys.containsKey(owned))
    f.controller.disableBiometric()
    assertFalse(f.controller.state.value.biometricEnabled)
    assertNull(f.store.biometricRecord())
    assertFalse(f.backend.keys.containsKey(owned))
    assertSame(primaryCanary,f.backend.keys["primary-biometric-canary"])
  }


}
