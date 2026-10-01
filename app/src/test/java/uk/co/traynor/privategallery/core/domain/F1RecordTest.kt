package uk.co.traynor.privategallery.core.domain

import java.nio.ByteBuffer
import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Test

class F1RecordTest {
  private val master = ByteArray(32) { it.toByte() }
  private val identity = DomainIdentity(ByteArray(16) { it.toByte() }, ByteArray(16) { (it + 16).toByte() })
  private fun context(purpose: Int = 2, generation: Long = 7) = F1Context(identity, purpose, ByteArray(16) { (it + 64).toByte() }, generation)

  @Test fun `frozen whole headers info and HKDF keys match unchanged fixture`() {
    val fixture = fixture()
    val salt = ByteArray(32) { (it + 32).toByte() }
    val nonce = ByteArray(12) { (it + 96).toByte() }
    val vectors = Regex("\\\"purpose\\\": (\\d+),\\s*\\\"info\\\": \\\"([0-9a-f]+)\\\",\\s*\\\"key\\\": \\\"([0-9a-f]+)\\\",\\s*\\\"wholeHeader\\\": (?:\\\"([0-9a-f]+)\\\"|null)").findAll(fixture).toList()
    assertEquals(11, vectors.size)
    vectors.forEach {
      val purpose = it.groupValues[1].toInt()
      val ctx = context(purpose)
      assertArrayEquals(hex(it.groupValues[2]), F1Record.keyInfo(ctx))
      assertArrayEquals(hex(it.groupValues[3]), F1Record.deriveKey(master, salt, ctx))
      if (purpose <= 9) assertArrayEquals(hex(it.groupValues[4]), F1Record.wholeHeader(ctx, salt, nonce, 0))
    }
    val digest = MessageDigest.getInstance("SHA-256").digest(fixture.toByteArray())
    assertArrayEquals(hex("ff70ba6e104e4a4c219a9b80e49e39015022139309f525d4617dafc96d1edb8c"), digest)
  }

  @Test fun `RFC 5869 extract expand known answer`() {
    val prk = F1Crypto.extract(hex("000102030405060708090a0b0c"), ByteArray(22) { 0x0b })
    assertArrayEquals(hex("077709362c2e32df0ddc3f0dc47bba6390b6c73bb50f9c3122ec844ad7c2b3e5"), prk)
    assertArrayEquals(hex("3cb25f25faacd57a90434f64d0362f2a2d2d0a90cf1a5a4c5db02d56ecc4c5bf34007208d5b887185865"), F1Crypto.expand(prk, hex("f0f1f2f3f4f5f6f7f8f9"), 42))
  }

  @Test fun `synthetic payload digest roundtrip every supported whole purpose`() {
    val body = ByteArray(65536) { ((it * 17) xor (it ushr 8)).toByte() }
    for (purpose in 1..9) {
      val encoded = F1Record.encrypt(master, context(purpose), body)
      assertEquals(156 + body.size + 16, encoded.size)
      val decoded = F1Record.decrypt(master, context(purpose), encoded)
      assertArrayEquals(MessageDigest.getInstance("SHA-256").digest(body), MessageDigest.getInstance("SHA-256").digest(decoded))
      assertArrayEquals(body, decoded)
    }
    assertArrayEquals(byteArrayOf(), F1Record.decrypt(master, context(), F1Record.encrypt(master, context(), byteArrayOf())))
  }

  @Test fun `wrong key or every expected context component fails closed`() {
    val encoded = F1Record.encrypt(master, context(), byteArrayOf(1, 2, 3))
    rejects { F1Record.decrypt(master.copyOf().also { it[0] = 99 }, context(), encoded) }
    val contexts = listOf(
      F1Context(DomainIdentity(ByteArray(16) { 9 }, identity.master), 2, context().objectId, 7),
      F1Context(DomainIdentity(identity.container, ByteArray(16) { 9 }), 2, context().objectId, 7),
      context(3), context(generation = 8), F1Context(identity, 2, ByteArray(16) { 9 }, 7)
    )
    contexts.forEach { rejects { F1Record.decrypt(master, it, encoded) } }
  }

  @Test fun `every header body and tag byte is authenticated`() {
    val encoded = F1Record.encrypt(master, context(), byteArrayOf(3, 1, 4))
    encoded.indices.forEach { offset ->
      rejects { F1Record.decrypt(master, context(), encoded.copyOf().also { it[offset] = (it[offset].toInt() xor 1).toByte() }) }
    }
  }

