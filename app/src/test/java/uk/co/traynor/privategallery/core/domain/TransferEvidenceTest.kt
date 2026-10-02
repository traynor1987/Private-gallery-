package uk.co.traynor.privategallery.core.domain

import java.nio.ByteBuffer
import org.junit.Assert.*
import org.junit.Test

class TransferEvidenceTest {
  private fun id(n: Int) = ByteArray(16) { n.toByte() }

  private fun bad(f: () -> Unit) {
    try {
      f()
      fail("accepted malformed body")
    } catch (_: Phase3FormatException) {}
  }

  private fun hiddenBootstrap() =
    ByteBuffer.allocate(120)
      .put("PGDOMB02".toByteArray())
      .putShort(2)
      .put(id(1))
      .put(id(2))
      .putLong(2)
      .putShort(2)
      .put(id(3))
      .put(id(4))
      .put(id(5))
      .putInt(0)
      .put(id(6))
      .array()

  private fun slot(n: Int, type: Int, state: Int): ByteArray =
    ByteBuffer.allocate(204)
      .put("PGSLOT01".toByteArray())
      .putShort(1)
      .putShort(156)
      .put(id(1))
      .put(id(2))
      .put(id(n))
      .putLong(1)
      .putShort(type.toShort())
      .putShort(state.toShort())
      .putShort(1)
      .putShort(type.toShort())
      .putInt(if (type == 1) 131072 else 0)
      .putInt(if (type == 1) 8 else 0)
      .putInt(if (type == 1) 1 else 0)
      .putShort(32)
      .putShort(0)
      .put(ByteArray(32) { 1 })
      .put(ByteArray(12) { 1 })
      .put(ByteArray(12))
      .putInt(48)
      .putShort(1)
      .putShort(0)
      .put(ByteArray(48) { 1 })
      .array()

  @Test
  fun credentialBodyBindsExactBootstrapCatalogAndOpenedEnvelopeSet() {
    val boot = HiddenBootstrap.parse(hiddenBootstrap())
    val slots = listOf(slot(7, 1, 0), slot(8, 2, 2))
    val ciphertext = ByteArray(200) { 9 }
    val catalog =
      ByteBuffer.allocate(136)
        .putShort(1)
        .putLong(2)
        .putShort(2)
        .apply {
          slots.forEachIndexed { i, b ->
            put(id(7 + i))
              .putLong(1)
              .put(digest(b))
              .putShort(if (i == 0) 1 else 2)
              .putShort(if (i == 0) 0 else 2)
              .putShort(1)
          }
        }
        .array()
    val body =
      ByteBuffer.allocate(100 + catalog.size)
        .put("PGAUTH02".toByteArray())
        .putShort(2)
        .put(id(9))
        .put(digest(boot.encode()))
        .put(digest(ciphertext))
        .putShort(2)
        .putShort(1)
        .putShort(1)
        .putInt(catalog.size)
        .put(catalog)
        .array()
    val proof = CredentialProof.parse(body)
    proof.validateBindings(boot, id(9), ciphertext, slots)
    bad { proof.validateBindings(boot, id(10), ciphertext, slots) }
    bad { proof.validateBindings(boot, id(9), ciphertext.copyOf().also { it[0] = 1 }, slots) }
    bad { proof.validateBindings(boot, id(9), ciphertext, listOf(slots[0], slots[0])) }
    bad {
      proof.validateBindings(
        boot,
        id(9),
        ciphertext,
        listOf(slots[0], slots[1].copyOf().also { it[203] = 2 }),
      )
    }
    bad {
      proof.validateBindings(
        boot,
        id(9),
        ciphertext,
        listOf(slots[0], slots[1].copyOf().also { it[0] = 0 }),
      )
    }
    proof.encode().fill(0)
    proof.catalog.encode().fill(0)
    proof.catalog.entries[0].envelopeHash.fill(0)
    assertArrayEquals(body, proof.encode())
  }

  @Test
  fun selectorsAndManifestBindingAreScopeValuesOnly() {
    for (magic in listOf("PGDOMP01", "PGTRP001")) {
      val bytes = ByteBuffer.allocate(26).put(magic.toByteArray()).putShort(1).put(id(1)).array()
      if (magic == "PGDOMP01") {
        assertArrayEquals(bytes, HiddenMediaSelector.parse(bytes).encode())
        bad { HiddenMediaSelector.parse(bytes + 0) }
      } else {
        assertArrayEquals(bytes, PrimaryTransferSelector.parse(bytes).encode())
        bad { PrimaryTransferSelector.parse(bytes + 0) }
      }
    }
    val reservation =
      MediaAttemptReservation.parse(
        ByteBuffer.allocate(26).putShort(2).put(id(1)).putLong(2).array()
      )
    val owner =
      MediaAttemptOwner.parse(
        ByteBuffer.allocate(55)
          .putShort(2)
          .put(digest(reservation.encode()))
          .putShort(3)
          .put(id(2))
          .put(0)
          .putShort(0)
          .array()
      )
    owner.validateBinding(reservation, MediaContext(9, id(1), 1))
    bad { owner.validateBinding(reservation, MediaContext(9, id(1), 2)) }
    bad { owner.validateBinding(reservation, MediaContext(9, id(3), 1)) }
  }

