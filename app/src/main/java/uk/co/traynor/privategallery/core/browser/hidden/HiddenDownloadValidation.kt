package uk.co.traynor.privategallery.core.browser.hidden

import uk.co.traynor.privategallery.core.browser.v2.validatedDirectVideoSource
import uk.co.traynor.privategallery.core.vault.VaultImportSource
import java.io.File
import java.io.InputStream

/** Reuses final-file validation while leaving acquisition and destination entirely Hidden-owned. */
internal fun <T> withValidatedHiddenDownload(cache: File, mime: String, input: InputStream,
    cancelled: () -> Boolean, save: (InputStream) -> T): T {
    if (!mime.startsWith("video/")) return save(input)
    val source = validatedDirectVideoSource(File(cache, "hidden-download-validation"),
        VaultImportSource("Hidden download", mime, { input }, isCancelled = cancelled), {})
    try { return source.openStream().use(save) }
    finally { source.onConsumed() }
}
