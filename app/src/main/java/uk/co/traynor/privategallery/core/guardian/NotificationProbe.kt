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
    val summaries: Int = 0,
    val estimatedMessages: Int = 0,
    val unestimatedEvents: Int = 0,
    val whileLocked: Int = 0,
)

internal data class ProbeMessage(val time: Long, val sender: String, val text: CharSequence)

internal class NotificationProbe {
    private val salt = ByteArray(32).also(SecureRandom()::nextBytes)
    private val seen = LinkedHashSet<Pair<ProbeAccount, List<Byte>>>()
    private val messages = HashSet<List<Byte>>()
    var results: Map<ProbeAccount, ProbeResult> = emptyMap()
        private set

    fun record(
        account: ProbeAccount, key: String, text: Boolean, dnd: Boolean, quietChannel: Boolean, grouped: Boolean,
        summary: Boolean = false, conversation: String = key, entries: List<ProbeMessage> = emptyList(),
        locked: Boolean = false,
    ) {
        val update = !seen.add(account to fingerprint(key))
        if (seen.size > 256) seen.remove(seen.first())
        val eligible = if (summary || account == ProbeAccount.UNRESOLVED) emptyList() else entries
        val identified = eligible.filter { it.time > 0L && it.sender.isNotBlank() && it.text.isNotBlank() }
        var estimated = 0
        for (message in identified) {
            if (messages.size >= 2048) break
            if (messages.add(fingerprint(account.name, conversation, message.time.toString(), message.sender, message.text))) {
                estimated++
            }
        }

        val before = results[account] ?: ProbeResult()
        results = results + (account to ProbeResult(
            before.posts + 1,
            before.withText + text.toInt(),
            before.underDnd + dnd.toInt(),
            before.quietChannel + quietChannel.toInt(),
            before.grouped + grouped.toInt(),
            before.updates + update.toInt(),
            before.summaries + summary.toInt(),
            before.estimatedMessages + estimated,
            before.unestimatedEvents + (!summary && (identified.isEmpty() || identified.size != entries.size ||
                messages.size >= 2048)).toInt(),
            before.whileLocked + locked.toInt(),
        ))
    }

    private fun fingerprint(vararg fields: CharSequence): List<Byte> {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(salt)
        for (field in fields) {
            val bytes = field.toString().toByteArray(Charsets.UTF_8)
            try {
                digest.update(java.nio.ByteBuffer.allocate(4).putInt(bytes.size).array())
                digest.update(bytes)
            } finally {
                bytes.fill(0)
            }
        }
        return digest.digest().toList()
    }

    private fun Boolean.toInt() = if (this) 1 else 0
}
