package uk.co.traynor.privategallery.core.sanctuary

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.system.Os
import android.system.OsConstants
import java.io.File
import java.io.FileOutputStream
import java.security.KeyStore
import java.util.Collections
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import uk.co.traynor.privategallery.core.evidence.EvidenceAccess

/** An unlocked Evidence Locker operation. The derived profile has no evidence-reading capability. */
internal class SanctuaryPatternLearning68(context: Context, private val access: EvidenceAccess) : AutoCloseable {
    enum class Pattern(val label: String, internal val bit: Int) {
        IMMEDIATE_REPLY_REQUEST("Requests for an immediate reply", 1),
        REPEATED_FOLLOW_UP("Repeated follow-ups", 2),
        INSULT("Insults", 4),
        THREAT("Threatening language", 8),
    }

    /** A short-lived, one-shot approval challenge with no original IDs or content. */
    class Consent internal constructor(val patterns: Set<Pattern>)

    private val profile = ProfileStore(context.applicationContext, access.owner.name.lowercase(Locale.ROOT))
    private val closed = AtomicBoolean()
    private val pending = AtomicReference<Consent?>()
    private val retirement = access.onRetire(::retire)

    /**
     * The owner selects records and confirms categories for each. Only selected record identities
     * and hashes participate. OCR, notes, names, screenshot bytes and AI output are never processed.
     */
    fun prepare(confirmations: Map<String, Set<Pattern>>): Consent {
        requireCurrent()
        val selection = confirmations.mapValues { (_, patterns) -> patterns.toSet() }
        require(selection.size in 1..128 && selection.values.all { it.isNotEmpty() }) {
            "Select evidence and confirm at least one pattern on each selected record."
        }
        val hashesByPattern = mutableMapOf<Pattern, MutableSet<String>>()
        val selected = access.store.read().evidence().filter { it.id() in selection }
        require(selected.size == selection.size) { "Selected evidence is unavailable in this Space." }
        for (record in selected) {
            requireCurrent()
            for (pattern in selection.getValue(record.id())) {
                hashesByPattern.getOrPut(pattern) { mutableSetOf() }.add(record.sha256())
            }
        }
        val recurring = Pattern.entries.filter { (hashesByPattern[it]?.size ?: 0) >= 2 }.toSet()
        val consent = Consent(Collections.unmodifiableSet(recurring))
        access.withRetirementBoundary {
            requireCurrent()
            pending.set(consent)
        }
        return consent
    }

    fun discard(consent: Consent) {
        pending.compareAndSet(consent, null)
    }

    /** Call only after the owner confirms the exact preview. Replaces the profile for this Space. */
    fun save(consent: Consent): Set<Pattern> {
        access.withRetirementBoundary {
            requireCurrent()
            check(pending.compareAndSet(consent, null)) { "Prepare a fresh profile preview." }
            check(consent.patterns.isNotEmpty()) { "No recurring categories were confirmed." }
        }
        profile.write(consent.patterns) { promote ->
            access.withRetirementBoundary {
                requireCurrent()
                promote()
            }
        }
        requireCurrent()
        return consent.patterns
    }

    fun currentProfile(): Set<Pattern> {
        requireCurrent()
        return profile.read().also { requireCurrent() }
    }

    private fun requireCurrent() {
        check(!closed.get()) { "Pattern learning has ended." }
        access.checkCurrent()
    }

    private fun retire() {
        closed.set(true)
        pending.set(null)
    }

    override fun close() {
        access.withRetirementBoundary(::retire)
        retirement.close()
    }

    /**
     * The format stores only a version and category flags. Device-local Keystore encryption
     * deliberately uses an independent alias per Space and binds each file to its owner.
     */
    private class ProfileStore(context: Context, owner: String) {
        private val directory = File(context.noBackupFilesDir, "sanctuary-patterns-v1")
        private val destination = File(directory, "$owner.enc")
        private val stagingPrefix = "$owner-profile-"
        private val alias = "private-gallery-sanctuary-patterns-v1-$owner"
        private val aad = alias.toByteArray(Charsets.UTF_8)

        fun write(patterns: Set<Pattern>, publish: (() -> Unit) -> Unit) = synchronized(FILE_GATE) {
            check(directory.isDirectory || directory.mkdirs()) { "Pattern profile storage is unavailable." }
            directory.listFiles { file -> file.name.startsWith(stagingPrefix) && file.name.endsWith(".stage") }
                ?.forEach { it.delete() }
            val mask = patterns.fold(0) { value, pattern -> value or pattern.bit }
            val payload = byteArrayOf(VERSION, mask.toByte())
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.ENCRYPT_MODE, key(create = true))
                updateAAD(aad)
            }
            val encrypted = try { cipher.doFinal(payload) } finally { payload.fill(0) }
            try {
                check(cipher.iv.size == IV_BYTES)
                val stage = File.createTempFile(stagingPrefix, ".stage", directory)
                try {
                    FileOutputStream(stage).use { stream ->
                        stream.write(cipher.iv)
                        stream.write(encrypted)
                        stream.fd.sync()
                    }
                    publish { Os.rename(stage.path, destination.path) }
                    val fd = Os.open(directory.path, OsConstants.O_RDONLY, 0)
                    try {
                        check(OsConstants.S_ISDIR(Os.fstat(fd).st_mode))
                        Os.fsync(fd)
                    } finally {
                        Os.close(fd)
                    }
                } finally {
                    stage.delete()
                }
            } finally {
                encrypted.fill(0)
            }
        }

        fun read(): Set<Pattern> = synchronized(FILE_GATE) {
            if (!destination.exists()) return@synchronized emptySet()
            check(destination.isFile && destination.length() == (IV_BYTES + 2 + 16).toLong()) {
                "Invalid pattern profile."
            }
            val stored = destination.readBytes()
            val payload = try {
                Cipher.getInstance("AES/GCM/NoPadding").run {
                    init(Cipher.DECRYPT_MODE, key(create = false), GCMParameterSpec(128, stored, 0, IV_BYTES))
                    updateAAD(aad)
                    doFinal(stored, IV_BYTES, stored.size - IV_BYTES)
                }
            } finally {
                stored.fill(0)
            }
            try {
                check(payload.size == 2 && payload[0] == VERSION) { "Unknown pattern profile format." }
                val mask = payload[1].toInt() and 0xff
                val patterns = Pattern.entries.filter { mask and it.bit != 0 }.toSet()
                check(patterns.isNotEmpty() && patterns.fold(0) { value, pattern -> value or pattern.bit } == mask) {
                    "Invalid pattern profile categories."
                }
                Collections.unmodifiableSet(patterns)
            } finally {
                payload.fill(0)
            }
        }

        private fun key(create: Boolean): SecretKey {
            val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            if (store.containsAlias(alias)) {
                return store.getKey(alias, null) as? SecretKey ?: error("Pattern profile key is unavailable.")
            }
            check(create && !destination.exists()) { "Pattern profile key is unavailable." }
            return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
                init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build())
                generateKey()
            }
        }

        private companion object {
            const val VERSION: Byte = 1
            const val IV_BYTES = 12
            val FILE_GATE = Any()
        }
    }
}
