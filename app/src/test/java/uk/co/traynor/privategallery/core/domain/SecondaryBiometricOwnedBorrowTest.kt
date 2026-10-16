package uk.co.traynor.privategallery.core.domain

import java.security.AlgorithmParameters
import java.security.Key
import java.security.Provider
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
import uk.co.traynor.privategallery.core.security.OriginalTemporaryKey
import uk.co.traynor.privategallery.core.security.OwnedResourceManifest
import uk.co.traynor.privategallery.core.security.ReleaseReservation
import uk.co.traynor.privategallery.core.security.testSecondaryAuthority

class SecondaryBiometricOwnedBorrowTest {
    private class HeldSpi : CipherSpi() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        lateinit var input: ByteArray
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
        override fun engineUpdateAAD(input: ByteArray, offset: Int, length: Int) {}
        override fun engineDoFinal(input: ByteArray, offset: Int, length: Int): ByteArray {
            this.input = input; entered.countDown(); check(release.await(10, TimeUnit.SECONDS))
            return ByteArray(48) { 8 }
        }
        override fun engineDoFinal(input: ByteArray, offset: Int, length: Int, output: ByteArray, outputOffset: Int): Int = error("unused")
    }
    private class HeldCipher(spi: HeldSpi) : Cipher(spi,
        object : Provider("HeldHiddenBiometric", 1.0, "Controlled test provider") {}, "AES/GCM/NoPadding")

    @Test fun nativeEnrollmentBorrowsExactlyItsIndependentlyOwnedSnapshotUntilReturn() {
        val spi = HeldSpi()
        val cipher = HeldCipher(spi).apply { init(Cipher.ENCRYPT_MODE, SecretKeySpec(ByteArray(32), "AES")) }
        val authority = testSecondaryAuthority { 0 }
        check(authority.completeAuthentication(authority.beginAuthentication(), ByteArray(32) { 7 }))
        val operation = checkNotNull(authority.operationOrNull(setOf(SecondaryScope.CREDENTIALS)))
        val slot = SecondaryBiometricSlot(object : SecondaryBiometricKeyBackend {
            override fun create(alias: String) = cipher
            override fun decrypt(alias: String, nonce: ByteArray): Cipher = error("unused")
            override fun deleteOwned(alias: String) {}
        })
        val owned = operation.createOwned(OwnedResourceManifest.io("pending-provider")) {
            create("pending-provider", { it.close() }) {
                slot.prepareEnrollment(DomainIdentity(ByteArray(16) { 1 }, ByteArray(16) { 2 }), ByteArray(16) { 3 }, 1)
            }
        }
        val pending = owned.value.value
        val failure = AtomicReference<Throwable?>()
        val worker = Thread {
            try { slot.finishEnrollment(pending, cipher, operation).fill(0) }
            catch (problem: Throwable) { failure.set(problem) }
        }
        worker.start()
        var snapshotOriginal: ReleaseReservation? = null
        try {
            assertTrue(spi.entered.await(10, TimeUnit.SECONDS))
            // Observe the exact array's independent physical original, not just a count
            // of provider calls or a general provider whose internal copy is unaccounted.
            val gate = authority.javaClass.getDeclaredField("gate").apply { isAccessible = true }.get(authority)
            val originals = synchronized(gate) {
                @Suppress("UNCHECKED_CAST")
                (authority.javaClass.getDeclaredField("reservedResources").apply { isAccessible = true }.get(authority)
                    as Set<ReleaseReservation>).toList()
            }
            snapshotOriginal = originals.singleOrNull { original ->
                val identities = original.javaClass.getDeclaredField("actualIdentities").apply { isAccessible = true }.get(original) as Array<*>
                val key = identities.firstOrNull() as? OriginalTemporaryKey
                key != null && key.javaClass.getDeclaredField("key").apply { isAccessible = true }.get(key) === spi.input
            }
            assertNotNull("the actual Native key input needs its own prefunded original", snapshotOriginal)
            authority.revoke()
            assertArrayEquals(ByteArray(32) { 7 }, spi.input)
            assertFalse(checkNotNull(snapshotOriginal).retirement.isComplete)
            assertFalse(owned.retirement.isComplete)
            assertFalse(authority.cleanupComplete)
        } finally {
            spi.release.countDown(); worker.join(10_000)
            owned.close(); operation.close(); authority.revoke()
            assertTrue(owned.retirement.await(10, TimeUnit.SECONDS))
        }
        assertFalse(worker.isAlive)
        assertNotNull(failure.get())
        assertTrue(checkNotNull(snapshotOriginal).retirement.await(10, TimeUnit.SECONDS))
        assertArrayEquals(ByteArray(32), spi.input)
        assertNull(authority.bindingOrNull())
    }
}
