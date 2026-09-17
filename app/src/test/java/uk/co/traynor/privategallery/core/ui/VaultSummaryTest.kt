package uk.co.traynor.privategallery.core.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import uk.co.traynor.privategallery.core.vault.VaultItem
import uk.co.traynor.privategallery.core.vault.VaultItemState

class VaultSummaryTest {
    @Test
    fun `summarises protected photos videos and storage`() {
        val items = listOf(
            item("image/jpeg", 100),
            item("video/mp4", 200),
            item("image/png", 300),
        )

        assertEquals(VaultSummary(photos = 2, videos = 1, bytes = 600), VaultSummary.from(items))
    }

    private fun item(mime: String, bytes: Long) = VaultItem(
        id = "id-$mime-$bytes",
        mimeType = mime,
        displayName = "hidden",
        importedAtEpochMillis = 0,
        plaintextSize = bytes,
        plaintextSha256 = ByteArray(32),
        payloadNonce = ByteArray(12),
        state = VaultItemState.COMPLETE,
    )
}
