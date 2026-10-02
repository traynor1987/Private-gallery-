package uk.co.traynor.privategallery.core.domain

internal class TransferJournal
private constructor(
  bytes: ByteArray,
  private val operation: Phase3Bytes,
  private val transfer: Phase3Bytes,
  private val localContainer: Phase3Bytes,
  private val localObject: Phase3Bytes,
  val localGeneration: Long,
  val state: Int,
  val action: Int,
  val expectedPriorIndexGeneration: Long,
  val proposedIndexGeneration: Long,
  private val counterpartContainer: Phase3Bytes,
  private val counterpartObject: Phase3Bytes,
  val counterpartGeneration: Long,
  private val receipt: Phase3Bytes,
  private val receiptSha: Phase3Bytes,
) : Phase3Body(bytes) {
  val operationId
    get() = operation.copy()

  val transferId
    get() = transfer.copy()

  val localContainerId
    get() = localContainer.copy()

  val localObjectId
    get() = localObject.copy()

  val counterpartContainerId
    get() = counterpartContainer.copy()

  val counterpartObjectId
    get() = counterpartObject.copy()

  val receiptId
    get() = receipt.copy()

  val receiptHash
    get() = receiptSha.copy()

  /** Checks evidence bindings; callers still authenticate both bodies and original operations. */
  fun validateReceipt(receipt: DestinationReceipt, completeEncryptedReceipt: ByteArray) {
    p3Check(completeEncryptedReceipt.size == 400)
    p3Check(
      same(transferId, receipt.transferId) &&
        same(receiptId, receipt.receiptId) &&
        same(receiptHash, digest(completeEncryptedReceipt))
    )
    val sourceLocal =
      same(localContainerId, receipt.sourceContainerId) &&
        same(localObjectId, receipt.sourceObjectId) &&
        localGeneration == receipt.sourceGeneration
    val destinationLocal =
      same(localContainerId, receipt.destinationContainerId) &&
        same(localObjectId, receipt.destinationObjectId) &&
        localGeneration == receipt.destinationGeneration
    val sourceCounterpart =
      same(counterpartContainerId, receipt.sourceContainerId) &&
        same(counterpartObjectId, receipt.sourceObjectId) &&
        counterpartGeneration == receipt.sourceGeneration
    val destinationCounterpart =
      same(counterpartContainerId, receipt.destinationContainerId) &&
        same(counterpartObjectId, receipt.destinationObjectId) &&
        counterpartGeneration == receipt.destinationGeneration
    p3Check(sourceLocal && destinationCounterpart || destinationLocal && sourceCounterpart)
  }

  companion object {
    fun parse(bytes: ByteArray): TransferJournal {
      val r = Phase3Reader(bytes, 182)
      r.version(1)
      val op = r.id()
      val t = r.id()
      val lc = r.id()
      val lo = r.id()
      val lg = r.positive()
      val state = r.enum(1..6)
      val action = r.enum(1..2)
      val prior = r.u64()
      val proposed = r.u64()
      val cc = r.id()
      val co = r.id()
      val cg = r.positive()
      val ri = Phase3Bytes(r.raw(16), 16)
      val rh = r.hash()
      r.end()
      val zeroId = ri.copy().all { it == 0.toByte() }
      val zeroHash = rh.copy().all { it == 0.toByte() }
      p3Check(zeroId == zeroHash && (state < 3 || !zeroId))
      p3Check(action == 2 || state != 5)
      return TransferJournal(
        r.all(),
        op,
        t,
        lc,
        lo,
        lg,
        state,
        action,
        prior,
        proposed,
        cc,
        co,
        cg,
        ri,
        rh,
      )
    }
  }
}

