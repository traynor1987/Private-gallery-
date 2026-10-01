package uk.co.traynor.privategallery.core.domain

import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

/** Test-only repository. It cannot import media or select an application container. */
class SecondarySyntheticRepositoryTest {
  @Test fun fixedSiblingSyntheticCiphertextRoundTripContextRejectionAndPrimaryIsolation() {
    val dir = Files.createTempDirectory("synthetic-domain").toFile()
    try {
      val primary = dir.resolve("vault"); primary.mkdir(); primary.resolve("colliding-id").writeText("primary-only")
      val root = dir.resolve("domain-store"); root.mkdir(); val payloads = root.resolve("payloads"); payloads.mkdir()
      val identity = DomainIdentity(F1Crypto.random(16), F1Crypto.random(16)); val master = F1Crypto.random(32); val id = F1Crypto.random(16)
      val context = F1Context(identity, 2, id, 1); val plaintext = ByteArray(32769) { (it * 7).toByte() }
      val bytes = F1Record.encrypt(master, context, plaintext); val ciphertext = payloads.resolve(id.hex())
      val anchor = dir.toPath()
      val directories = listOf(anchor, root.toPath(), payloads.toPath())
      SecondaryDirectoryHandles.withPinned(anchor, directories, directories.associateWith { DomainInventory.stat(it).key }) {
        DurableSecondaryIo.writeNew(ciphertext.toPath(), bytes); DurableSecondaryIo.syncDirectory(payloads.toPath())
      }
      val snapshot = DomainInventory.read(ciphertext.toPath(), 65536)
      val decoded = F1Record.decrypt(master, context, snapshot)
      assertArrayEquals(digest(plaintext), digest(decoded))
      val mismatches = listOf(
        F1Context(DomainIdentity(F1Crypto.random(16), identity.master), 2, id, 1),
        F1Context(DomainIdentity(identity.container, F1Crypto.random(16)), 2, id, 1),
        F1Context(identity, 3, id, 1), F1Context(identity, 2, F1Crypto.random(16), 1), F1Context(identity, 2, id, 2),
      )
      mismatches.forEach { mismatch -> try { F1Record.decrypt(master, mismatch, snapshot); fail("context accepted") } catch (_: F1Exception) {} }
      val bad = snapshot.copyOf(); bad[bad.lastIndex] = (bad.last().toInt() xor 1).toByte()
      try { F1Record.decrypt(master, context, bad); fail("tag accepted") } catch (_: F1Exception) {}
      val version = snapshot.copyOf(); version[9] = 2
      try { F1Record.decrypt(master, context, version); fail("version accepted") } catch (_: F1Exception) {}
      // Existing synthetic material is never fresh and no failed Secondary read tries Primary.
      assertEquals(SecondaryPreflight.UNAVAILABLE, SecondaryStore(dir).preflight())
      try { SecondaryStore(dir).authenticatePin("1234567890123456".toCharArray()); fail("fallback") } catch (_: SecondaryStoreException) {}
      assertEquals("primary-only", primary.resolve("colliding-id").readText())
      assertTrue(ciphertext.canonicalPath.startsWith(root.canonicalPath + java.io.File.separator))
      assertFalse(ciphertext.canonicalPath.startsWith(primary.canonicalPath + java.io.File.separator))
      decoded.fill(0); master.fill(0)
    } finally { dir.deleteRecursively() }
  }
}
