package uk.co.traynor.privategallery.core.domain

import java.nio.ByteBuffer
import org.junit.Assert.*
import org.junit.Test

class Phase3EncodingTest {
  private fun hex(s: String) = s.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

  private fun bad(block: () -> Unit) {
    try {
      block()
      fail("accepted malformed body")
    } catch (_: Phase3FormatException) {}
  }

  private val context = hex("0007010101010101010101010101010101010000000000000001")
  private val reference =
    hex(
      "00070101010101010101010101010101010100000000000000010000000000000190000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f"
    )
  private val usage =
    hex(
      "00020000000000002000000000002001600000000000000020000000000000000001484848484848484848484848484848484848484848484848484848484848484801010101010101010101010101010101"
    )
  private val hidden =
    hex(
      "5047444f4d42303200020101010101010101010101010101010102020202020202020202020202020202000000000000000200020303030303030303030303030303030304040404040404040404040404040404050505050505050505050505050505050000000006060606060606060606060606060606"
    )
  private val primary =
    hex(
      "504754524230303100010101010101010101010101010101010102020202020202020202020202020202030303030303030303030303030303030404040404040404040404040404040400000000000000010505050505050505050505050505050500000000"
    )
  private val empty = hex("000200000000000000020000000000000000000000")
  private val merge =
    hex(
      "50474d524730303100010000008b0000002431313131313131312d313131312d313131312d313131312d3131313131313131313131310000000a696d6167652f6a7065670000000953796e746865746963000000000000007b0000000000000003ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad000102030405060708090a0b00010001000001000000000000010000000263310000000a436f6c6c656374696f6e000000000000007b000001000000056974656d310100000000000001c800"
    )

  @Test
  fun literalCanonicalVectors() {
    assertArrayEquals(context, MediaContext.parse(context).encode())
    assertArrayEquals(reference, MediaReference.parse(reference).encode())
    assertArrayEquals(usage, MediaUsage.parse(usage).encode())
    assertArrayEquals(hidden, HiddenBootstrap.parse(hidden).encode())
    assertArrayEquals(primary, PrimaryTransferBootstrap.parse(primary).encode())
    assertArrayEquals(empty, HiddenMediaIndex.parse(empty).encode())
    assertArrayEquals(merge, RestoreMergeProjection.parse(merge).encode())
    val c = MediaContext.parse(context)
    assertEquals(7, c.purpose)
    assertArrayEquals(ByteArray(16) { 1 }, c.objectId)
    assertEquals(1L, c.generation)
    val r = MediaReference.parse(reference)
    assertEquals(c, r.context)
    assertEquals(400L, r.ciphertextLength)
    assertArrayEquals(ByteArray(32) { it.toByte() }, r.hash)
    val u = MediaUsage.parse(usage)
    assertEquals(8192L, u.encryptionInvocations)
    assertEquals(536961024L, u.ghashBlocks)
    assertEquals(8192L, u.chargedQueries)
    assertEquals(1L, u.chargeSequence)
    assertArrayEquals(ByteArray(32) { 0x48 }, u.ciphertextHash)
    assertArrayEquals(ByteArray(16) { 1 }, u.attemptId)
    val h = HiddenBootstrap.parse(hidden)
    assertArrayEquals(ByteArray(16) { 1 }, h.containerId)
    assertArrayEquals(ByteArray(16) { 2 }, h.masterId)
    assertEquals(2L, h.generation)
    assertEquals(2, h.state)
    assertArrayEquals(ByteArray(16) { 3 }, h.descriptorObjectId)
    assertArrayEquals(ByteArray(16) { 4 }, h.indexObjectId)
    assertArrayEquals(ByteArray(16) { 5 }, h.catalogObjectId)
    assertArrayEquals(ByteArray(16) { 6 }, h.credentialProofObjectId)
    val p = PrimaryTransferBootstrap.parse(primary)
    assertArrayEquals(ByteArray(16) { 1 }, p.containerId)
    assertArrayEquals(ByteArray(16) { 2 }, p.masterId)
    assertEquals(1L, p.generation)
    assertArrayEquals(ByteArray(16) { 3 }, p.catalogObjectId)
    assertArrayEquals(ByteArray(16) { 4 }, p.descriptorObjectId)
    assertArrayEquals(ByteArray(16) { 5 }, p.tokenId)
    val i = HiddenMediaIndex.parse(empty)
    assertEquals(2L, i.revision)
    assertTrue(i.items.isEmpty())
    assertTrue(i.collections.isEmpty())
    assertTrue(i.memberships.isEmpty())
    assertTrue(i.transfers.isEmpty())
    assertNull(i.favouriteCollectionId)
    val m = RestoreMergeProjection.parse(merge)
    assertEquals("11111111-1111-1111-1111-111111111111", m.item.sourceItemId)
    assertEquals("image/jpeg", m.item.mime)
    assertEquals("Synthetic", m.item.displayName)
    assertEquals(123L, m.item.importedAt)
    assertEquals(3L, m.item.plaintextLength)
    assertArrayEquals(
      hex("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"),
      m.item.plaintextHash,
    )
    assertArrayEquals(ByteArray(12) { it.toByte() }, m.item.nonce)
    assertEquals(1, m.item.legacyFormat)
    assertEquals(1, m.item.origin)
    assertEquals(0, m.item.restrictions)
    assertNull(m.item.sourceUri)
    assertNull(m.item.currentCrop)
    assertNull(m.item.previousCrop)
    assertEquals(1, m.collections.size)
    assertEquals("c1", m.collections.single().sourceCollectionId)
    assertEquals("Collection", m.collections.single().name)
    assertEquals(123L, m.collections.single().createdAt)
    assertEquals(0, m.collections.single().pinned)
    assertEquals("item1", m.collections.single().coverItemId)
    assertEquals(456L, m.collections.single().sourceMembershipAddedAt)
    assertNull(m.favouriteCollectionId)
    assertEquals(
      "ece5a09bb8ba49f808d585de8b8060203371233c06a5eb5c91479c8b9cb74a58",
      digest(RestoreMergeProjection.parse(merge).encode()).hex(),
    )
  }

