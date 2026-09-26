package uk.co.traynor.privategallery.core.security

/** Presentation policy only. No Vault key, index, payload or Browser store is changed. */
object HideContentPolicy {
    fun decode(exists: Boolean, value: String?): Boolean = exists && value != "false"
    fun <T> present(hidden: Boolean, values: List<T>): List<T> = if (hidden) emptyList() else values
    fun count(hidden: Boolean, value: Int): Int = if (hidden) 0 else value
}

/** Installed five times, the About version once, then Installed four times. Only completion persists. */
data class SecretDiscoveryState(private val step: Int = 0, val discovered: Boolean = false) {
    fun tapInstalled(): SecretDiscoveryState = when {
        discovered -> this
        step in 0..4 || step in 6..8 -> copy(step = step + 1)
        step == 9 -> copy(step = 10, discovered = true)
        else -> reset()
    }
    fun tapVersion(): SecretDiscoveryState = if (!discovered && step == 5) copy(step = 6) else reset()
    fun reset(): SecretDiscoveryState = SecretDiscoveryState()
    fun conceal(): SecretDiscoveryState = reset()
}
