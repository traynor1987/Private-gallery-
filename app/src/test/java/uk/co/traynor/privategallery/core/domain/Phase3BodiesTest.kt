package uk.co.traynor.privategallery.core.domain

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import org.junit.Assert.*
import org.junit.Test

class Phase3BodiesTest {
  private class W {
    val o = ByteArrayOutputStream()
    val d = DataOutputStream(o)

    fun n(v: Int) = apply { d.writeShort(v) }

    fun l(v: Long) = apply { d.writeLong(v) }

    fun i(v: Int) = apply { d.writeInt(v) }

    fun b(v: Int) = apply { d.writeByte(v) }

    fun raw(v: ByteArray) = apply { d.write(v) }

    fun id(v: Int) = raw(ByteArray(16) { v.toByte() })

    fun h(v: Int) = raw(ByteArray(32) { v.toByte() })

    fun s(v: String) = raw(v.toByteArray().let { W().i(it.size).raw(it).out() })

    fun c(p: Int, v: Int, g: Long = 1) = n(p).id(v).l(g)

    fun r(p: Int, v: Int, g: Long = 1) = c(p, v, g).l(200).h(v)

    fun out() = o.toByteArray()
  }

  private fun bad(f: () -> Unit) {
    try {
      f()
      fail("accepted malformed body")
    } catch (_: Phase3FormatException) {}
  }

  private fun itemBody() =
    W()
      .s("11111111-1111-1111-1111-111111111111")
      .s("image/jpeg")
      .s("image")
      .l(1)
      .l(3)
      .h(1)
      .raw(ByteArray(12))
      .n(1)
      .n(1)
      .b(0)
      .n(1)
      .n(0)
      .b(0)
      .b(0)
      .b(0)
      .out()

  private fun projection() =
    W()
      .raw("PGSRC001".toByteArray())
      .n(1)
      .raw(itemBody())
      .n(0)
      .b(0)
      .l(1)
      .l(2)
      .l(200)
      .l(3)
      .h(4)
      .l(1)
      .l(5)
      .out()

  private fun row(id: Int) = W().id(id).s("collection").l(1).b(0).out()

  private fun index(rows: List<ByteArray>) =
    W().n(2).l(1).n(0).n(rows.size).apply { rows.forEach(::raw) }.i(0).b(0).n(0).out()

  @Test
  fun rawByteOrderCountsAndUtf8Reject() {
    val canonical = index(listOf(row(1), row(0x80)))
    assertArrayEquals(canonical, HiddenMediaIndex.parse(canonical).encode())
    bad { HiddenMediaIndex.parse(index(listOf(row(0x80), row(1)))) }
    bad { HiddenMediaIndex.parse(index(listOf(row(1), row(1)))) }
    bad {
      HiddenMediaIndex.parse(
        index(listOf(row(1))).copyOf().also {
          it[38] = 0xc0.toByte()
          it[39] = 0x80.toByte()
        }
      )
    }
    bad { HiddenMediaIndex.parse(W().n(2).l(1).n(0).n(129).out()) }
    bad { HiddenMediaIndex.parse(W().n(2).l(1).n(0).n(0).i(65537).out()) }
    bad { HiddenMediaIndex.parse(W().n(2).l(1).n(0).n(0).i(0).b(1).id(1).n(0).out()) }
  }

  @Test
  fun projectionMergeIdentityAndStrictUuid() {
    val p = projection()
    assertArrayEquals(p, SourceProjection.parse(p).encode())
    bad { SourceProjection.parse(p + 0) }
    bad { SourceProjection.parse(p.copyOf().also { it[14] = 'z'.code.toByte() }) }
    val merge =
      W().raw("PGMRG001".toByteArray()).n(1).i(itemBody().size).raw(itemBody()).n(0).b(0).out()
    assertArrayEquals(merge, RestoreMergeProjection.parse(merge).encode())
    bad { RestoreMergeProjection.parse(merge.copyOf().also { it[13] = (it[13] + 1).toByte() }) }
  }

