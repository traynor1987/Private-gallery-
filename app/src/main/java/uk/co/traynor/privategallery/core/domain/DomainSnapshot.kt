package uk.co.traynor.privategallery.core.domain

import java.nio.ByteBuffer
import java.security.MessageDigest

internal fun ByteArray.hex(): String = joinToString("") { "%02x".format(it.toInt() and 255) }
internal fun digest(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)
internal fun same(a: ByteArray, b: ByteArray): Boolean = MessageDigest.isEqual(a, b)

class AuthenticatedDomain internal constructor(
  val identity: DomainIdentity,
  val confirmed: Boolean,
  val strongAuthInterval: StrongAuthInterval,
  val autoLock: SecondaryAutoLock,
  master: ByteArray,
) : AutoCloseable {
  val containerId = uk.co.traynor.privategallery.core.security.ContainerId.SECONDARY
  private var owned: ByteArray? = master
  @Synchronized fun takeMaster(): ByteArray = (owned ?: throw SecondaryStoreException()).also { owned = null }
  @Synchronized override fun close() { owned?.fill(0); owned = null }
}

class PendingSetup internal constructor(
  internal val owner: SecondaryStore,
  val identity: DomainIdentity,
  internal val selection: String,
  internal val replacement: Boolean,
  master: ByteArray,
  secret: ByteArray,
) : AutoCloseable {
  val containerId = uk.co.traynor.privategallery.core.security.ContainerId.SECONDARY
  private var ownedMaster: ByteArray? = master
  private var ownedSecret: ByteArray? = secret
  val recoverySecret: ByteArray @Synchronized get() = (ownedSecret ?: throw SecondaryStoreException()).copyOf()
  /** One-time UI ownership transfer; possession confirmation uses the authenticated slot. */
  @Synchronized internal fun takeRecoverySecret(): ByteArray = (ownedSecret ?: throw SecondaryStoreException()).also { ownedSecret = null }
  @Synchronized internal fun discardRecoverySecret() { ownedSecret?.fill(0); ownedSecret = null }
  @Synchronized internal fun masterCopy(): ByteArray = (ownedMaster ?: throw SecondaryStoreException()).copyOf()
  @Synchronized override fun close() { ownedMaster?.fill(0); ownedMaster = null; ownedSecret?.fill(0); ownedSecret = null }
}

/** A separate versioned device-envelope extension, never F1 slot type 3/4. */
class SecondaryBiometricRecord internal constructor(val identity: DomainIdentity, slotId: ByteArray, val generation: Long, envelope: ByteArray) {
  private val id = slotId.copyOf(); private val bytes = envelope.copyOf()
  val slotId: ByteArray get() = id.copyOf()
  val envelope: ByteArray get() = bytes.copyOf()
}

internal class DomainSnapshot(
  val token: String,
  val identity: DomainIdentity,
  val generation: Long,
  val confirmed: Boolean,
  val descriptorId: ByteArray,
  val indexId: ByteArray,
  val catalogId: ByteArray,
  val bootstrap: ByteArray,
  val descriptor: ByteArray,
  val index: ByteArray,
  val catalog: ByteArray,
  val pin: ByteArray,
  val recovery: ByteArray,
  val pendingRecovery: ByteArray?,
  val biometric: ByteArray?,
) {
  fun context(purpose: Int): F1Context = F1Context(identity, purpose, when (purpose) { 9 -> descriptorId; 8 -> catalogId; 1 -> indexId; else -> throw SecondaryStoreException() }, generation)
}

internal data class VerifiedDescriptor(
  val strong: StrongAuthInterval,
  val autoLock: SecondaryAutoLock,
  val biometricId: ByteArray?,
  val biometricGeneration: Long,
)

