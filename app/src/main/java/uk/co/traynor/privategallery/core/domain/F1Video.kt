package uk.co.traynor.privategallery.core.domain

import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.security.MessageDigest
import javax.crypto.Cipher

/** Frozen F1-r2 streaming records. Accounting is concrete and mandatory on every entry point. */
internal object F1Video {
  const val CHUNK_SIZE = 1048576
  const val MAX_LENGTH = 8L * 1024 * 1024 * 1024
  internal data class Plan(val chunks: Int, val chunkBlocks: Long, val ciphertextLength: Long)
  internal fun plan(length: Long): Plan {
    f1Check(length in 0..MAX_LENGTH)
    val count = ((length + CHUNK_SIZE - 1) / CHUNK_SIZE).toInt()
    val full = length / CHUNK_SIZE
    val last = length % CHUNK_SIZE
    val blocks = full * 65547L + if (last == 0L) 0 else 11 + (last + 15) / 16
    return Plan(count, blocks, Math.addExact(Math.addExact(length, 172L), count * 172L))
  }

  private fun header(store: MediaUsageStore, context: MediaContext, salt: ByteArray, nonce: ByteArray, total: Long, index: Int): ByteArray {
    val plan = plan(total)
    val size = if (index == -1) 0 else minOf(CHUNK_SIZE.toLong(), total - index.toLong() * CHUNK_SIZE).toInt()
    return ByteBuffer.allocate(156).put("PGFUTR01".toByteArray(Charsets.US_ASCII)).putShort(1).putShort(156).putShort(1)
      .putShort((if (index == -1) 10 else 11).toShort()).put(store.identity.container).put(store.identity.master)
      .put(context.objectId).putLong(context.generation).put(salt).put(nonce).putLong(size.toLong()).putLong(size + 16L)
      .putLong(total).putInt(index).putInt(plan.chunks).putInt(CHUNK_SIZE).putInt(size).array()
  }

  private fun chunkDomain(header: ByteArray): ByteArray = header.copyOf().also { ByteBuffer.wrap(it).putShort(14, 11) }

  fun write(master: ByteArray, context: MediaContext, input: InputStream, length: Long, expectedSha: ByteArray, output: OutputStream,
    usageStore: MediaUsageStore, attemptID: ByteArray, checkValid: () -> Unit): MediaReference = usageStore.withMaster(master) { owned ->
    val expected = expectedSha.copyOf()
    try { writeBound(owned,context,input,length,expected,output,usageStore,attemptID,checkValid) } finally { expected.fill(0) }
  }


  private fun writeBound(master: ByteArray, context: MediaContext, input: InputStream, length: Long, expectedSha: ByteArray, output: OutputStream,
    usageStore: MediaUsageStore, attemptID: ByteArray, checkValid: () -> Unit): MediaReference {
    usageStore.validMaster(master)
    f1Check(master.size == 32 && context.purpose == 10 && expectedSha.size == 32)
    val plan = plan(length)
    val ownedOutput = output as? OwnedMediaOutput ?: throw SecondaryStoreException()
    ownedOutput.checkForWrite(usageStore,context,attemptID)
    usageStore.valid(checkValid, true)
    usageStore.requireOwner(master, attemptID, context, checkValid)
    val salt = F1Crypto.random(32)
    val first = header(usageStore, context, salt, F1Crypto.random(12), length, -1)
    val domain = chunkDomain(first)
    val owner = ownedOutput
    val permit = usageStore.reserveEncryption(listOf(first, domain), listOf(1L, plan.chunks.toLong()), listOf(11L, plan.chunkBlocks), attemptID, owner, checkValid)
    var buffer = byteArrayOf()
    var headerKey = byteArrayOf()
    var chunksKey = byteArrayOf()
    try {
      ownedOutput.claim(usageStore,context,attemptID,permit,plan.ciphertextLength,first)
      val plainHash = MessageDigest.getInstance("SHA-256")
      val cipherHash = MessageDigest.getInstance("SHA-256")
      buffer = ByteArray(CHUNK_SIZE)
      headerKey = F1Record.deriveKey(master, salt, usageStore.context(context))
      chunksKey = F1Record.deriveKey(master, salt, F1Context(usageStore.identity, 11, context.objectId, context.generation))
      fun emit(bytes: ByteArray) { usageStore.valid(checkValid, true); output.write(bytes); cipherHash.update(bytes) }
      permit.consume(usageStore, owner, checkValid, first)
      emit(first)
      emit(F1Crypto.aead(Cipher.ENCRYPT_MODE, headerKey, first.copyOfRange(104, 116), first, byteArrayOf()))
      for (index in 0 until plan.chunks) {
        val needed = minOf(CHUNK_SIZE.toLong(), length - index.toLong() * CHUNK_SIZE).toInt()
        var offset = 0
        while (offset < needed) {
          usageStore.valid(checkValid, true)
          val got = input.read(buffer, offset, needed - offset)
          f1Check(got > 0)
          offset += got
        }
        plainHash.update(buffer, 0, needed)
        val h = header(usageStore, context, salt, F1Crypto.random(12), length, index)
        permit.consume(usageStore, owner, checkValid, h)
        val encrypted = F1Crypto.aead(Cipher.ENCRYPT_MODE, chunksKey, h.copyOfRange(104, 116), h, buffer, 0, needed)
        try { emit(h); emit(encrypted) } finally { encrypted.fill(0); buffer.fill(0) }
      }
      usageStore.valid(checkValid, true)
      f1Check(input.read() == -1 && MessageDigest.isEqual(plainHash.digest(), expectedSha))
      val sha = cipherHash.digest()
      val completion = ownedOutput.finish(sha)
      usageStore.complete(listOf(first, domain), completion, permit, checkValid)
      usageStore.valid(checkValid, true)
      return MediaReference(context, plan.ciphertextLength, sha)
    } finally { permit.close(); buffer.fill(0); headerKey.fill(0); chunksKey.fill(0); salt.fill(0) }
  }