  @Test
  fun reservationsOwnerAndCrossReferenceSemantics() {
    val reservation = W().n(2).id(1).l(2).out()
    assertArrayEquals(reservation, MediaAttemptReservation.parse(reservation).encode())
    val owner = W().n(2).h(3).n(3).id(4).b(0).n(2).c(1, 5).c(2, 6).out()
    assertArrayEquals(owner, MediaAttemptOwner.parse(owner).encode())
    bad { MediaAttemptOwner.parse(owner.copyOf().also { it[35] = 1 }) }
    bad { MediaAttemptOwner.parse(W().n(2).h(3).n(3).id(4).b(0).n(2).c(2, 6).c(1, 5).out()) }
    bad { MediaAttemptOwner.parse(W().n(2).h(3).n(3).id(4).b(0).n(17).out()) }
  }

  private fun entry(
    state: Int,
    terminal: Int =
      when (state) {
        7 -> 1
        9 -> 2
        10 -> 3
        11 -> 4
        12 -> 1
        else -> 0
      },
    receipt: Boolean = state in 3..10 || state == 12,
  ) =
    W()
      .id(1)
      .n(state)
      .n(2)
      .n(terminal)
      .l(1)
      .r(1, 2)
      .r(5, 3)
      .id(4)
      .id(5)
      .id(6)
      .apply {
        if (receipt) {
          b(1).c(7, 7)
          b(1).h(8)
          b(1).h(9)
        } else {
          b(0).b(0).b(0)
        }
        if (state in 6..7 || state == 12 && terminal == 1) {
          b(1).h(10)
          b(1).h(11)
        } else {
          b(0).b(0)
        }
        if (state == 12) {
          b(1).h(12)
          b(1).r(1, 13)
        } else {
          b(0).b(0)
        }
      }
      .out()

  private fun catalog(e: ByteArray) = W().raw("PGTRC001".toByteArray()).n(1).l(1).n(1).raw(e).out()

  @Test
  fun allTwelvePrimaryStatesAndForbiddenFields() {
    for (state in 1..12) {
      val b = catalog(entry(state))
      assertArrayEquals(b, PrimaryTransferCatalog.parse(b).encode())
    }
    bad { PrimaryTransferCatalog.parse(catalog(entry(1, receipt = true))) }
    bad { PrimaryTransferCatalog.parse(catalog(entry(3, receipt = false))) }
    bad { PrimaryTransferCatalog.parse(catalog(entry(7, terminal = 0))) }
    val cancelled = catalog(entry(11, receipt = true))
    assertArrayEquals(cancelled, PrimaryTransferCatalog.parse(cancelled).encode())
    bad { PrimaryTransferCatalog.parse(W().raw("PGTRC001".toByteArray()).n(1).l(1).n(65).out()) }
  }

  @Test
  fun descriptorAndCredentialProofCanonicalBodies() {
    val pd = W().raw("PGTRD001".toByteArray()).n(1).h(1).r(1, 2).n(2).r(1, 3).r(5, 4).out()
    assertArrayEquals(pd, PrimaryTransferDescriptor.parse(pd).encode())
    bad {
      PrimaryTransferDescriptor.parse(
        W().raw("PGTRD001".toByteArray()).n(1).h(1).r(1, 2).n(1).r(7, 3).out()
      )
    }
    val hd =
      W()
        .raw("PGDOMD02".toByteArray())
        .n(2)
        .n(2)
        .n(1)
        .n(1)
        .h(1)
        .h(2)
        .h(3)
        .n(0)
        .raw(ByteArray(56))
        .r(9, 4, 2)
        .n(0)
        .out()
    assertArrayEquals(hd, HiddenMediaDescriptor.parse(hd).encode())
    bad { HiddenMediaDescriptor.parse(hd.copyOf().also { it[114] = 1 }) }
    val slots =
      W().n(1).l(2).n(2).id(1).l(1).h(1).n(1).n(0).n(1).id(2).l(1).h(2).n(2).n(2).n(1).out()
    val proof =
      W()
        .raw("PGAUTH02".toByteArray())
        .n(2)
        .id(3)
        .h(4)
        .h(5)
        .n(2)
        .n(1)
        .n(1)
        .i(slots.size)
        .raw(slots)
        .out()
    assertArrayEquals(proof, CredentialProof.parse(proof).encode())
    bad { CredentialProof.parse(proof.copyOf().also { it[91] = 1 }) }
    bad { CredentialProof.parse(proof + 0) }
  }

