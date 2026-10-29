package uk.co.traynor.privategallery.core.vault

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.Handler
import android.os.HandlerThread
import android.os.ParcelFileDescriptor
import android.os.ProxyFileDescriptorCallback
import android.os.storage.StorageManager

/** Seekable PDF bytes stay in app memory. No provider, public URI, Javascript, or plaintext file. */
object VaultPdfPreview {
    private val handler by lazy { Handler(HandlerThread("vault-document-reader").apply {start()}.looper) }
    fun page(context: Context, bytes: ByteArray, index: Int, current: () -> Boolean): Pair<Bitmap,Int> {
        require(bytes.size.toLong() in 1..VaultDocumentPolicy.MAX_PDF_BYTES)
        check(current())
        val replyBuffer=java.util.concurrent.atomic.AtomicReference<ByteArray?>(null)
        val callback=object:ProxyFileDescriptorCallback() {
            override fun onGetSize():Long {check(current());return bytes.size.toLong()}
            override fun onRead(offset:Long,size:Int,data:ByteArray):Int {
                replyBuffer.getAndSet(data)?.takeIf {it!==data}?.fill(0)
                data.fill(0)
                try {
                    check(current());require(offset>=0 && size in 0..data.size)
                    val count=minOf(size.toLong(),(bytes.size-offset).coerceAtLeast(0)).toInt()
                    if(count>0)bytes.copyInto(data,0,offset.toInt(),offset.toInt()+count)
                    check(current());return count
                }catch(failure:Throwable){data.fill(0);throw failure}
            }
            override fun onRelease() {replyBuffer.getAndSet(null)?.fill(0)}
        }
        val descriptor=checkNotNull(context.getSystemService(StorageManager::class.java)).openProxyFileDescriptor(ParcelFileDescriptor.MODE_READ_ONLY,callback,handler)
        try {checkNotNull(descriptor).use {fd -> PdfRenderer(fd).use {pdf ->
            require(pdf.pageCount in 1..10000 && index in 0 until pdf.pageCount)
            pdf.openPage(index).use {page ->
                require(page.width>0 && page.height>0)
                val scale=1400.0/maxOf(page.width,page.height)
                val bitmap=Bitmap.createBitmap((page.width*scale).toInt().coerceAtLeast(1),(page.height*scale).toInt().coerceAtLeast(1),Bitmap.Config.ARGB_8888)
                try {bitmap.eraseColor(Color.WHITE);check(current());page.render(bitmap,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);check(current());return bitmap to pdf.pageCount}
                catch(failure:Throwable){bitmap.eraseColor(Color.TRANSPARENT);bitmap.recycle();throw failure}
            }
        }}} finally {replyBuffer.getAndSet(null)?.fill(0)}
    }
}
