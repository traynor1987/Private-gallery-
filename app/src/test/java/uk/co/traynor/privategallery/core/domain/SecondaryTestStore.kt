package uk.co.traynor.privategallery.core.domain

import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** In-process synthetic maintenance keys only. Production default is AndroidKeyStore, no fallback. */
internal object SyntheticRetirementKeys : SecondaryRetirementKeys {
  private val keys = ConcurrentHashMap<String, ByteArray>()
  val removedAliases = java.util.Collections.synchronizedList(mutableListOf<String>())
  private fun id(identity: DomainIdentity) = identity.container.hex() + identity.master.hex()
  private fun mac(key: ByteArray, body: ByteArray) = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(key,"HmacSHA256")) }.doFinal(body)
  override fun sign(identity: DomainIdentity, body: ByteArray) = mac(keys.computeIfAbsent(id(identity)) { F1Crypto.random(32) },body)
  override fun verify(identity: DomainIdentity, body: ByteArray, tag: ByteArray) {
    val key = keys[id(identity)] ?: throw SecondaryMaintenanceUnavailable()
    storeCheck(MessageDigest.isEqual(mac(key,body),tag))
  }
  fun forget(identity: DomainIdentity) { keys.remove(id(identity)) }
  fun hasKey(identity: DomainIdentity) = keys.containsKey(id(identity))
  override fun deleteBiometric(identity: DomainIdentity, slotId: ByteArray) { removedAliases += SecondaryBiometricEnvelope.alias(identity,slotId) }
}
internal fun testStore(files: File, io: SecondaryStorageIo = DurableSecondaryIo, @Suppress("UNUSED_PARAMETER") legacy: Unit = Unit) =
  SecondaryStore(files,io,SyntheticRetirementKeys,Unit)
