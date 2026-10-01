package uk.co.traynor.privategallery.core.domain

import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.PosixFilePermission

enum class SecondaryPreflight { FRESH, PENDING, READY, UNAVAILABLE }
class SecondaryStoreException : SecurityException("UNAVAILABLE")
internal fun storeCheck(ok: Boolean) { if (!ok) throw SecondaryStoreException() }

/** No failure/permission/link is interpreted as absence. Never follows a directory entry link. */
internal object DomainInventory {
  data class Entry(val key: Any, val directory: Boolean, val size: Long, val modified: Long)
  fun stat(path: Path): Entry {
    val a = Files.readAttributes(path, BasicFileAttributes::class.java, NOFOLLOW_LINKS)
    storeCheck(!a.isSymbolicLink && (a.isDirectory || a.isRegularFile))
    storeCheck(Files.isReadable(path) && (!a.isDirectory || Files.isExecutable(path)))
    // Effective-root permission checks alone hide genuinely inaccessible material in JVM tests.
    // Android deliberately rejects getFileStore even for app-private paths. Query
    // the path's attribute view directly; a failed permissions read still closes admission.
    val posix = Files.getFileAttributeView(path, java.nio.file.attribute.PosixFileAttributeView::class.java, NOFOLLOW_LINKS)
    if (posix != null) {
      val p = posix.readAttributes().permissions()
      storeCheck(p.any { it == PosixFilePermission.OWNER_READ || it == PosixFilePermission.GROUP_READ || it == PosixFilePermission.OTHERS_READ })
      if (a.isDirectory) storeCheck(p.any { it == PosixFilePermission.OWNER_EXECUTE || it == PosixFilePermission.GROUP_EXECUTE || it == PosixFilePermission.OTHERS_EXECUTE })
    }
    return Entry(a.fileKey() ?: throw SecondaryStoreException(), a.isDirectory, a.size(), a.lastModifiedTime().toMillis())
  }
  fun children(path: Path, limit: Int = 8192): List<Path> {
    val before = stat(path); storeCheck(before.directory)
    storeCheck(limit in 0..8192)
    val result = SecondaryDirectoryHandles.withDirectory(path) { stream ->
      val found = ArrayList<Path>()
      for (entry in stream) { storeCheck(found.size < limit); found.add(entry) }
      found.sortedBy { it.fileName.toString() }
    }
    storeCheck(before == stat(path)); return result
  }
  fun missingChild(parent: Path, name: String): Boolean = children(parent).none { it.fileName.toString() == name }
  fun snapshot(root: Path): Map<String, Entry> {
    val entries = linkedMapOf<String, Entry>()
    fun visit(p: Path, depth: Int) {
      storeCheck(depth <= 5 && entries.size < 8192)
      val before = stat(p); entries[root.relativize(p).toString()] = before
      if (before.directory) children(p, 8192 - entries.size).forEach { visit(it, depth + 1) }
      storeCheck(before == stat(p))
    }
    visit(root, 0); return entries
  }
  fun read(path: Path, bound: Int): ByteArray {
    val before = stat(path); storeCheck(!before.directory && before.size in 0..bound.toLong())
    val bytes = SecondaryDirectoryHandles.withDirectory(path.parent) { parent ->
      parent.newByteChannel(path.fileName, setOf(java.nio.file.StandardOpenOption.READ, NOFOLLOW_LINKS)).use { channel ->
        val buffer = java.nio.ByteBuffer.allocate(before.size.toInt())
        while (buffer.hasRemaining()) { storeCheck(channel.read(buffer) > 0) }
        storeCheck(channel.read(java.nio.ByteBuffer.allocate(1)) == -1)
        buffer.array()
      }
    }
    storeCheck(before == stat(path)); return bytes
  }
}

/** Real durable IO seams: tests inject failures around actual writes, syncs and atomic selection. */
internal interface SecondaryStorageIo {
  fun mkdir(path: Path)
  fun writeNew(path: Path, bytes: ByteArray)
  fun syncDirectory(path: Path)
  fun atomicReplace(source: Path, target: Path)
  fun remove(path: Path, directory: Boolean) { SecondaryDirectoryHandles.current().remove(path, directory) }
}
internal object DurableSecondaryIo : SecondaryStorageIo {
  override fun mkdir(path: Path) = SecondaryDirectoryHandles.current().mkdir(path)
  override fun writeNew(path: Path, bytes: ByteArray) = SecondaryDirectoryHandles.current().writeNew(path, bytes)
  override fun syncDirectory(path: Path) = SecondaryDirectoryHandles.current().syncDirectory(path)
  override fun atomicReplace(source: Path, target: Path) = SecondaryDirectoryHandles.current().atomicReplace(source, target)
}
