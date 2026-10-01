package uk.co.traynor.privategallery.core.domain

import org.junit.Assert.*
import org.junit.Test
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Synthetic device keys exercise format/lifetime only, never biometric authorization. */
class SecondaryBiometricSlotTest {
  private val identity = DomainIdentity(ByteArray(16) { 1 }, ByteArray(16) { 2 })
  private val id = ByteArray(16) { 3 }
  private val master = ByteArray(32) { it.toByte() }
  private class SyntheticBackend : SecondaryBiometricKeyBackend {
    val keys = mutableMapOf<String, SecretKey>()
    var creates = 0
    var lookups = 0
    var deletes = 0
    override fun create(alias: String): Cipher {
      if (keys.containsKey(alias)) throw F1Exception(F1Failure.UNAVAILABLE)
      creates++
      val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
      keys[alias] = key
      return Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key) }
    }
    override fun decrypt(alias: String, nonce: ByteArray): Cipher {
      lookups++
      val key = keys[alias] ?: throw F1Exception(F1Failure.UNAVAILABLE)
      return Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, nonce)) }
    }
    override fun deleteOwned(alias: String) { deletes++; keys.remove(alias) }
  }
  private fun reject(block: () -> Unit): F1Failure {
    try { block(); fail("accepted") } catch (e: F1Exception) { return e.failure }
    throw AssertionError()
  }
  private fun enrollment(slot: SecondaryBiometricSlot): ByteArray {
    val pending = slot.prepareEnrollment(identity, id, 1)
    return try {
      slot.finishEnrollment(pending, pending.cipher, master).also { pending.markInstalled() }
    } finally { pending.close() }
  }
  @Test fun roundTripDigestAndDefensiveOwnership() {
    val backend = SyntheticBackend(); val slot = SecondaryBiometricSlot(backend)
    val envelope = enrollment(slot)
    assertEquals(144, envelope.size)
    val pending = slot.prepareUnlock(identity, id, 1, envelope)
    assertArrayEquals(java.security.MessageDigest.getInstance("SHA-256").digest(envelope), pending.selectionDigest)
    pending.selectionDigest.fill(0)
    envelope.fill(0)
    val decoded = slot.finishUnlock(pending, pending.cipher)
    assertArrayEquals(master, decoded)
    decoded.fill(0)
    assertEquals(1, backend.creates)
    reject { slot.finishUnlock(pending, pending.cipher) }
    pending.close()
  }
  @Test fun everyByteMutationAndWrongExpectedContextReject() {
    val backend = SyntheticBackend(); val slot = SecondaryBiometricSlot(backend)
    val envelope = enrollment(slot)
    for (index in envelope.indices) {
      val changed = envelope.copyOf(); changed[index] = (changed[index].toInt() xor 1).toByte()
      reject { slot.prepareUnlock(identity, id, 1, changed).use { slot.finishUnlock(it, it.cipher) } }
    }
    for (wrong in listOf(DomainIdentity(ByteArray(16) { 4 }, identity.master), DomainIdentity(identity.container, ByteArray(16) { 4 }))) {
      reject { slot.prepareUnlock(wrong, id, 1, envelope) }
    }
    reject { slot.prepareUnlock(identity, ByteArray(16) { 4 }, 1, envelope) }
    reject { slot.prepareUnlock(identity, id, 2, envelope) }
    reject { slot.prepareUnlock(identity, id, 0, envelope) }
    reject { slot.prepareUnlock(identity, id, 1, envelope + 0) }
  }
  @Test fun parsingCompletesBeforeAnyLookupAndNoMissingKeyRegeneration() {
    val slot = SecondaryBiometricSlot(SyntheticBackend())
    val envelope = enrollment(slot)
    val missing = SyntheticBackend(); val reader = SecondaryBiometricSlot(missing)
    val version = envelope.copyOf().apply { this[9] = 2 }
    assertEquals(F1Failure.UNSUPPORTED, reject { reader.prepareUnlock(identity, id, 1, version) })
    assertEquals(0, missing.lookups)
    reject { reader.prepareUnlock(identity, ByteArray(16), 1, envelope) }
    assertEquals(0, missing.lookups)
    assertEquals(F1Failure.UNAVAILABLE, reject { reader.prepareUnlock(identity, id, 1, envelope) })
    assertEquals(0, missing.creates)
  }
  @Test fun differentDeviceKeyAndPrimaryStyleBodyReject() {
    val backend = SyntheticBackend(); val slot = SecondaryBiometricSlot(backend); val envelope = enrollment(slot)
    val alias = SecondaryBiometricEnvelope.alias(identity, id)
    backend.keys[alias] = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    reject { slot.prepareUnlock(identity, id, 1, envelope).use { slot.finishUnlock(it, it.cipher) } }
    val primaryCipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, backend.keys[alias]) }
    val primaryBody = primaryCipher.doFinal(master) // Legacy Primary has no new full-header AAD.
    val swapped = SecondaryBiometricEnvelope.header(identity, id, 1, primaryCipher.iv) + primaryBody
    reject { slot.prepareUnlock(identity, id, 1, swapped).use { slot.finishUnlock(it, it.cipher) } }
  }
  @Test fun cipherIdentityClosedConsumedAndAdapterOwnerAreEnforced() {
    val backend = SyntheticBackend(); val slot = SecondaryBiometricSlot(backend)
    val pending = slot.prepareEnrollment(identity, id, 1)
    val other = Cipher.getInstance("AES/GCM/NoPadding")
    reject { slot.finishEnrollment(pending, other, master) }
    reject { slot.finishEnrollment(pending, pending.cipher, master) }
    assertEquals(1, backend.deletes)
    val closed = slot.prepareEnrollment(identity, id, 1); closed.close()
    reject { slot.finishEnrollment(closed, closed.cipher, master) }
    val owned = slot.prepareEnrollment(identity, id, 1)
    reject { SecondaryBiometricSlot(backend).finishEnrollment(owned, owned.cipher, master) }
    reject { slot.finishEnrollment(owned, owned.cipher, master) }
    val envelope = enrollment(slot)
    val decrypt = slot.prepareUnlock(identity, id, 1, envelope)
    reject { slot.finishUnlock(decrypt, other) }
    reject { slot.finishUnlock(decrypt, decrypt.cipher) }
    val cancelled = slot.prepareUnlock(identity, id, 1, envelope); cancelled.close()
    reject { slot.finishUnlock(cancelled, cancelled.cipher) }
  }
  @Test fun aliasesAreExactAndFailedEnrollmentOwnsOnlyFreshAlias() {
    val alias = SecondaryBiometricEnvelope.alias(identity, id)
    assertEquals(alias, SecondaryBiometricEnvelope.alias(identity, id.copyOf()))
    assertNotEquals("private_gallery_biometric_v1", alias)
    assertNotEquals(alias, SecondaryBiometricEnvelope.alias(DomainIdentity(identity.container, ByteArray(16)), id))
    assertNotEquals(alias, SecondaryBiometricEnvelope.alias(DomainIdentity(ByteArray(16), identity.master), id))
    assertNotEquals(alias, SecondaryBiometricEnvelope.alias(identity, ByteArray(16)))
    val backend = SyntheticBackend(); val slot = SecondaryBiometricSlot(backend)
    val pending = slot.prepareEnrollment(identity, id, 1)
    reject { slot.prepareEnrollment(identity, id, 2) }
    assertEquals(0, backend.deletes)
    reject { pending.markInstalled() }
    pending.close()
    assertEquals(1, backend.deletes)
    val unfinished = slot.prepareEnrollment(identity, id, 1)
    slot.finishEnrollment(unfinished, unfinished.cipher, master)
    unfinished.close() // A failed store installation must discard this enrollment's key.
    assertFalse(backend.keys.containsKey(alias))
    val committed = enrollment(slot)
    assertTrue(backend.keys.containsKey(alias))
    reject { slot.prepareEnrollment(identity, id, 2) }
    assertTrue(backend.keys.containsKey(alias))
    assertEquals(144, committed.size)
  }
}
