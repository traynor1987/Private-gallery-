package uk.co.traynor.privategallery.core.editor.local

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
import java.security.MessageDigest
import java.io.ByteArrayInputStream
import java.util.concurrent.CancellationException

class ModelStoreTest {
    private val bytes = "fixture model data".toByteArray()
    private fun model(hash: String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }) =
        ModelSpec("fixture", "Fixture", "test", "https://huggingface.co/test/test/resolve/0000000000000000000000000000000000000000/model.safetensors", bytes.size.toLong(), hash, "Fixture licence", "licence.txt", 512, 1, 1)
    @Test fun corruptDownloadCannotBecomeInstalled() = runBlocking {
        val root = Files.createTempDirectory("models").toFile()
        try {
            val store = ModelStore(root, { Long.MAX_VALUE }) { _, _ -> ModelStream(ByteArrayInputStream(bytes), 0) }
            assertTrue(runCatching { store.download(model("0".repeat(64))) {} }.isFailure)
            assertFalse(store.isInstalled(model()))
            assertEquals(0, root.listFiles()!!.size)
        } finally { root.deleteRecursively() }
    }
    @Test fun installReverifyAndRemoveOnlyModelFiles() = runBlocking {
        val root = Files.createTempDirectory("models").toFile()
        try {
            val store = ModelStore(root, { Long.MAX_VALUE }) { _, _ -> ModelStream(ByteArrayInputStream(bytes), 0) }
            val progress = mutableListOf<Long>()
            store.download(model()) { progress += it }
            assertEquals(bytes.size.toLong(), progress.last())
            assertTrue(store.isInstalled(model()))
            assertTrue(store.verify(model()))
            store.file(model()).writeBytes(ByteArray(bytes.size))
            assertFalse(store.verify(model()))
            val unrelated = java.io.File(root, "unrelated").apply { writeText("keep") }
            store.remove(model())
            assertTrue(unrelated.exists())
            assertFalse(store.isInstalled(model()))
        } finally { root.deleteRecursively() }
    }
    @Test fun insufficientStorageDoesNotOpenConnection() = runBlocking {
        val root = Files.createTempDirectory("models").toFile()
        try {
            var opened = false
            val store = ModelStore(root, { 0L }) { _, _ -> opened = true; error("must not connect") }
            assertTrue(runCatching { store.download(model()) {} }.isFailure)
            assertFalse(opened)
        } finally { root.deleteRecursively() }
    }
    @Test fun cancelledDownloadNeverActivatesAndCanResume() = runBlocking {
        val root = Files.createTempDirectory("models").toFile()
        try {
            var cancelled = true
            var resumedAt = -1L
            val store = ModelStore(root, { Long.MAX_VALUE }) { _, offset ->
                resumedAt = offset
                ModelStream(object : ByteArrayInputStream(bytes.drop(offset.toInt()).toByteArray()) {
                    override fun read(buffer: ByteArray, off: Int, len: Int) = super.read(buffer, off, minOf(4, len))
                }, offset)
            }
            assertTrue(runCatching { store.download(model()) { if (it > 0 && cancelled) throw CancellationException() } }.isFailure)
            assertFalse(store.isInstalled(model()))
            cancelled = false
            store.download(model()) {}
            assertTrue(resumedAt > 0)
            assertTrue(store.verify(model()))
        } finally { root.deleteRecursively() }
    }
}
