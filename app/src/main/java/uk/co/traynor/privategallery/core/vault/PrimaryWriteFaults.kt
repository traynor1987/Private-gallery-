package uk.co.traynor.privategallery.core.vault

import java.io.File

/** Instance-only ciphertext checkpoints. Production defaults never inject failures or mutate files. */
enum class WriteCheckpoint {
    INDEX_BEFORE_WRITE, INDEX_AFTER_WRITE, INDEX_BEFORE_SYNC, INDEX_AFTER_SYNC,
    INDEX_BEFORE_VERIFY, INDEX_AFTER_VERIFY, INDEX_BEFORE_PROMOTION, INDEX_AFTER_PROMOTION,
    PAYLOAD_BEFORE_WRITE, PAYLOAD_AFTER_WRITE, PAYLOAD_BEFORE_SYNC, PAYLOAD_AFTER_SYNC,
    PAYLOAD_BEFORE_VERIFY, PAYLOAD_AFTER_VERIFY, PAYLOAD_BEFORE_PROMOTION, PAYLOAD_AFTER_PROMOTION,
}

fun interface PrimaryWriteFaults {
    fun checkpoint(point: WriteCheckpoint, ciphertext: File)
    companion object { val NONE = PrimaryWriteFaults { _, _ -> } }
}
