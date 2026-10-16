package uk.co.traynor.privategallery.core.security

import java.security.AlgorithmParameters
import java.security.Key
import java.security.SecureRandom
import java.security.spec.AlgorithmParameterSpec
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import javax.crypto.Cipher
import javax.crypto.CipherSpi
import javax.crypto.spec.SecretKeySpec
import org.junit.Assert.*
import org.junit.Test

class PreparedBiometricEnvelopeBorrowTest {
    private class HeldSpi : CipherSpi() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        lateinit var input: ByteArray
        var output: ByteArray? = null
        override fun engineSetMode(mode: String) {}
        override fun engineSetPadding(padding: String) {}
        override fun engineGetBlockSize() = 16
        override fun engineGetOutputSize(inputLen: Int) = 48
        override fun engineGetIV() = ByteArray(12) { 2 }
        override fun engineGetParameters(): AlgorithmParameters? = null
        override fun engineInit(opmode: Int, key: Key, random: SecureRandom?) {}
        override fun engineInit(opmode: Int, key: Key, params: AlgorithmParameterSpec?, random: SecureRandom?) {}
        override fun engineInit(opmode: Int, key: Key, params: AlgorithmParameters?, random: SecureRandom?) {}
        override fun engineUpdate(input: ByteArray, offset: Int, length: Int): ByteArray = error("unused")
        override fun engineUpdate(input: ByteArray, offset: Int, length: Int, output: ByteArray, outputOffset: Int): Int = error("unused")
        private fun hold(value: ByteArray) {
            input = value; entered.countDown(); check(release.await(10, TimeUnit.SECONDS))
        }
        override fun engineDoFinal(input: ByteArray, offset: Int, length: Int): ByteArray {
            hold(input); return ByteArray(48) { 8 }.also { output = it }
        }
        override fun engineDoFinal(input: ByteArray, offset: Int, length: Int, output: ByteArray, outputOffset: Int): Int {
            hold(input); output.fill(8, outputOffset, outputOffset + 48); this.output = output; return 48
        }
    }
    private class HeldCipher(spi: HeldSpi) : Cipher(spi, object : java.security.Provider("HeldBiometric", 1.0, "Controlled test provider") {}, "AES/GCM/NoPadding")

    @Test fun revokeCannotWipeEnrolledKeyWhileActualCipherStillBorrowsIt() {
        // Validate the synthetic provider before acquiring any authority-owned resource.
        val spi = HeldSpi()
        val cipher = HeldCipher(spi).apply { init(Cipher.ENCRYPT_MODE, SecretKeySpec(ByteArray(32), "AES")) }
        val authority = testPrimaryAuthority()
        authority.open(ByteArray(32) { 7 })
        val operation = checkNotNull(authority.operationOrNull(setOf(PrimaryScope.CREDENTIALS)))
        val envelope = ScopedIoGuard(operation, PrimaryScope.CREDENTIALS).createOwned(OwnedResourceManifest.io("envelope")) {
            PreparedBiometricEnvelope().also { attach("envelope", it) }
        }
        val failure = AtomicReference<Throwable?>()
        val worker = Thread {
            try { producePrimaryBiometricEnvelope(operation, envelope.value, cipher) }
            catch (problem: Throwable) { failure.set(problem) }
        }
        worker.start()
        try {
            assertTrue(spi.entered.await(10, TimeUnit.SECONDS))
            authority.revoke()
            assertArrayEquals("admitted Native borrow must retain its actual 32 bytes", ByteArray(32) { 7 }, spi.input)
            assertFalse(envelope.retirement.isComplete)
            assertFalse(authority.cleanupComplete)
            assertNull(authority.bindingOrNull())
        } finally { spi.release.countDown(); worker.join(10_000); envelope.close(); operation.close() }
        assertFalse(worker.isAlive)
        assertNotNull(failure.get())
        assertTrue(envelope.retirement.await(10, TimeUnit.SECONDS))
        assertArrayEquals(ByteArray(32), spi.input)
        assertArrayEquals(ByteArray(48), checkNotNull(spi.output))
        assertNull(authority.bindingOrNull())
    }
}
