package uk.co.traynor.privategallery.core.browser

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException

class CompleteResponseInputStreamTest {
    @Test fun largeResponseIsStreamedWithoutTruncation() {
        val bytes = ByteArray(16 * 1024 * 1024 + 7) { (it % 251).toByte() }
        CompleteResponseInputStream(ByteArrayInputStream(bytes), bytes.size.toLong()).use {
            assertArrayEquals(bytes, it.readBytes())
        }
    }
    @Test fun earlyEofIsFailureAndFreshRetryHasIndependentAccounting() {
        try {
            CompleteResponseInputStream(ByteArrayInputStream(ByteArray(10)), 100).readBytes()
            fail("Truncated response must fail")
        } catch (_: IOException) { }
        assertEquals(100, CompleteResponseInputStream(ByteArrayInputStream(ByteArray(100)), 100).readBytes().size)
    }
    @Test fun zeroLengthReadIsNotEof() {
        val stream = CompleteResponseInputStream(ByteArrayInputStream(byteArrayOf(1)), 1)
        assertEquals(0, stream.read(ByteArray(1), 0, 0))
        assertEquals(1, stream.read())
        assertEquals(-1, stream.read())
    }
}
