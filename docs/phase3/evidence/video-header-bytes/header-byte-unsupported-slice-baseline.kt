package uk.co.traynor.privategallery.core.browser.v2

/** Temporary feature TDD baseline; producer adoption waits for the byte-slice implementation. */
internal object VideoHeaderBytePolicy {
    fun reason(mime: String, bytes: ByteArray, count: Int): MediaSaveReason? {
        require(count in 0..bytes.size && bytes.size <= 64 * 1024)
        return VideoValidationPolicy.headerReason(mime, bytes)
    }
}