internal object DomainEncoding {
  private fun strongCode(value: StrongAuthInterval): Int = when (value) { StrongAuthInterval.EVERY_TIME -> 1; StrongAuthInterval.DAY -> 2; StrongAuthInterval.THREE_DAYS -> 3; StrongAuthInterval.SEVEN_DAYS -> 4 }
  private fun autoCode(value: SecondaryAutoLock): Int = when (value) { SecondaryAutoLock.IMMEDIATE -> 1; SecondaryAutoLock.THIRTY_SECONDS -> 2; SecondaryAutoLock.ONE_MINUTE -> 3; SecondaryAutoLock.FIVE_MINUTES -> 4 }
  private fun parseStrong(value: Int): StrongAuthInterval = when (value) { 1 -> StrongAuthInterval.EVERY_TIME; 2 -> StrongAuthInterval.DAY; 3 -> StrongAuthInterval.THREE_DAYS; 4 -> StrongAuthInterval.SEVEN_DAYS; else -> throw SecondaryStoreException() }
  private fun parseAuto(value: Int): SecondaryAutoLock = when (value) { 1 -> SecondaryAutoLock.IMMEDIATE; 2 -> SecondaryAutoLock.THIRTY_SECONDS; 3 -> SecondaryAutoLock.ONE_MINUTE; 4 -> SecondaryAutoLock.FIVE_MINUTES; else -> throw SecondaryStoreException() }
  private val bootstrapMagic = "PGDOMB01".toByteArray(Charsets.US_ASCII)
  private val descriptorMagic = "PGDOMD01".toByteArray(Charsets.US_ASCII)
  val emptyIndex = ByteBuffer.allocate(12).putShort(1).putLong(0).putShort(0).array()
  const val BOOTSTRAP_SIZE = 104
  fun bootstrap(identity: DomainIdentity, generation: Long, confirmed: Boolean, ids: List<ByteArray>): ByteArray =
    ByteBuffer.allocate(BOOTSTRAP_SIZE).put(bootstrapMagic).putShort(1).put(identity.container).put(identity.master)
      .putLong(generation).putShort(if (confirmed) 2 else 1).put(ids[0]).put(ids[1]).put(ids[2]).putInt(0).array()
  fun parseBootstrap(bytes: ByteArray): Triple<DomainIdentity, Long, Boolean> {
    storeCheck(bytes.size == BOOTSTRAP_SIZE); val b = ByteBuffer.wrap(bytes)
    storeCheck(bytes.copyOfRange(0, 8).contentEquals(bootstrapMagic) && b.getShort(8).toInt() == 1)
    storeCheck(b.getLong(42) > 0 && b.getShort(50).toInt() in 1..2 && b.getInt(100) == 0)
    return Triple(DomainIdentity(bytes.copyOfRange(10, 26), bytes.copyOfRange(26, 42)), b.getLong(42), b.getShort(50).toInt() == 2)
  }
  fun catalog(identity: DomainIdentity, generation: Long, slots: List<ByteArray>): ByteArray {
    val entries = slots.map { F1Slot.inspect(identity, it) }.sortedBy { it.slotId.hex() }
    storeCheck(entries.size in 2..3 && entries.map { it.slotId.hex() }.distinct().size == entries.size)
    return ByteBuffer.allocate(12 + entries.size * 62).putShort(1).putLong(generation).putShort(entries.size.toShort()).apply {
      entries.forEach { put(it.slotId).putLong(it.generation).put(it.digest).putShort(it.slotType.toShort()).putShort(it.recoveryState.toShort()).putShort(it.policyId.toShort()) }
    }.array()
  }
  fun descriptor(snapshot: DomainSnapshot, strong: StrongAuthInterval, auto: SecondaryAutoLock, bioId: ByteArray?, bioGeneration: Long): ByteArray =
    ByteBuffer.allocate(170).put(descriptorMagic).putShort(1).putShort(if (snapshot.confirmed) 2 else 1)
      .putShort(strongCode(strong).toShort()).putShort(autoCode(auto).toShort())
      .put(digest(snapshot.bootstrap)).put(digest(snapshot.index)).put(digest(snapshot.catalog))
      .putShort(if (bioId == null) 0 else 1).put(bioId ?: ByteArray(16)).putLong(bioGeneration)
      .put(snapshot.biometric?.let(::digest) ?: ByteArray(32)).array()
  fun verifyDescriptor(snapshot: DomainSnapshot, body: ByteArray): VerifiedDescriptor {
    storeCheck(body.size == 170); val b = ByteBuffer.wrap(body)
    storeCheck(body.copyOfRange(0, 8).contentEquals(descriptorMagic) && b.getShort(8).toInt() == 1)
    storeCheck(b.getShort(10).toInt() == if (snapshot.confirmed) 2 else 1)
    val strong = b.getShort(12).toInt(); val auto = b.getShort(14).toInt()
    storeCheck(strong in 1..4 && auto in 1..4)
    storeCheck(same(body.copyOfRange(16, 48), digest(snapshot.bootstrap)) && same(body.copyOfRange(48, 80), digest(snapshot.index)) && same(body.copyOfRange(80, 112), digest(snapshot.catalog)))
    val bio = b.getShort(112).toInt(); storeCheck(bio in 0..1)
    val id = body.copyOfRange(114, 130); val generation = b.getLong(130); val hash = body.copyOfRange(138, 170)
    if (bio == 0) storeCheck(id.all { it == 0.toByte() } && generation == 0L && hash.all { it == 0.toByte() } && snapshot.biometric == null)
    else storeCheck(generation > 0 && snapshot.biometric != null && same(hash, digest(snapshot.biometric)))
    return VerifiedDescriptor(parseStrong(strong), parseAuto(auto), if (bio == 1) id else null, generation)
  }
}
