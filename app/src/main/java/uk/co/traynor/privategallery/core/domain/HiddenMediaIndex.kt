package uk.co.traynor.privategallery.core.domain

internal class HiddenMediaItem
private constructor(
  bytes: ByteArray,
  private val item: Phase3Bytes,
  val format: Int,
  val payload: MediaReference,
  val plaintextLength: Long,
  private val plaintextSha: Phase3Bytes,
  val metadataRevision: Long,
  val state: Int,
  val deletedAt: Long?,
  val displayName: String,
  val mime: String,
  val importedAt: Long,
  val origin: Int,
  val restrictions: Int,
  val currentCrop: MediaCrop?,
  val previousCrop: MediaCrop?,
  val preview: MediaReference?,
) : Phase3Body(bytes) {
  val itemId
    get() = item.copy()

  val plaintextHash
    get() = plaintextSha.copy()

  companion object {
    internal fun read(r: Phase3Reader): HiddenMediaItem {
      val start = r.position
      val id = r.id()
      val f = r.enum(1..2)
      val p = r.reference()
      val n = r.u64()
      val sha = r.hash()
      val rev = r.positive()
      val state = r.enum(1..2)
      val deleted = r.optional { r.i64() }
      val name = r.string(true)
      val mime = r.string(true)
      val imported = r.i64()
      val origin = r.enum(1..5)
      val restrictions = r.enum(0..1)
      val crop = r.optional { r.crop() }
      val previous = r.optional { r.crop() }
      val preview = r.optional { r.reference() }
      p3Check(p.context.purpose == if (f == 1) 2 else 10)
      p3Check(n <= if (f == 1) P3_IMAGE_LIMIT else P3_VIDEO_LIMIT)
      val chunks = (n + 1048575L) / 1048576L
      val complete = if (f == 1) 172L + n else 172L + chunks * 172L + n
      p3Check(p.ciphertextLength == complete && mime.startsWith(if (f == 1) "image/" else "video/"))
      p3Check(if (state == 1) deleted == null else deleted != null && deleted > 0)
      p3Check(preview == null || preview.context.purpose == 3)
      return HiddenMediaItem(
        r.slice(start),
        id,
        f,
        p,
        n,
        sha,
        rev,
        state,
        deleted,
        name,
        mime,
        imported,
        origin,
        restrictions,
        crop,
        previous,
        preview,
      )
    }
  }
}

internal class HiddenMediaCollection
private constructor(
  bytes: ByteArray,
  private val id: Phase3Bytes,
  val name: String,
  val createdAt: Long,
  private val cover: Phase3Bytes?,
) : Phase3Body(bytes) {
  val collectionId
    get() = id.copy()

  val coverItemId
    get() = cover?.copy()

  companion object {
    internal fun read(r: Phase3Reader): HiddenMediaCollection {
      val s = r.position
      val id = r.id()
      val name = r.string(true)
      val time = r.i64()
      val cover = r.optional { r.id() }
      return HiddenMediaCollection(r.slice(s), id, name, time, cover)
    }
  }
}

internal class HiddenMediaMembership
private constructor(
  bytes: ByteArray,
  private val collection: Phase3Bytes,
  private val item: Phase3Bytes,
  val addedAt: Long,
) : Phase3Body(bytes) {
  val collectionId
    get() = collection.copy()

  val itemId
    get() = item.copy()

  companion object {
    internal fun read(r: Phase3Reader): HiddenMediaMembership {
      val s = r.position
      val c = r.id()
      val i = r.id()
      val a = r.i64()
      return HiddenMediaMembership(r.slice(s), c, i, a)
    }
  }
}

internal class HiddenMediaTransfer
private constructor(
  bytes: ByteArray,
  private val transfer: Phase3Bytes,
  val action: Int,
  val phase: Int,
  private val item: Phase3Bytes,
  private val sourceContainer: Phase3Bytes,
  private val sourceSnapshot: Phase3Bytes,
  val receipt: MediaContext,
  val historicalIndex: MediaContext,
  val journal: MediaContext,
  val terminal: Int,
  private val terminalSha: Phase3Bytes?,
) : Phase3Body(bytes) {
  val transferId
    get() = transfer.copy()

  val itemId
    get() = item.copy()

  val sourceContainerId
    get() = sourceContainer.copy()

  val sourceSnapshotId
    get() = sourceSnapshot.copy()

  val primaryTerminalHash
    get() = terminalSha?.copy()

  companion object {
    internal fun read(r: Phase3Reader): HiddenMediaTransfer {
      val s = r.position
      val t = r.id()
      val a = r.enum(1..2)
      val p = r.enum(1..6)
      val i = r.id()
      val c = r.id()
      val snapshot = r.id()
      p3Check(r.u64() == 1L)
      val receipt = r.context()
      val h = r.context()
      val j = r.context()
      val terminal = r.enum(0..4)
      val hash = r.optional { r.hash() }
      p3Check(
        p in 4..6 &&
          receipt.purpose == 7 &&
          receipt.generation == 1L &&
          h.purpose == 1 &&
          j.purpose == 5 &&
          (terminal == 0) == (hash == null)
      )
      p3Check(a == 2 || p != 5 && terminal !in 1..2)
      return HiddenMediaTransfer(r.slice(s), t, a, p, i, c, snapshot, receipt, h, j, terminal, hash)
    }
  }
}