  private fun mediaItem(state: Int = 1, deleted: Boolean = false, previewPurpose: Int = 3) =
    W()
      .id(1)
      .n(1)
      .c(2, 2)
      .l(175)
      .h(2)
      .l(3)
      .h(4)
      .l(1)
      .n(state)
      .apply {
        if (deleted) {
          b(1).l(1)
        } else b(0)
      }
      .s("image")
      .s("image/jpeg")
      .l(1)
      .n(1)
      .n(1)
      .b(0)
      .b(0)
      .b(1)
      .r(previewPurpose, 3)
      .out()

  private fun itemIndex(item: ByteArray, membership: Boolean = false) =
    W()
      .n(2)
      .l(1)
      .n(1)
      .raw(item)
      .n(1)
      .raw(row(4))
      .i(if (membership) 1 else 0)
      .apply {
        if (membership) {
          id(4).id(1).l(1)
        }
      }
      .b(1)
      .id(4)
      .n(0)
      .out()

  @Test
  fun hiddenItemsAndRelationshipsAreSemanticallyChecked() {
    val valid = itemIndex(mediaItem(), true)
    assertArrayEquals(valid, HiddenMediaIndex.parse(valid).encode())
    val trash = itemIndex(mediaItem(2, true))
    assertArrayEquals(trash, HiddenMediaIndex.parse(trash).encode())
    bad { HiddenMediaIndex.parse(itemIndex(mediaItem(1, true))) }
    bad { HiddenMediaIndex.parse(itemIndex(mediaItem(2, false))) }
    bad { HiddenMediaIndex.parse(itemIndex(mediaItem(previewPurpose = 2))) }
    bad { HiddenMediaIndex.parse(itemIndex(mediaItem(2, true), true)) }
    bad { HiddenMediaIndex.parse(valid.copyOf().also { it.fill(9, it.size - 43, it.size - 27) }) }
  }

  private fun transfer(terminal: Int = 0, phase: Int = 4) =
    W()
      .id(1)
      .n(2)
      .n(phase)
      .id(2)
      .id(3)
      .id(4)
      .l(1)
      .c(7, 5)
      .c(1, 6, 2)
      .c(5, 7, 2)
      .n(terminal)
      .apply { if (terminal == 0) b(0) else b(1).h(8) }
      .out()

  private fun transferIndex(row: ByteArray) = W().n(2).l(2).n(0).n(0).i(0).b(0).n(1).raw(row).out()

  @Test
  fun retainedTransfersKeepClosureAfterLogicalRemovalAndEveryTerminal() {
    val current = HiddenMediaIndex.parse(transferIndex(transfer()))
    assertTrue(current.items.isEmpty())
    assertEquals(3, current.retainedEvidenceContexts.size)
    for (terminal in 1..4) {
      val closed = HiddenMediaIndex.parse(transferIndex(transfer(terminal)))
      assertEquals(current.retainedEvidenceContexts, closed.retainedEvidenceContexts)
    }
    for (phase in 1..3) bad { HiddenMediaIndex.parse(transferIndex(transfer(phase = phase))) }
    bad { HiddenMediaIndex.parse(transferIndex(transfer(phase = 5).also { it[17] = 1 })) }
    bad { HiddenMediaIndex.parse(transferIndex(transfer(terminal = 1).also { it[17] = 1 })) }
    val shared =
      W()
        .n(2)
        .l(2)
        .n(0)
        .n(0)
        .i(0)
        .b(0)
        .n(2)
        .raw(transfer())
        .raw(transfer().also { it.fill(9, 0, 16) })
        .out()
    assertEquals(3, HiddenMediaIndex.parse(shared).retainedEvidenceContexts.size)
    try {
      (current.transfers as MutableList<HiddenMediaTransfer>).clear()
      fail("mutable list")
    } catch (_: UnsupportedOperationException) {}
  }

