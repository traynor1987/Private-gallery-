package uk.co.traynor.privategallery.core.domain

import android.content.Context
import android.content.ContextWrapper
import android.util.Base64
import androidx.test.core.app.ApplicationProvider
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.junit.Assert.*
import org.junit.Test
import uk.co.traynor.privategallery.core.security.BiometricVaultKeyStore

/** Actual Primary envelope adapter, independent synthetic device keys. Does not claim hardware auth. */
class PrimarySecondaryBiometricAdaptersTest {
  @Test fun actualPrimaryEnvelopeAndSecondaryEnvelopeCannotCrossUnwrap() {
    val base = ApplicationProvider.getApplicationContext<Context>()
    val prefix = "phase2-bio-${UUID.randomUUID()}"
    val context = object : ContextWrapper(base) {
      override fun getSharedPreferences(name: String, mode: Int) = base.getSharedPreferences("$prefix-$name", mode)
    }
    val prefs = context.getSharedPreferences("vault-biometric-envelope", 0)
    val pKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    val sKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    fun cipher(mode: Int, key: SecretKey, nonce: ByteArray? = null) = Cipher.getInstance("AES/GCM/NoPadding").apply {
      if (nonce == null) init(mode, key) else init(mode, key, GCMParameterSpec(128, nonce))
    }
    val backend = object : SecondaryBiometricKeyBackend {
      override fun create(alias: String) = cipher(Cipher.ENCRYPT_MODE, sKey)
      override fun decrypt(alias: String, nonce: ByteArray) = cipher(Cipher.DECRYPT_MODE, sKey, nonce)
      override fun deleteOwned(alias: String) {}
    }
    val primary = BiometricVaultKeyStore(context)
    val secondary = SecondaryBiometricSlot(backend)
    val identity = DomainIdentity(F1Crypto.random(16), F1Crypto.random(16)); val slotId = F1Crypto.random(16)
    val pMaster = F1Crypto.random(32); val sMaster = F1Crypto.random(32)
    try {
      val pCipher = cipher(Cipher.ENCRYPT_MODE, pKey)
      primary.saveAuthenticated(pCipher, pMaster)
      assertArrayEquals(pMaster, primary.unwrapAuthenticated(cipher(Cipher.DECRYPT_MODE, pKey, pCipher.iv)))
      assertThrows(javax.crypto.AEADBadTagException::class.java) {
        primary.unwrapAuthenticated(cipher(Cipher.DECRYPT_MODE, sKey, pCipher.iv))
      }
      val pBody = Base64.decode(prefs.getString("ciphertext", null), Base64.NO_WRAP)
      val sEnvelope = secondary.prepareEnrollment(identity, slotId, 1).use { pending ->
        secondary.finishEnrollment(pending, pending.cipher, sMaster).also { pending.markInstalled() }
      }
      secondary.prepareUnlock(identity, slotId, 1, sEnvelope).use {
        assertArrayEquals(sMaster, secondary.finishUnlock(it, it.cipher))
      }
      // Secondary's parser/context is satisfied, but Primary ciphertext still cannot authenticate.
      val substituted = SecondaryBiometricEnvelope.header(identity, slotId, 1, pCipher.iv) + pBody
      assertThrows(F1Exception::class.java) {
        secondary.prepareUnlock(identity, slotId, 1, substituted).use { secondary.finishUnlock(it, it.cipher) }
      }
      val sNonce = sEnvelope.copyOfRange(72, 84)
      prefs.edit().putString("nonce", Base64.encodeToString(sNonce, Base64.NO_WRAP))
        .putString("ciphertext", Base64.encodeToString(sEnvelope.copyOfRange(96, 144), Base64.NO_WRAP)).commit()
      assertThrows(javax.crypto.AEADBadTagException::class.java) {
        primary.unwrapAuthenticated(cipher(Cipher.DECRYPT_MODE, pKey, sNonce))
      }
    } finally { pMaster.fill(0); sMaster.fill(0); prefs.edit().clear().commit() }
  }
}
