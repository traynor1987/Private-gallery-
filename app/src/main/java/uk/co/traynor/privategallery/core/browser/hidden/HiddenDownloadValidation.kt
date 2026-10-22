package uk.co.traynor.privategallery.core.browser.hidden

import android.media.MediaDataSource
import uk.co.traynor.privategallery.core.browser.v2.VideoFileValidator

/** HiddenRepository invokes this on authenticated encrypted staging before deduplication/publication. */
internal fun hiddenDownloadedMediaValidator(mime: String, cancelled: () -> Boolean,
    expectedDurationMs: Long? = null): ((MediaDataSource, Long) -> Unit)? =
    if (!mime.startsWith("video/")) null else { source, size ->
        VideoFileValidator.inspect(source, size, mime, false, expectedDurationMs, cancelled)
        Unit
    }
