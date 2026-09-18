package uk.co.traynor.privategallery.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class VaultVideoDiagnosticsTest {
    @Test
    fun `playback error is mapped to a non-sensitive user message`() {
        assertEquals(
            "Protected video could not be played on this device.",
            VaultVideoDiagnostics.userMessageForPlayerError(),
        )
    }
}
