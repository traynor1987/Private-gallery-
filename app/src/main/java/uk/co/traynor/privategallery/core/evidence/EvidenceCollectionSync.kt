package uk.co.traynor.privategallery.core.evidence

import android.os.FileObserver
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.FileTime
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.launch
import uk.co.traynor.privategallery.core.hidden.HiddenRecordStore
import uk.co.traynor.privategallery.core.vault.VaultIndexSnapshot
import uk.co.traynor.privategallery.core.vault.VaultItemState

internal data class EvidenceCollectionOption(val id: String, val name: String)
internal data class EvidenceCollectionConnection(val id: String, val name: String)
internal data class EvidenceCollectionProgress(
    val discovered: Int = 0,
    val screenshots: Int = 0,
    val processed: Int = 0,
    val registered: Int = 0,
    val registeredTotal: Int = 0,
    val duplicates: Int = 0,
    val failed: Int = 0,
    val pendingExtraction: Int = 0,
    val unavailableCollections: Int = 0,
)

internal data class EvidenceCollectionSnapshot(
    val members: Map<EvidenceCollectionOption, List<EvidenceOriginal>>,
    val completeSourceIds: Set<String>,
)

/** Snapshot only authenticated metadata for selected memberships. No payload is opened here. */
internal fun evidenceCollections(
    index: VaultIndexSnapshot,
    ids: Set<String>,
    payloadExists: (String) -> Boolean,
): EvidenceCollectionSnapshot {
    val active = index.items.filter { it.state == VaultItemState.COMPLETE }.associateBy { it.id }
    val memberships = index.memberships.asSequence().filter { it.collectionId in ids }.groupBy { it.collectionId }
    val members = index.collections.asSequence().filter { it.id in ids }.associate { collection ->
        val option = EvidenceCollectionOption(collection.id, collection.name)
        option to memberships[collection.id].orEmpty().mapNotNull { membership ->
            active[membership.vaultItemId]?.takeIf { EvidenceMetadata.supported(it.mimeType) && payloadExists(it.id) }?.let { item ->
                val base = EvidenceMetadata.fromItem(item)
                val labels = base.embedded() + mapOf(
                    "sourceCollectionId" to collection.id,
                    "sourceCollectionName" to collection.name.take(200),
                )
                val metadata = EvidenceCatalog.Metadata(
                    base.sourceName(), base.fileCreatedAt(), base.fileModifiedAt(), base.dateAddedAt(),
                    base.captureAt(), base.width(), base.height(), labels, base.vaultOnly(),
                )
                EvidenceOriginal(item, metadata)
            }
        }
    }
    return EvidenceCollectionSnapshot(members, active.keys.filterTo(mutableSetOf(), payloadExists))
}

/** The connection stores only local identifiers; only the captured Vault can resolve them. */
internal class EvidenceCollectionSync(private val access: EvidenceAccess) {
    private val settings = ConnectionStore(access)

    fun available(): List<EvidenceCollectionOption> {
        access.checkCurrent()
        return access.sources.collections().map { EvidenceCollectionOption(it.id, it.name) }
            .also { access.checkCurrent() }
    }

    fun connections(): List<EvidenceCollectionConnection> {
        val connected = settings.read()
        if (connected.isEmpty()) return connected
        val currentNames = available().associate { it.id to it.name }
        return connected.map { it.copy(name = currentNames[it.id] ?: it.name) }
    }

    fun connect(id: String): List<EvidenceCollectionConnection> {
        val option = available().singleOrNull { it.id == id } ?: error("Collection unavailable in this vault")
        return settings.update { current ->
            if (current.any { it.id == id }) current else current + EvidenceCollectionConnection(option.id, option.name)
        }
    }

    fun disconnect(id: String): List<EvidenceCollectionConnection> =
        settings.update { current -> current.filterNot { it.id == id } }

    fun includesAny(sourceIds: Set<String>): Boolean {
        if (sourceIds.isEmpty()) return false
        val connected = settings.read().mapTo(mutableSetOf()) { it.id }
        if (connected.isEmpty()) return false
        return access.sources.collectionSnapshot(connected).members.values.any { members ->
            members.any { it.item.id in sourceIds }
        }
    }

