package uk.co.traynor.privategallery.core.domain

internal class HiddenBootstrap
private constructor(
  bytes: ByteArray,
  private val container: Phase3Bytes,
  private val master: Phase3Bytes,
  val generation: Long,
  val state: Int,
  private val descriptor: Phase3Bytes,
  private val index: Phase3Bytes,
  private val catalog: Phase3Bytes,
  private val proof: Phase3Bytes,
) : Phase3Body(bytes) {
  val containerId
    get() = container.copy()

  val masterId
    get() = master.copy()

  val descriptorObjectId
    get() = descriptor.copy()

  val indexObjectId
    get() = index.copy()

  val catalogObjectId
    get() = catalog.copy()

  val credentialProofObjectId
    get() = proof.copy()

  companion object {
    fun parse(bytes: ByteArray): HiddenBootstrap {
      val r = Phase3Reader(bytes, 120)
      r.magic("PGDOMB02")
      r.version(2)
      val c = r.id()
      val m = r.id()
      val g = r.positive()
      val s = r.enum(1..2)
      val d = r.id()
      val i = r.id()
      val ca = r.id()
      p3Check(r.u32() == 0L)
      val p = r.id()
      r.end()
      p3Check(listOf(d, i, ca, p).distinct().size == 4)
      return HiddenBootstrap(r.all(), c, m, g, s, d, i, ca, p)
    }
  }
}

internal class HiddenMediaDescriptor
private constructor(
  bytes: ByteArray,
  val state: Int,
  val strongCode: Int,
  val autoCode: Int,
  private val bootstrapSha: Phase3Bytes,
  private val indexSha: Phase3Bytes,
  private val catalogSha: Phase3Bytes,
  private val biometric: Phase3Bytes?,
  val biometricGeneration: Long,
  private val biometricSha: Phase3Bytes,
  val credentialProof: MediaReference,
  val closure: List<MediaReference>,
) : Phase3Body(bytes) {
  val bootstrapHash
    get() = bootstrapSha.copy()

  val indexHash
    get() = indexSha.copy()

  val catalogHash
    get() = catalogSha.copy()

  val biometricId
    get() = biometric?.copy()

  val biometricHash
    get() = biometricSha.copy()

  fun validateBindings(
    bootstrap: HiddenBootstrap,
    index: HiddenMediaIndex,
    encryptedIndex: ByteArray,
    encryptedCatalog: ByteArray,
    encryptedProof: ByteArray,
  ) {
    p3Check(encryptedIndex.size in 172..(P3_INDEX_LIMIT + 172))
    p3Check(encryptedCatalog.size in 172..(12 + 32 * 62 + 172))
    p3Check(encryptedProof.size in 172..2268)
    p3Check(
      state == bootstrap.state &&
        index.revision == bootstrap.generation &&
        same(bootstrapHash, digest(bootstrap.encode())) &&
        same(indexHash, digest(encryptedIndex)) &&
        same(catalogHash, digest(encryptedCatalog))
    )
    p3Check(
      credentialProof.context.generation == bootstrap.generation &&
        same(credentialProof.context.objectId, bootstrap.credentialProofObjectId) &&
        credentialProof.ciphertextLength == encryptedProof.size.toLong() &&
        same(credentialProof.hash, digest(encryptedProof))
    )
    p3Check(closure.map { it.context } == index.retainedEvidenceContexts)
  }

  companion object {
    fun parse(bytes: ByteArray): HiddenMediaDescriptor {
      val r = Phase3Reader(bytes, P3_SMALL_LIMIT)
      r.magic("PGDOMD02")
      r.version(2)
      val s = r.enum(1..2)
      val strong = r.enum(1..4)
      val auto = r.enum(1..4)
      val b = r.hash()
      val i = r.hash()
      val c = r.hash()
      val present = r.enum(0..1)
      val id = Phase3Bytes(r.raw(16), 16, present == 1)
      val g = r.u64()
      val h = r.hash()
      if (present == 0)
        p3Check(id.copy().all { it == 0.toByte() } && g == 0L && h.copy().all { it == 0.toByte() })
      else p3Check(g > 0)
      val proof = r.reference()
      p3Check(proof.context.purpose == 9)
      val n = r.count16(256, 66)
      val closure = p3List(List(n) { r.reference() })
      r.end()
      p3Sorted(closure) { it.context.encode() }
      p3Check(closure.all { it.context.purpose in setOf(1, 5, 7) })
      return HiddenMediaDescriptor(
        r.all(),
        s,
        strong,
        auto,
        b,
        i,
        c,
        if (present == 1) id else null,
        g,
        h,
        proof,
        closure,
      )
    }
  }
}

internal class CredentialCatalogEntry
private constructor(
  bytes: ByteArray,
  private val id: Phase3Bytes,
  val generation: Long,
  private val sha: Phase3Bytes,
  val slotType: Int,
  val recoveryState: Int,
  val policyId: Int,
) : Phase3Body(bytes) {
  val slotId
    get() = id.copy()

  val envelopeHash
    get() = sha.copy()

  companion object {
    internal fun read(r: Phase3Reader): CredentialCatalogEntry {
      val start = r.position
      val id = r.id()
      val gen = r.positive()
      val h = r.hash()
      val type = r.enum(1..4)
      val state = r.enum(0..2)
      r.version(1)
      p3Check(if (type == 2) state in 1..2 else state == 0)
      return CredentialCatalogEntry(r.slice(start), id, gen, h, type, state, 1)
    }
  }
}

