package uk.co.traynor.privategallery.ui

import android.view.MotionEvent
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.*
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.graphics.asAndroidBitmap
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
    private val narrow=androidx.compose.runtime.mutableStateOf(false)
    private val uploadCalls=java.util.concurrent.atomic.AtomicInteger(0)
    private var uploadDirectory:java.io.File?=null
    private var policySaved=false
    private var previousPolicy:String?=null
    private var delayVaultQuery=false
    private var completeVaultQuery:(()->Unit)?=null
    private val png by lazy {
        val b=android.graphics.Bitmap.createBitmap(32,32,android.graphics.Bitmap.Config.ARGB_8888).apply {eraseColor(android.graphics.Color.GREEN)}
        try {java.io.ByteArrayOutputStream().use {b.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it);it.toByteArray()}} finally {b.recycle()}
    }
    private val pngHash get()=java.security.MessageDigest.getInstance("SHA-256").digest(png).joinToString(""){"%02x".format(it)}
    @After fun cleanup() {
        if (::session.isInitialized) compose.runOnIdle { session.destroyAll() }
        uploadDirectory?.deleteRecursively()
        if(policySaved) InstrumentationRegistry.getInstrumentation().targetContext.getSharedPreferences("browser_upload_policy",0).edit().apply {
            if(previousPolicy==null)remove("file_uploads") else putString("file_uploads",previousPolicy)
        }.commit()
    }

    private fun mount(wrapReference:Boolean=false) {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val prefs=context.getSharedPreferences("browser_upload_policy",0)
        previousPolicy=prefs.getString("file_uploads",null);policySaved=true
        BrowserUploadPreference.write(context,BrowserUploadPolicy.VAULT_ONLY)
        uploadDirectory=java.io.File(context.cacheDir,"browser-upload/compat-${java.util.UUID.randomUUID()}")
        val item=uk.co.traynor.privategallery.core.vault.VaultItem("synthetic-upload","image/png","Harmless fixture.png",0,png.size.toLong(),
            java.security.MessageDigest.getInstance("SHA-256").digest(png),ByteArray(12),uk.co.traynor.privategallery.core.vault.VaultItemState.COMPLETE)
        session = if(!wrapReference) BrowserV2Session(compose.activity, BrowserVpnGate { true }, NoopBrowserV2Listener)
        else BrowserV2Session(compose.activity, BrowserVpnGate { true }, NoopBrowserV2Listener,
            webViewFactory=BrowserV2WebViewFactory {context,callbacks,id,desktop ->
                SecureWebViewFactory(callbacks).create(context,id,desktop).apply {
                    layoutParams=android.view.ViewGroup.LayoutParams(-2,-2)
                }
            })
        compose.setContent { PrivateGalleryTheme { androidx.compose.foundation.layout.Box(
            Modifier.requiredWidth(if (narrow.value) 392.dp else 840.dp).fillMaxHeight()) { BrowserV2ProductionDestination(
            session, BrowserSearchEngine.GOOGLE, { _, _ -> }, { _, _ -> }, false, {}, emptyList(),
            { _, _, _ -> }, {}, { it(emptyList()) }, { it() }, {},
            acceptanceProbeEnabled = false, staticContentHost = false,
            onLoadVaultItems={done->
                if(delayVaultQuery)completeVaultQuery={done(listOf(item))} else done(listOf(item))
            },
            onLoadVaultPreview={_,done->done(Result.success(checkNotNull(android.graphics.BitmapFactory.decodeByteArray(png,0,png.size))))},
            onPrepareVaultUpload={items,done->
                check(items.size==1 && items.single().id==item.id)
                uploadCalls.incrementAndGet()
                val directory=checkNotNull(uploadDirectory).apply {mkdirs()}
                val file=java.io.File(directory,"upload.png").apply {writeBytes(png)}
                val uri=androidx.core.content.FileProvider.getUriForFile(context,context.packageName+".fileprovider",file)
                done(Result.success(listOf(uri)))
            },
            onClearVaultUpload={uploadDirectory?.deleteRecursively()},
        ) } } }
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
        val metrics=org.json.JSONObject(org.json.JSONTokener(js("""JSON.stringify((function(){var d=document.getElementById('consent'),r=d.getBoundingClientRect(),t=document.getElementById('terms'),hit=document.elementFromPoint(r.left+24,r.top+24);return {width:innerWidth,height:innerHeight,dpr:devicePixelRatio,rect:{left:r.left,top:r.top,right:r.right,bottom:r.bottom,width:r.width,height:r.height},termsClient:t.clientHeight,termsScroll:t.scrollHeight,hitTag:hit&&hit.tagName,hitId:hit&&hit.id,display:getComputedStyle(d).display,position:getComputedStyle(d).position,transform:getComputedStyle(d.parentElement).transform,modalMaxHeight:getComputedStyle(d).maxHeight,termsMaxHeight:getComputedStyle(t).maxHeight,valid:r.width>200&&r.height>200&&r.left>=0&&r.right<=innerWidth+1&&r.top>=0&&r.bottom<=innerHeight+1&&t.scrollHeight>t.clientHeight&&!!hit&&hit.closest('dialog')===d}})())""")).nextValue() as String)
        compose.runOnIdle {val v=session.activeWebView();metrics.put("nativeWidth",v.width);metrics.put("nativeHeight",v.height);metrics.put("nativeFocus",v.hasWindowFocus());metrics.put("layoutHeight",v.layoutParams.height);metrics.put("layoutWidth",v.layoutParams.width)}
        val output=java.io.File(InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
            ?: java.io.File(InstrumentationRegistry.getInstrumentation().targetContext.externalMediaDirs.first(),"additional_test_output").absolutePath,
            "repair1752-consent/${if(dark) "dark" else "light"}").apply {mkdirs()}
        java.io.File(output,"geometry.json").writeText(metrics.toString(2))
        android.util.Log.i("PG1752Geometry",metrics.toString())
        // Capture BEFORE assertions: a failed geometry subcondition needs actual frame evidence.
        val ordinary=InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        ordinary?.let {capture ->try {java.io.File(output,"whole-display.png").outputStream().use {capture.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}} finally {capture.recycle()}}
        // ATD whole-display capture is black even though the activity surface renders.
        // Use the same PixelCopy-backed surface capture as existing browser presentation tests.
        var screenshot:android.graphics.Bitmap?=null
        var colored=0
        try {
            compose.waitUntil(15000) {
                screenshot?.recycle()
                screenshot=compose.onNodeWithTag("browser-v2-page-region").captureToImage().asAndroidBitmap()
                colored=0
                val frame=checkNotNull(screenshot)
                for(y in 0 until frame.height step 4)for(x in 0 until frame.width step 4) {
                    val c=frame.getPixel(x,y)
                    if(android.graphics.Color.red(c)>200 && android.graphics.Color.blue(c)>150 && android.graphics.Color.green(c)<100)colored++
                }
                colored>20
            }
            assertTrue("Rendered modal geometry: $metrics",metrics.getBoolean("valid"))
            assertEquals("CSS vh must use the real native viewport",metrics.getDouble("height")*.9,
                metrics.getString("modalMaxHeight").removeSuffix("px").toDouble(),2.0)
            assertTrue("Actual modal header pixels must be visible",colored>20)
        } finally {screenshot?.let {frame ->
            try {java.io.File(output,"modal.png").outputStream().use {frame.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}}
            finally {frame.recycle()}
        }}
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
        // Actual HTML input -> production picker UI -> explicit final confirmation -> local file bytes.
        // This fixture only constructs multipart bytes; it never sends a network request.
        assertEquals(0,uploadCalls.get())
        tap("file")
        compose.waitUntil(10000){compose.onAllNodesWithContentDescription("Harmless fixture.png").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithContentDescription("Harmless fixture.png").performClick()
        compose.onNodeWithText("Continue",substring=false).performClick()
        compose.onNodeWithText("Upload this file?",substring=false).assertExists()
        assertEquals("Selection alone must not release bytes",0,uploadCalls.get())
        compose.onNodeWithText("Upload",substring=false).performClick()
        compose.waitUntil(10000){js("!!window.fixtureUploadError || (!!window.fixtureUploadSha && window.fixtureMultipartLength > window.fixtureUploadLength && document.getElementById('consent').open)")=="true"}
        assertEquals("Local file read must preserve the selected synthetic PNG: "+js("window.fixtureUploadError"),"\"$pngHash\"",js("window.fixtureUploadSha"))
        assertEquals("true",js("window.fixtureMultipartLength > window.fixtureUploadLength"))
        assertEquals(1,uploadCalls.get())
        compose.waitUntil(5000){js("document.getElementById('consent').open")=="true"}
        tap("decline");assertEquals("false",js("document.getElementById('consent').open"))
        tap("normal");assertEquals("3",js("window.normalClicks"))
        // Resize the real retained AndroidView host, preserving its document and modal state.
        val retainedView=compose.runOnIdle {session.activeWebView()}
        val wide=js("innerWidth").toInt()
        compose.runOnIdle {narrow.value=true}
        compose.waitUntil(10000){js("innerWidth").toInt()<wide}
        tap("show")
        assertEquals("true",js("document.getElementById('consent').open"))
        assertEquals("true",js("(function(){var r=document.getElementById('consent').getBoundingClientRect();return r.width>200&&r.height>200&&r.left>=0&&r.right<=innerWidth+1&&document.getElementById('terms').clientHeight>0})()"))
        val small=js("innerWidth").toInt()
        compose.runOnIdle {assertSame("Narrowing must retain the WebView",retainedView,session.activeWebView())}
        tap("decline")
        compose.runOnIdle {narrow.value=false}
        compose.waitUntil(10000){js("innerWidth").toInt()==wide}
        compose.runOnIdle {assertSame("Restoring width must retain the WebView",retainedView,session.activeWebView())}
        tap("normal");assertEquals("4",js("window.normalClicks"))
        java.io.File(output,"upload-resize.json").writeText(org.json.JSONObject().put("syntheticPngSha256",pngHash).put("uploadBytes",png.size)
            .put("explicitPrepareCalls",uploadCalls.get()).put("multipartLength",js("window.fixtureMultipartLength").toInt()).put("wideViewport",wide).put("narrowViewport",small).put("restoredViewport",js("innerWidth").toInt()).toString(2))
        // Same-origin reload fixture verifies persisted state without external navigation.
        load(dark,transformed)
        assertEquals("true",js("localStorage.getItem('consent1752')==='manual' && document.cookie.includes('consent1752=manual')"))
        tap("decline");assertEquals("false",js("document.getElementById('consent').open"))
    }
    @Test fun extensionBasedUploadUsesThemedVaultPickerWithoutPrematureEgress() {
        mount();load(false,false)
        tap("decline")
        js("document.getElementById('file').setAttribute('accept','.png'); true")
        delayVaultQuery=true
        tap("file")
        compose.waitUntil(10000){completeVaultQuery!=null}
        compose.onNodeWithText("Loading Vault media…").assertIsDisplayed()
        compose.onNodeWithText("No compatible Vault items.",substring=true).assertDoesNotExist()
        compose.runOnIdle {checkNotNull(completeVaultQuery).invoke()}
        compose.waitUntil(10000){compose.onAllNodesWithContentDescription("Harmless fixture.png").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithText("Choose from Vault").assertIsDisplayed()
        compose.onNodeWithText("Continue").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Harmless fixture.png").performClick()
        compose.onNodeWithText("Continue").performClick()
        compose.onNodeWithText("Upload this file?").assertIsDisplayed()
        assertEquals(0,uploadCalls.get())
        compose.onNodeWithText("Cancel").performClick()
        assertEquals(0,uploadCalls.get())
    }
    @Test fun wrapContentReferenceReproducesCollapsedViewportHeights() {
        mount(wrapReference=true);load(false,false)
        assertEquals("Deliberate old layout reference must reproduce collapsed terms", "0",js("document.getElementById('terms').clientHeight"))
        assertEquals("Deliberate old layout reference must reproduce padding-only modal", "34",js("Math.round(document.getElementById('consent').getBoundingClientRect().height)"))
    }
    @Test fun lightDelayedConsentRendersAndRequiresManualCheckbox() = verify(false,false)
    @Test fun darkTransformedConsentRendersAndRemainsInteractive() = verify(true,true)
}
