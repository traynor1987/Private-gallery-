package uk.co.traynor.privategallery.core.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecoveryEnvelopeTest {
    @Test fun `formatted recovery key is accepted without storing formatting`() {
        val vdek = ByteArray(32) { it.toByte() }
        val recoveryKey = RecoveryKey.generate()
        val envelope = RecoveryEnvelope.create(recoveryKey.copyOf(), vdek)

        assertArrayEquals(vdek, RecoveryEnvelope.unwrap(RecoveryKey.display(recoveryKey).toCharArray(), envelope))
        recoveryKey.fill('\u0000')
        vdek.fill(0)
    }

    @Test fun `recovery key unwraps the same VDEK and a replacement PIN can unlock it`() {
        val vdek = ByteArray(32) { (it + 7).toByte() }
        val recoveryKey = RecoveryKey.generate()
        val recoveryEnvelope = RecoveryEnvelope.create(recoveryKey.copyOf(), vdek)

        val recovered = RecoveryEnvelope.unwrap(recoveryKey.copyOf(), recoveryEnvelope)
        val replacementPin = "654321".toCharArray()
        val replacementPinEnvelope = PinEnvelope.create(replacementPin, recovered)

        assertArrayEquals(vdek, PinEnvelope.unwrap("654321".toCharArray(), replacementPinEnvelope))
        recovered.fill(0)
        vdek.fill(0)
        recoveryKey.fill('\u0000')
    }

    @Test fun `wrong recovery key cannot unwrap the VDEK`() {
        val recoveryKey = RecoveryKey.generate()
        val envelope = RecoveryEnvelope.create(recoveryKey.copyOf(), ByteArray(32) { 9 })

        assertTrue(runCatching { RecoveryEnvelope.unwrap("wrong-recovery-key".toCharArray(), envelope) }.exceptionOrNull() is InvalidRecoveryKeyException)
        recoveryKey.fill('\u0000')
    }
}
