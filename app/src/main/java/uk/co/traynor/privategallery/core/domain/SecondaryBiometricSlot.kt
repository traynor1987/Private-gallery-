package uk.co.traynor.privategallery.core.domain

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec

/** No context/root/Primary access. Only a per-use authenticated CryptoObject can complete. */
class SecondaryBiometricSlot internal constructor(private val backend: SecondaryBiometricKeyBackend) {
  constructor() : this(AndroidSecondaryBiometricKeys())
  private val owner = Any()

  fun prepareEnrollment(identity: DomainIdentity, slotId: ByteArray, generation: Long): PendingEnrollment {
    f1Check(slotId.size == 16 && generation > 0)
    val alias = SecondaryBiometricEnvelope.alias(identity, slotId)
    // Allocate close acknowledgement metadata before any owned alias/provider exists.
    val closeResult = java.util.concurrent.CompletableFuture<Unit>()
    val cipher = neutral { backend.create(alias) }
    return try {
      neutral { PendingEnrollment(owner, cipher, SecondaryBiometricEnvelope.header(identity, slotId, generation, cipher.iv), alias, backend, closeResult) }
    } catch (e: Exception) {
      neutral { backend.deleteOwned(alias) }
      throw e
    }
  }

  /** Caller retains master ownership. Pending must be closed even if store installation fails. */
  fun finishEnrollment(pending: PendingEnrollment, authenticatedCipher: Cipher, master: ByteArray): ByteArray =
    pending.finish(owner, authenticatedCipher, master)

  fun prepareUnlock(identity: DomainIdentity, slotId: ByteArray, generation: Long, envelope: ByteArray): PendingUnlock {
    val snapshot = SecondaryBiometricEnvelope.parse(identity, slotId, generation, envelope)
    return try {
      val cipher = neutral { backend.decrypt(SecondaryBiometricEnvelope.alias(identity, slotId), snapshot.copyOfRange(72, 84)) }
      PendingUnlock(owner, cipher, snapshot)
    } catch (e: Exception) { snapshot.fill(0); throw e }
  }

  /** Owned mutable result is not authority; authenticate current store AND exact selection next. */
  fun finishUnlock(pending: PendingUnlock, authenticatedCipher: Cipher): ByteArray = pending.finish(owner, authenticatedCipher)