  @Test
  fun descriptorClosureEqualsExactRetainedUnion() {
    val boot = HiddenBootstrap.parse(hiddenBootstrap())
    val index =
      HiddenMediaIndex.parse(
        ByteBuffer.allocate(21)
          .putShort(2)
          .putLong(2)
          .putShort(0)
          .putShort(0)
          .putInt(0)
          .put(0)
          .putShort(0)
          .array()
      )
    val indexCipher = ByteArray(200) { 1 }
    val cat = ByteArray(200) { 2 }
    val proof = ByteArray(200) { 3 }
    val bytes =
      ByteBuffer.allocate(238)
        .put("PGDOMD02".toByteArray())
        .putShort(2)
        .putShort(2)
        .putShort(1)
        .putShort(1)
        .put(digest(boot.encode()))
        .put(digest(indexCipher))
        .put(digest(cat))
        .putShort(0)
        .put(ByteArray(56))
        .put(MediaReference(MediaContext(9, id(6), 2), 200, digest(proof)).encode())
        .putShort(0)
        .array()
    val descriptor = HiddenMediaDescriptor.parse(bytes)
    descriptor.validateBindings(boot, index, indexCipher, cat, proof)
    bad {
      descriptor.validateBindings(boot, index, indexCipher, cat, proof.copyOf().also { it[0] = 9 })
    }
    val extra =
      bytes.copyOf().also { it[237] = 1 } +
        MediaReference(MediaContext(1, id(7), 1), 200, ByteArray(32)).encode()
    bad {
      HiddenMediaDescriptor.parse(extra).validateBindings(boot, index, indexCipher, cat, proof)
    }
  }

  @Test
  fun malformedCorpusNeverEscapesAsIncidentalRuntimeException() {
    val canonical = hiddenBootstrap()
    for (n in 0 until canonical.size) bad { HiddenBootstrap.parse(canonical.copyOf(n)) }
    val random = java.util.Random(17)
    repeat(1000) {
      val input = ByteArray(random.nextInt(256))
      random.nextBytes(input)
      try {
        HiddenMediaIndex.parse(input)
      } catch (_: Phase3FormatException) {}
      try {
        PrimaryTransferCatalog.parse(input)
      } catch (_: Phase3FormatException) {}
      try {
        CredentialProof.parse(input)
      } catch (_: Phase3FormatException) {}
    }
  }

  @Test
  fun journalPinsCompleteReceiptHashAndBothExactObjectTuples() {
    val encrypted = ByteArray(400) { 1 }
    val receiptBytes =
      ByteBuffer.allocate(228)
        .putShort(1)
        .put(id(7))
        .put(id(3))
        .put(id(1))
        .put(id(2))
        .putLong(1)
        .put(id(4))
        .put(id(5))
        .putLong(2)
        .putLong(2)
        .putLong(3)
        .put(ByteArray(32) { 6 })
        .put(ByteArray(32) { 7 })
        .put(ByteArray(32) { 8 })
        .putShort(1)
        .array()
    val receipt = DestinationReceipt.parse(receiptBytes)
    val journalBytes =
      ByteBuffer.allocate(182)
        .putShort(1)
        .put(id(9))
        .put(id(3))
        .put(id(1))
        .put(id(2))
        .putLong(1)
        .putShort(4)
        .putShort(2)
        .putLong(1)
        .putLong(2)
        .put(id(4))
        .put(id(5))
        .putLong(2)
        .put(id(7))
        .put(digest(encrypted))
        .array()
    val journal = TransferJournal.parse(journalBytes)
    journal.validateReceipt(receipt, encrypted)
    bad { journal.validateReceipt(receipt, encrypted.copyOf().also { it[0] = 2 }) }
    bad {
      journal.validateReceipt(
        DestinationReceipt.parse(receiptBytes.copyOf().also { it[18] = 9 }),
        encrypted,
      )
    }
    bad {
      journal.validateReceipt(
        DestinationReceipt.parse(receiptBytes.copyOf().also { it[58] = 9 }),
        encrypted,
      )
    }
    bad { journal.validateReceipt(receipt, encrypted + 0) }
  }
}
