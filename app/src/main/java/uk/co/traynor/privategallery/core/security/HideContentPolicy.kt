package uk.co.traynor.privategallery.core.security

/** Presentation policy only. No Vault key, index, payload or Browser store is changed. */
object HideContentPolicy {
    fun decode(exists: Boolean, value: String?): Boolean = exists && value != "false"
    fun <T> present(hidden: Boolean, values: List<T>): List<T> = if (hidden) emptyList() else values
    fun count(hidden: Boolean, value: Int): Int = if (hidden) 0 else value
    fun canReveal(freshAuthentication: Boolean): Boolean = freshAuthentication
    fun canEnableScreenshots(freshAuthentication: Boolean): Boolean = freshAuthentication
}

data class SecretDiscoveryState(val taps: Int = 0, val discovered: Boolean = false) {
    val remaining: Int get() = (10 - taps).coerceAtLeast(0)
    fun tap(): SecretDiscoveryState = if (discovered) this else copy(taps = (taps + 1).coerceAtMost(10), discovered = taps + 1 >= 10)
    fun conceal(): SecretDiscoveryState = SecretDiscoveryState()
}