  class PendingEnrollment internal constructor(
    private val owner: Any, cipher: Cipher, private val header: ByteArray,
    private val alias: String, private val backend: SecondaryBiometricKeyBackend,
    private val closeResult: java.util.concurrent.CompletableFuture<Unit>
  ) : AutoCloseable {
    private var platformCipher: Cipher? = cipher
    /** App access ends with this request; this is not Android-internal retirement proof. */
    val cipher: Cipher @Synchronized get() {
      if (closed || consumed) throw F1Exception(F1Failure.UNAVAILABLE)
      return platformCipher ?: throw F1Exception(F1Failure.UNAVAILABLE)
    }
    private var consumed = false
    private var completed = false
    private var closed = false
    private var installed = false
    private var usingThread: Thread? = null
    private val useReturned = java.util.concurrent.CompletableFuture<Unit>()

    internal fun finish(expectedOwner: Any, returnedCipher: Cipher, master: ByteArray): ByteArray {
      if (Thread.holdsLock(this)) throw F1Exception(F1Failure.UNAVAILABLE)
      val admittedCipher = synchronized(this) {
        // Repeat rejection cannot retire the first completed enrollment.
        if (consumed || closed) throw F1Exception(F1Failure.UNAVAILABLE)
        val value = platformCipher ?: throw F1Exception(F1Failure.UNAVAILABLE)
        consumed = true; usingThread = Thread.currentThread()
        value
      }
      try {
        var copy: ByteArray? = null
        try {
          f1Check(owner === expectedOwner && admittedCipher === returnedCipher && master.size == 32)
          copy = master.copyOf()
          val body = neutral { admittedCipher.updateAAD(header); admittedCipher.doFinal(copy) }
          try {
            f1Check(body.size == 48)
            val envelope = header + body
            synchronized(this) {
              if (closed) { envelope.fill(0); throw F1Exception(F1Failure.UNAVAILABLE) }
              completed = true
            }
            return envelope
          } finally { body.fill(0) }
        } finally {
          copy?.fill(0); header.fill(0)
          synchronized(this) { usingThread = null }
          useReturned.complete(Unit)
        }
      } catch (failure: Throwable) {
        // The real provider call has unwound and the borrowed arrays are wiped.
        // Disposal may block/reenter, so it cannot inherit the enrollment monitor.
        close()
        throw failure
      }
    }

    /** Transfer ownership inside the originating promotion gate BEFORE the pointer syscall.
     * A durably signed retirement plan must own alias cleanup if selection fails. */
    @Synchronized fun markInstalled() {
      if (!completed || closed || installed) throw F1Exception(F1Failure.UNAVAILABLE)
      installed = true
    }

    // Preallocated original completion: duplicate close cannot acknowledge an unfinished
    // or failed first deletion. No provider callback runs while this monitor is held.
    private var closingThread: Thread? = null
    override fun close() {
      // An inherited monitor would keep provider work gated or duplicate waiting deadlocked.
      if (Thread.holdsLock(this)) throw F1Exception(F1Failure.UNAVAILABLE)
      var delete = false
      var waitForUse = false
      val first = synchronized(this) {
        if (usingThread === Thread.currentThread()) throw F1Exception(F1Failure.UNAVAILABLE)
        if (closed) {
          if (closingThread === Thread.currentThread()) throw F1Exception(F1Failure.UNAVAILABLE)
          false
        } else {
          closed = true
          consumed = true
          waitForUse = usingThread != null
          delete = !installed
          closingThread = Thread.currentThread()
          true
        }
      }
      if (first) {
        try {
          if (waitForUse) awaitBiometricReturn(useReturned)
          synchronized(this) { header.fill(0); platformCipher = null }
          if (delete) neutral { backend.deleteOwned(alias) }
          closeResult.complete(Unit)
        } catch (failure: Throwable) {
          closeResult.completeExceptionally(failure)
          throw failure
        } finally { synchronized(this) { closingThread = null } }
      } else {
        try { closeResult.get() }
        catch (interrupted: InterruptedException) {
          Thread.currentThread().interrupt()
          throw F1Exception(F1Failure.UNAVAILABLE)
        } catch (failed: java.util.concurrent.ExecutionException) {
          throw checkNotNull(failed.cause)
        }
      }
    }

  }

  class PendingUnlock internal constructor(private val owner: Any, cipher: Cipher, private val snapshot: ByteArray) : AutoCloseable {
    private var platformCipher: Cipher? = cipher
    val cipher: Cipher @Synchronized get() {
      if (closed || consumed) throw F1Exception(F1Failure.UNAVAILABLE)
      return platformCipher ?: throw F1Exception(F1Failure.UNAVAILABLE)
    }
    private var consumed = false
    private var closed = false
    private var usingThread: Thread? = null
    private val useReturned = java.util.concurrent.CompletableFuture<Unit>()
    private val digest = MessageDigest.getInstance("SHA-256").digest(snapshot)
    val selectionDigest: ByteArray @Synchronized get() {
      if (closed) throw F1Exception(F1Failure.UNAVAILABLE)
      return digest.copyOf()
    }

    internal fun finish(expectedOwner: Any, returnedCipher: Cipher): ByteArray {
      if (Thread.holdsLock(this)) throw F1Exception(F1Failure.UNAVAILABLE)
      val admittedCipher = synchronized(this) {
        if (consumed || closed) throw F1Exception(F1Failure.UNAVAILABLE)
        val value = platformCipher ?: throw F1Exception(F1Failure.UNAVAILABLE)
        consumed = true; usingThread = Thread.currentThread()
        value
      }
      try {
        f1Check(owner === expectedOwner && admittedCipher === returnedCipher)
        val output = neutral {
          admittedCipher.updateAAD(snapshot, 0, SecondaryBiometricEnvelope.HEADER_LENGTH)
          admittedCipher.doFinal(snapshot, SecondaryBiometricEnvelope.HEADER_LENGTH, 48)
        }
        synchronized(this) {
          if (closed || output.size != 32) {
            output.fill(0)
            throw F1Exception(if (closed) F1Failure.UNAVAILABLE else F1Failure.CORRUPT)
          }
        }
        return output
      } finally {
        snapshot.fill(0)
        synchronized(this) { usingThread = null }
        useReturned.complete(Unit)
      }
    }