  @Test
  fun primaryAndSourceRowsRequireRawUtf8OrderAndExactCounts() {
    val first = entry(1)
    val second = first.copyOf().also { it.fill(2, 0, 16) }
    fun pair(a: ByteArray, b: ByteArray) =
      W().raw("PGTRC001".toByteArray()).n(1).l(1).n(2).raw(a).raw(b).out()
    assertEquals(2, PrimaryTransferCatalog.parse(pair(first, second)).entries.size)
    bad { PrimaryTransferCatalog.parse(pair(second, first)) }
    bad { PrimaryTransferCatalog.parse(pair(first, first)) }
    fun collection(id: String) = W().s(id).s("name").l(1).n(0).b(0).b(1).l(1).out()
    fun source(a: String, b: String) =
      W()
        .raw("PGSRC001".toByteArray())
        .n(1)
        .raw(itemBody())
        .n(2)
        .raw(collection(a))
        .raw(collection(b))
        .b(0)
        .l(1)
        .l(2)
        .l(200)
        .l(3)
        .h(4)
        .l(1)
        .l(5)
        .out()
    assertEquals(2, SourceProjection.parse(source("z", "é")).collections.size)
    bad { SourceProjection.parse(source("é", "z")) }
    bad { SourceProjection.parse(source("z", "z")) }
  }

  @Test
  fun stringByteLimitBomAndUtf16EncodingAreStrict() {
    assertEquals(4100, Phase3Writer().string("a".repeat(4096)).encode().size)
    bad { Phase3Writer().string("é".repeat(2049)) }
    bad { Phase3Writer().string("\uFEFFname") }
    bad { Phase3Writer().string("\uD800") }
    bad { HiddenMediaIndex.parse(index(listOf(W().id(1).s("\uFEFFname").l(1).b(0).out()))) }
    bad { HiddenMediaIndex.parse(ByteArray(P3_INDEX_LIMIT + 1)) }
  }

  @Test
  fun primaryDescriptorRequiresExactCatalogAndClosure() {
    val bootstrap =
      PrimaryTransferBootstrap.parse(
        W().raw("PGTRB001".toByteArray()).n(1).id(1).id(2).id(3).id(4).l(1).id(5).i(0).out()
      )
    val catalog =
      PrimaryTransferCatalog.parse(W().raw("PGTRC001".toByteArray()).n(1).l(1).n(0).out())
    val encrypted = ByteArray(200) { 1 }
    val body =
      W()
        .raw("PGTRD001".toByteArray())
        .n(1)
        .raw(digest(bootstrap.encode()))
        .c(1, 3)
        .l(200)
        .raw(digest(encrypted))
        .n(0)
        .out()
    val descriptor = PrimaryTransferDescriptor.parse(body)
    descriptor.validateBindings(bootstrap, catalog, encrypted)
    bad { descriptor.validateBindings(bootstrap, catalog, encrypted.copyOf().also { it[0] = 2 }) }
    val foreignGeneration =
      PrimaryTransferCatalog.parse(W().raw("PGTRC001".toByteArray()).n(1).l(2).n(0).out())
    bad { descriptor.validateBindings(bootstrap, foreignGeneration, encrypted) }
    val extra = body.copyOf().also { it[it.lastIndex] = 1 } + W().r(1, 6).out()
    bad { PrimaryTransferDescriptor.parse(extra).validateBindings(bootstrap, catalog, encrypted) }
  }

  @Test
  fun mergeBindsFrozenItemAndCollectionDefinitions() {
    val source = SourceProjection.parse(projection())
    val merge =
      RestoreMergeProjection.parse(
        W().raw("PGMRG001".toByteArray()).n(1).i(itemBody().size).raw(itemBody()).n(0).b(0).out()
      )
    merge.validateSource(source)
    val edited = projection().also { it[69] = 'x'.code.toByte() }
    assertFalse(source.fingerprint.contentEquals(SourceProjection.parse(edited).fingerprint))
    bad { merge.validateSource(SourceProjection.parse(edited)) }
  }