internal class HiddenMediaIndex
private constructor(
  bytes: ByteArray,
  val revision: Long,
  val items: List<HiddenMediaItem>,
  val collections: List<HiddenMediaCollection>,
  val memberships: List<HiddenMediaMembership>,
  private val favourite: Phase3Bytes?,
  val transfers: List<HiddenMediaTransfer>,
) : Phase3Body(bytes) {
  private val completeCiphertextLength = bytes.size + 172

  val favouriteCollectionId
    get() = favourite?.copy()

  val retainedEvidenceContexts: List<MediaContext>
    get() =
      p3List(
        transfers
          .flatMap { listOf(it.historicalIndex, it.journal, it.receipt) }
          .distinct()
          .sortedWith { a, b -> p3Compare(a.encode(), b.encode()) }
      )

  /** Structural binding only; adapters still authenticate bytes and pin context/file identity. */
  fun validateHistoricalTarget(
    transfer: HiddenMediaTransfer,
    receipt: DestinationReceipt,
    destinationContainerId: ByteArray,
    completeEncryptedHistoricalIndex: ByteArray,
  ) {
    p3Check(completeEncryptedHistoricalIndex.size in 172..(P3_INDEX_LIMIT + 172))
    p3Check(completeEncryptedHistoricalIndex.size == completeCiphertextLength)
    val snapshot = completeEncryptedHistoricalIndex.copyOf()
    p3Check(same(digest(snapshot), receipt.indexHash))
    val row =
      transfers.singleOrNull { same(it.transferId, transfer.transferId) }
        ?: throw Phase3FormatException()
    val item =
      items.singleOrNull { same(it.itemId, transfer.itemId) } ?: throw Phase3FormatException()
    p3Check(
      row.action == transfer.action &&
        same(row.itemId, transfer.itemId) &&
        same(row.sourceContainerId, transfer.sourceContainerId) &&
        same(row.sourceSnapshotId, transfer.sourceSnapshotId) &&
        row.receipt == transfer.receipt &&
        row.historicalIndex == transfer.historicalIndex
    )
    p3Check(
      revision == transfer.historicalIndex.generation &&
        revision == receipt.destinationIndexGeneration &&
        same(receipt.transferId, row.transferId) &&
        same(receipt.receiptId, row.receipt.objectId) &&
        same(receipt.destinationContainerId, destinationContainerId)
    )
    p3Check(
      same(receipt.sourceContainerId, row.sourceContainerId) &&
        same(receipt.sourceObjectId, row.sourceSnapshotId) &&
        receipt.sourceGeneration == 1L
    )
    p3Check(
      same(receipt.destinationObjectId, item.payload.context.objectId) &&
        receipt.destinationGeneration == item.payload.context.generation &&
        receipt.plaintextLength == item.plaintextLength &&
        same(receipt.plaintextHash, item.plaintextHash) &&
        same(receipt.ciphertextHash, item.payload.hash)
    )
  }

  companion object {
    fun parse(bytes: ByteArray): HiddenMediaIndex {
      val r = Phase3Reader(bytes, P3_INDEX_LIMIT)
      r.version(2)
      val rev = r.positive()
      val ni = r.count16(512, 160)
      val items = p3List(List(ni) { HiddenMediaItem.read(r) })
      val nc = r.count16(128, 29)
      val collections = p3List(List(nc) { HiddenMediaCollection.read(r) })
      val nm = r.count32(65536, 40)
      val memberships = p3List(List(nm) { HiddenMediaMembership.read(r) })
      val f = r.optional { r.id() }
      val nt = r.count16(64, 157)
      val transfers = p3List(List(nt) { HiddenMediaTransfer.read(r) })
      r.end()
      p3Sorted(items) { it.itemId }
      p3Sorted(collections) { it.collectionId }
      p3Sorted(memberships) { it.collectionId + it.itemId }
      p3Sorted(transfers) { it.transferId }
      val itemById = items.associateBy { it.itemId.hex() }
      val collectionById = collections.associateBy { it.collectionId.hex() }
      memberships.forEach { membership ->
        p3Check(
          collectionById.containsKey(membership.collectionId.hex()) &&
            itemById[membership.itemId.hex()]?.state == 1
        )
      }
      collections.forEach { collection ->
        collection.coverItemId?.let { cover ->
          p3Check(
            itemById[cover.hex()]?.state == 1 &&
              memberships.any {
                same(it.collectionId, collection.collectionId) && same(it.itemId, cover)
              }
          )
        }
      }
      p3Check(f == null || collectionById.containsKey(f.copy().hex()))
      return HiddenMediaIndex(r.all(), rev, items, collections, memberships, f, transfers)
    }
  }
}