    /** The encrypted index emits only an invalidation, never names or data, and only while authorised. */
    fun changes(): Flow<Unit> = callbackFlow {
        access.checkCurrent()
        val file = access.sources.collectionIndexFile()
        val stamp = AtomicReference(indexStamp(file))
        val observer = if (file == null) null else file.parentFile?.takeIf { it.isDirectory }?.let { directory ->
            val flags = FileObserver.CLOSE_WRITE or FileObserver.MOVED_TO or FileObserver.DELETE or FileObserver.DELETE_SELF
            object : FileObserver(directory, flags) {
                override fun onEvent(event: Int, path: String?) {
                    if ((path == file.name || (event and FileObserver.DELETE_SELF) != 0) && access.current()) {
                        stamp.set(indexStamp(file))
                        trySend(Unit)
                    }
                }
            }
        }
        val retirement = access.onRetire { close() }
        observer?.startWatching()
        trySend(Unit)
        val fallback = launch {
            while (access.current()) {
                delay(60_000)
                if (!access.current()) break
                val latest = indexStamp(file)
                if (stamp.getAndSet(latest) != latest) trySend(Unit)
            }
        }
        awaitClose {
            fallback.cancel()
            observer?.stopWatching()
            retirement.close()
        }
    }.conflate()

    private data class IndexStamp(val fileKey: Any?, val modified: FileTime, val bytes: Long)

    private fun indexStamp(file: File?): IndexStamp? {
        if (file == null) return null
        return runCatching {
            val attributes = Files.readAttributes(file.toPath(), BasicFileAttributes::class.java)
            IndexStamp(attributes.fileKey(), attributes.lastModifiedTime(), attributes.size())
        }.getOrNull()
    }

    /**
     * Controller serialises calls with its existing operation mutex. Catalogue registration and OCR
     * are durable independent commits, so restart scans only unchecked work without a plaintext queue.
     */
    suspend fun sync(
        progress: (EvidenceCollectionProgress) -> Unit = {},
        retryExtraction: (suspend (EvidenceCatalog.Evidence) -> EvidenceCatalog)? = null,
        register: suspend (EvidenceOriginal) -> EvidenceCatalog,
    ): EvidenceCatalog {
        val operation = currentCoroutineContext()
        val connections = settings.read()
        var catalog = access.store.read()
        if (connections.isEmpty()) {
            progress(EvidenceCollectionProgress())
            return catalog
        }

        val snapshot = access.sources.collectionSnapshot(connections.mapTo(mutableSetOf()) { it.id })
        val candidates = snapshot.members.values.asSequence().flatten().distinctBy { it.item.id }.toList()
        val records = catalog.evidence().associateBy { it.sourceId() }.toMutableMap()
        // A restricted original cannot be aliased to an unrestricted reference.
        val fingerprints = catalog.evidence().asSequence().filter { it.sourceId() in snapshot.completeSourceIds }
            .map { Fingerprint(it.sha256(), it.metadata().vaultOnly()) }.toMutableSet()
        var result = EvidenceCollectionProgress(
            discovered = candidates.size,
            screenshots = candidates.count { it.item.mimeType.startsWith("image/") },
            unavailableCollections = connections.size - snapshot.members.size,
        )
        progress(result)

        for (candidate in candidates) {
            operation.ensureActive()
            access.checkCurrent()
            val sourceId = candidate.item.id
            val fingerprint = Fingerprint(candidate.item.plaintextSha256.toHexString(), candidate.item.vaultOnly)
            val current = records[sourceId]
            var registered = 0
            var duplicate = 0
            var failed = 0
            var pending = 0
            try {
                when {
                    current != null -> {
                        EvidenceController.checkSource(current, candidate)
                        if (current.needsExtraction() && retryExtraction != null) {
                            catalog = retryExtraction(current)
                            records[sourceId] = catalog.evidence().single { it.sourceId() == sourceId }
                        }
                    }
                    fingerprint in fingerprints -> duplicate = 1
                    else -> {
                        catalog = register(candidate)
                        val saved = catalog.evidence().single { it.sourceId() == sourceId }
                        EvidenceController.checkSource(saved, candidate)
                        records[sourceId] = saved
                        fingerprints += fingerprint
                        registered = 1
                    }
                }
                if (duplicate == 0 && records[sourceId]?.needsExtraction() == true) pending = 1
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                access.checkCurrent()
                // The record may have committed before OCR or the durability acknowledgement failed.
                catalog = access.store.read()
                catalog.evidence().firstOrNull { it.sourceId() == sourceId }?.let { saved ->
                    if (saved.sha256() == fingerprint.sha256 && saved.metadata().vaultOnly() == fingerprint.vaultOnly) {
                        records[sourceId] = saved
                        fingerprints += fingerprint
                        if (saved.needsExtraction()) pending = 1
                    }
                }
                failed = 1
            }
            operation.ensureActive()
            access.checkCurrent()
            result = result.copy(
                processed = result.processed + 1,
                registered = result.registered + registered,
                registeredTotal = result.registeredTotal + if (sourceId in records) 1 else 0,
                duplicates = result.duplicates + duplicate,
                failed = result.failed + failed,
                pendingExtraction = result.pendingExtraction + pending,
            )
            progress(result)
        }
        return catalog
    }

