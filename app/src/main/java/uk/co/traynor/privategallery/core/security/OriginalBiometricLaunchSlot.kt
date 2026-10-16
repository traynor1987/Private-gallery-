package uk.co.traynor.privategallery.core.security

/** One process-owned launch rendezvous. A locator never grants authority: it only
 * hands the exact already-owned pending record to one actual Activity instance. */
internal class OriginalBiometricLaunchSlot<T : Any> {
    private var original: T? = null
    private var locator: String? = null
    private var claimed = false
    @Synchronized fun reserve(value: T, token: String): Boolean {
        if (original != null) return false
        original = value; locator = token; claimed = false
        return true
    }
    @Synchronized fun claim(token: String?): T? {
        if (token == null || token != locator || claimed) return null
        return original?.also { claimed = true }
    }
    @Synchronized fun retire(value: T): Boolean {
        if (original !== value) return false
        original = null; locator = null; claimed = false
        return true
    }
}
