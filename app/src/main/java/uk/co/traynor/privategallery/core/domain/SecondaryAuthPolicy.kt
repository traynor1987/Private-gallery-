package uk.co.traynor.privategallery.core.domain

enum class StrongAuthInterval(val durationMillis: Long) {
    EVERY_TIME(0), DAY(86_400_000), THREE_DAYS(259_200_000), SEVEN_DAYS(604_800_000),
}

enum class SecondaryAutoLock(val timeoutMillis: Long) {
    IMMEDIATE(0), THIRTY_SECONDS(30_000), ONE_MINUTE(60_000), FIVE_MINUTES(300_000),
}

/** In-process monotonic PIN recency and bounded backoff, never serialized as authority. */
class SecondaryAuthPolicy(private val clock: () -> Long = { System.nanoTime() / 1_000_000 }) {
    private var lastPinAt: Long? = null
    private var failureCount = 0
    private var blockedUntil: Long? = null
    var strongAuthInterval: StrongAuthInterval = StrongAuthInterval.DAY
        private set
    var autoLock: SecondaryAutoLock = SecondaryAutoLock.IMMEDIATE
        private set

    @Synchronized fun setStrongAuthInterval(interval: StrongAuthInterval) {
        strongAuthInterval = interval
    }

    @Synchronized fun setAutoLock(policy: SecondaryAutoLock) { autoLock = policy }

    @Synchronized fun canAttempt(): Boolean = retryAfterMillis() == 0L

    @Synchronized fun retryAfterMillis(): Long {
        val until = blockedUntil ?: return 0
        val now = clock()
        return if (now >= until) 0 else (until - now).coerceIn(0, MAX_BACKOFF_MILLIS)
    }

    @Synchronized fun canUseBiometric(): Boolean {
        if (!canAttempt()) return false
        val last = lastPinAt ?: return false
        val now = clock()
        return now >= last && now - last < strongAuthInterval.durationMillis
    }

    /** Call only after authenticated independent PIN unwrap/catalog verification succeeds. */
    @Synchronized fun recordPinSuccess() {
        check(canAttempt()) { "Secondary authentication delayed" }
        lastPinAt = clock()
        clearFailures()
    }

    /** A verified biometric unwrap can clear failures but never extends PIN recency. */
    @Synchronized fun recordBiometricSuccess() {
        check(canUseBiometric()) { "Secondary PIN required" }
        clearFailures()
    }

    @Synchronized fun recordFailure() {
        failureCount = (failureCount + 1).coerceAtMost(MAX_FAILURE_COUNT)
        if (failureCount < BACKOFF_START_FAILURE) return
        val delay = (1_000L shl (failureCount - BACKOFF_START_FAILURE)).coerceAtMost(MAX_BACKOFF_MILLIS)
        val now = clock()
        val next = if (now > Long.MAX_VALUE - delay) Long.MAX_VALUE else now + delay
        blockedUntil = blockedUntil?.let { maxOf(it, next) } ?: next
    }

    /** Recovery or credential/security changes require PIN before convenience authentication. */
    @Synchronized fun onSecurityChanged() { lastPinAt = null }

    private fun clearFailures() { failureCount = 0; blockedUntil = null }

    companion object {
        const val MAX_BACKOFF_MILLIS = 60_000L
        private const val BACKOFF_START_FAILURE = 5
        private const val MAX_FAILURE_COUNT = 11
    }
}