  @Test
  fun exactFramingAndByteOwnership() {
    val source = reference.copyOf()
    val parsed = MediaReference.parse(source)
    source.fill(0)
    parsed.encode().fill(0)
    parsed.hash.fill(0)
    parsed.context.objectId.fill(0)
    assertArrayEquals(reference, parsed.encode())
    assertEquals(parsed, MediaReference.parse(reference))
    assertEquals(parsed.hashCode(), MediaReference.parse(reference).hashCode())
    for (i in context.indices) bad { MediaContext.parse(context.copyOf(i)) }
    bad { MediaContext.parse(context + 0) }
    bad { MediaReference.parse(reference + 0) }
    bad { MediaUsage.parse(usage + 0) }
    bad { HiddenMediaIndex.parse(empty + 0) }
    bad { HiddenBootstrap.parse(hidden + 0) }
    bad { PrimaryTransferBootstrap.parse(primary + 0) }
    bad { RestoreMergeProjection.parse(merge + 0) }
  }

  @Test
  fun malformedCountsEnumsOverflowOptionalAndZeroIds() {
    bad { MediaContext.parse(context.copyOf().also { it[1] = 12 }) }
    bad { MediaContext.parse(context.copyOf().also { it.fill(0, 2, 18) }) }
    bad { MediaContext.parse(context.copyOf().also { it[18] = 0x80.toByte() }) }
    bad { MediaReference.parse(reference.copyOf().also { it.fill(0, 26, 34) }) }
    bad { MediaUsage.parse(usage.copyOf().also { it.fill(0, 26, 34) }) }
    bad {
      HiddenMediaIndex.parse(
        empty.copyOf().also {
          it[10] = 2
          it[11] = 1
        }
      )
    }
    bad { HiddenMediaIndex.parse(empty.copyOf().also { it[18] = 2 }) }
    bad { HiddenBootstrap.parse(hidden.copyOf().also { it[103] = 1 }) }
    bad { PrimaryTransferBootstrap.parse(primary.copyOf().also { it[101] = 1 }) }
    bad {
      RestoreMergeProjection.parse(
        merge.copyOf().also {
          it[18] = 0xc0.toByte()
          it[19] = 0x80.toByte()
        }
      )
    }
  }

