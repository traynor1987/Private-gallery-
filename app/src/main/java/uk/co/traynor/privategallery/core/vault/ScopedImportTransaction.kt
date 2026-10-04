package uk.co.traynor.privategallery.core.vault

import java.io.FilterInputStream
import java.io.InputStream
import uk.co.traynor.privategallery.core.security.ScopedIoGuard

/**
 * Legacy Primary import ordering only; this produces no transfer verification authority.
 * Provider creation/disposal is outside storage. Final selection follows actual source close.
 * The commit callback must freshly authenticate prepared ciphertext after reacquiring storage.
 */
internal fun <P, R> runScopedImport(
    guard: ScopedIoGuard,
    storage: Any,
    source: () -> InputStream,
    prepare: (InputStream) -> P,
    commit: (P) -> R,
    ownedSource: (() -> uk.co.traynor.privategallery.core.security.OwnedInput)? = null,
): R {
    val prepared = (ownedSource?.invoke()?.adopt(guard) ?: guard.input(source)).use { input ->
        synchronized(storage) {
            guard.check()
            // Crypto wrappers may close their input during preparation; provider retirement
            // belongs to the outer .use, after the storage monitor has actually been released.
            prepare(object : FilterInputStream(input) { override fun close() = Unit })
        }
    }
    return synchronized(storage) { guard.check(); commit(prepared) }
}