    private data class Fingerprint(val sha256: String, val vaultOnly: Boolean)

    private fun EvidenceCatalog.Evidence.needsExtraction() = size() <= MAX_OCR_BYTES && ocr().isEmpty()

    private companion object {
        const val MAX_OCR_BYTES = 32L * 1024 * 1024
    }
}

private class ConnectionStore(private val access: EvidenceAccess) {
    private val directory = File(access.root, "evidence-collections-68").canonicalFile
    private val records = HiddenRecordStore(directory)
    private val lock = locks.computeIfAbsent(directory.path) { Any() }

    fun read(): List<EvidenceCollectionConnection> = transaction(null)

    fun update(change: (List<EvidenceCollectionConnection>) -> List<EvidenceCollectionConnection>) = transaction(change)

    private fun transaction(change: ((List<EvidenceCollectionConnection>) -> List<EvidenceCollectionConnection>)?):
        List<EvidenceCollectionConnection> = synchronized(lock) {
        access.leases.acquire().use { lease ->
            check(lease.current())
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(lease.key(), "HmacSHA256"))
            val key = mac.doFinal("private-gallery:evidence:68:collections:v1:${access.owner.name}".encodeToByteArray())
            try {
                records.clearStaging()
                val current = if (records.file("metadata", RECORD_ID).exists()) {
                    val bytes = records.read("metadata", RECORD_ID, key)
                    try { decode(bytes) } finally { bytes.fill(0) }
                } else emptyList()
                check(lease.current())
                val next = change?.invoke(current) ?: current
                if (next != current) {
                    val bytes = encode(next)
                    val stage = try { records.stage("metadata", RECORD_ID, bytes, key) }
                        finally { bytes.fill(0) }
                    try {
                        lease.commit { records.promote(stage, "metadata", RECORD_ID) }
                        records.syncPromotions()
                    } finally { stage.delete() }
                }
                check(lease.current())
                next
            } finally { key.fill(0) }
        }
    }

    private fun encode(values: List<EvidenceCollectionConnection>): ByteArray {
        check(values.size <= LIMIT && values.distinctBy { it.id }.size == values.size)
        return EvidenceWipingBuffer(512 * 1024) { access.current() }.use { output ->
            val data = DataOutputStream(output)
            data.writeInt(SCHEMA)
            data.writeInt(values.size)
            for ((id, name) in values) {
                check(id.length in 1..MAX_ID && name.length in 1..MAX_NAME)
                data.writeUTF(id)
                data.writeUTF(name)
            }
            output.toByteArray()
        }
    }

    private fun decode(bytes: ByteArray): List<EvidenceCollectionConnection> =
        DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            check(input.readInt() == SCHEMA)
            val count = input.readInt()
            check(count in 0..LIMIT)
            val values = List(count) {
                val id = input.readUTF()
                val name = input.readUTF()
                check(id.length in 1..MAX_ID && name.length in 1..MAX_NAME)
                EvidenceCollectionConnection(id, name)
            }
            check(input.read() == -1 && values.distinctBy { it.id }.size == values.size)
            values
        }

    companion object {
        private const val SCHEMA = 1
        private const val LIMIT = 128
        private const val MAX_ID = 256
        private const val MAX_NAME = 1024
        private const val RECORD_ID = "00000000-0000-4000-8000-000000000068"
        private val locks = ConcurrentHashMap<String, Any>()
    }
}