  @Test
  fun historicalBindingUsesOriginalTargetWithoutRequiringCurrentJournalOrTerminal() {
    val originalRow = transfer().also { it.fill(1, 20, 36) }
    val bytes = W().n(2).l(2).n(1).raw(mediaItem()).n(0).i(0).b(0).n(1).raw(originalRow).out()
    val historical = HiddenMediaIndex.parse(bytes)
    val currentRow =
      transfer(terminal = 1, phase = 6).also {
        it.fill(1, 20, 36)
        it.fill(9, 130, 146)
      }
    val current = HiddenMediaIndex.parse(transferIndex(currentRow))
    val encryptedHistoricalIndex = ByteArray(bytes.size + 172) { 9 }
    val receiptBytes =
      W()
        .n(1)
        .id(5)
        .id(1)
        .id(3)
        .id(4)
        .l(1)
        .id(8)
        .id(2)
        .l(1)
        .l(2)
        .l(3)
        .h(4)
        .h(2)
        .raw(digest(encryptedHistoricalIndex))
        .n(1)
        .out()
    val receipt = DestinationReceipt.parse(receiptBytes)
    historical.validateHistoricalTarget(
      current.transfers.single(),
      receipt,
      ByteArray(16) { 8 },
      encryptedHistoricalIndex,
    )
    bad {
      historical.validateHistoricalTarget(
        current.transfers.single(),
        receipt,
        ByteArray(16) { 9 },
        encryptedHistoricalIndex,
      )
    }
    bad {
      historical.validateHistoricalTarget(
        current.transfers.single(),
        DestinationReceipt.parse(receiptBytes.copyOf().also { it[130] = 9 }),
        ByteArray(16) { 8 },
        encryptedHistoricalIndex,
      )
    }
    bad {
      current.validateHistoricalTarget(
        current.transfers.single(),
        receipt,
        ByteArray(16) { 8 },
        encryptedHistoricalIndex,
      )
    }
  }

  private val sourceUuid = "11111111-1111-1111-1111-111111111111"

  private fun restoreRelationshipRow(cover: String?, membership: Long?): ByteArray =
    W()
      .s("c1")
      .s("Collection")
      .l(123)
      .n(0)
      .apply {
        if (cover == null) b(0) else b(1).s(cover)
        if (membership == null) b(0) else b(1).l(membership)
      }
      .out()

  private fun restoreSource(cover: String?, membership: Long?): SourceProjection =
    SourceProjection.parse(
      W()
        .raw("PGSRC001".toByteArray())
        .n(1)
        .raw(itemBody())
        .n(1)
        .raw(restoreRelationshipRow(cover, membership))
        .b(1)
        .s("source-favourite")
        .l(1)
        .l(2)
        .l(200)
        .l(3)
        .h(4)
        .l(1)
        .l(5)
        .out()
    )

  private fun restoreMerge(cover: String?, membership: Long?): RestoreMergeProjection =
    RestoreMergeProjection.parse(
      W()
        .raw("PGMRG001".toByteArray())
        .n(1)
        .i(itemBody().size)
        .raw(itemBody())
        .n(1)
        .raw(restoreRelationshipRow(cover, membership))
        .b(1)
        .s("current-favourite")
        .out()
    )

  @Test
  fun matchingOriginalRestoreRelationshipsPreserveCurrentFavourite() {
    val source = restoreSource(sourceUuid, 456)
    val merge = restoreMerge(sourceUuid, 456)
    merge.validateSource(source)
    assertEquals("source-favourite", source.favouriteCollectionId)
    assertEquals("current-favourite", merge.favouriteCollectionId)
    assertEquals(sourceUuid, merge.collections.single().coverItemId)
    assertEquals(456L, merge.collections.single().sourceMembershipAddedAt)
    restoreMerge(sourceUuid, null).validateSource(restoreSource(sourceUuid, null))
    restoreMerge(null, 456).validateSource(restoreSource(null, 456))
    restoreMerge("other-item", 456).validateSource(restoreSource("other-item", 456))
  }

  @Test
  fun restoreRejectsChangedOriginalMembershipTimestamp() {
    bad { restoreMerge(sourceUuid, 457).validateSource(restoreSource(sourceUuid, 456)) }
  }

