package uk.co.traynor.privategallery.core.security

import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.*
import org.junit.Test

class PrimaryStorageInventoryTest {
    @Test fun missingAndFullyEnumeratedEmptyDirectoriesAreAdmitted() = isolated { parent ->
        val root = parent.resolve("vault")
        assertTrue(PrimaryStorageInventory.isEmptyOrMissing(root))
        Files.createDirectories(root.resolve("payloads/empty"))
        Files.createDirectories(root.resolve("staging"))
        assertTrue(PrimaryStorageInventory.isEmptyOrMissing(root))
    }

    @Test fun unknownAndUnreadableFilesAreMaterialRegardlessOfExtension() = isolated { parent ->
        val root = Files.createDirectories(parent.resolve("vault/payloads"))
        val sole = root.resolve("unknown.fragment")
        Files.write(sole, byteArrayOf(4, 5, 6))
        assertTrue(sole.toFile().setReadable(false, false))
        try {
            assertFalse(PrimaryStorageInventory.isEmptyOrMissing(parent.resolve("vault")))
            assertFalse(PrimaryVaultSetupGuard.canCreate(parent.toFile(), false))
        } finally {
            assertTrue(sole.toFile().setReadable(true, false))
            assertArrayEquals(byteArrayOf(4, 5, 6), Files.readAllBytes(sole))
        }
    }

    @Test fun missingOrNonDirectoryParentCannotEstablishMissingPrimary() = isolated { parent ->
        val unavailable = parent.resolve("missing")
        assertFalse(PrimaryStorageInventory.isEmptyOrMissing(unavailable.resolve("vault")))
        assertFalse(PrimaryStorageInventory.canCreate(unavailable))
        Files.write(unavailable, byteArrayOf(1))
        assertFalse(PrimaryStorageInventory.isEmptyOrMissing(unavailable.resolve("vault")))
    }

    @Test fun danglingRootAndRestoreLinksBlockFreshAdmission() = isolated { parent ->
        for (name in listOf("vault", "vault-restore-staging")) {
            val link = parent.resolve(name)
            Files.createSymbolicLink(link, parent.resolve("missing-target"))
            try { assertFalse(name, PrimaryVaultSetupGuard.canCreate(parent.toFile(), false)) }
            finally { Files.delete(link) }
        }
        assertTrue(PrimaryVaultSetupGuard.canCreate(parent.toFile(), false))
    }

    @Test fun nestedLinksCannotHideForeignEmptyDirectoriesOrCycles() = isolated { parent ->
        val root = Files.createDirectories(parent.resolve("vault/payloads"))
        val foreign = Files.createDirectory(parent.resolve("foreign"))
        for (target in listOf(foreign, root, parent.resolve("missing"))) {
            val link = root.resolve("link")
            Files.createSymbolicLink(link, target)
            try { assertFalse(PrimaryStorageInventory.isEmptyOrMissing(parent.resolve("vault"))) }
            finally { Files.delete(link) }
        }
        assertEquals(0L, Files.list(foreign).use { it.count() })
    }

    @Test fun unavailableFilesystemDoesNotTurnInventoryExceptionIntoAbsence() = isolated { parent ->
        val archive = parent.resolve("synthetic.zip")
        val uri = java.net.URI.create("jar:" + archive.toUri())
        val fs = FileSystems.newFileSystem(uri, mapOf("create" to "true"))
        val root = fs.getPath("/vault")
        fs.close()
        assertFalse(PrimaryStorageInventory.isEmptyOrMissing(root))
        assertFalse(PrimaryStorageInventory.canCreate(root))
    }

    @Test fun excessiveEmptyDirectoryInventoryDeniesRatherThanExpandingWithoutBound() = isolated { parent ->
        val root = Files.createDirectory(parent.resolve("vault"))
        repeat(1024) { Files.createDirectory(root.resolve("empty-$it")) }
        assertFalse(PrimaryStorageInventory.isEmptyOrMissing(root))
        assertFalse(PrimaryVaultSetupGuard.canCreate(parent.toFile(), false))
    }

    @Test fun excessiveSingleDirectoryListingDeniesBeforeUnboundedQueueAllocation() = isolated { parent ->
        val root = Files.createDirectory(parent.resolve("vault"))
        repeat(4097) { Files.createDirectory(root.resolve("empty-$it")) }
        assertFalse(PrimaryStorageInventory.isEmptyOrMissing(root))
    }

    private fun isolated(test: (Path) -> Unit) {
        val parent = Files.createTempDirectory("primary-inventory")
        try { test(parent) } finally { parent.toFile().deleteRecursively() }
    }
}
