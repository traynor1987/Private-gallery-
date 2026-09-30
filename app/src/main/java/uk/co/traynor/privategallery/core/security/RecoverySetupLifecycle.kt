package uk.co.traynor.privategallery.core.security

import java.security.MessageDigest
import java.util.Base64
import java.util.WeakHashMap
import uk.co.traynor.privategallery.core.crypto.InvalidRecoveryKeyException
import uk.co.traynor.privategallery.core.crypto.RecoveryEnvelope
import uk.co.traynor.privategallery.core.crypto.RecoveryKey
import uk.co.traynor.privategallery.core.crypto.RecoveryWrappedKey

enum class RecoverySetupState { NOT_CONFIGURED, PENDING_CONFIRMATION, CONFIRMED, CORRUPT }

/** Compare-and-commit replaces one complete record atomically; false never establishes confirmation. */
internal interface RecoveryEnvelopePersistence {
    fun read(): Map<String, String>
    fun commit(expected: Map<String, String>, values: Map<String, String>): Boolean
}

internal class FailClosedRecoveryPersistence(
    private val identity: Any,
    private val delegate: RecoveryEnvelopePersistence,
) : RecoveryEnvelopePersistence {
    // Android retains SharedPreferences across Activity/store reconstruction. A failed disk
    // commit must not become confirmed through its already-published in-memory values.
    override fun read(): Map<String, String> = synchronized(identity) {
        if (hasFailed()) mapOf("write_failure" to "unavailable") else delegate.read()
    }

    override fun commit(expected: Map<String, String>, values: Map<String, String>): Boolean = synchronized(identity) {
        if (hasFailed() || delegate.read() != expected) return@synchronized false
        try {
            delegate.commit(expected, values).also { if (!it) markFailed() }
        } catch (failure: Exception) {
            markFailed()
            throw failure
        }
    }

    private fun hasFailed() = synchronized(failures) { failures.containsKey(identity) }
    private fun markFailed() = synchronized(failures) { failures[identity] = true }
    private companion object {
        val failures = WeakHashMap<Any, Boolean>()
    }
}

/** Ciphertext-only lifecycle. Callers bind final commits to their authenticated Primary operation. */
internal class RecoverySetupLifecycle(private val persistence: RecoveryEnvelopePersistence) {
    @Volatile private var writeFailed = false
    private var installedRestore: Map<String, String>? = null
    val setupState: RecoverySetupState get() = snapshot().state
    val isLegacyExisting: Boolean get() = snapshot().legacy
    val isPossessionVerified: Boolean get() = snapshot().let { it.state == RecoverySetupState.CONFIRMED && !it.unverifiedLegacy }

    fun create(vdek: ByteArray, commit: (() -> Unit) -> Unit = { it() }): CharArray =
        createPending(vdek, RecoverySetupState.NOT_CONFIGURED, commit)

    /** An authenticated owner may restart a lost one-time display, only while still pending. */
    fun restartPending(vdek: ByteArray, commit: (() -> Unit) -> Unit = { it() }): CharArray =
        createPending(vdek, RecoverySetupState.PENDING_CONFIRMATION, commit)

    private fun createPending(vdek: ByteArray, required: RecoverySetupState, commit: (() -> Unit) -> Unit): CharArray {
        val before = snapshot()
        check(before.state == required) { "Recovery setup state does not permit creation" }
        val secret = RecoveryKey.generate()
        try {
            val envelope = RecoveryEnvelope.create(secret.copyOf(), vdek)
            commit { replace(before.values, encode(envelope, pending = true)) }
            return secret
        } catch (failure: Throwable) {
            secret.fill('\u0000')
            throw failure
        }
    }

    /** Authenticate owner re-entry and compare with the active existing VDEK before promotion. */
    fun confirm(secret: CharArray, expectedVdek: ByteArray, commit: (() -> Unit) -> Unit = { it() }) {
        try {
            require(expectedVdek.size == 32)
            val before = snapshot()
            check(before.state == RecoverySetupState.PENDING_CONFIRMATION || before.state == RecoverySetupState.CONFIRMED) {
                "Recovery setup is unavailable"
            }
            val unwrapped = RecoveryEnvelope.unwrap(secret, checkNotNull(before.envelope))
            try {
                if (!MessageDigest.isEqual(unwrapped, expectedVdek)) throw InvalidRecoveryKeyException()
            } finally {
                unwrapped.fill(0)
            }
            if (before.state == RecoverySetupState.CONFIRMED && !before.unverifiedLegacy) return
            val origin = if (before.legacy) "legacy" else "new"
            commit { replace(before.values, encode(before.envelope, pending = false, origin = origin)) }
        } finally {
            secret.fill('\u0000')
        }
    }

