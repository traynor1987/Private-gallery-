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
    private val keys = mutableMapOf<String, SecretKey>()
    val deleted = CountDownLatch(1)
    override fun create(alias: String): Cipher {
      check(alias !in keys)
      val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
      keys[alias] = key
      return Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key) }
    }
    override fun decrypt(alias: String, nonce: ByteArray) = Cipher.getInstance("AES/GCM/NoPadding").apply {
      init(Cipher.DECRYPT_MODE, checkNotNull(keys[alias]), GCMParameterSpec(128, nonce))
    }
    override fun deleteOwned(alias: String) { keys.remove(alias); deleted.countDown() }
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
    val store = testStore(files, object : SecondaryStorageIo by DurableSecondaryIo {
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

}
