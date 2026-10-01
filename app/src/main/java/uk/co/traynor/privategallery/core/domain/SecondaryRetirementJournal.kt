package uk.co.traynor.privategallery.core.domain

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.nio.ByteBuffer
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey

/** Maintenance authentication only: this key never wraps/derives a master or grants a session. */
internal interface SecondaryRetirementKeys {
  fun sign(identity: DomainIdentity, body: ByteArray): ByteArray
  fun verify(identity: DomainIdentity, body: ByteArray, tag: ByteArray)
  fun deleteBiometric(identity: DomainIdentity, slotId: ByteArray)
}
internal object AndroidSecondaryRetirementKeys : SecondaryRetirementKeys {
  private fun alias(identity: DomainIdentity) = "pg.maintenance.v1." + identity.container.hex() + "." + identity.master.hex()
  private fun store() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
  override fun sign(identity: DomainIdentity, body: ByteArray): ByteArray {
    val name = alias(identity)
    val existing = store().getKey(name, null) as? SecretKey
    val key = existing ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256, "AndroidKeyStore").apply {
      init(KeyGenParameterSpec.Builder(name, KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY)
        .setDigests(KeyProperties.DIGEST_SHA256).setKeySize(256).build())
    }.generateKey()
    return mac(key, body)
  }
  override fun verify(identity: DomainIdentity, body: ByteArray, tag: ByteArray) {
    // A missing verification key is unavailable, never silently regenerated.
    val key = store().getKey(alias(identity), null) as? SecretKey ?: throw SecondaryStoreException()
    storeCheck(MessageDigest.isEqual(mac(key, body), tag))
  }
  override fun deleteBiometric(identity: DomainIdentity, slotId: ByteArray) {
    // Derive only this exact owned alias; never enumerate aliases or touch Primary.
    store().deleteEntry(SecondaryBiometricEnvelope.alias(identity, slotId))
  }
  private fun mac(key: SecretKey, body: ByteArray) = Mac.getInstance("HmacSHA256").apply { init(key) }.doFinal(body)
}

/** Signed before any new master-wrapper persistence; both possible pointer outcomes are explicit. */
internal class RetirementPlan(
  val identity: DomainIdentity,
  val prior: String,
  val priorBinding: ByteArray,
  val next: String,
  val nextBinding: ByteArray?,
  val tokens: List<String>,
  val biometricSlots: List<String>,
) {
  fun body(): ByteArray {
    storeCheck(tokens.size in 1..8192 && biometricSlots.size <= 8192)
    storeCheck(tokens == tokens.distinct().sorted() && biometricSlots == biometricSlots.distinct().sorted())
    storeCheck(prior in tokens && next in tokens && prior != next && priorBinding.size == 32 && (nextBinding == null || nextBinding.size == 32))
    fun bytes(id: String): ByteArray { storeCheck(id.matches(Regex("[0-9a-f]{32}"))); return id.chunked(2).map { it.toInt(16).toByte() }.toByteArray() }
    return ByteBuffer.allocate(146 + 16 * (tokens.size + biometricSlots.size))
      .put("PGRET001".toByteArray(Charsets.US_ASCII)).putShort(1).putShort(if (nextBinding == null) 1 else 2)
      .put(identity.container).put(identity.master).put(bytes(prior)).put(priorBinding).put(bytes(next))
      .put(nextBinding ?: ByteArray(32)).putShort(tokens.size.toShort()).putShort(biometricSlots.size.toShort()).putShort(0)
      .also { b -> tokens.forEach { b.put(bytes(it)) }; biometricSlots.forEach { b.put(bytes(it)) } }.array()
  }
  companion object {
    const val MAX_BYTES = 146 + 16 * 16384 + 32
    fun parse(encoded: ByteArray, keys: SecondaryRetirementKeys): RetirementPlan {
      storeCheck(encoded.size in 178..MAX_BYTES)
      val body = encoded.copyOfRange(0, encoded.size - 32)
      val b = ByteBuffer.wrap(body)
      storeCheck(body.copyOfRange(0, 8).contentEquals("PGRET001".toByteArray(Charsets.US_ASCII)) && b.getShort(8).toInt() == 1)
      val state = b.getShort(10).toInt(); storeCheck(state in 1..2)
      val identity = DomainIdentity(body.copyOfRange(12,28), body.copyOfRange(28,44))
      keys.verify(identity, body, encoded.copyOfRange(body.size, encoded.size))
      val count = b.getShort(140).toInt() and 65535; val aliases = b.getShort(142).toInt() and 65535
      storeCheck(count in 2..8192 && aliases <= 8192 && body.size == 146 + 16 * (count + aliases))
      // Bytes 144–145 reserve future schema fields; unknown extensions fail closed.
      storeCheck(b.getShort(144).toInt() == 0)
      fun ids(start: Int, size: Int) = List(size) { body.copyOfRange(start + 16*it, start + 16*(it+1)).hex() }
      val nextHash = body.copyOfRange(108,140)
      if (state == 1) storeCheck(nextHash.all { it == 0.toByte() })
      val plan = RetirementPlan(identity, body.copyOfRange(44,60).hex(), body.copyOfRange(60,92),
        body.copyOfRange(92,108).hex(), if (state == 2) nextHash else null, ids(146,count), ids(146 + 16*count,aliases))
      storeCheck(plan.body().contentEquals(body)); return plan
    }
  }
}
