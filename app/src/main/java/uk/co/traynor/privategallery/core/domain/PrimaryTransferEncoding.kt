package uk.co.traynor.privategallery.core.domain

import java.util.UUID

internal class PrimaryTransferBootstrap
private constructor(
  bytes: ByteArray,
  private val container: Phase3Bytes,
  private val master: Phase3Bytes,
  private val catalog: Phase3Bytes,
  private val descriptor: Phase3Bytes,
  val generation: Long,
  private val token: Phase3Bytes,
) : Phase3Body(bytes) {
  val containerId
    get() = container.copy()

  val masterId
    get() = master.copy()

  val catalogObjectId
    get() = catalog.copy()

  val descriptorObjectId
    get() = descriptor.copy()

  val tokenId
    get() = token.copy()

  companion object {
    fun parse(bytes: ByteArray): PrimaryTransferBootstrap {
      val r = Phase3Reader(bytes, 102)
      r.magic("PGTRB001")
      r.version(1)
      val c = r.id()
      val m = r.id()
      val ca = r.id()
      val d = r.id()
      val g = r.positive()
      val t = r.id()
      p3Check(r.u32() == 0L)
      r.end()
      p3Check(ca != d)
      return PrimaryTransferBootstrap(r.all(), c, m, ca, d, g, t)
    }
  }
}

internal class PrimaryTransferDescriptor
private constructor(
  bytes: ByteArray,
  private val bootstrapSha: Phase3Bytes,
  val catalog: MediaReference,
  val closure: List<MediaReference>,
) : Phase3Body(bytes) {
  val bootstrapHash
    get() = bootstrapSha.copy()

  fun validateBindings(
    bootstrap: PrimaryTransferBootstrap,
    catalogBody: PrimaryTransferCatalog,
    encryptedCatalog: ByteArray,
  ) {
    p3Check(encryptedCatalog.size in 172..(P3_INDEX_LIMIT + 172))
    p3Check(
      same(bootstrapHash, digest(bootstrap.encode())) &&
        catalog.context.generation == bootstrap.generation &&
        same(catalog.context.objectId, bootstrap.catalogObjectId) &&
        catalogBody.generation == bootstrap.generation &&
        catalog.ciphertextLength == encryptedCatalog.size.toLong() &&
        same(catalog.hash, digest(encryptedCatalog))
    )
    val expected =
      catalogBody.entries
        .flatMap { listOfNotNull(it.sourceProjection, it.journal, it.priorTerminalCatalog) }
        .distinct()
        .sortedWith { a, b -> p3Compare(a.context.encode(), b.context.encode()) }
    p3Check(closure == expected)
  }

  companion object {
    fun parse(bytes: ByteArray): PrimaryTransferDescriptor {
      val r = Phase3Reader(bytes, P3_SMALL_LIMIT)
      r.magic("PGTRD001")
      r.version(1)
      val b = r.hash()
      val c = r.reference()
      p3Check(c.context.purpose == 1)
      val n = r.count16(256, 66)
      val closure = p3List(List(n) { r.reference() })
      r.end()
      p3Sorted(closure) { it.context.encode() }
      p3Check(closure.all { it.context.purpose in setOf(1, 5) })
      return PrimaryTransferDescriptor(r.all(), b, c, closure)
    }
  }
}

