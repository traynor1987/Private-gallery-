package uk.co.traynor.privategallery.core.crypto

import java.security.SecureRandom
import org.junit.Assert.assertArrayEquals
import org.junit.Test

class PinEnvelopeTest {
  @Test fun `changing pin retains vault key`() {
    val vdek = ByteArray(32).also(SecureRandom()::nextBytes)
    val changed = PinEnvelope.changePin("1234".toCharArray(), "5678".toCharArray(), PinEnvelope.create("1234".toCharArray(), vdek))
    assertArrayEquals(vdek, PinEnvelope.unwrap("5678".toCharArray(), changed))
  }
}
