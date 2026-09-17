package uk.co.traynor.privategallery.core.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class VaultGridPolicyTest {
    @Test
    fun `outer-display width uses a compact three-column grid`() {
        assertEquals(3, VaultGridPolicy.columnsFor(412))
    }

    @Test
    fun `fold inner display gains columns without stretching tiles`() {
        assertEquals(6, VaultGridPolicy.columnsFor(840))
    }

    @Test
    fun `very narrow display remains usable`() {
        assertEquals(2, VaultGridPolicy.columnsFor(280))
    }
}
