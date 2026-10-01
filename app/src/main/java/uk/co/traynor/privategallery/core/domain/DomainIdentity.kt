package uk.co.traynor.privategallery.core.domain

import java.security.MessageDigest

/** Opaque public namespace IDs, not authority or master-key material. */
class DomainIdentity(container: ByteArray, master: ByteArray) {
  private val containerBytes: ByteArray
  private val masterBytes: ByteArray
  init {
    f1Check(container.size == 16 && master.size == 16)
    containerBytes = container.copyOf()
    masterBytes = master.copyOf()
  }
  val container: ByteArray get() = containerBytes.copyOf()
  val master: ByteArray get() = masterBytes.copyOf()
  internal fun matches(container: ByteArray, master: ByteArray): Boolean =
    MessageDigest.isEqual(containerBytes, container) and MessageDigest.isEqual(masterBytes, master)
}

/** Expected immutable record identity supplied by a scoped authenticated caller. */
class F1Context(val identity: DomainIdentity, val purpose: Int, objectId: ByteArray, val generation: Long) {
  private val objectBytes: ByteArray
  init {
    f1Check(purpose in 1..11 && objectId.size == 16 && generation > 0)
    objectBytes = objectId.copyOf()
  }
  val objectId: ByteArray get() = objectBytes.copyOf()
  internal fun matchesObject(objectId: ByteArray): Boolean = MessageDigest.isEqual(objectBytes, objectId)
}

enum class F1Failure { UNSUPPORTED, CORRUPT, UNAVAILABLE }

/** Fixed neutral failure; deliberately carries no provider/ciphertext/context details. */
class F1Exception(val failure: F1Failure) : SecurityException(failure.name)

internal fun f1Check(condition: Boolean) {
  if (!condition) throw F1Exception(F1Failure.CORRUPT)
}
