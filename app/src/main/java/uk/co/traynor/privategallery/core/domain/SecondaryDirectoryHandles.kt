package uk.co.traynor.privategallery.core.domain

import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.SecureDirectoryStream
import java.nio.file.StandardOpenOption.*
import java.nio.file.attribute.BasicFileAttributeView

/**
 * Every domain syscall uses single child names relative to already-open directories.
 * filesDir is the trusted Android context anchor; no path within domain-store is trusted.
 * Unsupported providers fail closed. The thread-local only carries a synchronous IO scope,
 * never a key, a session, or an active-container selector.
 */
internal object SecondaryDirectoryHandles {
  private val active = ThreadLocal<PinnedDirectories>()
  fun <T> withPinned(anchor: Path, directories: List<Path>, expectedKeys: Map<Path, Any>, action: () -> T): T {
    storeCheck(active.get() == null)
    val pinned = PinnedDirectories(anchor, expectedKeys)
    try {
      directories.forEach(pinned::open)
      active.set(pinned)
      val result = action()
      pinned.checkBindings()
      return result
    } finally { active.remove(); pinned.close() }
  }
  fun current(): PinnedDirectories = active.get() ?: throw SecondaryStoreException()
  /** Read/inventory calls anchor at filesDir too; never reopen domain ancestors by absolute path. */
  fun <T> withDirectory(path: Path, action: (SecureDirectoryStream<Path>) -> T): T {
    val absolute = path.toAbsolutePath().normalize()
    var cursor: Path? = absolute
    var anchor = absolute
    while (cursor != null) {
      if (cursor.fileName?.toString() == "domain-store") { anchor = cursor.parent; break }
      cursor = cursor.parent
    }
    val expected = linkedMapOf<Path, Any>()
    cursor = absolute
    while (cursor != null && cursor.startsWith(anchor)) {
      expected[cursor] = DomainInventory.stat(cursor).key
      if (cursor == anchor) break
      cursor = cursor.parent
    }
    return PinnedDirectories(anchor, expected).use { pinned ->
      val result = action(pinned.open(absolute))
      pinned.checkBindings()
      result
    }
  }

  class PinnedDirectories(private val anchor: Path, private val expectedKeys: Map<Path, Any>) : AutoCloseable {
    private val streams = linkedMapOf<Path, SecureDirectoryStream<Path>>()
    private val keys = linkedMapOf<Path, Any>()
    init {
      val before = DomainInventory.stat(anchor)
      storeCheck(before.directory)
      storeCheck(expectedKeys[anchor] == before.key)
      val raw = Files.newDirectoryStream(anchor)
      if (raw !is SecureDirectoryStream<Path>) { raw.close(); throw SecondaryStoreException() }
      try {
        storeCheck(raw.getFileAttributeView(BasicFileAttributeView::class.java).readAttributes().fileKey() == before.key)
        storeCheck(DomainInventory.stat(anchor).key == before.key)
        streams[anchor] = raw; keys[anchor] = before.key
      } catch (failure: Throwable) { raw.close(); throw failure }
    }
    fun open(path: Path): SecureDirectoryStream<Path> {
      streams[path]?.let { return it }
      storeCheck(path.startsWith(anchor) && path != anchor)
      val parent = open(path.parent)
      val before = DomainInventory.stat(path)
      storeCheck(before.directory)
      storeCheck(expectedKeys[path] == before.key)
      val stream = parent.newDirectoryStream(path.fileName, NOFOLLOW_LINKS)
      try {
        storeCheck(stream.getFileAttributeView(BasicFileAttributeView::class.java).readAttributes().fileKey() == before.key)
        storeCheck(DomainInventory.stat(path).key == before.key)
        streams[path] = stream; keys[path] = before.key
        return stream
      } catch (failure: Throwable) { stream.close(); throw failure }
    }
    fun checkBindings() { keys.forEach { (path, key) -> storeCheck(DomainInventory.stat(path).key == key) } }
    fun parent(path: Path): SecureDirectoryStream<Path> = streams[path.parent] ?: throw SecondaryStoreException()
    fun writeNew(path: Path, bytes: ByteArray) {
      checkBindings()
      parent(path).newByteChannel(path.fileName, setOf(CREATE_NEW, WRITE, NOFOLLOW_LINKS)).use { channel ->
        val file = channel as? FileChannel ?: throw SecondaryStoreException()
        val buffer = ByteBuffer.wrap(bytes)
        while (buffer.hasRemaining()) storeCheck(file.write(buffer) > 0)
        file.force(true)
      }
    }
    fun syncDirectory(path: Path) {
      checkBindings()
      val stream = streams[path] ?: throw SecondaryStoreException()
      stream.newByteChannel(path.fileSystem.getPath("."), setOf(READ, NOFOLLOW_LINKS)).use {
        (it as? FileChannel ?: throw SecondaryStoreException()).force(true)
      }
    }
    fun remove(path: Path, directory: Boolean) {
      checkBindings()
      val stream = parent(path)
      val a = stream.getFileAttributeView(path.fileName, BasicFileAttributeView::class.java, NOFOLLOW_LINKS).readAttributes()
      storeCheck(!a.isSymbolicLink && if (directory) a.isDirectory else a.isRegularFile)
      if (directory) stream.deleteDirectory(path.fileName) else stream.deleteFile(path.fileName)
    }
    fun atomicReplace(source: Path, target: Path) {
      checkBindings()
      val from = parent(source); val to = parent(target)
      val attributes = from.getFileAttributeView(source.fileName, BasicFileAttributeView::class.java, NOFOLLOW_LINKS).readAttributes()
      storeCheck(attributes.isRegularFile && !attributes.isSymbolicLink)
      // SecureDirectoryStream.move is atomic. Never fall back to delete + non-atomic move.
      from.move(source.fileName, to, target.fileName)
    }
    fun mkdir(path: Path) {
      checkBindings()
      // SecureDirectoryStream has no mkdirat API. Create an EMPTY staging directory at the
      // trusted context anchor, then atomically move it into the pinned domain parent.
      // No credentials/plaintext are staged here. No domain component is path-resolved.
      val stage = Files.createTempDirectory(anchor, "domain-stage-")
      try {
        checkBindings()
        val from = streams[anchor] ?: throw SecondaryStoreException()
        from.move(stage.fileName, parent(path), path.fileName)
      } finally {
        try { streams[anchor]?.deleteDirectory(stage.fileName) } catch (_: java.nio.file.NoSuchFileException) { }
      }
    }
    override fun close() { streams.values.toList().asReversed().forEach { it.close() } }
  }
}
