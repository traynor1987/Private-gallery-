package uk.co.traynor.privategallery.core.security

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

/** Public ciphertext metadata only. These flags preserve the existing stored
 * lifecycle semantics; they neither verify possession now nor authorize writes. */
internal data class PrimaryConfirmedRecoveryRecord(
    val salt: String,
    val nonce: String,
    val ciphertext: String,
    val legacyExisting: Boolean,
    val possessionVerified: Boolean,
)

/** Structural validation without any decoding or provider construction. Java's
 * basic decoder is strict and permits absent padding and noncanonical unused
 * pad bits; retain the exact existing strings rather than reencoding them. */
internal fun classifyPrimaryRecoveryRecord(values: Map<String, *>): PrimaryConfirmedRecoveryRecord {
    val legacy = values.size == 3 && values.keys == setOf("salt", "nonce", "ciphertext")
    val confirmed = values.size == 5 && values.keys == setOf("salt", "nonce", "ciphertext", "state", "origin") &&
        values["state"] == "confirmed" && values["origin"] in setOf("new", "legacy", "restored")
    check(legacy || confirmed) { "Confirmed recovery is unavailable" }
    return PrimaryConfirmedRecoveryRecord(
        boundedRecoveryField(values["salt"], 16), boundedRecoveryField(values["nonce"], 12),
        boundedRecoveryField(values["ciphertext"], 48), legacy || values["origin"] == "legacy", !legacy,
    )
}

private fun boundedRecoveryField(value: Any?, size: Int): String {
    val text = checkNotNull(value as? String) { "Invalid recovery envelope field" }
    val dataLength = (size * 8 + 5) / 6
    val paddedLength = ((size + 2) / 3) * 4
    check(text.length == dataLength || text.length == paddedLength) { "Invalid recovery envelope size" }
    for (index in text.indices) {
        val char = text[index]
        if (index >= dataLength) check(char == '=') { "Invalid recovery envelope padding" }
        else check(char in 'A'..'Z' || char in 'a'..'z' || char in '0'..'9' || char == '+' || char == '/') {
            "Invalid recovery envelope alphabet"
        }
    }
    return text
}

/** Exact funded descriptor root. Late read/parse return cannot resurrect it after
 * revocation. Only immutable encrypted fields escape, never decoded bytes. */
internal class PreparedPrimaryRecoveryRecord : AcknowledgedCloseable {
    private val gate = Any()
    private val done = CompletableFuture<Unit>()
    private var record: PrimaryConfirmedRecoveryRecord? = null
    private var using = true
    private var retired = false
    fun bind(value: PrimaryConfirmedRecoveryRecord) = synchronized(gate) { check(using && record == null); record = value }
    fun descriptor(): PrimaryConfirmedRecoveryRecord = synchronized(gate) {
        check(!using && !retired) { "Original recovery record unavailable" }; checkNotNull(record)
    }
    fun returned() { synchronized(gate) { using = false }; finish() }
    override fun closeAcknowledged(): CompletionStage<Unit> {
        synchronized(gate) { retired = true }; finish(); return done
    }
    private fun finish() {
        val ready = synchronized(gate) { if (retired && !using) { record = null; true } else false }
        if (ready) done.complete(Unit)
    }
}
