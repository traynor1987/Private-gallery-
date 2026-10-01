package uk.co.traynor.privategallery.core.domain

import android.os.Build
import android.security.keystore.KeyProperties
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.SecretKey
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Never enrolls/removes biometrics or uses owner slots; creates only random synthetic IDs. */
@RunWith(AndroidJUnit4::class)
class SecondaryBiometricKeystoreTest {
  private fun random(size: Int) = ByteArray(size).also { SecureRandom().nextBytes(it) }
  private fun store() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

  @Test fun missingDecryptAliasNeverCreatesAKey() {
    val identity = DomainIdentity(random(16), random(16)); val id = random(16)
    val alias = SecondaryBiometricEnvelope.alias(identity, id)
    val envelope = SecondaryBiometricEnvelope.header(identity, id, 1, random(12)) + ByteArray(48)
    assertFalse(store().containsAlias(alias))
    try {
      SecondaryBiometricSlot().prepareUnlock(identity, id, 1, envelope)
      fail("Missing decrypt alias accepted")
    } catch (e: F1Exception) { assertEquals(F1Failure.UNAVAILABLE, e.failure) }
    assertFalse(store().containsAlias(alias))
  }

  @Test fun actualGeneratedKeyIsHardwarePerUseOrRefusedAndUnauthenticatedFinishFails() {
    val identity = DomainIdentity(random(16), random(16)); val id = random(16)
    val alias = SecondaryBiometricEnvelope.alias(identity, id)
    val slot = SecondaryBiometricSlot()
    val pending = try { slot.prepareEnrollment(identity, id, 1) }
    catch (e: F1Exception) {
      // No enrolled strong biometric / software-only / unsupported platform: fail closed.
      assertEquals(F1Failure.UNAVAILABLE, e.failure)
      assertFalse(store().containsAlias(alias))
      return
    }
    try {
      val key = store().getKey(alias, null) as SecretKey
      val info = AndroidSecondaryBiometricKeys().verify(key)
      assertEquals(256, info.keySize)
      assertTrue(info.isUserAuthenticationRequired)
      assertTrue(info.isUserAuthenticationRequirementEnforcedBySecureHardware)
      assertTrue(info.isInvalidatedByBiometricEnrollment)
      assertTrue(info.userAuthenticationValidityDurationSeconds in -1..0)
      if (Build.VERSION.SDK_INT >= 31) {
        assertTrue(info.securityLevel == KeyProperties.SECURITY_LEVEL_TRUSTED_ENVIRONMENT || info.securityLevel == KeyProperties.SECURITY_LEVEL_STRONGBOX)
      } else assertTrue(info.isInsideSecureHardware)
      if (Build.VERSION.SDK_INT >= 30) assertEquals(KeyProperties.AUTH_BIOMETRIC_STRONG, info.userAuthenticationType)
      assertEquals(12, pending.cipher.iv.size)
      try {
        slot.finishEnrollment(pending, pending.cipher, random(32))
        fail("CryptoObject operation completed without authenticating")
      } catch (e: F1Exception) { assertEquals(F1Failure.UNAVAILABLE, e.failure) }
    } finally { pending.close() }
    assertFalse(store().containsAlias(alias))
  }
}
