package uk.co.traynor.privategallery.core.domain

import java.nio.ByteBuffer
import java.security.MessageDigest
import javax.crypto.Cipher
import org.bouncycastle.crypto.generators.SCrypt

/** Parsed public metadata is untrusted until unwrap AND active authenticated catalog binding. */
class F1SlotMetadata internal constructor(
  slotId: ByteArray,
  val generation: Long,
  val slotType: Int,
  val recoveryState: Int,
  val policyId: Int,
  digest: ByteArray
) {
  private val id = slotId.copyOf()
  private val hash = digest.copyOf()
  val slotId: ByteArray get() = id.copyOf()
  val digest: ByteArray get() = hash.copyOf()
}

/** Portable F1-r2 PIN/recovery envelopes. Device layers require a separate reviewed adapter. */
object F1Slot {
  const val HEADER_LENGTH = 156
  private const val ENVELOPE_LENGTH = HEADER_LENGTH + 48
  private const val N = 131072
  private const val R = 8
  private const val P = 1
  private val magic = "PGSLOT01".toByteArray(Charsets.US_ASCII)
  private val label = "private-gallery:future:slot:v1".toByteArray(Charsets.US_ASCII)

  /** Consumes and wipes PIN characters on all exits; never modifies caller master. */
  fun createPin(identity: DomainIdentity, pin: CharArray, master: ByteArray, generation: Long): ByteArray = try {
    f1Check(master.size == 32 && generation > 0)
    val bytes = pinBytes(pin)
    try { create(identity, bytes, master, generation, 1, 0) } finally { bytes.fill(0) }
  } finally { pin.fill('\u0000') }

  /** Caller must verify the exact envelope against the authenticated active catalog. */
  fun unwrapPin(identity: DomainIdentity, pin: CharArray, envelope: ByteArray): ByteArray = try {
    val parsed = parse(identity, envelope)
    f1Check(parsed.metadata.slotType == 1)
    val bytes = pinBytes(pin)
    try { unwrap(bytes, parsed) } finally { bytes.fill(0) }
  } finally { pin.fill('\u0000') }

  fun createRecovery(identity: DomainIdentity, secret: ByteArray, master: ByteArray, generation: Long, confirmed: Boolean): ByteArray {
    f1Check(secret.size == 32 && master.size == 32 && generation > 0)
    val bytes = secret.copyOf()
    return try { create(identity, bytes, master, generation, 2, if (confirmed) 2 else 1) }
    finally { bytes.fill(0) }
  }

  /** Pending recovery may unwrap for possession confirmation but is not configured recovery. */
  fun unwrapRecovery(identity: DomainIdentity, secret: ByteArray, envelope: ByteArray): ByteArray {
    f1Check(secret.size == 32)
    val parsed = parse(identity, envelope)
    f1Check(parsed.metadata.slotType == 2)
    val bytes = secret.copyOf()
    return try { unwrap(bytes, parsed) } finally { bytes.fill(0) }
  }

  /** Bounded parse only. This does not authenticate metadata or establish an active slot. */
  fun inspect(identity: DomainIdentity, envelope: ByteArray): F1SlotMetadata = parse(identity, envelope).metadata

  private class Parsed(val snapshot: ByteArray, val metadata: F1SlotMetadata) {
    val header: ByteArray get() = snapshot.copyOfRange(0, HEADER_LENGTH)
    val salt: ByteArray get() = snapshot.copyOfRange(92, 124)
    val nonce: ByteArray get() = snapshot.copyOfRange(124, 136)
    val info: ByteArray get() = keyInfo(
      DomainIdentity(snapshot.copyOfRange(12, 28), snapshot.copyOfRange(28, 44)),
      metadata.slotId, metadata.generation, metadata.slotType, metadata.policyId
    )
  }

