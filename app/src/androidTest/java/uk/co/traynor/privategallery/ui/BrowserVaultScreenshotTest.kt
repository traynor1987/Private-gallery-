package uk.co.traynor.privategallery.ui

import android.graphics.BitmapFactory
import android.graphics.Color
import android.view.WindowManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.test.core.app.ActivityScenario
import org.junit.Assert.*
import org.junit.Test
import uk.co.traynor.privategallery.core.vault.VaultImportSource
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Local synthetic content only; no network, owner media or plaintext files. */
class BrowserVaultScreenshotTest {
    private fun fixture(html:String="<body style='margin:0;background:#ff0000'>Local fixture</body>",
                        test:(ActivityScenario<ComponentActivity>,WebView)->Unit) {
        ActivityScenario.launch(ComponentActivity::class.java).use {scenario ->
            lateinit var view:WebView
            val frame=CountDownLatch(1)
            scenario.onActivity {activity ->
                activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                view=WebView(activity).apply {
                    settings.javaScriptEnabled=true
                    webViewClient=object:WebViewClient() {
                        override fun onPageFinished(web:WebView,url:String) {
                            web.postVisualStateCallback(1,object:WebView.VisualStateCallback() {
                                override fun onComplete(requestId:Long) {
                                    // WebView visual readiness/postOnAnimation precede Window buffer
                                    // submission. PixelCopy needs a frame actually committed to its surface.
                                    check(web.isHardwareAccelerated)
                                    web.viewTreeObserver.registerFrameCommitCallback {frame.countDown()}
                                    web.invalidate()
                                    activity.window.decorView.invalidate()
                                }
                            })
                        }
                    }
                }
                activity.setContentView(FrameLayout(activity).apply {
                    setBackgroundColor(Color.BLUE)
                    addView(view,FrameLayout.LayoutParams(240,200).apply {leftMargin=30;topMargin=50})
                })
                view.loadDataWithBaseURL("https://screenshot.invalid",html,"text/html","UTF-8",null)
            }
            assertTrue("WebView window frame committed",frame.await(10,TimeUnit.SECONDS))
            try {test(scenario,view)} finally {scenario.onActivity {
                (view.parent as? android.view.ViewGroup)?.removeView(view)
                view.destroy()
            }}
        }
    }
    private fun capture(scenario:ActivityScenario<ComponentActivity>,view:WebView,confirmed:Boolean=true,
                        authorized:()->Boolean={true}):Result<VaultImportSource> {
        val done=CountDownLatch(1)
        var captured:Result<VaultImportSource>?=null
        scenario.onActivity {activity ->BrowserVaultScreenshot.capture(activity.window,view,confirmed,authorized) {
            captured=it;done.countDown()
        }}
        assertTrue("Capture result",done.await(10,TimeUnit.SECONDS))
        return checkNotNull(captured)
    }
    @Test fun visibleWebsiteOnlyAndWipedAfterConsumption()=fixture {scenario,view ->
        val source=capture(scenario,view).getOrThrow()
        try {
            assertEquals("image/png",source.mimeType)
            assertTrue(source.vaultOnly)
            val bitmap=checkNotNull(source.openStream().use(BitmapFactory::decodeStream))
            try {assertEquals(240,bitmap.width);assertEquals(200,bitmap.height);assertEquals(Color.RED,bitmap.getPixel(120,150))}
            finally {bitmap.eraseColor(Color.TRANSPARENT);bitmap.recycle()}
        } finally {source.onConsumed()}
        assertTrue(runCatching {source.openStream()}.isFailure)
    }
    @Test fun refusesSecureWindowWithoutChangingProtection()=fixture {scenario,view ->
        scenario.onActivity {it.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)}
        assertTrue(capture(scenario,view).isFailure)
        scenario.onActivity {assertTrue(it.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE!=0)}
    }
    @Test fun requiresConfirmationAndCurrentAuthority()=fixture {scenario,view ->
        assertTrue(capture(scenario,view,confirmed=false).isFailure)
        assertTrue(capture(scenario,view,authorized={false}).isFailure)
    }
    @Test fun rejectsVideoAndJavascriptDisabledDocuments()=fixture("<video></video>") {scenario,view ->
        assertTrue(capture(scenario,view).isFailure)
        scenario.onActivity {view.settings.javaScriptEnabled=false}
        assertTrue(capture(scenario,view).isFailure)
    }
}