  private class Framing(val header: ByteArray, val total: Long, val plan: Plan)
  private fun framing(context: MediaContext, input: OwnedMediaCiphertext, store: MediaUsageStore): Framing {
    f1Check(context.purpose == 10 && input.belongsTo(store))
    val h = input.read(0, 156)
    validateContext(h, context, store)
    f1Check(input.reference.context == context)
    val b = ByteBuffer.wrap(h)
    f1Check(b.getLong(116) == 0L && b.getLong(124) == 16L && b.getInt(140) == -1 && b.getInt(152) == 0)
    val total = b.getLong(132)
    val p = plan(total)
    f1Check(b.getInt(144) == p.chunks && b.getInt(148) == CHUNK_SIZE && input.reference.ciphertextLength == p.ciphertextLength)
    return Framing(h, total, p)
  }

  private fun validateContext(header: ByteArray, context: MediaContext, store: MediaUsageStore) {
    f1Check(header.size == 156 && header.copyOfRange(0,8).contentEquals("PGFUTR01".toByteArray(Charsets.US_ASCII)))
    val b = ByteBuffer.wrap(header)
    if (F1Crypto.u16(b, 8) != 1) throw F1Exception(F1Failure.UNSUPPORTED)
    f1Check(F1Crypto.u16(b, 10) == 156 && F1Crypto.u16(b, 12) == 1 && F1Crypto.u16(b, 14) == context.purpose)
    f1Check(store.identity.matches(header.copyOfRange(16,32), header.copyOfRange(32,48)) && header.copyOfRange(48,64).contentEquals(context.objectId) && b.getLong(64) == context.generation)
  }

  fun open(master: ByteArray, context: MediaContext, input: OwnedMediaCiphertext, usageStore: MediaUsageStore, checkValid: () -> Unit): Reader = usageStore.withMaster(master) { owned -> openBound(owned,context,input,usageStore,checkValid) }


  private fun openBound(master: ByteArray, context: MediaContext, input: OwnedMediaCiphertext, usageStore: MediaUsageStore, checkValid: () -> Unit): Reader {
    usageStore.validMaster(master)
    usageStore.valid(checkValid)
    val framing = framing(context, input, usageStore)
    val reader = Reader(master, context, input, usageStore, checkValid, framing.header, framing.total, framing.plan)
    try {
      usageStore.precharge(listOf(framing.header), listOf(1), input.reference.hash, reader, checkValid).use { lease -> reader.authenticateHeader(lease) }
      return reader
    } catch (failure: Throwable) { reader.close(); throw failure }
  }

  internal class VerifiedVideo(private val length: Long, plain: ByteArray, cipher: ByteArray) {
    private val p = plain.copyOf(); private val c = cipher.copyOf()
    val plaintextLength: Long get() = length
    val plaintextHash: ByteArray get() = p.copyOf()
    val ciphertextHash: ByteArray get() = c.copyOf()
  }

  fun verify(master: ByteArray, context: MediaContext, input: OwnedMediaCiphertext, length: Long, expectedSha: ByteArray,
    usageStore: MediaUsageStore, checkValid: () -> Unit): VerifiedVideo = usageStore.withMaster(master) { owned ->
    val expected = expectedSha.copyOf()
    try { verifyBound(owned,context,input,length,expected,usageStore,checkValid) } finally { expected.fill(0) }
  }


