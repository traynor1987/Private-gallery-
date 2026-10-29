package uk.co.traynor.privategallery.ui

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.SurfaceView
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.webkit.WebView
import java.util.concurrent.atomic.AtomicBoolean
import uk.co.traynor.privategallery.core.browser.v2.BrowserScreenshotBuffer
import uk.co.traynor.privategallery.core.browser.v2.BrowserScreenshotPolicy
import uk.co.traynor.privategallery.core.vault.VaultImportSource

/** PixelCopy only: never clears FLAG_SECURE or invokes WebView.draw to bypass capture policy.
 * Captures the visible WebView rectangle, not full-page content or browser chrome. Video, frames,
 * plugins, native child surfaces and documents that cannot be inspected are conservatively refused.
 * The consumer must call source.onConsumed(), including cancellation/rejection paths.
 */
internal object BrowserVaultScreenshot {
    private val inFlight=AtomicBoolean(false)
    private const val SAFE_DOCUMENT = "(function(){try{return !document.querySelector('video,audio,iframe,frame,object,embed') && !document.fullscreenElement;}catch(e){return false;}})()"

    fun capture(window: Window, view: WebView, ownerConfirmed: Boolean, authorized: () -> Boolean,
                complete: (Result<VaultImportSource>) -> Unit) {
        check(Looper.myLooper() == Looper.getMainLooper())
        if(!inFlight.compareAndSet(false,true)){complete(Result.failure(IllegalStateException("A screenshot is already being saved.")));return}
        val handler=Handler(Looper.getMainLooper())
        val finished=AtomicBoolean(false)
        fun finish(result: Result<VaultImportSource>) {
            if(finished.compareAndSet(false,true)) {
                if(result.isFailure)inFlight.set(false)
                try { complete(result) } catch(failure: Throwable) { result.getOrNull()?.onConsumed?.invoke();throw failure }
            } else result.getOrNull()?.onConsumed?.invoke()
        }
        fun reject() = finish(Result.failure(IllegalStateException(BrowserScreenshotPolicy.REFUSAL)))
        fun bounds():Rect? {
            val local=Rect()
            if(!view.getLocalVisibleRect(local))return null
            val location=IntArray(2);view.getLocationInWindow(location)
            local.offset(location[0],location[1])
            val decor=window.decorView
            if(!local.intersect(0,0,decor.width,decor.height))return null
            return local
        }
        fun allowed(rect:Rect?, safe:Boolean)=runCatching { BrowserScreenshotPolicy.allowed(ownerConfirmed,
            authorized(), window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0,
            window.decorView.hasWindowFocus(),view.isAttachedToWindow && view.isShown,
            safe && !hasSurface(view),rect?.width() ?: 0,rect?.height() ?: 0) }.getOrDefault(false)
        val rect=bounds()
        if(!allowed(rect,true) || !view.settings.javaScriptEnabled){reject();return}
        // Inspection never changes site settings. JavaScript-disabled documents are unsupported.
        val timeout=Runnable {reject()};handler.postDelayed(timeout,2000)
        try {view.evaluateJavascript(SAFE_DOCUMENT) { safe ->
            handler.removeCallbacks(timeout)
            if(finished.get())return@evaluateJavascript
            if(safe!="true" || !allowed(rect,true) || rect!=bounds()){reject();return@evaluateJavascript}
            val bitmap=try {Bitmap.createBitmap(rect!!.width(),rect.height(),Bitmap.Config.ARGB_8888)}catch(_:Throwable){reject();return@evaluateJavascript}
            fun wipeBitmap(){try{bitmap.eraseColor(Color.TRANSPARENT)}finally{bitmap.recycle()}}
            try {PixelCopy.request(window,rect!!,bitmap,{status ->
                var output:BrowserScreenshotBuffer?=null
                try {
                    check(status==PixelCopy.SUCCESS && allowed(rect,true) && rect==bounds())
                    output=BrowserScreenshotBuffer(BrowserScreenshotPolicy.MAX_PNG_BYTES)
                    check(bitmap.compress(Bitmap.CompressFormat.PNG,100,output))
                    check(allowed(rect,true))
                    val owned=checkNotNull(output)
                    val consumed=AtomicBoolean(false)
                    val source=VaultImportSource("browser-screenshot-${System.currentTimeMillis()}.png","image/png",
                        openStream={check(!consumed.get() && authorized());owned.input()},vaultOnly=true,
                        onConsumed={if(consumed.compareAndSet(false,true)){owned.wipe();inFlight.set(false)}},
                        isCancelled={!authorized()},capturedAtEpochMillis=System.currentTimeMillis(),
                        publishIfAllowed={commit ->check(authorized());commit()})
                    output=null
                    finish(Result.success(source))
                } catch(_:Throwable){reject()}
                finally {output?.wipe();wipeBitmap()}
            },handler)} catch(_:Throwable){wipeBitmap();reject()}
        }}catch(_:Exception){handler.removeCallbacks(timeout);reject()}
    }

    private fun hasSurface(view:View):Boolean = view is SurfaceView || view is TextureView ||
        (view is ViewGroup && (0 until view.childCount).any {hasSurface(view.getChildAt(it))})

}
