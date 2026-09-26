package uk.co.traynor.privategallery.core.editor

import java.io.File

/** Explicit, owner-initiated cleanup of the two retired model IDs in noBackupFilesDir.
 * Never traverses directories or accepts paths supplied by a page, provider or user.
 */
class RetiredModelStorage(noBackupFilesDir: File) {
    private val root = File(noBackupFilesDir, "ai-models")
    private val names = listOf("sd15-fp16-v1", "sdxl-base-1-v1")
        .flatMap { id -> listOf("$id.safetensors", "$id.part", "$id.verified") }
    private fun candidates(): List<File> {
        if (!root.isDirectory || java.nio.file.Files.isSymbolicLink(root.toPath())) return emptyList()
        return names.map { File(root, it) }.filter { it.isFile && !java.nio.file.Files.isSymbolicLink(it.toPath()) }
    }
    fun reclaimableBytes(): Long = candidates().sumOf { it.length() }
    fun remove(): Long {
        var reclaimed = 0L
        for (file in candidates()) {
            val size = file.length()
            check(file.delete()) { "Could not remove downloaded AI models." }
            reclaimed += size
        }
        return reclaimed
    }
}
