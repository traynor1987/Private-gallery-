package uk.co.traynor.privategallery.core.browser.v2

import java.io.IOException

/** Reads only the original bounded buffer. No decoded String, copied array or substring. */
internal object ManifestProtectionPolicy {
    fun isProtected(bytes: ByteArray, length: Int): Boolean {
        require(length >= 0 && length <= bytes.size && length <= 64 * 1024) { "Unsupported manifest bound" }
        var cursor = 0
        while (cursor < length) {
            val rune = rune(bytes, cursor, length)
            val code = (rune ushr 3).toInt()
            if (code == 0 || code in 1..8 || code in 11..12 || code in 14..31 || code in 127..159)
                throw IOException("Unsupported manifest text encoding")
            cursor += (rune and 7).toInt()
        }
        // Preserve XML's optional UTF8 BOM; it cannot hide a first-line HLS key marker.
        var lineStart = if (length >= 3 && unsigned(bytes[0]) == 0xef && unsigned(bytes[1]) == 0xbb && unsigned(bytes[2]) == 0xbf) 3 else 0
        while (lineStart < length) {
            var lineEnd = lineStart
            while (lineEnd < length && bytes[lineEnd] != 10.toByte() && bytes[lineEnd] != 13.toByte()) lineEnd++
            var first = lineEnd
            var last = lineStart
            cursor = lineStart
            while (cursor < lineEnd) {
                val rune = rune(bytes, cursor, lineEnd)
                val code = (rune ushr 3).toInt()
                val next = cursor + (rune and 7).toInt()
                if (!Character.isWhitespace(code) && !Character.isSpaceChar(code)) {
                    if (first == lineEnd) first = cursor
                    last = next
                }
                cursor = next
            }
            if (markerEnd(bytes, first, last, SESSION_KEY) >= 0) return true
            val keyEnd = markerEnd(bytes, first, last, KEY)
            if (keyEnd >= 0 && !onlyNone(bytes, keyEnd, last)) return true
            lineStart = lineEnd + 1 // CRLF's empty second line is harmless.
        }
        cursor = 0
        while (cursor < length) {
            if (bytes[cursor] == '<'.code.toByte()) {
                // Keep the earlier conservative prefix rejection, including malformed suffixes.
                if (markerEnd(bytes, cursor + 1, length, CONTENT_PROTECTION) >= 0) return true
                var name = cursor + 1
                while (name < length && nameByte(unsigned(bytes[name]))) {
                    // Prefixes may be valid UTF8 names; ambiguous additional colons
                    // cannot erase a protection component already encountered.
                    if (bytes[name] == ':'.code.toByte() && markerEnd(bytes, name + 1, length, CONTENT_PROTECTION) >= 0) return true
                    name++
                }
            }
            cursor++
        }
        return false
    }

    /** NONE may be the sole field. Quoted URI bait, duplicate/other fields never grant eligibility. */
    private fun onlyNone(bytes: ByteArray, start: Int, end: Int): Boolean {
        var cursor = spaces(bytes, start, end)
        if (!matches(bytes, cursor, end, METHOD)) return false
        cursor = spaces(bytes, cursor + METHOD.length, end)
        if (cursor >= end || bytes[cursor] != '='.code.toByte()) return false
        cursor = spaces(bytes, cursor + 1, end)
        if (!matches(bytes, cursor, end, NONE)) return false
        return spaces(bytes, cursor + NONE.length, end) == end
    }

    private fun spaces(bytes: ByteArray, start: Int, end: Int): Int {
        var cursor = start
        while (cursor < end && (bytes[cursor] == 32.toByte() || bytes[cursor] == 9.toByte())) cursor++
        return cursor
    }
    /** Retain legacy Unicode case-equivalent marker denial, using the actual byte endpoint. */
    private fun markerEnd(bytes: ByteArray, start: Int, end: Int, literal: String): Int {
        if (start < 0) return -1
        var cursor = start
        var index = 0
        while (index < literal.length) {
            if (cursor >= end) return -1
            val actual = rune(bytes, cursor, end)
            val code = (actual ushr 3).toInt()
            val folded = Character.toLowerCase(Character.toUpperCase(code))
            if (folded != literal[index].uppercaseChar().lowercaseChar().code) return -1
            cursor += (actual and 7).toInt()
            index++
        }
        return cursor
    }
    private fun matches(bytes: ByteArray, start: Int, end: Int, literal: String): Boolean {
        if (start < 0 || end - start < literal.length) return false
        var index = 0
        while (index < literal.length) {
            val actual = unsigned(bytes[start + index])
            val folded = if (actual in 97..122) actual - 32 else actual
            if (folded != literal[index].uppercaseChar().code) return false
            index++
        }
        return true
    }
    private fun nameByte(code: Int): Boolean = code >= 128 || code in 65..90 || code in 97..122 || code in 48..57 || code == 58 || code == 95 || code == 45 || code == 46
    private fun unsigned(value: Byte): Int = value.toInt() and 255

    /** Packed primitive code point/width; invalid, overlong, surrogate and incomplete UTF8 deny. */
    private fun rune(bytes: ByteArray, start: Int, end: Int): Long {
        val first = unsigned(bytes[start])
        val width: Int
        var code: Int
        when (first) {
            in 0..127 -> { width = 1; code = first }
            in 0xc2..0xdf -> { width = 2; code = first and 31 }
            in 0xe0..0xef -> { width = 3; code = first and 15 }
            in 0xf0..0xf4 -> { width = 4; code = first and 7 }
            else -> throw IOException("Unsupported manifest UTF8")
        }
        if (width > end - start) throw IOException("Incomplete manifest UTF8")
        var index = 1
        while (index < width) {
            val next = unsigned(bytes[start + index])
            if (next and 0xc0 != 0x80) throw IOException("Unsupported manifest UTF8")
            code = (code shl 6) or (next and 63)
            index++
        }
        if ((width == 2 && code < 0x80) || (width == 3 && code < 0x800) || (width == 4 && code < 0x10000) ||
            code in 0xd800..0xdfff || code > 0x10ffff) throw IOException("Unsupported manifest UTF8")
        return (code.toLong() shl 3) or width.toLong()
    }
    private const val KEY = "#EXT-X-KEY:"
    private const val SESSION_KEY = "#EXT-X-SESSION-KEY:"
    private const val METHOD = "METHOD"
    private const val NONE = "NONE"
    private const val CONTENT_PROTECTION = "CONTENTPROTECTION"
}
