package uk.co.traynor.privategallery.core.domain

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Base64
import org.junit.Assert.*
import org.junit.Test
import uk.co.traynor.privategallery.core.crypto.*

/** Cross the actual legacy Primary formats and independent F1 formats with expendable keys. */
class PrimarySecondaryCryptoTest {
  private val primary = F1Crypto.random(32)
  private val secondary = F1Crypto.random(32)
  private val identity = DomainIdentity(F1Crypto.random(16), F1Crypto.random(16))
  private val context = F1Context(identity, 2, F1Crypto.random(16), 1)
  private fun rejected(action: () -> Unit) {
    try { action() } catch (_: SecurityException) { return } catch (_: javax.crypto.AEADBadTagException) { return }
    fail("cross-domain authority accepted")
  }
  @Test fun actualPrimaryAndSecondaryPayloadsRejectTheOtherMaster() {
    assertFalse(primary.contentEquals(secondary))
    val body = ByteArray(4096) { (it * 31).toByte() }
    val aad = "synthetic-primary-item".toByteArray()
    val primaryOut = ByteArrayOutputStream()
    val header = VaultCipher.encrypt(ByteArrayInputStream(body), primaryOut, primary, aad)
    val output = ByteArrayOutputStream()
    VaultCipher.decrypt(ByteArrayInputStream(primaryOut.toByteArray()), output, primary, aad, header)
    assertArrayEquals(body, output.toByteArray())
    rejected { VaultCipher.decrypt(ByteArrayInputStream(primaryOut.toByteArray()), ByteArrayOutputStream(), secondary, aad, header) }
    val secondaryObject = F1Record.encrypt(secondary, context, body)
    assertArrayEquals(body, F1Record.decrypt(secondary, context, secondaryObject))
    rejected { F1Record.decrypt(primary, context, secondaryObject) }
  }
  @Test fun actualPrimaryAndSecondaryPinSlotsRejectTheOtherCredential() {
    val primarySlot = PinEnvelope.create("111111111111".toCharArray(), primary)
    val secondarySlot = F1Slot.createPin(identity, "222222222222".toCharArray(), secondary, 1)
    assertArrayEquals(primary, PinEnvelope.unwrap("111111111111".toCharArray(), primarySlot))
    assertArrayEquals(secondary, F1Slot.unwrapPin(identity, "222222222222".toCharArray(), secondarySlot))
    rejected { PinEnvelope.unwrap("222222222222".toCharArray(), primarySlot) }
    rejected { F1Slot.unwrapPin(identity, "111111111111".toCharArray(), secondarySlot) }
  }
  @Test fun actualPrimaryAndSecondaryRecoverySlotsRejectTheOtherSecret() {
    val primarySecret = F1Crypto.random(32)
    val secondarySecret = F1Crypto.random(32)
    fun primaryChars() = Base64.getUrlEncoder().withoutPadding().encodeToString(primarySecret).toCharArray()
    val primarySlot = RecoveryEnvelope.create(primaryChars(), primary)
    val secondarySlot = F1Slot.createRecovery(identity, secondarySecret, secondary, 1, true)
    try {
      assertArrayEquals(primary, RecoveryEnvelope.unwrap(primaryChars(), primarySlot))
      assertArrayEquals(secondary, F1Slot.unwrapRecovery(identity, secondarySecret, secondarySlot))
      rejected { F1Slot.unwrapRecovery(identity, primarySecret, secondarySlot) }
      rejected { RecoveryEnvelope.unwrap(secondarySecret.joinToString("") { "%02x".format(it.toInt() and 255) }.toCharArray(), primarySlot) }
    } finally { primarySecret.fill(0); secondarySecret.fill(0) }
  }
}
