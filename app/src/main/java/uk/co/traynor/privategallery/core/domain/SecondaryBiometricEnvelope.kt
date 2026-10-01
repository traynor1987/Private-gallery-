package uk.co.traynor.privategallery.core.domain

import java.nio.ByteBuffer
import java.security.MessageDigest

/** H2 device-only extension; never an ordinary PGSLOT01 slot or session authority. */
object SecondaryBiometricEnvelope {
  const val HEADER_LENGTH = 96
  const val ENVELOPE_LENGTH = HEADER_LENGTH + 48
  private val magic = "PGBIO001".toByteArray(Charsets.US_ASCII)

  /** Untrusted bounded metadata validation, completed before any Keystore lookup. */
  fun selectionDigest(identity: DomainIdentity, slotId: ByteArray, generation: Long, envelope: ByteArray): ByteArray =
    parse(identity, slotId, generation, envelope).let { parsed ->
      try { MessageDigest.getInstance("SHA-256").digest(parsed) } finally { parsed.fill(0) }
    }

  internal fun alias(identity: DomainIdentity, slotId: ByteArray): String {
    f1Check(slotId.size == 16)
    fun hex(bytes: ByteArray): String = bytes.joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }
    return "pg.device.slot.v1." + hex(identity.container) + "." + hex(identity.master) + "." + hex(slotId)
  }

  internal fun header(identity: DomainIdentity, slotId: ByteArray, generation: Long, providerNonce: ByteArray): ByteArray {
    f1Check(slotId.size == 16 && generation > 0 && providerNonce.size == 12)
    return ByteBuffer.allocate(HEADER_LENGTH).put(magic).putShort(1).putShort(HEADER_LENGTH.toShort())
      .putShort(1).putShort(1).put(identity.container).put(identity.master).put(slotId).putLong(generation)
      .put(providerNonce).putShort(32).putShort(16).putInt(48).putInt(0).array()
  }

  internal fun parse(identity: DomainIdentity, slotId: ByteArray, generation: Long, envelope: ByteArray): ByteArray {
    f1Check(slotId.size == 16 && generation > 0 && envelope.size == ENVELOPE_LENGTH)
    val copy = envelope.copyOf()
    try {
      val b = ByteBuffer.wrap(copy)
      f1Check(copy.copyOfRange(0, 8).contentEquals(magic))
      if ((b.getShort(8).toInt() and 65535) != 1 || (b.getShort(12).toInt() and 65535) != 1) {
        throw F1Exception(F1Failure.UNSUPPORTED)
      }
      f1Check(b.getShort(10).toInt() == HEADER_LENGTH && b.getShort(14).toInt() == 1)
      f1Check(identity.matches(copy.copyOfRange(16, 32), copy.copyOfRange(32, 48)))
      f1Check(MessageDigest.isEqual(slotId, copy.copyOfRange(48, 64)))
      f1Check(b.getLong(64) == generation && generation > 0)
      f1Check(b.getShort(84).toInt() == 32 && b.getShort(86).toInt() == 16 && b.getInt(88) == 48 && b.getInt(92) == 0)
      return copy
    } catch (e: Exception) { copy.fill(0); throw e }
  }
}
