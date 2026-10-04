package uk.co.traynor.privategallery.core.domain

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.Collections

/** Structural values only. No parsed identifier, hash or body grants authority. */
internal class Phase3FormatException : IllegalArgumentException("Invalid Phase 3 encoding")

internal fun p3Check(value: Boolean) {
  if (!value) throw Phase3FormatException()
}

internal const val P3_INDEX_LIMIT = 16 * 1024 * 1024
internal const val P3_SMALL_LIMIT = 65536
internal const val P3_IMAGE_LIMIT = 48L * 1024 * 1024
internal const val P3_VIDEO_LIMIT = 8L * 1024 * 1024 * 1024

internal abstract class Phase3Body(encoded: ByteArray) {
  private val owned = encoded.copyOf()

  fun encode(): ByteArray = owned.copyOf()

  final override fun equals(other: Any?): Boolean =
    other != null &&
      javaClass == other.javaClass &&
      other is Phase3Body &&
      owned.contentEquals(other.owned)

  final override fun hashCode(): Int = 31 * javaClass.hashCode() + owned.contentHashCode()
}

internal class Phase3Bytes(bytes: ByteArray, size: Int, nonzero: Boolean = false) {
  private val owned: ByteArray

  init {
    p3Check(size in setOf(12, 16, 32) && bytes.size == size)
    owned = bytes.copyOf()
    p3Check(!nonzero || owned.any { it != 0.toByte() })
  }

  fun copy(): ByteArray = owned.copyOf()

  override fun equals(other: Any?): Boolean =
    other is Phase3Bytes && owned.contentEquals(other.owned)

  override fun hashCode(): Int = owned.contentHashCode()
}

internal fun p3Compare(a: ByteArray, b: ByteArray): Int {
  for (i in 0 until minOf(a.size, b.size)) {
    val d = (a[i].toInt() and 255) - (b[i].toInt() and 255)
    if (d != 0) return d
  }
  return a.size - b.size
}

internal fun <T> p3List(values: List<T>): List<T> = Collections.unmodifiableList(ArrayList(values))

internal fun <T> p3Sorted(values: List<T>, key: (T) -> ByteArray) {
  values.zipWithNext().forEach { (a, b) -> p3Check(p3Compare(key(a), key(b)) < 0) }
}

internal class Phase3Reader(bytes: ByteArray, maximum: Int) {
  private val bytes: ByteArray
  var position = 0
    private set

  init {
    p3Check(bytes.size <= maximum)
    this.bytes = bytes.copyOf()
  }

  val remaining
    get() = bytes.size - position

  fun raw(n: Int): ByteArray {
    p3Check(n >= 0 && n <= remaining)
    return bytes.copyOfRange(position, position + n).also { position += n }
  }

  fun all(): ByteArray = bytes.copyOf()

  fun slice(start: Int): ByteArray = bytes.copyOfRange(start, position)

  fun u8(): Int = raw(1)[0].toInt() and 255

  fun u16(): Int = ByteBuffer.wrap(raw(2)).short.toInt() and 65535

  fun u32(): Long = ByteBuffer.wrap(raw(4)).int.toLong() and 0xffffffffL

  fun u64(): Long = ByteBuffer.wrap(raw(8)).long.also { p3Check(it >= 0) }

  fun positive(): Long = u64().also { p3Check(it > 0) }

  fun i64(): Long = ByteBuffer.wrap(raw(8)).long

  fun id(): Phase3Bytes = Phase3Bytes(raw(16), 16, true)

  fun hash(): Phase3Bytes = Phase3Bytes(raw(32), 32)

  fun version(v: Int) {
    p3Check(u16() == v)
  }

  fun magic(s: String) {
    p3Check(raw(8).contentEquals(s.toByteArray(Charsets.US_ASCII)))
  }

  fun enum(range: IntRange): Int = u16().also { p3Check(it in range) }

  fun count16(max: Int, min: Int): Int =
    u16().also { p3Check(it <= max && it.toLong() * min <= remaining) }

  fun count32(max: Int, min: Int): Int =
    u32().also { p3Check(it <= max && it * min <= remaining) }.toInt()

  fun string(nonempty: Boolean = false): String {
    val n = u32()
    p3Check(n <= 4096 && n <= remaining && (!nonempty || n > 0))
    val raw = raw(n.toInt())
    p3Check(
      !(raw.size >= 3 &&
        raw[0] == 0xef.toByte() &&
        raw[1] == 0xbb.toByte() &&
        raw[2] == 0xbf.toByte())
    )
    return try {
      Charsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(raw))
        .toString()
    } catch (_: java.nio.charset.CharacterCodingException) {
      throw Phase3FormatException()
    }
  }

  fun <T> optional(read: () -> T): T? =
    when (u8()) {
      0 -> null
      1 -> read()
      else -> throw Phase3FormatException()
    }

  fun end() {
    p3Check(remaining == 0)
  }

  fun context(): MediaContext = MediaContext.parse(raw(26))

  fun reference(): MediaReference = MediaReference.parse(raw(66))

  fun crop(): MediaCrop = MediaCrop.parse(raw(16))
}

/** Bounded encoder for callers constructing new validated immutable bodies through parse. */
internal class Phase3Writer(private val maximum: Int = P3_SMALL_LIMIT) {
  private val output = ByteArrayOutputStream()
  private val d = DataOutputStream(output)

  private fun room(n: Int) {
    p3Check(n >= 0 && output.size().toLong() + n <= maximum)
  }

  fun raw(v: ByteArray) = apply {
    room(v.size)
    d.write(v)
  }