    override fun close() {
      if (Thread.holdsLock(this)) throw F1Exception(F1Failure.UNAVAILABLE)
      val waitForUse = synchronized(this) {
        if (usingThread === Thread.currentThread()) throw F1Exception(F1Failure.UNAVAILABLE)
        closed = true; consumed = true
        usingThread != null
      }
      if (waitForUse) awaitBiometricReturn(useReturned)
      synchronized(this) { snapshot.fill(0); digest.fill(0); platformCipher = null }
    }
  }
}

/** Internal seam for synthetic format/lifetime tests; production always uses Android Keystore. */
internal interface SecondaryBiometricKeyBackend {
  /** Fresh alias only. Own failed creation cleanup internally; never replace an existing key. */
  fun create(alias: String): Cipher
  fun decrypt(alias: String, nonce: ByteArray): Cipher
  fun deleteOwned(alias: String)
}

private inline fun <T> neutral(block: () -> T): T = try { block() }
catch (e: F1Exception) { throw e }
catch (_: AEADBadTagException) { throw F1Exception(F1Failure.CORRUPT) }
catch (_: Exception) { throw F1Exception(F1Failure.UNAVAILABLE) }

internal class AndroidSecondaryBiometricKeys : SecondaryBiometricKeyBackend {
  private fun store(): KeyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
  override fun create(alias: String): Cipher = synchronized(aliasLock) {
    val store = store()
    if (store.containsAlias(alias)) throw F1Exception(F1Failure.UNAVAILABLE)
    // Under this process-wide lock no other enrollment may acquire this owned alias.
    try {
      val spec = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
        .setKeySize(256).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
        .setRandomizedEncryptionRequired(true).setUserAuthenticationRequired(true)
        .setInvalidatedByBiometricEnrollment(true)
      if (Build.VERSION.SDK_INT >= 30) spec.setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
      else spec.setUserAuthenticationValidityDurationSeconds(-1)
      val key = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply { init(spec.build()) }.generateKey()
      verify(key)
      Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key) }
    } catch (e: Exception) {
      store.deleteEntry(alias)
      throw e
    }
  }

  override fun decrypt(alias: String, nonce: ByteArray): Cipher = synchronized(aliasLock) {
    val key = store().getKey(alias, null) as? SecretKey ?: throw F1Exception(F1Failure.UNAVAILABLE)
    verify(key)
    Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, nonce)) }
  }

  override fun deleteOwned(alias: String) = synchronized(aliasLock) { store().deleteEntry(alias) }

  internal fun verify(key: SecretKey): KeyInfo {
    val info = SecretKeyFactory.getInstance(key.algorithm, "AndroidKeyStore").getKeySpec(key, KeyInfo::class.java) as KeyInfo
    val hardware = if (Build.VERSION.SDK_INT >= 31) {
      info.securityLevel == KeyProperties.SECURITY_LEVEL_TRUSTED_ENVIRONMENT || info.securityLevel == KeyProperties.SECURITY_LEVEL_STRONGBOX
    } else info.isInsideSecureHardware
    val perUse = info.userAuthenticationValidityDurationSeconds in -1..0 &&
      (if (Build.VERSION.SDK_INT >= 30) info.userAuthenticationType == KeyProperties.AUTH_BIOMETRIC_STRONG else info.userAuthenticationValidityDurationSeconds == -1)
    if (!hardware || !info.isUserAuthenticationRequired || !info.isUserAuthenticationRequirementEnforcedBySecureHardware ||
      !info.isInvalidatedByBiometricEnrollment || !perUse || info.keySize != 256 ||
      info.purposes != (KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT) ||
      !info.blockModes.contentEquals(arrayOf(KeyProperties.BLOCK_MODE_GCM)) ||
      !info.encryptionPaddings.contentEquals(arrayOf(KeyProperties.ENCRYPTION_PADDING_NONE))) {
      throw F1Exception(F1Failure.UNAVAILABLE)
    }
    return info
  }

  private companion object { val aliasLock = Any() }
}

/** Actual synchronous provider-return barrier, never cancellation/timeout dispatch. */
private fun awaitBiometricReturn(result: java.util.concurrent.CompletableFuture<Unit>) {
  try { result.get() }
  catch (interrupted: InterruptedException) {
    Thread.currentThread().interrupt(); throw F1Exception(F1Failure.UNAVAILABLE)
  } catch (failed: java.util.concurrent.ExecutionException) { throw checkNotNull(failed.cause) }
}
