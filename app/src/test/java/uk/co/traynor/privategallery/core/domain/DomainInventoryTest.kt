package uk.co.traynor.privategallery.core.domain

import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Test

class DomainInventoryTest {
  @Test fun freshMeansMissingOrDirectlyEmptyOnly() {
    val dir = Files.createTempDirectory("inventory-test").toFile()
    try {
      assertEquals(SecondaryPreflight.FRESH, SecondaryStore(dir).preflight())
      val root = dir.resolve("domain-store"); root.mkdir()
      assertEquals(SecondaryPreflight.FRESH, SecondaryStore(dir).preflight())
      root.resolve("unknown").mkdir()
      assertEquals(SecondaryPreflight.UNAVAILABLE, SecondaryStore(dir).preflight())
    } finally { dir.deleteRecursively() }
  }
  @Test fun directoryEntryBoundRejectsBeforeCollectingAnUnboundedList() {
    val dir = Files.createTempDirectory("inventory-bound").toFile()
    try {
      val root = dir.resolve("domain-store"); root.mkdir()
      repeat(8193) { Files.createFile(root.resolve("entry-$it").toPath()) }
      try { DomainInventory.children(root.toPath()); fail("unbounded list accepted") } catch (_: SecondaryStoreException) {}
      assertEquals(SecondaryPreflight.UNAVAILABLE, SecondaryStore(dir).preflight())
    } finally { dir.deleteRecursively() }
  }
  @Test fun linkAndPartialAndUnreadableMaterialReject() {
    val dir = Files.createTempDirectory("inventory-test").toFile()
    try {
      val root = dir.resolve("domain-store")
      Files.createSymbolicLink(root.toPath(), dir.toPath())
      assertEquals(SecondaryPreflight.UNAVAILABLE, SecondaryStore(dir).preflight())
      Files.delete(root.toPath()); root.mkdir(); root.resolve("partial").writeText("x")
      assertEquals(SecondaryPreflight.UNAVAILABLE, SecondaryStore(dir).preflight())
      root.resolve("partial").delete(); val nested = root.resolve("unknown"); nested.mkdir()
      Files.setPosixFilePermissions(nested.toPath(), emptySet())
      assertEquals(SecondaryPreflight.UNAVAILABLE, SecondaryStore(dir).preflight())
      Files.setPosixFilePermissions(nested.toPath(), java.nio.file.attribute.PosixFilePermissions.fromString("rwx------"))
    } finally { dir.deleteRecursively() }
  }
}