internal class PrimaryTransferEntry
private constructor(
  bytes: ByteArray,
  private val transfer: Phase3Bytes,
  val localState: Int,
  val action: Int,
  val terminalKind: Int,
  val holdRevision: Long,
  val sourceProjection: MediaReference,
  val journal: MediaReference,
  private val hiddenContainer: Phase3Bytes,
  private val hiddenMaster: Phase3Bytes,
  private val item: Phase3Bytes,
  val receipt: MediaContext?,
  private val receiptSha: Phase3Bytes?,
  private val verifiedSelection: Phase3Bytes?,
  private val merge: Phase3Bytes?,
  private val priorIndex: Phase3Bytes?,
  private val releaseAck: Phase3Bytes?,
  val priorTerminalCatalog: MediaReference?,
) : Phase3Body(bytes) {
  val transferId
    get() = transfer.copy()

  val hiddenContainerId
    get() = hiddenContainer.copy()

  val hiddenMasterId
    get() = hiddenMaster.copy()

  val destinationItemId
    get() = item.copy()

  val receiptHash
    get() = receiptSha?.copy()

  val verifiedSelectionHash
    get() = verifiedSelection?.copy()

  val mergeHash
    get() = merge?.copy()

  val priorIndexHash
    get() = priorIndex?.copy()

  val releaseAckIndexHash
    get() = releaseAck?.copy()

  companion object {
    internal fun read(r: Phase3Reader): PrimaryTransferEntry {
      val s = r.position
      val id = r.id()
      val state = r.enum(1..12)
      val action = r.enum(1..2)
      val terminal = r.enum(0..4)
      val rev = r.positive()
      val source = r.reference()
      val journal = r.reference()
      val hc = r.id()
      val hm = r.id()
      val item = r.id()
      val receipt = r.optional { r.context() }
      val receiptHash = r.optional { r.hash() }
      val selected = r.optional { r.hash() }
      val merge = r.optional { r.hash() }
      val prior = r.optional { r.hash() }
      val ack = r.optional { r.hash() }
      val priorTerminal = r.optional { r.reference() }
      p3Check(
        source.context.purpose == 1 &&
          source.context.generation == 1L &&
          journal.context.purpose == 5
      )
      p3Check(receipt == null || receipt.purpose == 7 && receipt.generation == 1L)
      p3Check(priorTerminal == null || priorTerminal.context.purpose == 1)
      val expectedTerminal =
        when (state) {
          7 -> 1
          9 -> 2
          10 -> 3
          11 -> 4
          12 -> terminal
          else -> 0
        }
      p3Check(terminal == expectedTerminal && (state != 12 || terminal in 1..4))
      p3Check(action == 2 || state !in 4..9 && !(state == 12 && terminal in 1..2))
      val hasReceipt = receipt != null
      p3Check(hasReceipt == (receiptHash != null) && hasReceipt == (selected != null))
      p3Check(
        when (state) {
          1,
          2 -> !hasReceipt
          11 -> true
          else -> hasReceipt
        }
      )
      val merged = state in 6..7 || state == 12 && terminal == 1
      p3Check(merged == (merge != null) && merged == (prior != null))
      p3Check((state == 12) == (ack != null) && (state == 12) == (priorTerminal != null))
      return PrimaryTransferEntry(
        r.slice(s),
        id,
        state,
        action,
        terminal,
        rev,
        source,
        journal,
        hc,
        hm,
        item,
        receipt,
        receiptHash,
        selected,
        merge,
        prior,
        ack,
        priorTerminal,
      )
    }
  }
}

internal class PrimaryTransferCatalog
private constructor(
  bytes: ByteArray,
  val generation: Long,
  val entries: List<PrimaryTransferEntry>,
) : Phase3Body(bytes) {
  companion object {
    fun parse(bytes: ByteArray): PrimaryTransferCatalog {
      val r = Phase3Reader(bytes, P3_INDEX_LIMIT)
      r.magic("PGTRC001")
      r.version(1)
      val g = r.positive()
      val n = r.count16(64, 217)
      val e = p3List(List(n) { PrimaryTransferEntry.read(r) })
      r.end()
      p3Sorted(e) { it.transferId }
      return PrimaryTransferCatalog(r.all(), g, e)
    }
  }
}

internal class PrimarySourceItem
private constructor(
  bytes: ByteArray,
  val sourceItemId: String,
  val mime: String,
  val displayName: String,
  val importedAt: Long,
  val plaintextLength: Long,
  private val plaintextSha: Phase3Bytes,
  private val iv: Phase3Bytes,
  val legacyFormat: Int,
  val sourceUri: String?,
  val origin: Int,
  val restrictions: Int,
  val currentCrop: MediaCrop?,
  val previousCrop: MediaCrop?,
) : Phase3Body(bytes) {
  val plaintextHash
    get() = plaintextSha.copy()

  val nonce
    get() = iv.copy()

  companion object {
    internal fun read(r: Phase3Reader): PrimarySourceItem {
      val s = r.position
      val id = r.string(true)
      val mime = r.string(true)
      val name = r.string(true)
      val time = r.i64()
      val n = r.u64()
      val h = r.hash()
      val iv = Phase3Bytes(r.raw(12), 12)
      val f = r.enum(1..2)
      r.version(1)
      val uri = r.optional { r.string() }
      val origin = r.enum(1..5)
      val restrictions = r.enum(0..1)
      val deleted = r.optional { r.i64() }
      val current = r.optional { r.crop() }
      val previous = r.optional { r.crop() }
      p3Check(
        try {
          UUID.fromString(id).toString() == id
        } catch (_: IllegalArgumentException) {
          false
        }
      )
      p3Check(deleted == null && n <= if (f == 1) 64L * 1024 * 1024 else P3_VIDEO_LIMIT)
      return PrimarySourceItem(
        r.slice(s),
        id,
        mime,
        name,
        time,
        n,
        h,
        iv,
        f,
        uri,
        origin,
        restrictions,
        current,
        previous,
      )
    }
  }
}