  private fun verifyBound(master: ByteArray, context: MediaContext, input: OwnedMediaCiphertext, length: Long, expectedSha: ByteArray,
    usageStore: MediaUsageStore, checkValid: () -> Unit): VerifiedVideo {
    usageStore.validMaster(master)
    usageStore.valid(checkValid)
    val framing = framing(context, input, usageStore)
    val reader = Reader(master, context, input, usageStore, checkValid, framing.header, framing.total, framing.plan)
    return reader.use { it.verifyAll(length, expectedSha) }
  }

  internal class Reader internal constructor(master: ByteArray, private val context: MediaContext, private val input: OwnedMediaCiphertext,
    private val store: MediaUsageStore, private val checkValid: () -> Unit, header: ByteArray, val plaintextLength: Long, private val plan: Plan) : AutoCloseable {
    private val master = store.withMaster(master) { it.copyOf() }
    private val header = header.copyOf()
    @Volatile private var headerAuthenticated = false
    @Volatile private var closed = false
    init {
      try {
        val original=framing(context,input,store)
        f1Check(this.header.contentEquals(original.header) && plaintextLength==original.total && plan==original.plan)
      } catch(failure: Throwable) { this.master.fill(0); this.header.fill(0); throw failure }
    }
    private fun valid() { f1Check(!closed); store.valid(checkValid); input.checkBinding() }
    internal fun authenticateHeader(lease: MediaUsageStore.Permit) = synchronized(input) {
      headerAuthenticated=false
      valid()
      lease.consume(store, this, checkValid, header)
      val key = F1Record.deriveKey(master, header.copyOfRange(72, 104), store.context(context))
      try { F1Crypto.aead(Cipher.DECRYPT_MODE, key, header.copyOfRange(104, 116), header, input.read(156, 16)).fill(0) } finally { key.fill(0) }
      valid()
      headerAuthenticated=true
    }
    fun readChunk(index: Int): ByteArray {
      f1Check(!closed && headerAuthenticated && index in 0 until plan.chunks)
      val domain = chunkDomain(header)
      // Root4 charging precedes the one reader5 mutex. No root callback from crypto/read/close.
      val lease = store.precharge(listOf(domain), listOf(1L), input.reference.hash, this, checkValid)
      return lease.use { synchronized(input) { chunk(index, lease) } }
    }
    private fun chunk(index: Int, lease: MediaUsageStore.Permit, cipherHash: MessageDigest? = null): ByteArray {
      valid()
      f1Check(headerAuthenticated)
      val offset = 172L + index.toLong() * (CHUNK_SIZE + 172L)
      val length = minOf(CHUNK_SIZE.toLong(), plaintextLength - index.toLong() * CHUNK_SIZE).toInt()
      val h = input.read(offset, 156)
      validateContext(h, MediaContext(11, context.objectId, context.generation), store)
      val b = ByteBuffer.wrap(h)
      f1Check(h.copyOfRange(72,104).contentEquals(header.copyOfRange(72,104)) && b.getLong(116) == length.toLong() && b.getLong(124) == length + 16L && b.getLong(132) == plaintextLength && b.getInt(140) == index && b.getInt(144) == plan.chunks && b.getInt(148) == CHUNK_SIZE && b.getInt(152) == length)
      val encrypted = input.read(offset + 156, length + 16)
      cipherHash?.update(h); cipherHash?.update(encrypted)
      lease.consume(store, this, checkValid, h)
      val key = F1Record.deriveKey(master, header.copyOfRange(72, 104), F1Context(store.identity, 11, context.objectId, context.generation))
      return try {
        val plaintext = F1Crypto.aead(Cipher.DECRYPT_MODE, key, h.copyOfRange(104, 116), h, encrypted)
        try { valid(); plaintext } catch (failure: Throwable) { plaintext.fill(0); throw failure }
      } finally { key.fill(0); encrypted.fill(0) }
    }
    fun verifyAll(length: Long, expectedSha: ByteArray): VerifiedVideo {
      val expected=expectedSha.copyOf()
      return try { verifyAllBound(length,expected) } finally { expected.fill(0) }
    }
    private fun verifyAllBound(length: Long, expectedSha: ByteArray): VerifiedVideo {
      f1Check(!closed && length == plaintextLength && expectedSha.size == 32)
      val lease = store.precharge(listOf(header, chunkDomain(header)), listOf(1, plan.chunks.toLong()), input.reference.hash, this, checkValid)
      return lease.use { synchronized(input) {
        authenticateHeader(lease)
        val plain = MessageDigest.getInstance("SHA-256")
        val cipher = MessageDigest.getInstance("SHA-256")
        cipher.update(header); cipher.update(input.read(156, 16))
        for (i in 0 until plan.chunks) { val bytes = chunk(i, lease, cipher); try { plain.update(bytes) } finally { bytes.fill(0) } }
        valid()
        val p = plain.digest()
        val c = cipher.digest()
        f1Check(MessageDigest.isEqual(p, expectedSha) && MessageDigest.isEqual(c, input.reference.hash))
        VerifiedVideo(plaintextLength, p, c)
      } }
    }
    override fun close() = synchronized(input) { if (!closed) { closed = true; headerAuthenticated=false; master.fill(0); header.fill(0); input.close() } }
  }
}

