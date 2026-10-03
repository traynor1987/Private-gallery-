package uk.co.traynor.privategallery.core.browser.v2

/** Classifies only the declared slice of the original bounded owned transfer/header array. */
internal object VideoHeaderBytePolicy {
    fun reason(mime: String, bytes: ByteArray, count: Int): MediaSaveReason? {
        requireSlice(bytes, count)
        var leading = 0
        while (leading < count) {
            val packed = decoded(bytes, leading, count)
            if (packed < 0) break
            val point = packed.toInt()
            if (!Character.isWhitespace(point) && !Character.isSpaceChar(point)) break
            leading += (packed ushr 32).toInt()
        }
        if (starts(bytes, leading, count, "#EXTM3U") || starts(bytes, leading, count, "<MPD"))
            return MediaSaveReason.MANIFEST_DETECTED
        if (starts(bytes, leading, count, "<html") || starts(bytes, leading, count, "<!doctype") ||
            starts(bytes, leading, count, "{")) return MediaSaveReason.NON_MEDIA_RESPONSE
        return if (valid(mime, bytes, count)) null else MediaSaveReason.NON_MEDIA_RESPONSE
    }

    fun valid(mime: String, bytes: ByteArray, count: Int): Boolean {
        requireSlice(bytes, count)
        return when (mime) {
            "video/mp4", "video/quicktime" -> count >= 12 && bytes[4] == 0x66.toByte() &&
                bytes[5] == 0x74.toByte() && bytes[6] == 0x79.toByte() && bytes[7] == 0x70.toByte()
            "video/webm", "video/x-matroska" -> count >= 4 && bytes[0] == 0x1a.toByte() &&
                bytes[1] == 0x45.toByte() && bytes[2] == 0xdf.toByte() && bytes[3] == 0xa3.toByte()
            "video/mp2t" -> count >= 1 && bytes[0] == 0x47.toByte()
            else -> false
        }
    }

    private fun requireSlice(bytes: ByteArray, count: Int) {
        require(count >= 0 && count <= bytes.size && bytes.size <= 64 * 1024) { "Invalid original header slice" }
    }

    private fun starts(bytes: ByteArray, offset: Int, count: Int, marker: String): Boolean {
        var cursor = offset
        var index = 0
        while (index < marker.length) {
            val packed = decoded(bytes, cursor, count)
            if (packed < 0) return false
            val actual = Character.toLowerCase(Character.toUpperCase(packed.toInt()))
            val expected = Character.toLowerCase(Character.toUpperCase(marker[index].code))
            if (actual != expected) return false
            cursor += (packed ushr 32).toInt()
            index++
        }
        return true
    }

    /** Primitive point/width pair; invalid or incomplete UTF8 never reads outside count. */
    private fun decoded(bytes: ByteArray, offset: Int, count: Int): Long {
        if (offset >= count) return -1
        val first = bytes[offset].toInt() and 0xff
        if (first < 0x80) return (1L shl 32) or first.toLong()
        val width: Int
        val minimum: Int
        var point: Int
        when (first) {
            in 0xc2..0xdf -> { width = 2; minimum = 0x80; point = first and 0x1f }
            in 0xe0..0xef -> { width = 3; minimum = 0x800; point = first and 0x0f }
            in 0xf0..0xf4 -> { width = 4; minimum = 0x10000; point = first and 0x07 }
            else -> return -1
        }
        if (count - offset < width) return -1
        var part = 1
        while (part < width) {
            val next = bytes[offset + part].toInt() and 0xff
            if (next and 0xc0 != 0x80) return -1
            point = (point shl 6) or (next and 0x3f)
            part++
        }
        if (point < minimum || point > 0x10ffff || point in 0xd800..0xdfff) return -1
        return (width.toLong() shl 32) or point.toLong()
    }
}