  @Test
  fun frozenEvidenceLiteralLayoutsAndSentinelRules() {
    val journal =
      hex(
        "0001" +
          "01".repeat(16) +
          "02".repeat(16) +
          "03".repeat(16) +
          "04".repeat(16) +
          "0000000000000001" +
          "00040002" +
          "0000000000000001" +
          "0000000000000002" +
          "05".repeat(16) +
          "06".repeat(16) +
          "0000000000000001" +
          "07".repeat(16) +
          "08".repeat(32)
      )
    val receipt =
      hex(
        "0001" +
          "07".repeat(16) +
          "02".repeat(16) +
          "03".repeat(16) +
          "04".repeat(16) +
          "0000000000000001" +
          "05".repeat(16) +
          "06".repeat(16) +
          "0000000000000002" +
          "0000000000000002" +
          "0000000000000003" +
          "09".repeat(32) +
          "0a".repeat(32) +
          "0b".repeat(32) +
          "0001"
      )
    assertEquals(182, journal.size)
    assertEquals(228, receipt.size)
    assertArrayEquals(journal, TransferJournal.parse(journal).encode())
    assertArrayEquals(receipt, DestinationReceipt.parse(receipt).encode())
    val j = TransferJournal.parse(journal)
    assertArrayEquals(ByteArray(16) { 1 }, j.operationId)
    assertArrayEquals(ByteArray(16) { 2 }, j.transferId)
    assertArrayEquals(ByteArray(16) { 3 }, j.localContainerId)
    assertArrayEquals(ByteArray(16) { 4 }, j.localObjectId)
    assertEquals(1L, j.localGeneration)
    assertEquals(4, j.state)
    assertEquals(2, j.action)
    assertEquals(1L, j.expectedPriorIndexGeneration)
    assertEquals(2L, j.proposedIndexGeneration)
    assertArrayEquals(ByteArray(16) { 5 }, j.counterpartContainerId)
    assertArrayEquals(ByteArray(16) { 6 }, j.counterpartObjectId)
    assertEquals(1L, j.counterpartGeneration)
    assertArrayEquals(ByteArray(16) { 7 }, j.receiptId)
    assertArrayEquals(ByteArray(32) { 8 }, j.receiptHash)
    val d = DestinationReceipt.parse(receipt)
    assertArrayEquals(ByteArray(16) { 7 }, d.receiptId)
    assertArrayEquals(ByteArray(16) { 2 }, d.transferId)
    assertArrayEquals(ByteArray(16) { 3 }, d.sourceContainerId)
    assertArrayEquals(ByteArray(16) { 4 }, d.sourceObjectId)
    assertEquals(1L, d.sourceGeneration)
    assertArrayEquals(ByteArray(16) { 5 }, d.destinationContainerId)
    assertArrayEquals(ByteArray(16) { 6 }, d.destinationObjectId)
    assertEquals(2L, d.destinationGeneration)
    assertEquals(2L, d.destinationIndexGeneration)
    assertEquals(3L, d.plaintextLength)
    assertArrayEquals(ByteArray(32) { 9 }, d.plaintextHash)
    assertArrayEquals(ByteArray(32) { 10 }, d.ciphertextHash)
    assertArrayEquals(ByteArray(32) { 11 }, d.indexHash)
    bad { TransferJournal.parse(journal.copyOf().also { it.fill(0, 134, 182) }) }
    bad { DestinationReceipt.parse(receipt.copyOf().also { it[227] = 2 }) }
    bad { TransferJournal.parse(journal + 0) }
    bad { DestinationReceipt.parse(receipt + 0) }
    bad {
      TransferJournal.parse(
        journal.copyOf().also {
          it[75] = 5
          it[77] = 1
        }
      )
    }
    val pre =
      journal.copyOf().also {
        it[75] = 1
        it.fill(0, 134, 182)
      }
    assertArrayEquals(pre, TransferJournal.parse(pre).encode())
    bad { TransferJournal.parse(pre.copyOf().also { it[181] = 1 }) }
  }

  @Test
  fun cropRejectsNegativeZeroNanAndEmptyGeometry() {
    fun crop(a: Float, b: Float, c: Float, d: Float) =
      ByteBuffer.allocate(16).putFloat(a).putFloat(b).putFloat(c).putFloat(d).array()
    assertArrayEquals(crop(0f, 0f, 1f, 1f), MediaCrop.parse(crop(0f, 0f, 1f, 1f)).encode())
    bad { MediaCrop.parse(crop(-0f, 0f, 1f, 1f)) }
    bad { MediaCrop.parse(crop(0f, Float.NaN, 1f, 1f)) }
    bad { MediaCrop.parse(crop(1f, 0f, 1f, 1f)) }
    bad { MediaCrop.parse(crop(0f, 0f, 2f, 1f)) }
  }
}
