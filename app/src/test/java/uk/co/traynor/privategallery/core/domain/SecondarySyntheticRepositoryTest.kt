package uk.co.traynor.privategallery.core.domain

import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test
import uk.co.traynor.privategallery.core.media.EncryptedPreviewCache
import uk.co.traynor.privategallery.core.vault.EncryptedPayloadStore

/** Test-only repository. It cannot import media or select an application container. */
class SecondarySyntheticRepositoryTest {
  @Test fun identicalPayloadNamesAcrossFixedRootsDoNotSelectTheOtherCiphertext() {
    val dir = Files.createTempDirectory("domain-payload-collision").toFile()
    val primaryMaster = F1Crypto.random(32); val secondaryMaster = F1Crypto.random(32)
    try {
      val objectId = F1Crypto.random(16); val id = objectId.hex()
      val primary = EncryptedPayloadStore(dir.resolve("vault"))
      val primaryBody = "primary payload only".toByteArray()
      val stored = primary.writeAndVerify(id, primaryBody.inputStream(), primaryMaster)
      val secondaryRoot = dir.resolve("domain-store/payloads").apply { mkdirs() }
      val secondaryFile = secondaryRoot.resolve(stored.file.name)
      val context = F1Context(DomainIdentity(F1Crypto.random(16), F1Crypto.random(16)), 2, objectId, 1)
      val secondaryBody = "secondary synthetic payload only".toByteArray()
      secondaryFile.writeBytes(F1Record.encrypt(secondaryMaster, context, secondaryBody))
      assertEquals(stored.file.name, secondaryFile.name)
      assertNotEquals(stored.file.canonicalPath, secondaryFile.canonicalPath)
      assertArrayEquals(primaryBody, primary.decryptToBytes(stored, primaryMaster))
      assertArrayEquals(secondaryBody, F1Record.decrypt(secondaryMaster, context, secondaryFile.readBytes()))
      assertThrows(F1Exception::class.java) { F1Record.decrypt(primaryMaster, context, secondaryFile.readBytes()) }
      assertFalse(primary.verify(stored, secondaryMaster))
      val secondaryBefore = secondaryFile.readBytes()
      val retired = primary.retireForDeletion(id)
      assertArrayEquals(secondaryBefore, secondaryFile.readBytes())
      // Missing Primary ciphertext must not fall back to the colliding sibling object.
      assertThrows(java.io.FileNotFoundException::class.java) { primary.decryptToBytes(stored, primaryMaster) }
      assertTrue(retired.exists()); assertTrue(primary.restoreRetiredPayload(id))
      assertArrayEquals(primaryBody, primary.decryptToBytes(stored, primaryMaster))
      secondaryFile.delete()
      assertArrayEquals(primaryBody, primary.decryptToBytes(stored, primaryMaster))
    } finally { primaryMaster.fill(0); secondaryMaster.fill(0); dir.deleteRecursively() }
  }

  @Test fun identicalPreviewNamesAcrossFixedRootsDoNotShareEvictionOrKeys() {
    val dir = Files.createTempDirectory("domain-preview-collision").toFile()
    val primaryMaster = F1Crypto.random(32); val secondaryMaster = F1Crypto.random(32)
    try {
      val objectId = F1Crypto.random(16); val id = objectId.hex(); val revision = "1"
      val primaryRoot = dir.resolve("vault/previews")
      val cache = EncryptedPreviewCache(primaryRoot)
      val primaryBody = "primary preview only".toByteArray()
      cache.put(id, revision, primaryBody, primaryMaster)
      val primaryFile = primaryRoot.listFiles()!!.single()
      val secondaryFile = dir.resolve("domain-store/previews").apply { mkdirs() }.resolve(primaryFile.name)
      val context = F1Context(DomainIdentity(F1Crypto.random(16), F1Crypto.random(16)), 3, objectId, 1)
      val secondaryBody = "secondary synthetic preview only".toByteArray()
      secondaryFile.writeBytes(F1Record.encrypt(secondaryMaster, context, secondaryBody))
      assertEquals(primaryFile.name, secondaryFile.name)
      assertArrayEquals(primaryBody, cache.get(id, revision, primaryMaster))
      assertArrayEquals(secondaryBody, F1Record.decrypt(secondaryMaster, context, secondaryFile.readBytes()))
      assertThrows(F1Exception::class.java) { F1Record.decrypt(primaryMaster, context, secondaryFile.readBytes()) }
      val secondaryBefore = secondaryFile.readBytes()
      // Failed authentication deletes only the Primary cache entry.
      assertNull(cache.get(id, revision, secondaryMaster))
      assertArrayEquals(secondaryBefore, secondaryFile.readBytes())
      assertNull(cache.get(id, revision, primaryMaster))
      cache.put(id, revision, primaryBody, primaryMaster)
      cache.put(id, "2", byteArrayOf(9), primaryMaster)
      assertNull(cache.get(id, revision, primaryMaster))
      assertArrayEquals(secondaryBefore, secondaryFile.readBytes())
      cache.remove(id)
      assertArrayEquals(secondaryBody, F1Record.decrypt(secondaryMaster, context, secondaryFile.readBytes()))
    } finally { primaryMaster.fill(0); secondaryMaster.fill(0); dir.deleteRecursively() }
  }

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
      assertEquals(SecondaryPreflight.UNAVAILABLE, testStore(dir).preflight())
      try { testStore(dir).authenticatePin("1234567890123456".toCharArray()); fail("fallback") } catch (_: SecondaryStoreException) {}
      assertEquals("primary-only", primary.resolve("colliding-id").readText())
      assertTrue(ciphertext.canonicalPath.startsWith(root.canonicalPath + java.io.File.separator))
      assertFalse(ciphertext.canonicalPath.startsWith(primary.canonicalPath + java.io.File.separator))
      decoded.fill(0); master.fill(0)
    } finally { dir.deleteRecursively() }
  }
}