internal class DestinationReceipt
private constructor(
  bytes: ByteArray,
  private val receipt: Phase3Bytes,
  private val transfer: Phase3Bytes,
  private val sourceContainer: Phase3Bytes,
  private val sourceObject: Phase3Bytes,
  val sourceGeneration: Long,
  private val destinationContainer: Phase3Bytes,
  private val destinationObject: Phase3Bytes,
  val destinationGeneration: Long,
  val destinationIndexGeneration: Long,
  val plaintextLength: Long,
  private val plaintextSha: Phase3Bytes,
  private val ciphertextSha: Phase3Bytes,
  private val indexSha: Phase3Bytes,
) : Phase3Body(bytes) {
  val receiptId
    get() = receipt.copy()

  val transferId
    get() = transfer.copy()

  val sourceContainerId
    get() = sourceContainer.copy()

  val sourceObjectId
    get() = sourceObject.copy()

  val destinationContainerId
    get() = destinationContainer.copy()

  val destinationObjectId
    get() = destinationObject.copy()

  val plaintextHash
    get() = plaintextSha.copy()

  val ciphertextHash
    get() = ciphertextSha.copy()

  val indexHash
    get() = indexSha.copy()

  companion object {
    fun parse(bytes: ByteArray): DestinationReceipt {
      val r = Phase3Reader(bytes, 228)
      r.version(1)
      val ri = r.id()
      val t = r.id()
      val sc = r.id()
      val so = r.id()
      val sg = r.positive()
      val dc = r.id()
      val d = r.id()
      val dg = r.positive()
      val ig = r.positive()
      val n = r.u64()
      val p = r.hash()
      val c = r.hash()
      val i = r.hash()
      r.version(1)
      r.end()
      return DestinationReceipt(r.all(), ri, t, sc, so, sg, dc, d, dg, ig, n, p, c, i)
    }
  }
}

internal class MediaAttemptReservation
private constructor(
  bytes: ByteArray,
  private val attempt: Phase3Bytes,
  val targetGeneration: Long,
) : Phase3Body(bytes) {
  val attemptId
    get() = attempt.copy()

  companion object {
    fun parse(bytes: ByteArray): MediaAttemptReservation {
      val r = Phase3Reader(bytes, 26)
      r.version(2)
      val a = r.id()
      val g = r.positive()
      r.end()
      return MediaAttemptReservation(r.all(), a, g)
    }
  }
}

internal class MediaSourceBinding
private constructor(
  bytes: ByteArray,
  private val transfer: Phase3Bytes,
  private val container: Phase3Bytes,
  private val snapshot: Phase3Bytes,
) : Phase3Body(bytes) {
  val transferId
    get() = transfer.copy()

  val sourceContainerId
    get() = container.copy()

  val sourceSnapshotId
    get() = snapshot.copy()

  companion object {
    fun parse(bytes: ByteArray): MediaSourceBinding {
      val r = Phase3Reader(bytes, 56)
      val t = r.id()
      val c = r.id()
      val s = r.id()
      p3Check(r.u64() == 1L)
      r.end()
      return MediaSourceBinding(r.all(), t, c, s)
    }
  }
}

internal class MediaAttemptOwner
private constructor(
  bytes: ByteArray,
  private val reservationSha: Phase3Bytes,
  val action: Int,
  private val item: Phase3Bytes,
  val sourceBinding: MediaSourceBinding?,
  val contexts: List<MediaContext>,
) : Phase3Body(bytes) {
  val reservationHash
    get() = reservationSha.copy()

  val itemId
    get() = item.copy()

  fun validateBinding(reservation: MediaAttemptReservation, ownerContext: MediaContext) {
    p3Check(
      same(reservationHash, digest(reservation.encode())) &&
        ownerContext.purpose == 9 &&
        ownerContext.generation == 1L &&
        same(ownerContext.objectId, reservation.attemptId)
    )
  }

  companion object {
    fun parse(bytes: ByteArray): MediaAttemptOwner {
      val r = Phase3Reader(bytes, P3_SMALL_LIMIT)
      r.version(2)
      val h = r.hash()
      val a = r.enum(1..3)
      val i = r.id()
      val s = r.optional { MediaSourceBinding.parse(r.raw(56)) }
      val n = r.count16(16, 26)
      val c = p3List(List(n) { r.context() })
      r.end()
      p3Sorted(c) { it.encode() }
      p3Check((a == 3) == (s == null))
      return MediaAttemptOwner(r.all(), h, a, i, s, c)
    }
  }
}
