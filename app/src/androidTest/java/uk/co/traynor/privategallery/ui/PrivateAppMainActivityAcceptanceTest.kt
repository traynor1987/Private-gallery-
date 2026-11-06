package uk.co.traynor.privategallery.ui

import android.content.SharedPreferences
import android.webkit.WebView
import androidx.compose.runtime.MutableState
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.Job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import uk.co.traynor.privategallery.MainActivity
import uk.co.traynor.privategallery.ProtectedSessionState
import uk.co.traynor.privategallery.Route
import uk.co.traynor.privategallery.core.browser.v2.BrowserV2Session
import uk.co.traynor.privategallery.core.home.HomeLayout
import uk.co.traynor.privategallery.core.privateapps.PrivateAppCatalog
import uk.co.traynor.privategallery.core.security.AutoLockTimeout
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Base64
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Actual MainActivity, its production session/factory, and ordinary Primary settings. Synthetic
 * in-memory authority matches other MainActivity fixtures; no credentials, VPN gate, renderer
 * factory, WebView security setting, media policy or Hidden route is replaced by this test.
 */
class PrivateAppMainActivityAcceptanceTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    private fun invoke(activity:MainActivity,name:String,vararg arguments:Any?):Any? =
        MainActivity::class.java.declaredMethods.single {it.name==name}.apply {isAccessible=true}.invoke(activity,*arguments)
    @Suppress("UNCHECKED_CAST") private fun route(activity:MainActivity)=
        MainActivity::class.java.getDeclaredField("route\$delegate").apply {isAccessible=true}.get(activity) as MutableState<Route>

    private data class Fixture(val app:PrivateAppCatalog.App,val owner:BrowserV2Session,val id:String,val view:WebView)
    private class Backup(private val root:File) {
        private val files=if(root.exists())root.walkTopDown().filter {it.isFile}.associate {it.relativeTo(root).path to it.readBytes()}else emptyMap()
        init {root.deleteRecursively()}
        fun restore() {
            root.deleteRecursively()
            files.forEach {(path,bytes)->File(root,path).apply {parentFile?.mkdirs();writeBytes(bytes)};bytes.fill(0)}
        }
    }
    private fun restoreSettings(preferences:SharedPreferences,values:Map<String,Any?>) {
        preferences.edit().apply {values.forEach {(key,value)->when(value) {
            null->remove(key)
            is Boolean->putBoolean(key,value)
            is String->putString(key,value)
            is Int->putInt(key,value)
            else->error("Unexpected fixture preference type")
        }}}.commit()
    }

    private fun withFixture(allowCapture:Boolean=false,test:(Fixture)->Unit) {
        val activity=compose.activity
        val state=ViewModelProvider(activity)[ProtectedSessionState::class.java]
        val settings=activity.getSharedPreferences("private-gallery-settings",0)
        val names=listOf("browser-vpn-required","browser-vpn-auto-connect","browser-save-history","browser-clear-data-on-lock","auto-lock-timeout","hide-content","allow-screenshots")
        val previous=names.associateWith {settings.all[it]}
        val beta=activity.getSharedPreferences("nova-browser-beta",0)
        val oldBeta=mapOf("notice-revision" to beta.all["notice-revision"])
        val backup=listOf("private-apps","browser-session","evidence-state/home2","evidence-state/home1710").map {Backup(File(activity.filesDir,it))}
        val key=ByteArray(32){67}
        val lifetime=activity.lifecycleScope.coroutineContext[Job]
        try {
            compose.runOnIdle {
                invoke(activity,"applyBrowserRequireVpn",false)
                invoke(activity,"applyBrowserAutoConnectVpn",false)
                invoke(activity,"applyBrowserSaveHistory",false)
                invoke(activity,"applyClearBrowserDataOnLock",false)
                invoke(activity,"applyAutoLockTimeout",AutoLockTimeout.MINUTES_5)
                invoke(activity,"applyHideContent",false)
                if(allowCapture)invoke(activity,"applyAllowScreenshots",true)
                state.hidden.leave();state.key=key;state.session.unlock()
                state.homePrimaryAuthentication=state.session.captureAuthenticationEpoch();state.homeVisible=false
                route(activity).value=Route.VAULT
                invoke(activity,"launchHomeShortcut",HomeLayout.Shortcut.BROWSER)
            }
            compose.waitUntil(15_000) {
                compose.onAllNodesWithTag("browser-beta-continue").fetchSemanticsNodes().isNotEmpty() ||
                    compose.onAllNodesWithTag("browser-v2-root").fetchSemanticsNodes().isNotEmpty()
            }
            if(compose.onAllNodesWithTag("browser-beta-continue").fetchSemanticsNodes().isNotEmpty())
                compose.onNodeWithTag("browser-beta-continue").performScrollTo().performClick()
            val app=PrivateAppCatalog.App(UUID.randomUUID().toString(),"Main Activity audio fixture",
                "https://app-mode-main.invalid/","https://app-mode-main.invalid/","standalone","",false,true,false)
            val fixture=compose.runOnIdle {
                val owner=invoke(activity,"getBrowserV2Session") as BrowserV2Session
                val controller=checkNotNull(activity.privateAppsController())
                controller.save(app);controller.launch(app.id())
                val view=checkNotNull(owner.activeWebViewOrNull())
                assertTrue("Production gesture requirement stays enabled",view.settings.mediaPlaybackRequiresUserGesture)
                view.stopLoading()
                view.loadDataWithBaseURL(app.startUrl(),html(),"text/html","UTF-8",app.startUrl())
                Fixture(app,owner,owner.tabs.activeTab.id,view)
            }
            compose.waitUntil(15_000) {
                js(fixture.view,"document.readyState==='complete' && document.getElementById('fixture-audio')?.readyState>=2")=="true" &&
                    compose.runOnIdle {!fixture.owner.tabs.activeTab.loading && fixture.owner.pageFailure(fixture.id)==null}
            }
            compose.onNodeWithContentDescription("App menu").assertIsDisplayed()
            test(fixture)
        } finally {
            instrumentation.runOnMainSync {
                activity.lockNovaSpace()
                state.hidden.leave();state.key=null;state.session.lock();route(activity).value=Route.LOCK
            }
            // Finish owned writes before restoring test-identity files/settings.
            compose.activityRule.scenario.close()
            runBlocking {lifetime?.children?.toList()?.joinAll()}
            backup.forEach {it.restore()}
            restoreSettings(settings,previous);restoreSettings(beta,oldBeta);key.fill(0)
        }
    }

    @Test fun productionAppReservesNavigationAndHomeRetainsUserStartedAudioWhileExitClosesIt()=withFixture {f ->
        val root=compose.onNodeWithTag("browser-v2-root").fetchSemanticsNode().boundsInRoot
        val page=compose.onNodeWithTag("browser-v2-page-region").fetchSemanticsNode().boundsInRoot
        val host=compose.onNodeWithTag("browser-v2-webview-host").fetchSemanticsNode().boundsInRoot
        val navigation=compose.onNodeWithTag("nova-navigation").fetchSemanticsNode().boundsInRoot
        assertTrue(page.top>=root.top);assertEquals(page.bottom,navigation.top,1f)
        assertTrue(navigation.bottom<=root.bottom)
        assertEquals(page.top,host.top,1f);assertEquals(page.bottom,host.bottom,1f)
        compose.onNodeWithTag("browser-v2-chrome").assertDoesNotExist()
        compose.onNodeWithTag("browser-v2-address").assertDoesNotExist()
        compose.onNodeWithTag("browser-v2-toolbar").assertDoesNotExist()
        val parent=compose.runOnIdle {checkNotNull(f.view.parent).also {
            assertEquals(host.height.toInt(),f.view.height);assertTrue(f.view.isAttachedToWindow)
        }}
        compose.onNodeWithTag("browser-v2-webview-host").performTouchInput {click(center)}
        compose.waitUntil(15_000) {js(f.view,"window.fixtureStarted===true && !document.getElementById('fixture-audio').paused")=="true"}
        compose.onNodeWithContentDescription("App menu").performClick()
        compose.onNodeWithTag("private-app-menu-sheet").assertIsDisplayed()
        compose.runOnIdle {compose.activity.onBackPressedDispatcher.onBackPressed()}
        compose.onNodeWithTag("private-app-menu-sheet").assertDoesNotExist()
        compose.runOnIdle {assertEquals(f.id,f.owner.tabs.activeTab.id);assertSame(f.view,f.owner.activeWebViewOrNull());assertSame(parent,f.view.parent)}
        assertEquals("true",js(f.view,"window.fixtureStarted===true && !document.getElementById('fixture-audio').paused"))
        compose.onNodeWithContentDescription("App menu").performClick()
        compose.onNodeWithText("Nova Home").performScrollTo().performClick()
        compose.onNodeWithTag("preview-home").assertIsDisplayed()
        compose.onNodeWithText("Open in Browser").assertDoesNotExist()
        compose.runOnIdle {
            assertTrue(compose.activity.primaryBrowserCoveredByNovaHome())
            assertTrue(f.owner.mediaNetworkingAllowed())
            assertEquals(f.id,f.owner.tabs.activeTab.id);assertEquals(f.app.id(),f.owner.privateApp(f.id)?.id())
            assertSame(parent,f.view.parent);assertTrue(f.view.isAttachedToWindow)
            assertFalse(f.view.isEnabled);assertFalse(f.view.isFocusable)
        }
        val before=js(f.view,"document.getElementById('fixture-audio').currentTime").toDouble()
        compose.waitUntil(10_000) {
            js(f.view,"!document.getElementById('fixture-audio').paused")=="true" &&
                js(f.view,"document.getElementById('fixture-audio').currentTime").toDouble()>before
        }
        // Reopen through the real Home drawer/controller, retaining the same page and session.
        compose.onNodeWithTag("open-apps").performClick()
        compose.onNodeWithTag("drawer-search").performTextInput(f.app.name())
        compose.onNodeWithTag("app-web:${f.app.id()}").performClick()
        compose.onNodeWithContentDescription("App menu").assertIsDisplayed()
        compose.runOnIdle {assertEquals(f.id,f.owner.tabs.activeTab.id);assertSame(f.view,f.owner.activeWebViewOrNull())}
        assertEquals("true",js(f.view,"window.fixtureStarted===true && !document.getElementById('fixture-audio').paused"))
        compose.onNodeWithContentDescription("App menu").performClick()
        compose.onNodeWithText("Exit App").performScrollTo().performClick()
        compose.runOnIdle {
            assertNull(f.view.parent);assertFalse(f.view.isAttachedToWindow)
            assertFalse(f.owner.tabs.tabs.any {it.id==f.id});assertNull(f.owner.privateApp(f.id))
            assertTrue(f.owner.createdPrivateAppWebViews().none {it.second===f.view})
        }
        // Do not call methods on the destroyed provider: Exit uses the real session close path.
        compose.onNodeWithText("Open in Browser").assertDoesNotExist()
    }

    @Test fun productionPopupDismissesAndCapturedMenuActionsCannotSurviveBackgroundOrLock()=withFixture {f ->
        fun action(label:String)=checkNotNull(compose.onNodeWithText(label).performScrollTo().fetchSemanticsNode().config[SemanticsActions.OnClick].action)
        compose.onNodeWithContentDescription("App menu").performClick()
        val backgroundOpen=action("Open in Browser");val backgroundExit=action("Exit App")
        val ids=compose.runOnIdle {f.owner.tabs.tabs.map {it.id}}
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        instrumentation.runOnMainSync {
            assertFalse(f.owner.mediaNetworkingAllowed());backgroundOpen();backgroundExit()
            assertEquals(ids,f.owner.tabs.tabs.map {it.id});assertEquals(f.id,f.owner.tabs.activeTab.id)
            assertEquals(f.app.id(),f.owner.privateApp(f.id)?.id())
        }
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.onNodeWithText("Open in Browser").assertDoesNotExist()
        compose.runOnIdle {assertSame(f.view,f.owner.activeWebViewOrNull())}
        compose.onNodeWithContentDescription("App menu").performClick()
        val lockedOpen=action("Open in Browser");val lockedExit=action("Exit App");val lockedHome=action("Nova Home")
        compose.runOnIdle {
            compose.activity.lockNovaSpace()
            val lockedIds=f.owner.tabs.tabs.map {it.id}
            lockedOpen();lockedExit();lockedHome()
            assertEquals(lockedIds,f.owner.tabs.tabs.map {it.id})
            assertFalse(compose.activity.primarySessionRetainedForNova());assertFalse(f.owner.hasPrivateAppTabs())
            assertNull(f.view.parent)
        }
        compose.onNodeWithContentDescription("App menu").assertDoesNotExist()
        compose.onNodeWithText("Open in Browser").assertDoesNotExist()
        compose.onNodeWithTag("preview-home").assertDoesNotExist()
    }

    private fun js(view:WebView,expression:String):String {
        val done=CountDownLatch(1);var value:String?=null
        instrumentation.runOnMainSync {view.evaluateJavascript(expression){value=it;done.countDown()}}
        assertTrue("Local fixture JavaScript callback",done.await(10,TimeUnit.SECONDS))
        return checkNotNull(value)
    }

    @Test fun actualBlackScreenMenuKeepsPlayingWebViewBlocksTouchesAndWakesWithoutNavigation()=withFixture(allowCapture=true) {f ->
        startBlackAudio(f)
        val originalParent=compose.runOnIdle{f.view.parent}
        val url=js(f.view,"location.href")
        val brightness=compose.runOnIdle{compose.activity.window.attributes.screenBrightness}
        compose.runOnIdle{compose.activity.window.attributes=compose.activity.window.attributes.apply{screenBrightness=.68f}}
        try {
            showBlack(f)
            captureBlackWindow(f,"actual-main-playing")
            val before=js(f.view,"document.getElementById('fixture-audio').currentTime").toDouble()
            compose.waitUntil(10000){js(f.view,"!document.getElementById('fixture-audio').paused")=="true" && js(f.view,"document.getElementById('fixture-audio').currentTime").toDouble()>before}
            blackTap(0)
            compose.runOnIdle{assertTrue(compose.activity.novaBlackScreenActive)}
            assertEquals("1",js(f.view,"window.fixtureClicks"))
            assertEquals("true",js(f.view,"setTimeout(function(){window.blackAlertBefore=1;alert('Synthetic black screen prompt');window.blackAlertAfter=1;},0);true"))
            compose.waitUntil(10000){js(f.view,"window.blackAlertAfter===1")=="true"}
            assertEquals("true",js(f.view,"(function(){var field=document.createElement('input');field.id='synthetic-black-focus';document.body.appendChild(field);field.focus();window.focus();return true;})()"))
            compose.waitForIdle()
            compose.runOnIdle{assertTrue(checkNotNull(compose.activity.window.decorView.findViewWithTag<android.view.View>("nova-black-screen")).hasFocus())}
            captureBlackWindow(f,"actual-main-after-script-focus")
            compose.runOnIdle{assertTrue(compose.activity.novaBlackScreenActive);compose.activity.onBackPressedDispatcher.onBackPressed()}
            compose.waitUntil(10000){!compose.activity.novaBlackScreenActive}
            compose.runOnIdle{assertEquals(.68f,compose.activity.window.attributes.screenBrightness,0f)}
            assertEquals(url,js(f.view,"location.href"));assertEquals("1",js(f.view,"window.fixtureClicks"))
            showBlack(f);blackTap(0);blackTap(70)
            compose.waitUntil(10000){!compose.activity.novaBlackScreenActive}
            compose.runOnIdle{assertSame(f.view,f.owner.activeWebViewOrNull());assertSame(originalParent,f.view.parent);assertEquals(f.id,f.owner.tabs.activeTab.id);assertEquals(.68f,compose.activity.window.attributes.screenBrightness,0f)}
            assertEquals("true",js(f.view,"window.fixtureStarted===true && !document.getElementById('fixture-audio').paused"))
            assertEquals("1",js(f.view,"window.fixtureClicks"))
        }finally{compose.runOnIdle{compose.activity.dismissNovaBlackScreen();compose.activity.window.attributes=compose.activity.window.attributes.apply{screenBrightness=brightness}}}
    }
    @Test fun actualBlackScreenHomeSwitchRetainsAudioAndPanicRevokesTheNextCover()=withFixture {f ->
        startBlackAudio(f);showBlack(f)
        val original=compose.runOnIdle{compose.activity.captureNovaBlackScreenAuthority(f.id,f.app,f.view)}
        compose.runOnIdle{compose.activity.returnPrivateHome();assertFalse(compose.activity.novaBlackScreenActive);assertFalse(original())}
        assertEquals("true",js(f.view,"!document.getElementById('fixture-audio').paused"))
        compose.runOnIdle{
            invoke(compose.activity,"launchHomeShortcut",HomeLayout.Shortcut.BROWSER)
            f.owner.openRetainedPrivateApp(f.app)
        }
        compose.waitUntil(15000){compose.onAllNodesWithContentDescription("App menu").fetchSemanticsNodes().isNotEmpty()}
        showBlack(f)
        val before=compose.runOnIdle{checkNotNull(compose.activity.window.decorView.findViewWithTag<android.view.View>("nova-black-screen"))}
        compose.runOnIdle{compose.activity.panicExit();assertFalse(compose.activity.novaBlackScreenActive)}
        compose.waitUntil(10000){!ViewModelProvider(compose.activity)[ProtectedSessionState::class.java].session.isUnlocked}
        compose.runOnIdle{before.performAccessibilityAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK,null);assertFalse(compose.activity.novaBlackScreenActive);assertFalse(original())}
    }
    @Test fun actualBlackScreenAuthenticationExpiryCannotWakeOrRestoreARevokedPage()=withFixture {f ->
        startBlackAudio(f);showBlack(f)
        val original=compose.runOnIdle{compose.activity.captureNovaBlackScreenAuthority(f.id,f.app,f.view)}
        val cover=compose.runOnIdle{checkNotNull(compose.activity.window.decorView.findViewWithTag<android.view.View>("nova-black-screen"))}
        compose.runOnIdle{
            ViewModelProvider(compose.activity)[ProtectedSessionState::class.java].session.lock()
            compose.activity.refreshNovaBlackScreenAuthority()
            assertFalse(compose.activity.novaBlackScreenActive);assertFalse(original())
            cover.performAccessibilityAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK,null)
            assertFalse(ViewModelProvider(compose.activity)[ProtectedSessionState::class.java].session.isUnlocked)
            assertFalse(compose.activity.requestNovaBlackScreen(original))
        }
        compose.onNodeWithTag("preview-home").assertDoesNotExist()
    }
    private fun startBlackAudio(f:Fixture) {
        compose.onNodeWithTag("browser-v2-webview-host").performTouchInput{click(center)}
        compose.waitUntil(15000){js(f.view,"window.fixtureStarted===true && !document.getElementById('fixture-audio').paused")=="true"}
    }
    private fun showBlack(f:Fixture) {
        compose.onNodeWithContentDescription("App menu").performClick()
        compose.onNodeWithText("Black Screen Mode").performScrollTo().performClick()
        compose.waitUntil(15000){compose.runOnIdle{compose.activity.novaBlackScreenActive && compose.activity.window.decorView.hasWindowFocus()}}
        compose.runOnIdle{assertSame(f.view,f.owner.activeWebViewOrNull());assertEquals(f.id,f.owner.tabs.activeTab.id);assertEquals(0f,compose.activity.window.attributes.screenBrightness,0f)}
    }
    private var blackTapBase=0L
    private fun blackTap(offset:Long) {
        compose.runOnIdle {
            if(offset==0L)blackTapBase=android.os.SystemClock.uptimeMillis()
            val down=blackTapBase+offset;val decor=compose.activity.window.decorView
            for((action,at)in listOf(android.view.MotionEvent.ACTION_DOWN to down,android.view.MotionEvent.ACTION_UP to down+5)) {
                val event=android.view.MotionEvent.obtain(down,at,action,decor.width/2f,decor.height/2f,0)
                try{compose.activity.dispatchTouchEvent(event)}finally{event.recycle()}
            }
        }
    }
    private fun captureBlackWindow(f:Fixture,label:String) {
        compose.waitUntil(10000){compose.runOnIdle{compose.activity.window.decorView.rootWindowInsets?.let{
            !it.isVisible(android.view.WindowInsets.Type.statusBars()) && !it.isVisible(android.view.WindowInsets.Type.navigationBars()) && !it.isVisible(android.view.WindowInsets.Type.ime())}==true}}
        val bitmap=compose.runOnIdle{
            assertEquals(0,compose.activity.window.attributes.flags and android.view.WindowManager.LayoutParams.FLAG_SECURE)
            android.graphics.Bitmap.createBitmap(compose.activity.window.decorView.width,compose.activity.window.decorView.height,android.graphics.Bitmap.Config.ARGB_8888)
        }
        try {
            val gate=CountDownLatch(1);var status=-1
            compose.runOnIdle{android.view.PixelCopy.request(compose.activity.window,bitmap,{status=it;gate.countDown()},android.os.Handler(android.os.Looper.getMainLooper()))}
            assertTrue(gate.await(10,TimeUnit.SECONDS));assertEquals(android.view.PixelCopy.SUCCESS,status)
            val pixels=IntArray(bitmap.width*bitmap.height);bitmap.getPixels(pixels,0,bitmap.width,0,0,bitmap.width,bitmap.height)
            assertTrue("Actual Main window must be opaque pure black",pixels.all{it==android.graphics.Color.BLACK})
            val additional=InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")?:File(compose.activity.externalMediaDirs.first(),"additional_test_output").absolutePath
            val output=File(additional,"nova-black61").apply{check(isDirectory||mkdirs())}
            File(output,"$label.png").outputStream().use{assertTrue(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it))}
            File(output,"$label.json").writeText(org.json.JSONObject().put("synthetic",true).put("actualMain",true).put("sdk",android.os.Build.VERSION.SDK_INT).put("app",f.app.id()).put("tab",f.id).put("sameWebView",true).put("playing",js(f.view,"!document.getElementById('fixture-audio').paused")).put("pureBlack",true).put("brightness",0).put("width",bitmap.width).put("height",bitmap.height).toString(2))
        }finally{bitmap.recycle()}
    }

    private fun html():String {
        val rate=8_000;val data=rate*20*2
        val wav=ByteBuffer.allocate(44+data).order(ByteOrder.LITTLE_ENDIAN)
        wav.put("RIFF".toByteArray());wav.putInt(36+data);wav.put("WAVEfmt ".toByteArray());wav.putInt(16)
        wav.putShort(1);wav.putShort(1);wav.putInt(rate);wav.putInt(rate*2);wav.putShort(2);wav.putShort(16)
        wav.put("data".toByteArray());wav.putInt(data)
        return """<!doctype html><meta name="viewport" content="width=device-width,initial-scale=1">
            <style>html,body{margin:0;height:100%;width:100%}button{height:100%;width:100%;font:24px sans-serif}</style>
            <script>window.fixtureClicks=0;</script>
            <button onclick="window.fixtureClicks++;document.getElementById('fixture-audio').play().then(()=>window.fixtureStarted=true)">Play synthetic silent audio</button>
            <audio id="fixture-audio" loop preload="auto" src="data:audio/wav;base64,${Base64.getEncoder().encodeToString(wav.array())}"></audio>""".trimIndent()
    }
}
