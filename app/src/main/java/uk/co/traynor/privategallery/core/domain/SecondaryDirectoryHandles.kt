package uk.co.traynor.privategallery.core.domain

import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.DirectoryStream
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
      return pinned.withAccess {
        directories.forEach(pinned::open)
        active.set(pinned)
        try {
          val result = action()
          pinned.checkBindings()
          result
        } finally { active.remove() }
      }
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
      pinned.withAccess {
        val result = action(pinned.open(absolute))
        pinned.checkBindings()
        result
      }
    }
  }

  class PinnedDirectories(private val anchor: Path, private val expectedKeys: Map<Path, Any>,
    openAnchor: (Path) -> DirectoryStream<Path> = { Files.newDirectoryStream(it) },
  ) : AutoCloseable {
    @Volatile private var closed = false
    private val lifetimeGate = java.lang.Object()
    private val closeResult = java.util.concurrent.CompletableFuture<Unit>()
    private var closingThread: Thread? = null
    private var usingThread: Thread? = null
    private var uses = 0

    /** A synchronous scope, not an authority or an ownership-registration substitute.
     * Close seals new ingress, then waits for the actual admitted Native borrower.
     * No Native operation executes while lifetimeGate is held. */
    internal fun <T> withAccess(action: () -> T): T {
      synchronized(lifetimeGate) {
        check(!closed) { "Pinned directory scope closed" }
        check(usingThread == null || usingThread === Thread.currentThread()) { "Concurrent pinned directory access" }
        check(uses < Int.MAX_VALUE)
        usingThread = Thread.currentThread(); uses++
      }
      try { return action() }
      finally {
        synchronized(lifetimeGate) {
          uses--
          if (uses == 0) { usingThread = null; lifetimeGate.notifyAll() }
        }
      }
    }
    private var preparedMove: PreparedReplace? = null
    private val streams = linkedMapOf<Path, SecureDirectoryStream<Path>>()
    private val keys = linkedMapOf<Path, Any>()
    init {
      val before = DomainInventory.stat(anchor)
      storeCheck(before.directory)
      storeCheck(expectedKeys[anchor] == before.key)
      val raw = openAnchor(anchor)
      if (raw !is SecureDirectoryStream<Path>) { raw.close(); throw SecondaryStoreException() }
      try {
        storeCheck(raw.getFileAttributeView(BasicFileAttributeView::class.java).readAttributes().fileKey() == before.key)
        storeCheck(DomainInventory.stat(anchor).key == before.key)
        streams[anchor] = raw; keys[anchor] = before.key
      } catch (failure: Throwable) { raw.close(); throw failure }
    }
    fun open(path: Path): SecureDirectoryStream<Path> = withAccess {
      streams[path]?.let { return@withAccess it }
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
        stream
      } catch (failure: Throwable) { stream.close(); throw failure }
    }
    fun checkBindings() = withAccess { keys.forEach { (path, key) -> storeCheck(DomainInventory.stat(path).key == key) } }
    internal fun parent(path: Path): SecureDirectoryStream<Path> = withAccess { streams[path.parent] ?: throw SecondaryStoreException() }
    fun writeNew(path: Path, bytes: ByteArray) = withAccess {
      checkBindings()
      parent(path).newByteChannel(path.fileName, setOf(CREATE_NEW, WRITE, NOFOLLOW_LINKS)).use { channel ->
        val file = channel as? FileChannel ?: throw SecondaryStoreException()
        val buffer = ByteBuffer.wrap(bytes)
        while (buffer.hasRemaining()) storeCheck(file.write(buffer) > 0)
        file.force(true)
      }
    }
    fun syncDirectory(path: Path) = withAccess {
      checkBindings()
      val stream = streams[path] ?: throw SecondaryStoreException()
      stream.newByteChannel(path.fileSystem.getPath("."), setOf(READ, NOFOLLOW_LINKS)).use {
        (it as? FileChannel ?: throw SecondaryStoreException()).force(true)
      }
    }
    fun remove(path: Path, directory: Boolean) = withAccess {
      checkBindings()
      val stream = parent(path)
      val a = stream.getFileAttributeView(path.fileName, BasicFileAttributeView::class.java, NOFOLLOW_LINKS).readAttributes()
      storeCheck(!a.isSymbolicLink && if (directory) a.isDirectory else a.isRegularFile)
      if (directory) stream.deleteDirectory(path.fileName) else stream.deleteFile(path.fileName)
    }
    /** Perform all source/parent Native verification before entering an authorization gate.
     * The resulting action is synchronous and one-shot; parent ownership remains with this
     * pinned scope. This is not a resource or authentication authority transfer. */
    fun prepareAtomicReplace(source: Path, target: Path): PreparedReplace = withAccess {
      check(!closed) { "Pinned directory scope closed" }
      checkBindings()
      val from = parent(source); val to = parent(target)
      val attributes = from.getFileAttributeView(source.fileName, BasicFileAttributeView::class.java, NOFOLLOW_LINKS).readAttributes()
      storeCheck(attributes.isRegularFile && !attributes.isSymbolicLink)
      PreparedReplace(this, source, target, from, to).also { preparedMove = it }
    }
    fun atomicReplace(source: Path, target: Path) {
      check(!closed) { "Pinned directory scope closed" }
      // The prepared path runs under authorization gates. Its whole consumer borrow
      // was admitted by withPinned BEFORE those gates; never acquire a reader gate here.
      val ready = preparedMove?.takeIf { it.matches(source, target) }
      if (ready != null) { ready.commit(); return }
      withAccess { prepareAtomicReplace(source, target).commit() }
    }
    class PreparedReplace internal constructor(
      private val owner: PinnedDirectories,
      private val source: Path,
      private val target: Path,
      private val from: SecureDirectoryStream<Path>,
      private val to: SecureDirectoryStream<Path>,
    ) {
      private val thread = Thread.currentThread()
      private var used = false
      internal fun matches(source: Path, target: Path) = this.source == source && this.target == target
      fun commit() {
        // Same-thread outer scope already owns the Native borrow. No new rank5
        // gate may be acquired from the controller/authority publication gates.
        check(Thread.currentThread() === thread && owner.usingThread === thread && owner.uses > 0 && !owner.closed && !used) { "Prepared selection unavailable" }
        used = true
        // Only the prepared relative atomic publication is allowed inside the short gate.
        // Never fall back to unlink + non-atomic move, reopen parents, verify or close here.
        from.move(source.fileName, to, target.fileName)
      }
    }
    fun mkdir(path: Path) = withAccess {
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
    override fun close() {
      val first = synchronized(lifetimeGate) {
        check(usingThread !== Thread.currentThread()) { "Pinned borrower cannot retire itself" }
        check(closingThread !== Thread.currentThread()) { "Pinned close cannot join itself" }
        if (closed) false else { closed = true; closingThread = Thread.currentThread(); true }
      }
      if (!first) {
        try { closeResult.get() }
        catch (interrupted: InterruptedException) { Thread.currentThread().interrupt(); throw interrupted }
        catch (failed: java.util.concurrent.ExecutionException) { throw checkNotNull(failed.cause) }
        return
      }
      var failure: Throwable? = null
      var interrupted = false
      try {
        synchronized(lifetimeGate) {
          while (usingThread != null) {
            try { lifetimeGate.wait() }
            catch (_: InterruptedException) { interrupted = true }
          }
        }
        preparedMove = null
        // Attempt every sibling after a returned failure. A hung close still needs the
        // independently funded dispatch migration; this is not a replacement for it.
        for (stream in streams.values.toList().asReversed()) {
          try { stream.close() }
          catch (problem: Throwable) {
            if (problem is InterruptedException) interrupted = true
            if (failure == null) failure = problem
            else if (failure !== problem) failure.addSuppressed(problem)
          }
        }
      } catch (problem: Throwable) {
        if (problem is InterruptedException) interrupted = true
        failure = problem
      } finally {
        synchronized(lifetimeGate) { closingThread = null }
        if (interrupted) Thread.currentThread().interrupt()
        if (failure == null) closeResult.complete(Unit) else closeResult.completeExceptionally(failure)
      }
      failure?.let { throw it }
    }
  }
}
