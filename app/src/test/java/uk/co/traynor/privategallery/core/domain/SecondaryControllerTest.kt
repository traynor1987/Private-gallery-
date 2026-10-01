package uk.co.traynor.privategallery.core.domain

import java.io.File
import java.nio.file.Files
import java.util.concurrent.Executor
import javax.crypto.Cipher
import org.junit.Assert.*
import org.junit.Test

class SecondaryControllerTest {
  private class Queue : Executor {
    val tasks = ArrayDeque<Runnable>()
    override fun execute(command: Runnable) { tasks.add(command) }
    fun drain() { while (tasks.isNotEmpty()) tasks.removeFirst().run() }
  }
  private class Fixture : AutoCloseable {
    val files = Files.createTempDirectory("controller").toFile()
    val queue = Queue()
    var now = 10L
    val authority = SecondarySessionAuthority { now }
    val policy = SecondaryAuthPolicy { now }
    val store = testStore(files)
    val bio = SecondaryBiometricSlot(object : SecondaryBiometricKeyBackend {
      override fun create(alias: String): Cipher = error("No device in JVM")
      override fun decrypt(alias: String, nonce: ByteArray): Cipher = error("No device in JVM")
      override fun deleteOwned(alias: String) {}
    })
    val controller = SecondaryController(store, authority, policy, bio, queue) { it.concatToString() == "111111111111" }
    fun discover() { controller.discover(DiscoveryChallenge()); queue.drain() }
    fun create() {
      discover(); controller.setup("222222222222".toCharArray()); queue.drain()
      val display = requireNotNull(controller.takeRecoveryDisplay())
      controller.acknowledgeRecoveryDisplay()
      val secret = display.concatToString().chunked(2).map { it.toInt(16).toByte() }.toByteArray(); display.fill('\u0000')
      controller.confirmRecovery(secret); queue.drain()
      assertEquals(SecondaryRoute.READY, controller.state.value.route)
    }
    override fun close() { controller.close(); queue.drain(); files.deleteRecursively() }
  }
  @Test fun discoveryDoesNotCreateRootOrAuthorityAndExitCancelsQueuedSetup() = Fixture().use { f ->
    f.discover(); assertEquals(SecondaryRoute.SETUP, f.controller.state.value.route)
    assertNull(f.authority.operationOrNull()); assertFalse(File(f.files,"domain-store").exists())
    val pin = "222222222222".toCharArray(); f.controller.setup(pin); f.controller.exit(); f.queue.drain()
    assertTrue(pin.all { it == '\u0000' }); assertNull(f.authority.operationOrNull())
    assertFalse(File(f.files,"domain-store").exists()); assertEquals(SecondaryRoute.CLOSED,f.controller.state.value.route)
  }
  @Test fun setupRejectsPrimaryCredentialAndRequiresPossession() = Fixture().use { f ->
    f.discover(); f.controller.setup("111111111111".toCharArray()); f.queue.drain()
    assertEquals(SecondaryRoute.SETUP,f.controller.state.value.route); assertFalse(File(f.files,"domain-store").exists())
    f.controller.setup("222222222222".toCharArray()); f.queue.drain()
    assertEquals(SecondaryRoute.RECOVERY_DISPLAY,f.controller.state.value.route); assertNull(f.authority.operationOrNull())
    val first = requireNotNull(f.controller.takeRecoveryDisplay()); assertEquals(64,first.size); first.fill('\u0000')
    assertNull(f.controller.takeRecoveryDisplay()); f.controller.acknowledgeRecoveryDisplay()
    f.controller.confirmRecovery(ByteArray(32)); f.queue.drain()
    assertNull(f.authority.operationOrNull()); assertEquals(SecondaryPreflight.PENDING,f.store.preflight())
  }
  @Test fun stalePinCompletionCannotReopenAndColdControllerHasNoAuthority() = Fixture().use { f ->
    f.create(); f.controller.exit(); f.discover()
    val pin="222222222222".toCharArray(); f.controller.unlock(pin); f.controller.onScreenOff(); f.queue.drain()
    assertNull(f.authority.operationOrNull()); assertEquals(SecondaryRoute.CLOSED,f.controller.state.value.route)
    assertTrue(pin.all { it == '\u0000' })
    val cold = SecondarySessionAuthority { f.now }; assertNull(cold.operationOrNull())
    assertFalse(SecondaryAuthPolicy { f.now }.canUseBiometric())
  }
  @Test fun backgroundDeadlineClearsUiAndRejectsLateResume() = Fixture().use { f ->
    f.create(); f.controller.updateSettings(StrongAuthInterval.DAY, SecondaryAutoLock.THIRTY_SECONDS); f.queue.drain()
    f.controller.onBackgrounded(); assertEquals(SecondaryRoute.CLOSED,f.controller.state.value.route)
    f.now += 30_000; f.controller.onForegrounded(); assertNull(f.authority.operationOrNull())
    assertEquals(SecondaryRoute.CLOSED,f.controller.state.value.route)
  }
  @Test fun backgroundTimerDoesNotResumeOrExtendTheHiddenSession() = Fixture().use { f ->
    f.create(); f.controller.updateSettings(StrongAuthInterval.DAY, SecondaryAutoLock.THIRTY_SECONDS); f.queue.drain()
    f.controller.onBackgrounded()
    f.now += 29_999; f.controller.checkBackgroundExpiry()
    assertEquals(SecondaryRoute.CLOSED, f.controller.state.value.route)
    f.now++; f.controller.checkBackgroundExpiry()
    assertNull(f.authority.operationOrNull())
    f.controller.onForegrounded()
    assertEquals(SecondaryRoute.CLOSED, f.controller.state.value.route)
  }
  @Test fun settingsAndPinMutationRequireOriginalSessionAndNoPrimaryWrites() = Fixture().use { f ->
    val primary=File(f.files,"primary-proof").apply { writeText("unchanged") }; f.create()
    f.controller.changePin("333333333333".toCharArray()); f.controller.exit(); f.queue.drain()
    f.store.authenticatePin("222222222222".toCharArray()).close()
    assertEquals("unchanged",primary.readText()); assertNull(f.authority.operationOrNull())
    f.controller.changePin("333333333333".toCharArray()); f.queue.drain()
    assertEquals(SecondaryRoute.CLOSED,f.controller.state.value.route)
  }
  @Test fun recoveryDisplayDestroysThePendingOriginalAndCannotBeRedisplayed() = Fixture().use { f ->
    f.discover(); f.controller.setup("222222222222".toCharArray()); f.queue.drain()
    val pending = SecondaryController::class.java.getDeclaredField("pending").apply { isAccessible = true }.get(f.controller) as PendingSetup
    val original = PendingSetup::class.java.getDeclaredField("ownedSecret").apply { isAccessible = true }.get(pending) as ByteArray
    val display = checkNotNull(f.controller.takeRecoveryDisplay())
    try {
      assertTrue("original recovery buffer must be destroyed after one-time display transfer", original.all { it == 0.toByte() })
      assertNull(f.controller.takeRecoveryDisplay())
      f.controller.acknowledgeRecoveryDisplay()
      val reentered = display.concatToString().chunked(2).map { it.toInt(16).toByte() }.toByteArray()
      f.controller.confirmRecovery(reentered); f.queue.drain()
      assertEquals(SecondaryRoute.READY, f.controller.state.value.route)
    } finally { display.fill('\u0000') }
  }

}
