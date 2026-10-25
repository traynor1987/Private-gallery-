package uk.co.traynor.privategallery.ui

import android.view.MotionEvent
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import uk.co.traynor.privategallery.core.browser.BrowserSearchEngine
import uk.co.traynor.privategallery.core.browser.v2.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Harmless local HTML through the active Primary renderer. No network or owner media. */
class BrowserConsent1752Test {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var session: BrowserV2Session
    @After fun cleanup() { if (::session.isInitialized) compose.runOnIdle { session.destroyAll() } }

    private fun mount() {
        session = BrowserV2Session(compose.activity, BrowserVpnGate { true }, NoopBrowserV2Listener)
        compose.setContent { PrivateGalleryTheme { BrowserV2ProductionDestination(
            session, BrowserSearchEngine.GOOGLE, { _, _ -> }, { _, _ -> }, false, {}, emptyList(),
            { _, _, _ -> }, {}, { it(emptyList()) }, { it() }, {},
            acceptanceProbeEnabled = false, staticContentHost = false,
        ) } }
        compose.waitForIdle()
        compose.runOnIdle {
            android.util.Log.i("PG1752", "provider=${WebView.getCurrentWebViewPackage()?.versionName} hardware=${session.activeWebView().isHardwareAccelerated}")
        }
    }
    private fun js(script: String): String {
        val done=CountDownLatch(1)
        var result=""
        compose.runOnIdle {session.activeWebView().evaluateJavascript(script) {result=it;done.countDown()}}
        assertTrue("JavaScript callback completed",done.await(10,TimeUnit.SECONDS))
        return result
    }
    private fun load(dark: Boolean, transformed: Boolean) {
        val body=InstrumentationRegistry.getInstrumentation().context.assets.open("consent1752.html").bufferedReader().use {it.readText()}
            .replace("FIXTURE_THEME",if(dark) "dark" else "light")
            .replace("FIXTURE_TRANSFORM",if(transformed) "translateZ(0)" else "none")
        compose.runOnIdle {session.activeWebView().loadDataWithBaseURL("https://consent-fixture.invalid/",body,"text/html","UTF-8",null)}
        compose.waitUntil(15000){js("!!document.getElementById('consent') && document.getElementById('consent').open")=="true"}
    }
    /** Inject real Android touch events, using CSS-to-view scale and actual screen origin. */
    private fun tap(id: String) {
        val point=org.json.JSONArray(js("JSON.stringify((function(){var e=document.getElementById('$id');e.scrollIntoView({block:'center'});var r=e.getBoundingClientRect();return [(r.left+r.right)/2,(r.top+r.bottom)/2,innerWidth]})())").let {org.json.JSONTokener(it).nextValue() as String})
        var x=0f;var y=0f
        compose.runOnIdle {
            val view=session.activeWebView();val location=IntArray(2);view.getLocationOnScreen(location)
            val scale=view.width/point.getDouble(2)
            x=(location[0]+point.getDouble(0)*scale).toFloat();y=(location[1]+point.getDouble(1)*scale).toFloat()
        }
        val now=android.os.SystemClock.uptimeMillis()
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        for(action in listOf(MotionEvent.ACTION_DOWN,MotionEvent.ACTION_UP)) {
            val event=MotionEvent.obtain(now,android.os.SystemClock.uptimeMillis(),action,x,y,0)
            try {instrumentation.sendPointerSync(event)} finally {event.recycle()}
        }
        instrumentation.waitForIdleSync()
    }
    private fun verify(dark: Boolean, transformed: Boolean) {
        mount();load(dark,transformed)
        compose.waitUntil(10000) {compose.runOnIdle {session.activeWebView().isAttachedToWindow && session.activeWebView().width>0 && session.activeWebView().height>0}}
        val presented=CountDownLatch(1)
        compose.runOnIdle {session.activeWebView().postVisualStateCallback(1752,object:WebView.VisualStateCallback() {
            override fun onComplete(requestId:Long) {presented.countDown()}
        })}
        assertTrue("Provider committed the tested visual state",presented.await(10,TimeUnit.SECONDS))
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        val metrics=org.json.JSONObject(org.json.JSONTokener(js("""JSON.stringify((function(){var d=document.getElementById('consent'),r=d.getBoundingClientRect(),t=document.getElementById('terms'),hit=document.elementFromPoint(r.left+24,r.top+24);return {width:innerWidth,height:innerHeight,dpr:devicePixelRatio,rect:{left:r.left,top:r.top,right:r.right,bottom:r.bottom,width:r.width,height:r.height},termsClient:t.clientHeight,termsScroll:t.scrollHeight,hitTag:hit&&hit.tagName,hitId:hit&&hit.id,display:getComputedStyle(d).display,position:getComputedStyle(d).position,transform:getComputedStyle(d.parentElement).transform,valid:r.width>200&&r.height>200&&r.left>=0&&r.right<=innerWidth+1&&r.top>=0&&r.bottom<=innerHeight+1&&t.scrollHeight>t.clientHeight&&!!hit&&hit.closest('dialog')===d}})())""")).nextValue() as String)
        compose.runOnIdle {val v=session.activeWebView();metrics.put("nativeWidth",v.width);metrics.put("nativeHeight",v.height);metrics.put("nativeFocus",v.hasWindowFocus())}
        val output=java.io.File(InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
            ?: java.io.File(InstrumentationRegistry.getInstrumentation().targetContext.externalMediaDirs.first(),"additional_test_output").absolutePath,
            "repair1752-consent/${if(dark) "dark" else "light"}").apply {mkdirs()}
        java.io.File(output,"geometry.json").writeText(metrics.toString(2))
        android.util.Log.i("PG1752Geometry",metrics.toString())
        // Capture BEFORE assertions: a failed geometry subcondition needs actual frame evidence.
        val screenshot=checkNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        try {
            java.io.File(output,"modal.png").outputStream().use {screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
            assertTrue("Rendered modal geometry: $metrics",metrics.getBoolean("valid"))
            var colored=0
            for(y in 0 until screenshot.height step 4)for(x in 0 until screenshot.width step 4) {
                val c=screenshot.getPixel(x,y)
                if(android.graphics.Color.red(c)>200 && android.graphics.Color.blue(c)>150 && android.graphics.Color.green(c)<100)colored++
            }
            assertTrue("Actual modal header pixels must be visible",colored>20)
        } finally {screenshot.recycle()}
        tap("accept")
        assertEquals("true",js("document.getElementById('consent').open"))
        tap("agreed")
        assertEquals("true",js("document.getElementById('agreed').checked"))
        tap("accept")
        compose.waitUntil(5000){js("document.getElementById('consent').open")=="false"}
        tap("normal")
        assertEquals("1",js("window.normalClicks"))
        assertEquals("true",js("localStorage.getItem('consent1752')==='manual' && document.cookie.includes('consent1752=manual')"))
        compose.runOnIdle {session.activeWebView().onPause();session.activeWebView().onResume();session.activeWebView().requestFocus()}
        tap("normal");assertEquals("2",js("window.normalClicks"))
        // Same-origin reload fixture verifies persisted state without external navigation.
        load(dark,transformed)
        assertEquals("true",js("localStorage.getItem('consent1752')==='manual' && document.cookie.includes('consent1752=manual')"))
        tap("decline");assertEquals("false",js("document.getElementById('consent').open"))
    }
    @Test fun lightDelayedConsentRendersAndRequiresManualCheckbox() = verify(false,false)
    @Test fun darkTransformedConsentRendersAndRemainsInteractive() = verify(true,true)
}
