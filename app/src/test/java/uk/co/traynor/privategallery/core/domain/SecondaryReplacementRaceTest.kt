package uk.co.traynor.privategallery.core.domain

import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.*
import org.junit.Test

class SecondaryReplacementRaceTest {
  @Test fun ordinaryRootReplacementCannotRedirectSelectionIntoPrimary() {
    val dir = Files.createTempDirectory("replacement-selection").toFile()
    try {
      val root = dir.resolve("domain-store").toPath()
      val parked = dir.resolve("parked-domain").toPath()
      val primary = dir.resolve("vault").toPath()
      Files.createDirectory(primary)
      Files.createDirectory(primary.resolve("temporary"))
      Files.write(primary.resolve("selected"), "primary-canary".toByteArray())
      var injected = false
      val io = object : SecondaryStorageIo by DurableSecondaryIo {
        override fun atomicReplace(source: Path, target: Path) {
          if (target == root.resolve("selected")) {
            injected = true
            val pointer = Files.readAllBytes(source)
            Files.move(root, parked)
            Files.move(primary, root)
            Files.write(root.resolve("temporary").resolve(source.fileName), pointer)
            try { DurableSecondaryIo.atomicReplace(source, target) }
            finally { Files.move(root, primary); Files.move(parked, root) }
          } else DurableSecondaryIo.atomicReplace(source, target)
        }
      }
      try { SecondaryStore(dir, io, Unit).create("1234567890123456".toCharArray(), {}, { it() }).close() }
      catch (_: SecondaryStoreException) { }
      assertTrue(injected)
      assertArrayEquals("primary-canary".toByteArray(), Files.readAllBytes(primary.resolve("selected")))
    } finally { dir.deleteRecursively() }
  }
}