    fun unlock(secret: CharArray): ByteArray = try {
        val before = snapshot()
        check(before.state == RecoverySetupState.CONFIRMED) { "Confirmed recovery is unavailable" }
        RecoveryEnvelope.unwrap(secret, checkNotNull(before.envelope))
    } finally {
        secret.fill('\u0000')
    }

    fun exportEnvelope(): RecoveryWrappedKey {
        val before = snapshot()
        check(before.state == RecoverySetupState.CONFIRMED) { "Confirmed recovery is unavailable" }
        return checkNotNull(before.envelope)
    }

    /** Only called after the archive reader has authenticated possession. Bytes remain unchanged. */
    fun installForRestoredVault(envelope: RecoveryWrappedKey) {
        val before = snapshot()
        check(before.state == RecoverySetupState.NOT_CONFIGURED) { "Recovery setup already exists or is unavailable" }
        require(valid(envelope))
        val record = encode(envelope, pending = false, origin = "restored")
        replace(before.values, record)
        installedRestore = record
    }

    /** Never erase a legacy or separately confirmed envelope on an unrelated failed restore. */
    fun clearFailedRestore() {
        val installed = checkNotNull(installedRestore) { "No recovery envelope installed by this restore" }
        replace(installed, emptyMap())
        installedRestore = null
    }

    private fun replace(expected: Map<String, String>, replacement: Map<String, String>) = synchronized(persistence) {
        check(persistence.read() == expected) { "Recovery setup changed before commit" }
        try {
            if (!persistence.commit(expected, replacement)) {
                writeFailed = true
                error("Unable to persist recovery envelope")
            }
        } catch (failure: Exception) {
            writeFailed = true
            throw failure
        }
    }

    private data class Snapshot(
        val values: Map<String, String>, val state: RecoverySetupState,
        val envelope: RecoveryWrappedKey? = null, val legacy: Boolean = false,
        val unverifiedLegacy: Boolean = false,
    )

    private fun snapshot(): Snapshot {
        if (writeFailed) return Snapshot(emptyMap(), RecoverySetupState.CORRUPT)
        val values = try { persistence.read() } catch (_: Exception) { return Snapshot(emptyMap(), RecoverySetupState.CORRUPT) }
        if (values.isEmpty()) return Snapshot(values, RecoverySetupState.NOT_CONFIGURED)
        try {
            val legacyFields = setOf("salt", "nonce", "ciphertext")
            val pendingFields = setOf("pending_salt", "pending_nonce", "pending_ciphertext", "state")
            val confirmedFields = legacyFields + setOf("state", "origin")
            val state = when {
                values.keys == legacyFields -> RecoverySetupState.CONFIRMED
                values.keys == pendingFields && values["state"] == "pending" -> RecoverySetupState.PENDING_CONFIRMATION
                values.keys == confirmedFields && values["state"] == "confirmed" && values["origin"] in setOf("new", "legacy", "restored") -> RecoverySetupState.CONFIRMED
                else -> return Snapshot(values, RecoverySetupState.CORRUPT)
            }
            val prefix = if (state == RecoverySetupState.PENDING_CONFIRMATION) "pending_" else ""
            val envelope = RecoveryWrappedKey(
                Base64.getDecoder().decode(values.getValue(prefix + "salt")),
                Base64.getDecoder().decode(values.getValue(prefix + "nonce")),
                Base64.getDecoder().decode(values.getValue(prefix + "ciphertext")),
            )
            if (!valid(envelope)) return Snapshot(values, RecoverySetupState.CORRUPT)
            val unverifiedLegacy = values.keys == legacyFields
            return Snapshot(values, state, envelope, unverifiedLegacy || values["origin"] == "legacy", unverifiedLegacy)
        } catch (_: Exception) {
            return Snapshot(values, RecoverySetupState.CORRUPT)
        }
    }

    private fun valid(envelope: RecoveryWrappedKey) = envelope.salt.size == 16 && envelope.nonce.size == 12 && envelope.ciphertext.size == 48
    private fun encode(envelope: RecoveryWrappedKey, pending: Boolean, origin: String = "new"): Map<String, String> {
        val prefix = if (pending) "pending_" else ""
        return buildMap {
            put(prefix + "salt", Base64.getEncoder().encodeToString(envelope.salt))
            put(prefix + "nonce", Base64.getEncoder().encodeToString(envelope.nonce))
            put(prefix + "ciphertext", Base64.getEncoder().encodeToString(envelope.ciphertext))
            put("state", if (pending) "pending" else "confirmed")
            if (!pending) put("origin", origin)
        }
    }
}