  @Test
  fun restoreRejectsRemovedOriginalMembership() {
    bad { restoreMerge(sourceUuid, null).validateSource(restoreSource(sourceUuid, 456)) }
  }

  @Test
  fun restoreRejectsAddedMembershipAbsentFromOriginalSource() {
    bad { restoreMerge(sourceUuid, 456).validateSource(restoreSource(sourceUuid, null)) }
  }

  @Test
  fun restoreRejectsChangedOriginalCover() {
    bad { restoreMerge("foreign-cover", 456).validateSource(restoreSource(sourceUuid, 456)) }
    bad { restoreMerge(null, 456).validateSource(restoreSource(sourceUuid, 456)) }
    bad { restoreMerge(sourceUuid, 456).validateSource(restoreSource(null, 456)) }
  }

  @Test
  fun historicalReceiptRejectsCommittedIndexHashSubstitution() {
    val originalRow = transfer().also { it.fill(1, 20, 36) }
    val historical =
      HiddenMediaIndex.parse(
        W().n(2).l(2).n(1).raw(mediaItem()).n(0).i(0).b(0).n(1).raw(originalRow).out()
      )
    val encryptedHistoricalIndex = ByteArray(historical.encode().size + 172) { 9 }
    val receiptBytes =
      W()
        .n(1)
        .id(5)
        .id(1)
        .id(3)
        .id(4)
        .l(1)
        .id(8)
        .id(2)
        .l(1)
        .l(2)
        .l(3)
        .h(4)
        .h(2)
        .raw(digest(encryptedHistoricalIndex))
        .n(1)
        .out()
    val receipt = DestinationReceipt.parse(receiptBytes)
    historical.validateHistoricalTarget(
      historical.transfers.single(),
      receipt,
      ByteArray(16) { 8 },
      encryptedHistoricalIndex,
    )
    val substituted =
      DestinationReceipt.parse(
        receiptBytes.copyOf().also { it[194] = (it[194].toInt() xor 1).toByte() }
      )
    bad {
      historical.validateHistoricalTarget(
        historical.transfers.single(),
        substituted,
        ByteArray(16) { 8 },
        encryptedHistoricalIndex,
      )
    }
  }

  @Test
  fun historicalReceiptRequiresExactBoundedCompleteCiphertextSnapshot() {
    val row = transfer().also { it.fill(1, 20, 36) }
    val historical =
      HiddenMediaIndex.parse(W().n(2).l(2).n(1).raw(mediaItem()).n(0).i(0).b(0).n(1).raw(row).out())
    val encrypted = ByteArray(historical.encode().size + 172) { 9 }
    fun receipt(ciphertext: ByteArray) =
      DestinationReceipt.parse(
        W()
          .n(1)
          .id(5)
          .id(1)
          .id(3)
          .id(4)
          .l(1)
          .id(8)
          .id(2)
          .l(1)
          .l(2)
          .l(3)
          .h(4)
          .h(2)
          .raw(digest(ciphertext))
          .n(1)
          .out()
      )
    val validReceipt = receipt(encrypted)
    historical.validateHistoricalTarget(
      historical.transfers.single(),
      validReceipt,
      ByteArray(16) { 8 },
      encrypted,
    )
    bad {
      historical.validateHistoricalTarget(
        historical.transfers.single(),
        validReceipt,
        ByteArray(16) { 8 },
        encrypted.copyOf().also { it[0] = 8 },
      )
    }
    val truncated = encrypted.copyOf(encrypted.size - 1)
    bad {
      historical.validateHistoricalTarget(
        historical.transfers.single(),
        receipt(truncated),
        ByteArray(16) { 8 },
        truncated,
      )
    }
    val trailing = encrypted + 0
    bad {
      historical.validateHistoricalTarget(
        historical.transfers.single(),
        receipt(trailing),
        ByteArray(16) { 8 },
        trailing,
      )
    }
    bad {
      historical.validateHistoricalTarget(
        historical.transfers.single(),
        validReceipt,
        ByteArray(16) { 8 },
        ByteArray(P3_INDEX_LIMIT + 173),
      )
    }
  }
}