  @Test fun `reject length overflow negative generation chunk fields and physical mismatch`() {
    val encoded = F1Record.encrypt(master, context(), byteArrayOf())
    listOf(64, 116, 124, 132).forEach { offset ->
      rejects { F1Record.decrypt(master, context(), encoded.copyOf().also { ByteBuffer.wrap(it).putLong(offset, Long.MIN_VALUE) }) }
    }
    listOf(140, 144, 148, 152).forEach { offset ->
      rejects { F1Record.decrypt(master, context(), encoded.copyOf().also { ByteBuffer.wrap(it).putInt(offset, 1) }) }
    }
    rejects { F1Record.decrypt(master, context(), encoded.copyOf(encoded.size - 1)) }
    rejects { F1Record.decrypt(master, context(), encoded + 0) }
    rejects { F1Record.decrypt(master, context(), byteArrayOf()) }
    assertEquals(F1Failure.UNSUPPORTED, caught { F1Record.decrypt(master, context(), encoded.copyOf().also { it[9] = 2 }) }.failure)
  }

  @Test fun `purpose limits checked before ciphertext allocation and video unavailable`() {
    assertEquals(64L * 1024 * 1024, F1Record.maximumBodyLength(2))
    for (purpose in listOf(1, 8)) assertEquals(16L * 1024 * 1024, F1Record.maximumBodyLength(purpose))
    for (purpose in listOf(5, 7, 9)) assertEquals(65536L, F1Record.maximumBodyLength(purpose))
    for (purpose in listOf(0, 10, 11, 65535)) rejects { F1Record.encrypt(master, context(purpose), byteArrayOf()) }
    for (purpose in listOf(5, 7, 9)) rejects { F1Record.encrypt(master, context(purpose), ByteArray(65537)) }
    for (purpose in 1..9) {
      val maximum = F1Record.maximumBodyLength(purpose)
      val header = ByteBuffer.wrap(F1Record.wholeHeader(context(purpose), ByteArray(32), ByteArray(12), maximum))
      assertEquals(maximum, header.getLong(116)); assertEquals(maximum + 16, header.getLong(124))
      rejects { F1Record.wholeHeader(context(purpose), ByteArray(32), ByteArray(12), maximum + 1) }
      val encoded = F1Record.encrypt(master, context(purpose), byteArrayOf())
      rejects { F1Record.decrypt(master, context(purpose), encoded.copyOf().also { ByteBuffer.wrap(it).putLong(116, maximum + 1) }) }
    }
    rejects { F1Record.wholeHeader(context(), ByteArray(32), ByteArray(12), Long.MAX_VALUE) }
    rejects { F1Context(identity, 2, ByteArray(16), 0) }
    rejects { F1Record.encrypt(ByteArray(31), context(), byteArrayOf()) }
  }

  @Test fun `fresh writes randomize salt nonce and immutable identities resist caller mutation`() {
    val container = ByteArray(16) { it.toByte() }
    val identity = DomainIdentity(container, ByteArray(16))
    val objectId = ByteArray(16)
    val ctx = F1Context(identity, 2, objectId, Long.MAX_VALUE)
    container.fill(99); objectId.fill(99); identity.container.fill(99); ctx.objectId.fill(99)
    assertEquals(0, identity.container[0].toInt()); assertEquals(0, ctx.objectId[0].toInt())
    val first = F1Record.encrypt(master, ctx, byteArrayOf(4))
    val second = F1Record.encrypt(master, ctx, byteArrayOf(4))
    assertFalse(first.copyOfRange(72, 104).contentEquals(second.copyOfRange(72, 104)))
    assertFalse(first.copyOfRange(104, 116).contentEquals(second.copyOfRange(104, 116)))
    assertArrayEquals(master, ByteArray(32) { it.toByte() })
  }

  companion object {
    internal fun fixture() = requireNotNull(F1RecordTest::class.java.getResourceAsStream("/phase0/future-format-v1-vectors.json")).bufferedReader().use { it.readText() }
    internal fun hex(value: String) = value.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    internal fun caught(block: () -> Unit): F1Exception {
      try { block() } catch (e: F1Exception) { return e }
      throw AssertionError("Expected fixed-category F1 rejection")
    }
    internal fun rejects(block: () -> Unit) { caught(block) }
  }
}
