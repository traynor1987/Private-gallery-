package uk.co.traynor.privategallery.ui

import android.view.View
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Base64
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import uk.co.traynor.privategallery.core.browser.v2.*
import uk.co.traynor.privategallery.core.privateapps.PrivateAppCatalog

/** Real provider HTML audio started by one user gesture; silence, no network or autoplay override. */
@RunWith(AndroidJUnit4::class)
class NovaPrivateAppAudioTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    private lateinit var session:BrowserV2Session
    private var activeId by mutableStateOf("")
    private var activeView by mutableStateOf<WebView?>(null)
    private var covered by mutableStateOf(false)
    private fun app(name:String)=PrivateAppCatalog.App(UUID.randomUUID().toString(),name,
        "https://audio-fixture.invalid/${name.lowercase()}","","standalone","",false,false,false)
    private fun showActive(){activeId=session.tabs.activeTab.id;activeView=session.activeWebView()}
    @After fun cleanup(){if(::session.isInitialized)compose.runOnIdle {session.destroyAll()}}

    @Test fun userStartedAudioStaysAttachedAndPlayingAcrossHomeAppAndStandaloneBrowserUntilBackground() {
        val created=linkedMapOf<String,NovaRetainedPrivateAppsTest.RecordingView>()
        val music=app("Music");val chat=app("Chat")
        compose.runOnIdle {
            assertEquals(37,android.os.Build.VERSION.SDK_INT)
            session=BrowserV2Session(compose.activity,BrowserVpnGate {true},NoopBrowserV2Listener,
                webViewFactory=BrowserV2WebViewFactory {context,_,id,_->
                    NovaRetainedPrivateAppsTest.RecordingView(context).apply {
                        // Production SecureWebViewFactory uses MATCH_PARENT: WRAP_CONTENT collapses
                        // CSS viewport-relative heights even when Compose measures a full host.
                        layoutParams=android.view.ViewGroup.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT,android.view.ViewGroup.LayoutParams.MATCH_PARENT)
                        settings.javaScriptEnabled=true // Matches production; gesture requirement stays untouched.
                        assertTrue(settings.mediaPlaybackRequiresUserGesture)
                    }.also {created[id]=it}
                })
            session.openRetainedPrivateApp(music);showActive()
        }
        compose.setContent {RetainedPrivateAppWebViews(session,activeId,activeView,covered)}
        val musicId=compose.runOnIdle {session.tabs.activeTab.id}
        val musicView=created.getValue(musicId)
        val fixture="""<!doctype html><meta name="viewport" content="width=device-width,initial-scale=1">
          <style>html,body{margin:0;width:100%;height:100%}button{width:100%;height:100%;font:24px sans-serif}</style>
          <button id="start" onclick="window.fixtureClicks=(window.fixtureClicks||0)+1;document.getElementById('fixture-audio').play().then(function(){window.fixtureStarted=true}).catch(function(e){window.fixtureError=e.name})">Play silent synthetic audio</button>
          <audio id="fixture-audio" loop preload="auto" src="data:audio/wav;base64,${silentWav()}"></audio>""".trimIndent()
        compose.runOnIdle {musicView.loadDataWithBaseURL("https://audio-fixture.invalid/",fixture,"text/html","UTF-8",null)}
        compose.waitUntil(10_000){compose.runOnIdle {musicView.isAttachedToWindow && musicView.hasWindowFocus() && musicView.width>0 && musicView.height>0}}
        awaitFixture(musicView,"Complete, laid-out local page and decoded silent WAV",
            "document.readyState==='complete' && !!document.getElementById('start') && document.getElementById('start').getBoundingClientRect().height>0 && document.getElementById('fixture-audio').readyState>=2")
        android.util.Log.i("NovaAudioFixture","Before user gesture: "+fixtureState(musicView))
        compose.onNodeWithTag("browser-v2-webview-host").performTouchInput {click(center)}
        awaitFixture(musicView,"The single real user gesture reaches the DOM button","window.fixtureClicks===1")
        awaitFixture(musicView,"The user-started WAV is playing","window.fixtureStarted===true && !document.getElementById('fixture-audio').paused")
        val originalParent=compose.runOnIdle {checkNotNull(musicView.parent).also {musicView.reset()}}
        fun assertRetained() {
            compose.waitForIdle()
            compose.runOnIdle {
                assertSame("Music must remain in the same attached AndroidView host",originalParent,musicView.parent)
                assertTrue(musicView.isAttachedToWindow)
                assertEquals(0,musicView.nativePauses);assertEquals(0,musicView.pauseScripts);assertEquals(0,musicView.destructions)
                assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS,musicView.importantForAccessibility)
                assertFalse(musicView.isFocusable);assertFalse(musicView.isEnabled)
            }
            val before=js(musicView,"document.getElementById('fixture-audio').currentTime")
            compose.waitUntil(10_000) {
                js(musicView,"!document.getElementById('fixture-audio').paused")=="true" &&
                    js(musicView,"document.getElementById('fixture-audio').currentTime")!=before
            }
        }
        compose.runOnIdle {covered=true}
        assertRetained()
        compose.runOnIdle {covered=false;session.openRetainedPrivateApp(chat);showActive()}
        assertRetained()
        compose.runOnIdle {session.newTabFromNovaHome();showActive();assertNull(session.privateApp(activeId))}
        assertRetained()
        compose.runOnIdle {session.openRetainedPrivateApp(chat);showActive()}
        assertRetained()
        compose.runOnIdle {session.pauseForBackground()}
        compose.waitUntil(10_000){js(musicView,"document.getElementById('fixture-audio').paused")=="true"}
        compose.runOnIdle {
            assertFalse(session.mediaNetworkingAllowed())
            assertEquals(1,musicView.nativePauses);assertEquals(1,musicView.pauseScripts)
            assertThrows(IllegalStateException::class.java){session.selectFromNovaHome(musicId)}
        }
    }

    private fun js(view:WebView,expression:String):String {
        val done=CountDownLatch(1);var result:String?=null
        compose.runOnIdle {view.evaluateJavascript(expression){result=it;done.countDown()}}
        assertTrue("Local fixture JavaScript callback",done.await(10,TimeUnit.SECONDS))
        return checkNotNull(result)
    }
    private fun fixtureState(view:WebView):String {
        val dom=js(view,"JSON.stringify({clicks:window.fixtureClicks||0,started:window.fixtureStarted===true,error:window.fixtureError||null,visibility:document.visibilityState,focus:document.hasFocus(),viewport:[innerWidth,innerHeight],button:document.getElementById('start')?.getBoundingClientRect().toJSON(),audio:document.getElementById('fixture-audio')?{paused:document.getElementById('fixture-audio').paused,time:document.getElementById('fixture-audio').currentTime,duration:document.getElementById('fixture-audio').duration,ready:document.getElementById('fixture-audio').readyState,network:document.getElementById('fixture-audio').networkState,error:document.getElementById('fixture-audio').error?.code}:null})")
        val native=compose.runOnIdle {"native=${view.width}x${view.height},attached=${view.isAttachedToWindow},windowFocus=${view.hasWindowFocus()},viewFocus=${view.hasFocus()},enabled=${view.isEnabled},visibility=${view.visibility},layout=${view.layoutParams.width}x${view.layoutParams.height}"}
        return "$native; $dom"
    }
    private fun awaitFixture(view:WebView,label:String,condition:String) {
        try {compose.waitUntil(15_000){js(view,condition)=="true"}}
        catch(failure:Throwable) {
            val state=fixtureState(view)
            android.util.Log.e("NovaAudioFixture","$label failed: $state")
            throw AssertionError("$label; $state",failure)
        }
    }
    private fun silentWav():String {
        val samples=8_000;val data=samples*2
        val wav=ByteBuffer.allocate(44+data).order(ByteOrder.LITTLE_ENDIAN)
        wav.put("RIFF".toByteArray());wav.putInt(36+data);wav.put("WAVEfmt ".toByteArray());wav.putInt(16)
        wav.putShort(1);wav.putShort(1);wav.putInt(samples);wav.putInt(samples*2);wav.putShort(2);wav.putShort(16)
        wav.put("data".toByteArray());wav.putInt(data)
        return Base64.getEncoder().encodeToString(wav.array())
    }
}
