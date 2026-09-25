package uk.co.traynor.privategallery.core.editor.local

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.*
import java.security.MessageDigest

class ModelStream(val input: InputStream, val offset: Long, private val closeConnection: () -> Unit = {}) : Closeable {
    override fun close() { try { input.close() } finally { closeConnection() } }
}
/** All paths derive from validated manifest IDs. Never accepts an image, Vault path or arbitrary filename. */
class ModelStore(
    private val root: File,
    private val freeBytes: () -> Long = { root.usableSpace },
    private val open: (ModelSpec, Long) -> ModelStream = ModelHttps::open,
) {
    private val mutex = Mutex()
    init { check(root.mkdirs() || root.isDirectory) }
    fun file(model: ModelSpec) = File(root, "${model.id}.safetensors")
    private fun partial(model: ModelSpec) = File(root, "${model.id}.part")
    private fun marker(model: ModelSpec) = File(root, "${model.id}.verified")
    fun usedBytes(): Long = root.listFiles()?.filter { it.isFile }?.sumOf { it.length() } ?: 0L
    fun isInstalled(model: ModelSpec): Boolean = file(model).length() == model.bytes &&
        runCatching { marker(model).readText() == model.sha256 }.getOrDefault(false)
    suspend fun verify(model: ModelSpec): Boolean = withContext(Dispatchers.IO) { mutex.withLock {
        if (!isInstalled(model)) return@withLock false
        val valid = digest(file(model)) == model.sha256
        if (!valid) marker(model).delete()
        valid
    } }
    suspend fun remove(model: ModelSpec) = withContext(Dispatchers.IO) { mutex.withLock {
        listOf(marker(model), file(model), partial(model)).forEach { check(!it.exists() || it.delete()) { "Could not remove model." } }
    } }
    suspend fun download(model: ModelSpec, progress: (Long) -> Unit) = withContext(Dispatchers.IO) { mutex.withLock {
        val part = partial(model)
        if (part.length() > model.bytes) check(part.delete())
        var offset = part.length()
        check(freeBytes() >= model.bytes - offset + STORAGE_RESERVE) { "Not enough storage for this model and safety reserve." }
        marker(model).delete()
        progress(offset)
        if (offset < model.bytes) {
            open(model, offset).use { response ->
                require(response.offset == 0L || response.offset == offset) { "Invalid download range." }
                if (response.offset == 0L) offset = 0L
                FileOutputStream(part, offset > 0).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    try {
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val count = response.input.read(buffer)
                            if (count < 0) break
                            require(offset + count <= model.bytes) { "Model download is larger than its manifest." }
                            output.write(buffer, 0, count); offset += count
                            progress(offset)
                        }
                        output.fd.sync()
                    } finally { buffer.fill(0) }
                }
            }
        }
        currentCoroutineContext().ensureActive()
        check(part.length() == model.bytes) { "Download incomplete. Retry to resume." }
        if (digest(part) != model.sha256) {
            part.delete()
            error("Model integrity verification failed. Download again.")
        }
        currentCoroutineContext().ensureActive()
        check(part.renameTo(file(model))) { "Could not install model." }
        // Crash before marker creation leaves the file inactive, never falsely installed.
        FileOutputStream(marker(model)).use { it.write(model.sha256.toByteArray()); it.fd.sync() }
        progress(model.bytes)
    } }
    private suspend fun digest(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        try { file.inputStream().use { input -> while (true) {
            currentCoroutineContext().ensureActive()
            val count = input.read(buffer); if (count < 0) break
            digest.update(buffer, 0, count)
        } } } finally { buffer.fill(0) }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
    companion object { const val STORAGE_RESERVE = 512L * 1024 * 1024 }
}
