package uk.co.traynor.privategallery.core.security

import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes

/** Admission inventory, never a best-effort walk. Links and unknown entries are material.
 * A failed stat/list or an observed change denies admission; no failure means "missing".
 * App-owned writers still recheck admission at their serialized credential promotion. */
internal object PrimaryStorageInventory {
    fun canCreate(filesDir: Path): Boolean = safely { requireEmptyOrMissing(filesDir.resolve("vault"), true) }

    fun isEmptyOrMissing(path: Path): Boolean = safely { requireEmptyOrMissing(path, false) }

    private fun requireEmptyOrMissing(path: Path, rejectRestoreStage: Boolean) {
        val parent = snapshot(checkNotNull(path.parent))
        if (rejectRestoreStage) {
            val restore = parent.path.resolve("vault-restore-staging")
            require(restore.fileName !in parent.children) { "Primary restore owns admission" }
            requireAbsent(restore)
        }
        if (path.fileName !in parent.children) requireAbsent(path)
        else requireEmptyDirectory(path)
        requireUnchanged(parent)
    }

    private fun requireEmptyDirectory(root: Path) {
        val pending = java.util.ArrayDeque<Path>()
        val inspected = mutableListOf<DirectorySnapshot>()
        pending.add(root)
        while (pending.isNotEmpty()) {
            require(inspected.size < 1024) { "Primary inventory exceeds admission bound" }
            val directory = snapshot(pending.removeFirst())
            inspected += directory
            for (name in directory.children) {
                val child = directory.path.resolve(name)
                // NOFOLLOW also rejects dangling links, cycles and paths outside Primary.
                require(attributes(child).isDirectory) { "Existing or unknown Primary material" }
                pending.add(child)
            }
        }
        // Re-list every visited directory, rather than trusting a silently skipped subtree.
        // Replacement, disappearance, additions and removals observed during the scan deny.
        inspected.asReversed().forEach(::requireUnchanged)
    }

    private data class DirectorySnapshot(val path: Path, val identity: BasicFileAttributes,
                                         val children: Set<Path>)

    private fun snapshot(path: Path): DirectorySnapshot {
        val before = attributes(path)
        require(before.isDirectory && !before.isSymbolicLink && Files.isReadable(path) && Files.isExecutable(path)) {
            "Primary directory unavailable"
        }
        val children = Files.newDirectoryStream(path).use { entries ->
            val names = mutableSetOf<Path>()
            for (entry in entries) {
                require(names.size < 4096) { "Primary directory exceeds admission bound" }
                names.add(entry.fileName)
            }
            names.toSet()
        }
        requireSameDirectory(before, attributes(path))
        return DirectorySnapshot(path, before, children)
    }

    private fun requireUnchanged(before: DirectorySnapshot) {
        val after = snapshot(before.path)
        requireSameDirectory(before.identity, after.identity)
        require(before.children == after.children) { "Primary inventory changed" }
    }

    private fun requireSameDirectory(before: BasicFileAttributes, after: BasicFileAttributes) {
        require(after.isDirectory && !after.isSymbolicLink &&
            before.fileKey() != null && before.fileKey() == after.fileKey() &&
            before.lastModifiedTime() == after.lastModifiedTime()) {
            "Primary directory changed"
        }
    }

    private fun requireAbsent(path: Path) {
        try { attributes(path) }
        catch (_: NoSuchFileException) { return }
        error("Primary material appeared")
    }

    private fun attributes(path: Path): BasicFileAttributes =
        Files.readAttributes(path, BasicFileAttributes::class.java, NOFOLLOW_LINKS)

    private inline fun safely(check: () -> Unit): Boolean =
        try { check(); true } catch (_: Exception) { false }
}
