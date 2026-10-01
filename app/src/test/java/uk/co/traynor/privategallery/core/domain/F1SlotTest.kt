package uk.co.traynor.privategallery.core.domain

import java.nio.ByteBuffer
import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Test
import uk.co.traynor.privategallery.core.domain.F1RecordTest.Companion.caught
import uk.co.traynor.privategallery.core.domain.F1RecordTest.Companion.hex
import uk.co.traynor.privategallery.core.domain.F1RecordTest.Companion.rejects

class F1SlotTest {
  private val identity = DomainIdentity(ByteArray(16) { it.toByte() }, ByteArray(16) { (it + 16).toByte() })
  private val master = ByteArray(32) { it.toByte() }
  private val secret = ByteArray(32) { (it + 80).toByte() }
  private val pin = "1234567890123456"

  @Test fun `frozen recovery slot canonical context and HKDF key match`() {
    val fixture = F1RecordTest.fixture()
    val slot = fixture.substringAfter("\"slot\": {").substringBefore("\"rfc5869Case1\"")
    fun field(name: String) = Regex("\\\"$name\\\": \\\"([0-9a-f]+)\\\"").find(slot)!!.groupValues[1]
    val id = hex(field("slotId"))
    val info = F1Slot.keyInfo(identity, id, 3, 2, 1)
    assertArrayEquals(hex(field("info")), info)
    val salt = ByteArray(32) { (it + 32).toByte() }
    assertArrayEquals(hex(field("syntheticRecoveryKey")), F1Crypto.hkdf(master, salt, info))
  }

  @Test fun `portable recovery authenticates pending confirmed metadata and digest`() {
    for (confirmed in listOf(false, true)) {
      val envelope = F1Slot.createRecovery(identity, secret, master, 3, confirmed)
      assertEquals(204, envelope.size)
      assertArrayEquals(master, F1Slot.unwrapRecovery(identity, secret, envelope))
      val metadata = F1Slot.inspect(identity, envelope)
      assertEquals(3L, metadata.generation); assertEquals(2, metadata.slotType)
      assertEquals(if (confirmed) 2 else 1, metadata.recoveryState); assertEquals(1, metadata.policyId)
      assertEquals(16, metadata.slotId.size)
      assertArrayEquals(MessageDigest.getInstance("SHA-256").digest(envelope), metadata.digest)
      metadata.slotId.fill(0); metadata.digest.fill(0)
      assertFalse(metadata.slotId.all { it == 0.toByte() })
      rejects { F1Slot.unwrapRecovery(identity, secret.copyOf().also { it[0] = 0 }, envelope) }
      rejects { F1Slot.unwrapRecovery(DomainIdentity(ByteArray(16), identity.master), secret, envelope) }
      rejects { F1Slot.unwrapRecovery(DomainIdentity(identity.container, ByteArray(16)), secret, envelope) }
      rejects { F1Slot.unwrapPin(identity, pin.toCharArray(), envelope) }
      val mutatedState = envelope.copyOf().also { it[71] = if (confirmed) 1 else 2 }
      rejects { F1Slot.unwrapRecovery(identity, secret, mutatedState) }
    }
  }

  @Test fun `each recovery header and wrapped master byte rejects mutation`() {
    val envelope = F1Slot.createRecovery(identity, secret, master, 1, false)
    envelope.indices.forEach { offset ->
      rejects { F1Slot.unwrapRecovery(identity, secret, envelope.copyOf().also { it[offset] = (it[offset].toInt() xor 1).toByte() }) }
    }
  }

  @Test fun `PIN uses exact reviewed scrypt and separate credential domain`() {
    val chars = pin.toCharArray()
    val envelope = F1Slot.createPin(identity, chars, master, 1)
    assertTrue(chars.all { it == '\u0000' })
    val header = ByteBuffer.wrap(envelope)
    assertEquals(131072, header.getInt(76)); assertEquals(8, header.getInt(80)); assertEquals(1, header.getInt(84))
    assertEquals(1, F1Slot.inspect(identity, envelope).slotType)
    assertArrayEquals(master, F1Slot.unwrapPin(identity, pin.toCharArray(), envelope))
    rejects { F1Slot.unwrapPin(identity, "1234567890123457".toCharArray(), envelope) }
    rejects { F1Slot.unwrapRecovery(identity, secret, envelope) }
    listOf(76, 80, 84).forEach { offset ->
      rejects { F1Slot.unwrapPin(identity, pin.toCharArray(), envelope.copyOf().also { ByteBuffer.wrap(it).putInt(offset, Int.MAX_VALUE) }) }
    }
    val other = DomainIdentity(ByteArray(16) { 4 }, ByteArray(16) { 5 })
    rejects { F1Slot.unwrapPin(other, pin.toCharArray(), envelope) }
  }

  @Test fun `PIN syntax length key sizes and generation are strict and rejected chars wiped`() {
    for (invalid in listOf("1234", "1".repeat(65), "1".repeat(11) + "a", "1".repeat(11) + "\u0661", "1".repeat(11) + "\ud800", " 123456789012")) {
      val chars = invalid.toCharArray()
      rejects { F1Slot.createPin(identity, chars, master, 1) }
      assertTrue(chars.all { it == '\u0000' })
    }
    rejects { F1Slot.createRecovery(identity, ByteArray(31), master, 1, false) }
    rejects { F1Slot.createRecovery(identity, secret, ByteArray(31), 1, false) }
    rejects { F1Slot.createRecovery(identity, secret, master, 0, false) }
    assertArrayEquals(ByteArray(32) { (it + 80).toByte() }, secret)
  }

  @Test fun `slot parser rejects unknown format types KDF reserved physical and cross fields`() {
    val envelope = F1Slot.createRecovery(identity, secret, master, 1, true)
    for (offset in listOf(10, 68, 70, 72, 74, 76, 80, 84, 88, 90, 136, 148, 152, 154)) {
      rejects { F1Slot.inspect(identity, envelope.copyOf().also { it[offset] = 0x7f }) }
    }
    rejects { F1Slot.inspect(identity, envelope.copyOf().also { ByteBuffer.wrap(it).putLong(60, Long.MIN_VALUE) }) }
    rejects { F1Slot.inspect(identity, envelope.copyOf(envelope.size - 1)) }
    rejects { F1Slot.inspect(identity, envelope + 0) }
    rejects { F1Slot.inspect(identity, byteArrayOf()) }
    assertEquals(F1Failure.UNSUPPORTED, caught { F1Slot.inspect(identity, envelope.copyOf().also { it[9] = 2 }) }.failure)
  }

  @Test fun `replacement creates independent slot salt and nonce`() {
    val first = F1Slot.createRecovery(identity, secret, master, Long.MAX_VALUE, false)
    val second = F1Slot.createRecovery(identity, secret, master, Long.MAX_VALUE, true)
    for ((start, end) in listOf(44 to 60, 92 to 124, 124 to 136)) assertFalse(first.copyOfRange(start, end).contentEquals(second.copyOfRange(start, end)))
    assertEquals(Long.MAX_VALUE, F1Slot.inspect(identity, first).generation)
  }
}
