package uk.co.traynor.privategallery.core.update

import java.io.InputStream
import java.io.OutputStream
import java.io.InterruptedIOException
import java.security.MessageDigest

/** Bounded byte transfer used by the existing Nova Store/update downloader. */
internal object VerifiedUpdateTransfer {
    fun copy(input: InputStream, output: OutputStream, expectedBytes: Long, maximum: Long,
             digest: MessageDigest, cancelled: () -> Boolean, progress: (Long,Long) -> Unit): Long {
        require(expectedBytes <= maximum)
        val buffer=ByteArray(64*1024)
        var bytes=0L
        try {
            while(true) {
                if(cancelled())throw InterruptedIOException("Download cancelled")
                val count=input.read(buffer)
                if(count<0)break
                if(count==0)continue
                bytes+=count
                check(bytes<=maximum) { "Update exceeds size limit" }
                digest.update(buffer,0,count);output.write(buffer,0,count);progress(bytes,expectedBytes)
            }
            check(bytes>0 && (expectedBytes<=0 || bytes==expectedBytes)) { "Incomplete download" }
            return bytes
        } finally {buffer.fill(0)}
    }
}
