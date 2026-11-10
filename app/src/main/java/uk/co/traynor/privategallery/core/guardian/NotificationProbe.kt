package uk.co.traynor.privategallery.core.guardian

import java.security.MessageDigest
import java.security.SecureRandom

internal enum class ProbeAccount(val label: String) {
    MAIN("Main profile candidate"),
    FRIENDS("Enrolled Friends profile candidate"),
    UNRESOLVED("Unresolved profile"),
}

internal fun probeAccount(personal: Boolean, serial: Long, friendsSerial: Long?): ProbeAccount = when {
    serial < 0 || personal && serial == friendsSerial -> ProbeAccount.UNRESOLVED
    personal -> ProbeAccount.MAIN
    serial == friendsSerial -> ProbeAccount.FRIENDS
    else -> ProbeAccount.UNRESOLVED
}

internal data class ProbeResult(
    val posts: Int = 0,
    val withText: Int = 0,
    val underDnd: Int = 0,
    val quietChannel: Int = 0,
    val grouped: Int = 0,
    val updates: Int = 0,
)

internal class NotificationProbe {
    private val salt = ByteArray(32).also(SecureRandom()::nextBytes)
    private val seen = LinkedHashSet<Pair<ProbeAccount, List<Byte>>>()
    var results: Map<ProbeAccount, ProbeResult> = emptyMap()
        private set

    fun record(account: ProbeAccount, key: String, text: Boolean, dnd: Boolean, quietChannel: Boolean, grouped: Boolean) {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(salt)
        val update = !seen.add(account to digest.digest(key.toByteArray(Charsets.UTF_8)).toList())
        if (seen.size > 256) seen.remove(seen.first())

        val before = results[account] ?: ProbeResult()
        results = results + (account to ProbeResult(
            before.posts + 1,
            before.withText + text.toInt(),
            before.underDnd + dnd.toInt(),
            before.quietChannel + quietChannel.toInt(),
            before.grouped + grouped.toInt(),
            before.updates + update.toInt(),
        ))
    }

    private fun Boolean.toInt() = if (this) 1 else 0
}
