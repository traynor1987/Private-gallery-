package uk.co.traynor.privategallery.core.security

/** Exact app request identity, not proof of the AndroidX shared prompt's retirement. */
class BiometricUnlockAttemptState {
    class Attempt internal constructor(private var expectedCrypto: Any? = null) {
        internal fun matches(value: Any?): Boolean = value != null && expectedCrypto === value
        internal fun detach() { expectedCrypto = null }
    }
    private var active: Attempt? = null
    @Synchronized fun begin(): Attempt = replace(null)
    @Synchronized fun begin(expectedCrypto: Any): Attempt = replace(expectedCrypto)
    private fun replace(expectedCrypto: Any?): Attempt {
        active?.detach()
        return Attempt(expectedCrypto).also { active = it }
    }
    @Synchronized fun isCurrent(attempt: Attempt): Boolean = active === attempt
    /** AndroidX may route a saved old result to the current callback: bind its CryptoObject too. */
    @Synchronized fun claim(attempt: Attempt, returnedCrypto: Any?): Boolean {
        if (active !== attempt || !attempt.matches(returnedCrypto)) return false
        attempt.detach(); active = null
        return true
    }
    /** Terminal error/cancellation only; this overload must never authorize authentication success. */
    @Synchronized fun claim(attempt: Attempt): Boolean {
        if (active !== attempt) return false
        attempt.detach(); active = null
        return true
    }
    @Synchronized fun cancel(attempt: Attempt) {
        if (active === attempt) { attempt.detach(); active = null }
    }
    @Synchronized fun lock() { active?.detach(); active = null }
}
