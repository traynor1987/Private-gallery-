package uk.co.traynor.privategallery.core.vault

/** Conservative byte-signature fallback for untrusted remote content-type hints. */
object VaultMimePolicy {
    fun effectiveType(hint: String, name: String, prefix: ByteArray): String {
        if (hint.startsWith("image/") || hint.startsWith("video/")) return hint
        detected(prefix)?.let { return it }
        return extensionType(name) ?: hint.ifBlank { "application/octet-stream" }
    }

    private fun detected(bytes: ByteArray): String? = when {
        bytes.size >= 8 && bytes.copyOfRange(0, 8).contentEquals(byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)) -> "image/png"
        bytes.size >= 3 && bytes[0] == 0xff.toByte() && bytes[1] == 0xd8.toByte() && bytes[2] == 0xff.toByte() -> "image/jpeg"
        bytes.size >= 6 && String(bytes, 0, 6, Charsets.US_ASCII) in setOf("GIF87a", "GIF89a") -> "image/gif"
        bytes.size >= 12 && String(bytes, 0, 4, Charsets.US_ASCII) == "RIFF" && String(bytes, 8, 4, Charsets.US_ASCII) == "WEBP" -> "image/webp"
        bytes.size >= 12 && String(bytes, 4, 4, Charsets.US_ASCII) == "ftyp" -> "video/mp4"
        else -> null
    }

    private fun extensionType(name: String): String? = when (name.substringAfterLast('.', "").lowercase()) {
        "png" -> "image/png"; "jpg", "jpeg" -> "image/jpeg"; "gif" -> "image/gif"; "webp" -> "image/webp"
        "mp4", "m4v" -> "video/mp4"; else -> null
    }
}