/** Charged whole records for schema2 data; the legacy uncharged primitive API is unchanged. */
internal object F1ChargedRecord {
  fun seal(master: ByteArray, context: MediaContext, plaintext: ByteArray, store: MediaUsageStore, attemptID: ByteArray, checkValid: () -> Unit): ByteArray = store.withMaster(master) { owned ->
    f1Check(plaintext.size.toLong() <= store.maximumBodyLength(context.purpose))
    val body = plaintext.copyOf()
    try { sealBound(owned,context,body,store,attemptID,checkValid) } finally { body.fill(0) }
  }


  private fun sealBound(master: ByteArray, context: MediaContext, plaintext: ByteArray, store: MediaUsageStore, attemptID: ByteArray, checkValid: () -> Unit): ByteArray {
    store.requireOwner(master, attemptID, context, checkValid)
    return store.openOutput(master,context,attemptID,checkValid).use { sealOwned(master,context,plaintext,store,attemptID,it,checkValid) }
  }
  internal fun sealOwned(master: ByteArray, context: MediaContext, plaintext: ByteArray, store: MediaUsageStore, attemptID: ByteArray, output: OwnedMediaOutput, checkValid: () -> Unit): ByteArray = store.withMaster(master) { owned ->
    f1Check(plaintext.size.toLong() <= store.maximumBodyLength(context.purpose))
    val body = plaintext.copyOf()
    try { sealOwnedBound(owned,context,body,store,attemptID,output,checkValid) } finally { body.fill(0) }
  }


  private fun sealOwnedBound(master: ByteArray, context: MediaContext, plaintext: ByteArray, store: MediaUsageStore, attemptID: ByteArray, output: OwnedMediaOutput, checkValid: () -> Unit): ByteArray {
    store.validMaster(master)
    f1Check(master.size == 32)
    val ctx = store.context(context)
    val header = F1Record.wholeHeader(ctx, F1Crypto.random(32), F1Crypto.random(12), plaintext.size.toLong())
    output.checkForWrite(store,context,attemptID)
    val owner = output
    val permit = store.reserveEncryption(listOf(header), listOf(1), listOf(11L + (plaintext.size + 15L) / 16), attemptID, owner, checkValid)
    var key = byteArrayOf()
    return try {
      output.claim(store,context,attemptID,permit,plaintext.size + 172L,header)
      key = F1Record.deriveKey(master, header.copyOfRange(72, 104), ctx)
      permit.consume(store, owner, checkValid, header)
      val bytes = header + F1Crypto.aead(Cipher.ENCRYPT_MODE, key, header.copyOfRange(104, 116), header, plaintext)
      output.write(bytes)
      val completion = output.finish(digest(bytes))
      store.complete(listOf(header), completion, permit, checkValid)
      bytes
    } finally { permit.close(); key.fill(0) }
  }
  fun open(master: ByteArray, context: MediaContext, encoded: ByteArray, expectedHash: ByteArray, store: MediaUsageStore, checkValid: () -> Unit): ByteArray = store.withMaster(master) { owned ->
    f1Check(expectedHash.size == 32)
    val expected = expectedHash.copyOf()
    try { openBound(owned,context,encoded,expected,store,checkValid) } finally { expected.fill(0) }
  }


  private fun openBound(master: ByteArray, context: MediaContext, encoded: ByteArray, expectedHash: ByteArray, store: MediaUsageStore, checkValid: () -> Unit): ByteArray {
    store.validMaster(master)
    f1Check(encoded.size >= 172 && encoded.size.toLong() <= store.maximumBodyLength(context.purpose) + 172)
    val bytes = encoded.copyOf()
    val owner = Any()
    val permit = store.precharge(listOf(bytes.copyOfRange(0, 156)), listOf(1L), expectedHash, owner, checkValid)
    return permit.use {
      permit.consume(store, owner, checkValid, bytes)
      val plaintext = F1Record.decrypt(master, store.context(context), bytes)
      try { store.valid(checkValid); f1Check(MessageDigest.isEqual(digest(bytes), expectedHash)); plaintext }
      catch (failure: Throwable) { plaintext.fill(0); throw failure }
    }
  }
}
