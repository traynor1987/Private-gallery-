package uk.co.traynor.privategallery.ui

import org.junit.Assert.assertArrayEquals
import org.junit.Test

class VaultVideoBufferPolicyTest {
    @Test
    fun `disposing an old player buffer cannot clear the newly loaded buffer`() {
        val previous = byteArrayOf(1, 2, 3)
        val current = byteArrayOf(4, 5, 6)

        VaultVideoBufferPolicy.clear(previous)

        assertArrayEquals(byteArrayOf(0, 0, 0), previous)
        assertArrayEquals(byteArrayOf(4, 5, 6), current)
    }
}
