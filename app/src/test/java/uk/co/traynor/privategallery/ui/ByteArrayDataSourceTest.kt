package uk.co.traynor.privategallery.ui

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class ByteArrayDataSourceTest {
    @Test
    fun `serves an in-memory range for playback seeking`() {
        val source = InMemoryMediaBytes(byteArrayOf(10, 11, 12, 13, 14))

        assertEquals(2, source.availableAt(2, 2))
        val output = ByteArray(2)
        assertEquals(2, source.copyAt(2, output, 0, output.size))
        assertArrayEquals(byteArrayOf(12, 13), output)
        assertEquals(0, source.copyAt(5, output, 0, output.size))
    }
}
