package uk.co.traynor.privategallery.core.browser.v2

import uk.co.traynor.privategallery.core.security.PrimaryOperation

internal class BrowserSessionAdmission<T>(val session: T, val owner: PrimaryOperation?)

/** Caller's exact attempted owner; neutral construction has no Primary or network authority. */
internal fun <T> browserSessionAdmission(owner: PrimaryOperation?, factory: (PrimaryOperation?) -> T): BrowserSessionAdmission<T> {
    if (owner == null) return BrowserSessionAdmission(factory(null), null)
    return try {
        BrowserSessionAdmission(factory(owner), owner)
    } catch (failure: Throwable) {
        owner.close()
        if (failure is Error) throw failure
        BrowserSessionAdmission(factory(null), null)
    }
}