internal class CredentialCatalog
private constructor(
  bytes: ByteArray,
  val generation: Long,
  val entries: List<CredentialCatalogEntry>,
) : Phase3Body(bytes) {
  companion object {
    fun parse(bytes: ByteArray): CredentialCatalog {
      val r = Phase3Reader(bytes, 12 + 32 * 62)
      r.version(1)
      val g = r.positive()
      val n = r.count16(32, 62)
      val e = p3List(List(n) { CredentialCatalogEntry.read(r) })
      r.end()
      p3Sorted(e) { it.slotId }
      return CredentialCatalog(r.all(), g, e)
    }
  }
}

/** Authenticated credential body, never an in-process HoldRestoreProof or session. */
internal class CredentialProof
private constructor(
  bytes: ByteArray,
  private val token: Phase3Bytes,
  private val bootstrapSha: Phase3Bytes,
  private val catalogSha: Phase3Bytes,
  val strongCode: Int,
  val autoCode: Int,
  val catalog: CredentialCatalog,
) : Phase3Body(bytes) {
  val selectedAttemptId
    get() = token.copy()

  val bootstrapHash
    get() = bootstrapSha.copy()

  val encryptedCatalogHash
    get() = catalogSha.copy()

  fun validateBindings(
    bootstrap: HiddenBootstrap,
    selectedAttemptId: ByteArray,
    encryptedCatalog: ByteArray,
    envelopes: List<ByteArray>,
  ) {
    p3Check(encryptedCatalog.size in 172..(12 + 32 * 62 + 172))
    p3Check(
      bootstrap.state == 2 &&
        same(this.selectedAttemptId, selectedAttemptId) &&
        same(bootstrapHash, digest(bootstrap.encode())) &&
        same(encryptedCatalogHash, digest(encryptedCatalog)) &&
        catalog.generation == bootstrap.generation
    )
    p3Check(envelopes.size == catalog.entries.size)
    val identity = DomainIdentity(bootstrap.containerId, bootstrap.masterId)
    val inspected =
      envelopes.map {
        p3Check(it.size == 204)
        try {
          F1Slot.inspect(identity, it.copyOf())
        } catch (_: F1Exception) {
          throw Phase3FormatException()
        }
      }
    p3Check(inspected.map { it.slotId.hex() }.distinct().size == inspected.size)
    for (entry in catalog.entries) {
      val slot =
        inspected.singleOrNull { same(it.slotId, entry.slotId) } ?: throw Phase3FormatException()
      p3Check(
        slot.generation == entry.generation &&
          same(slot.digest, entry.envelopeHash) &&
          slot.slotType == entry.slotType &&
          slot.recoveryState == entry.recoveryState &&
          slot.policyId == entry.policyId
      )
    }
  }

  companion object {
    fun parse(bytes: ByteArray): CredentialProof {
      val r = Phase3Reader(bytes, 96 + 4 + 12 + 32 * 62)
      r.magic("PGAUTH02")
      r.version(2)
      val token = r.id()
      val b = r.hash()
      val c = r.hash()
      r.version(2)
      val strong = r.enum(1..4)
      val auto = r.enum(1..4)
      val length = r.u32()
      p3Check(length <= 12 + 32 * 62 && length <= r.remaining)
      val catalog = CredentialCatalog.parse(r.raw(length.toInt()))
      r.end()
      p3Check(
        catalog.entries.size in 2..3 &&
          catalog.entries.count { it.slotType == 1 && it.recoveryState == 0 } == 1 &&
          catalog.entries.count { it.slotType == 2 && it.recoveryState == 2 } == 1 &&
          catalog.entries.all { it.slotType in 1..2 } &&
          catalog.entries.count { it.recoveryState == 1 } <= 1
      )
      return CredentialProof(r.all(), token, b, c, strong, auto, catalog)
    }
  }
}

internal class HiddenMediaPreview
private constructor(
  bytes: ByteArray,
  private val item: Phase3Bytes,
  val payload: MediaContext,
  val metadataRevision: Long,
  private val jpeg: ByteArray,
) : Phase3Body(bytes) {
  val itemId
    get() = item.copy()

  val jpegBytes
    get() = jpeg.copyOf()

  fun validateBinding(item: HiddenMediaItem) {
    p3Check(
      same(itemId, item.itemId) &&
        payload == item.payload.context &&
        metadataRevision == item.metadataRevision
    )
  }

  companion object {
    fun parse(bytes: ByteArray): HiddenMediaPreview {
      val r = Phase3Reader(bytes, 58 + 2 * 1024 * 1024)
      r.version(2)
      val i = r.id()
      val p = r.context()
      p3Check(p.purpose in setOf(2, 10))
      val rev = r.positive()
      r.version(1)
      val n = r.u32()
      p3Check(n <= 2 * 1024 * 1024 && n <= r.remaining)
      val jpeg = r.raw(n.toInt())
      r.end()
      return HiddenMediaPreview(r.all(), i, p, rev, jpeg)
    }
  }
}