internal class SourceCollection
private constructor(
  bytes: ByteArray,
  val sourceCollectionId: String,
  val name: String,
  val createdAt: Long,
  val pinned: Int,
  val coverItemId: String?,
  val sourceMembershipAddedAt: Long?,
) : Phase3Body(bytes) {
  companion object {
    internal fun read(r: Phase3Reader): SourceCollection {
      val s = r.position
      val id = r.string(true)
      val name = r.string(true)
      val time = r.i64()
      val pinned = r.enum(0..1)
      val cover = r.optional { r.string(true) }
      val member = r.optional { r.i64() }
      return SourceCollection(r.slice(s), id, name, time, pinned, cover, member)
    }
  }
}

internal data class SourcePhysicalIdentity(
  val device: Long,
  val inode: Long,
  val encryptedSize: Long,
  val mtimeNanos: Long,
  private val sha: Phase3Bytes,
) {
  val ciphertextHash
    get() = sha.copy()
}

internal data class SourceRootIdentity(val device: Long, val inode: Long)

internal class SourceProjection
private constructor(
  bytes: ByteArray,
  val item: PrimarySourceItem,
  val collections: List<SourceCollection>,
  val favouriteCollectionId: String?,
  val payloadIdentity: SourcePhysicalIdentity,
  val primaryRootIdentity: SourceRootIdentity,
) : Phase3Body(bytes) {
  val fingerprint
    get() = digest(encode())

  companion object {
    fun parse(bytes: ByteArray): SourceProjection {
      val r = Phase3Reader(bytes, P3_SMALL_LIMIT)
      r.magic("PGSRC001")
      r.version(1)
      val item = PrimarySourceItem.read(r)
      val n = r.count16(128, 22)
      val rows = p3List(List(n) { SourceCollection.read(r) })
      val f = r.optional { r.string(true) }
      val identity = SourcePhysicalIdentity(r.u64(), r.u64(), r.u64(), r.i64(), r.hash())
      val root = SourceRootIdentity(r.u64(), r.u64())
      r.end()
      p3Sorted(rows) { it.sourceCollectionId.toByteArray(Charsets.UTF_8) }
      p3Check(
        rows.all { it.sourceMembershipAddedAt != null || it.coverItemId == item.sourceItemId }
      )
      return SourceProjection(r.all(), item, rows, f, identity, root)
    }
  }
}

internal class RestoreMergeProjection
private constructor(
  bytes: ByteArray,
  val item: PrimarySourceItem,
  val collections: List<SourceCollection>,
  val favouriteCollectionId: String?,
) : Phase3Body(bytes) {
  val mergeHash
    get() = digest(encode())

  /** Restores the frozen item and relationships; favourite remains the current intent context. */
  fun validateSource(source: SourceProjection) {
    p3Check(item == source.item && collections.size == source.collections.size)
    collections.zip(source.collections).forEach { (row, old) ->
      p3Check(
        row.sourceCollectionId == old.sourceCollectionId &&
          row.name == old.name &&
          row.createdAt == old.createdAt &&
          row.pinned == old.pinned &&
          row.coverItemId == old.coverItemId &&
          row.sourceMembershipAddedAt == old.sourceMembershipAddedAt
      )
    }
  }

  companion object {
    fun parse(bytes: ByteArray): RestoreMergeProjection {
      val r = Phase3Reader(bytes, P3_SMALL_LIMIT)
      r.magic("PGMRG001")
      r.version(1)
      val n = r.u32()
      p3Check(n in 1..P3_SMALL_LIMIT && n <= r.remaining)
      val section = Phase3Reader(r.raw(n.toInt()), P3_SMALL_LIMIT)
      val item = PrimarySourceItem.read(section)
      section.end()
      val count = r.count16(128, 22)
      val rows = p3List(List(count) { SourceCollection.read(r) })
      val f = r.optional { r.string(true) }
      r.end()
      p3Sorted(rows) { it.sourceCollectionId.toByteArray(Charsets.UTF_8) }
      return RestoreMergeProjection(r.all(), item, rows, f)
    }
  }
}