  fun u8(v: Int) = apply {
    p3Check(v in 0..255)
    room(1)
    d.writeByte(v)
  }

  fun u16(v: Int) = apply {
    p3Check(v in 0..65535)
    room(2)
    d.writeShort(v)
  }

  fun u32(v: Long) = apply {
    p3Check(v in 0..0xffffffffL)
    room(4)
    d.writeInt(v.toInt())
  }

  fun u64(v: Long) = apply {
    p3Check(v >= 0)
    room(8)
    d.writeLong(v)
  }

  fun i64(v: Long) = apply {
    room(8)
    d.writeLong(v)
  }

  fun string(v: String) = apply {
    p3Check(v.length <= 4096 && !v.startsWith("\uFEFF"))
    val raw =
      try {
        val b =
          Charsets.UTF_8.newEncoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .encode(java.nio.CharBuffer.wrap(v))
        ByteArray(b.remaining()).also { b.get(it) }
      } catch (_: java.nio.charset.CharacterCodingException) {
        throw Phase3FormatException()
      }
    p3Check(raw.size <= 4096)
    u32(raw.size.toLong())
    raw(raw)
  }

  fun encode(): ByteArray = output.toByteArray()
}

internal class MediaContext
private constructor(
  bytes: ByteArray,
  val purpose: Int,
  private val id: Phase3Bytes,
  val generation: Long,
) : Phase3Body(bytes) {
  val objectId
    get() = id.copy()

  private constructor(
    value: MediaContext
  ) : this(value.encode(), value.purpose, value.id, value.generation)

  constructor(
    purpose: Int,
    objectId: ByteArray,
    generation: Long,
  ) : this(parse(Phase3Writer(26).u16(purpose).raw(objectId).u64(generation).encode()))

  companion object {
    fun parse(bytes: ByteArray): MediaContext {
      val r = Phase3Reader(bytes, 26)
      val p = r.enum(1..11)
      val id = r.id()
      val g = r.positive()
      r.end()
      return MediaContext(r.all(), p, id, g)
    }
  }
}

internal class MediaReference
private constructor(
  bytes: ByteArray,
  val context: MediaContext,
  val ciphertextLength: Long,
  private val sha: Phase3Bytes,
) : Phase3Body(bytes) {
  val hash
    get() = sha.copy()

  private constructor(
    value: MediaReference
  ) : this(value.encode(), value.context, value.ciphertextLength, value.sha)

  constructor(
    context: MediaContext,
    ciphertextLength: Long,
    hash: ByteArray,
  ) : this(parse(Phase3Writer(66).raw(context.encode()).u64(ciphertextLength).raw(hash).encode()))

  companion object {
    fun parse(bytes: ByteArray): MediaReference {
      val r = Phase3Reader(bytes, 66)
      val c = r.context()
      val l = r.u64()
      p3Check(l >= 172)
      val h = r.hash()
      r.end()
      return MediaReference(r.all(), c, l, h)
    }
  }
}

internal class MediaCrop
private constructor(
  bytes: ByteArray,
  val left: Float,
  val top: Float,
  val right: Float,
  val bottom: Float,
) : Phase3Body(bytes) {
  companion object {
    fun parse(bytes: ByteArray): MediaCrop {
      val r = Phase3Reader(bytes, 16)
      val b = ByteBuffer.wrap(r.raw(16))
      val v = List(4) { b.float }
      r.end()
      p3Check(v.all { it.isFinite() && it.toRawBits() != Int.MIN_VALUE })
      p3Check(v[0] >= 0 && v[0] < v[2] && v[2] <= 1 && v[1] >= 0 && v[1] < v[3] && v[3] <= 1)
      return MediaCrop(r.all(), v[0], v[1], v[2], v[3])
    }
  }
}

internal class MediaUsage
private constructor(
  bytes: ByteArray,
  val encryptionInvocations: Long,
  val ghashBlocks: Long,
  val chargedQueries: Long,
  val chargeSequence: Long,
  private val sha: Phase3Bytes,
  private val attempt: Phase3Bytes,
) : Phase3Body(bytes) {
  val ciphertextHash
    get() = sha.copy()

  val attemptId
    get() = attempt.copy()

  companion object {
    fun parse(bytes: ByteArray): MediaUsage {
      val r = Phase3Reader(bytes, 82)
      r.version(2)
      val i = r.u64()
      val b = r.u64()
      val q = r.u64()
      val s = r.positive()
      val h = r.hash()
      val a = r.id()
      r.end()
      p3Check(i <= 1L.shl(20) && b <= 1L.shl(32) && q <= 1L.shl(20))
      return MediaUsage(r.all(), i, b, q, s, h, a)
    }
  }
}

internal class HiddenMediaSelector
private constructor(bytes: ByteArray, private val token: Phase3Bytes) : Phase3Body(bytes) {
  val attemptId
    get() = token.copy()

  companion object {
    fun parse(bytes: ByteArray): HiddenMediaSelector {
      val r = Phase3Reader(bytes, 26)
      r.magic("PGDOMP01")
      r.version(1)
      val t = r.id()
      r.end()
      return HiddenMediaSelector(r.all(), t)
    }
  }
}

internal class PrimaryTransferSelector
private constructor(bytes: ByteArray, private val token: Phase3Bytes) : Phase3Body(bytes) {
  val tokenId
    get() = token.copy()

  companion object {
    fun parse(bytes: ByteArray): PrimaryTransferSelector {
      val r = Phase3Reader(bytes, 26)
      r.magic("PGTRP001")
      r.version(1)
      val t = r.id()
      r.end()
      return PrimaryTransferSelector(r.all(), t)
    }
  }
}
