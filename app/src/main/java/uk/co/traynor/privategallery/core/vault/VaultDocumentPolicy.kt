package uk.co.traynor.privategallery.core.vault

import java.io.FilterInputStream
import java.io.InputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

/** Documents are inert data: never render HTML, execute scripts, or infer type from a filename. */
object VaultDocumentPolicy {
    const val MAX_PDF_BYTES = 32L * 1024 * 1024
    const val MAX_TEXT_BYTES = 2L * 1024 * 1024
    fun isDocument(mime: String) = mime in setOf("application/pdf", "text/plain", "text/csv")
    fun maxBytes(mime: String) = if (mime == "application/pdf") MAX_PDF_BYTES else MAX_TEXT_BYTES
    fun label(mime: String) = when(mime) { "application/pdf" -> "PDF document"; "text/csv" -> "CSV document"; else -> "Text document" }
    fun extension(mime: String) = when(mime) { "application/pdf" -> "pdf"; "text/csv" -> "csv"; else -> "txt" }
    fun acceptsDownload(mime: String) = isDocument(mime) || mime.startsWith("image/") || mime in setOf("video/mp4", "video/webm", "video/quicktime", "video/x-matroska")

    /** EOF validation runs before encrypted staging can be published; no plaintext file is created. */
    fun validatingStream(mime: String, input: InputStream, cancelled: () -> Boolean = {false}): InputStream {
        require(isDocument(mime))
        return object: FilterInputStream(input) {
            private val prefix = ByteArray(8)
            private val tail = ByteArray(1024)
            private val text = if(mime == "application/pdf") null else ByteArray(MAX_TEXT_BYTES.toInt())
            private var count = 0L
            private var verified = false
            override fun read(): Int { val one=ByteArray(1);return try {if(read(one,0,1)<0)-1 else one[0].toInt() and 255}finally {one.fill(0)} }
            override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
                if(cancelled())throw IOException("Document save cancelled")
                val n=`in`.read(bytes,offset,length)
                if(n>0) {
                    if(count+n>maxBytes(mime))throw IOException("Document exceeds supported size")
                    repeat(n) { index -> val position=count+index;val b=bytes[offset+index]
                        if(position<prefix.size)prefix[position.toInt()]=b
                        tail[(position%tail.size).toInt()]=b
                    }
                    text?.let {bytes.copyInto(it,count.toInt(),offset,offset+n)}
                    count+=n
                } else if(n<0 && !verified) {
                    if(count==0L)throw IOException("Empty document")
                    if(mime=="application/pdf") {
                        if(!String(prefix,Charsets.US_ASCII).matches(Regex("%PDF-[12]\\.[0-9]")))throw IOException("Not a PDF document")
                        val size=minOf(count,tail.size.toLong()).toInt()
                        val ordered=ByteArray(size) {tail[((count-size+it)%tail.size).toInt()]}
                        try {if(!String(ordered,Charsets.ISO_8859_1).trimEnd().endsWith("%%EOF"))throw IOException("Incomplete PDF document")} finally {ordered.fill(0)}
                    } else {
                        val decoded=Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(checkNotNull(text),0,count.toInt()))
                        try {while(decoded.hasRemaining()) {val c=decoded.get();if(c<' ' && c !in "\t\n\r")throw IOException("Unsupported binary document")}} finally {if(decoded.hasArray())decoded.array().fill('\u0000')}
                    }
                    verified=true
                }
                if(cancelled())throw IOException("Document save cancelled")
                return n
            }
            override fun close() {try {super.close()} finally {prefix.fill(0);tail.fill(0);text?.fill(0)}}
        }
    }
}