  private fun parse(identity: DomainIdentity, envelope: ByteArray): Parsed {
    // Both supported ordinary profiles have one fixed physical size; no KDF runs here.
    f1Check(envelope.size == ENVELOPE_LENGTH)
    val snapshot = envelope.copyOf()
    val input = ByteBuffer.wrap(snapshot)
    f1Check(snapshot.copyOfRange(0, 8).contentEquals(magic))
    if (F1Crypto.u16(input, 8) != 1) throw F1Exception(F1Failure.UNSUPPORTED)
    f1Check(F1Crypto.u16(input, 10) == HEADER_LENGTH)
    f1Check(identity.matches(snapshot.copyOfRange(12, 28), snapshot.copyOfRange(28, 44)))
    val generation = input.getLong(60)
    val type = F1Crypto.u16(input, 68)
    val state = F1Crypto.u16(input, 70)
    val kdf = F1Crypto.u16(input, 74)
    f1Check(generation > 0 && F1Crypto.u16(input, 72) == 1)
    f1Check(F1Crypto.u16(input, 88) == 32 && F1Crypto.u16(input, 90) == 0)
    f1Check((136 until 148).all { snapshot[it] == 0.toByte() })
    f1Check(input.getInt(148) == 48 && F1Crypto.u16(input, 152) == 1 && F1Crypto.u16(input, 154) == 0)
    when (type) {
      1 -> f1Check(state == 0 && kdf == 1 && input.getInt(76) == N && input.getInt(80) == R && input.getInt(84) == P)
      2 -> f1Check(state in 1..2 && kdf == 2 && input.getInt(76) == 0 && input.getInt(80) == 0 && input.getInt(84) == 0)
      else -> throw F1Exception(F1Failure.CORRUPT)
    }
    val metadata = F1SlotMetadata(snapshot.copyOfRange(44, 60), generation, type, state, 1, MessageDigest.getInstance("SHA-256").digest(snapshot))
    return Parsed(snapshot, metadata)
  }

  private fun create(identity: DomainIdentity, secret: ByteArray, master: ByteArray, generation: Long, type: Int, state: Int): ByteArray {
    val id = F1Crypto.random(16)
    val salt = F1Crypto.random(32)
    val nonce = F1Crypto.random(12)
    val header = ByteBuffer.allocate(HEADER_LENGTH).put(magic).putShort(1).putShort(HEADER_LENGTH.toShort())
      .put(identity.container).put(identity.master).put(id).putLong(generation)
      .putShort(type.toShort()).putShort(state.toShort()).putShort(1).putShort(if (type == 1) 1 else 2)
      .putInt(if (type == 1) N else 0).putInt(if (type == 1) R else 0).putInt(if (type == 1) P else 0)
      .putShort(32).putShort(0).put(salt).put(nonce).put(ByteArray(12)).putInt(48).putShort(1).putShort(0).array()
    val key = wrappingKey(secret, salt, keyInfo(identity, id, generation, type, 1), type)
    val copy = master.copyOf()
    return try { header + F1Crypto.aead(Cipher.ENCRYPT_MODE, key, nonce, header, copy) }
    finally { key.fill(0); copy.fill(0) }
  }

  private fun unwrap(secret: ByteArray, parsed: Parsed): ByteArray {
    val key = wrappingKey(secret, parsed.salt, parsed.info, parsed.metadata.slotType)
    return try { F1Crypto.aead(Cipher.DECRYPT_MODE, key, parsed.nonce, parsed.header, parsed.snapshot, HEADER_LENGTH, 48) }
    finally { key.fill(0) }
  }

  private fun wrappingKey(secret: ByteArray, salt: ByteArray, info: ByteArray, type: Int): ByteArray {
    val base = if (type == 1) SCrypt.generate(secret, salt, N, R, P, 32) else secret.copyOf()
    return try { F1Crypto.hkdf(base, salt, info) } finally { base.fill(0) }
  }

  private fun pinBytes(pin: CharArray): ByteArray {
    f1Check(pin.size in 12..64 && pin.all { it in '0'..'9' })
    return ByteArray(pin.size) { pin[it].code.toByte() }
  }

  internal fun keyInfo(identity: DomainIdentity, slotId: ByteArray, generation: Long, type: Int, policy: Int): ByteArray {
    f1Check(slotId.size == 16 && generation > 0 && type in 1..4 && policy == 1)
    return ByteBuffer.allocate(94).putShort(label.size.toShort()).put(label).putShort(1)
      .put(identity.container).put(identity.master).put(slotId).putLong(generation)
      .putShort(type.toShort()).putShort(policy.toShort()).array()
  }
}
