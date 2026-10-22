package uk.co.traynor.privategallery.core.browser.hidden

import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.test.core.app.ActivityScenario
import androidx.webkit.ProfileStore
import org.junit.Assert.*
import org.junit.Test
import java.io.DataInputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Actual production interception + WebView + local SOCKS fixture; not live Tor/VPN evidence. */
class HiddenRedirectRenderingTest {
    @Test fun delayedOldMainRequestCannotAcquireNewNavigationAuthority() {
        ActivityScenario.launch(ComponentActivity::class.java).use {scenario ->
            scenario.onActivity {activity ->
                var dispatches=0
                val client=HiddenNavigationClient({dispatches++;null},{true},HiddenConnectionPolicy.Mode.TOR_ONLY)
                val view=WebView(activity)
                try {
                    client.expect("https://old.invalid/")
                    client.expect("https://new.invalid/")
                    val old=object:android.webkit.WebResourceRequest {
                        override fun getUrl()=android.net.Uri.parse("https://old.invalid/")
                        override fun isForMainFrame()=true
                        override fun isRedirect()=false
                        override fun hasGesture()=false
                        override fun getMethod()="GET"
                        override fun getRequestHeaders()=emptyMap<String,String>()
                    }
                    assertEquals(451,client.shouldInterceptRequest(view,old).statusCode)
                    assertEquals(0,dispatches)
                    client.invalidate()
                    assertEquals(451,client.shouldInterceptRequest(view,old).statusCode)
                    assertEquals(0,dispatches)
                } finally {view.destroy()}
            }
        }
    }
    @Test fun canonicalRedirectRendersWithRelativeCssWhileWebViewNetworkStaysDisabled() {
        ServerSocket(0,10,InetAddress.getByAddress(byteArrayOf(127,0,0,1))).use {server ->
            val css=CountDownLatch(1);val asset=CountDownLatch(1);val page=CountDownLatch(1)
            val executor=Executors.newSingleThreadExecutor()
            val requests=java.util.Collections.synchronizedList(mutableListOf<String>())
            val worker=executor.submit {
                while(!server.isClosed) {
                    val socket=try {server.accept()} catch(_:java.net.SocketException){break}
                    socket.use {
                        it.soTimeout=10000
                        val input=DataInputStream(it.getInputStream());val output=it.getOutputStream()
                        check(input.readUnsignedByte()==5);repeat(input.readUnsignedByte()){input.readUnsignedByte()}
                        output.write(byteArrayOf(5,0));output.flush()
                        check(input.readUnsignedByte()==5);check(input.readUnsignedByte()==1);input.readUnsignedByte();check(input.readUnsignedByte()==3)
                        val host=ByteArray(input.readUnsignedByte());input.readFully(host);input.readUnsignedShort()
                        output.write(byteArrayOf(5,0,0,1,0,0,0,0,0,80));output.flush()
                        val headers=StringBuilder();while(!headers.endsWith("\r\n\r\n"))headers.append(input.readUnsignedByte().toChar())
                        val path=headers.toString().substringBefore("\r\n").split(" ")[1]
                        requests.add("${String(host)}$path")
                        val (status,type,extra,body)=when(path) {
                            "/start" -> listOf("302 Found","text/plain","Location: http://destination.invalid/final/page\r\n","redirect bytes must not render")
                            "/final/style.css" -> listOf("302 Found","text/plain","Location: http://styles.invalid/assets/theme.css\r\n", "redirect")
                            "/assets/theme.css" -> {css.countDown();listOf("200 OK","text/css","","body{min-height:100px;background-image:url(pixel.svg)}")}
                            "/assets/pixel.svg" -> {asset.countDown();listOf("200 OK","image/svg+xml","","<svg xmlns='http://www.w3.org/2000/svg' width='1' height='1'/>")}
                            else -> listOf("200 OK","text/html","","<html><head><title>Canonical destination</title><link rel='stylesheet' href='style.css'></head><body>Safe page</body></html>")
                        }
                        val bytes=body.toByteArray()
                        output.write("HTTP/1.1 $status\r\nContent-Type: $type\r\n${extra}Content-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray());output.write(bytes);output.flush()
                    }
                }
            }
            HiddenHttpTransport.tor(server.localPort){true}.use {transport ->
                try {
                    ActivityScenario.launch(ComponentActivity::class.java).use {scenario ->
                        val name="hidden16-redirect-"+UUID.randomUUID()
                        lateinit var view:WebView
                        lateinit var client:HiddenNavigationClient
                        scenario.onActivity {activity ->
                            ProfileStore.getInstance().getOrCreateProfile(name)
                            view=HiddenWebViewFactory.create(activity,name)
                            client=object:HiddenNavigationClient({transport},{true},HiddenConnectionPolicy.Mode.TOR_ONLY) {
                                override fun onPageFinished(view:WebView,url:String?) {
                                    if(url=="http://destination.invalid/final/page" && view.title=="Canonical destination")page.countDown()
                                }
                            }
                            view.webViewClient=client;activity.setContentView(view)
                            client.navigate(view,"http://source.invalid/start")
                        }
                        assertTrue("Canonical page rendered",page.await(20,TimeUnit.SECONDS))
                        assertTrue("Relative CSS uses final page URL",css.await(5,TimeUnit.SECONDS))
                        assertTrue("Redirected CSS assets use canonical stylesheet URL",asset.await(10,TimeUnit.SECONDS))
                        scenario.onActivity {
                            assertEquals("http://destination.invalid/final/page",view.url)
                            assertTrue(view.settings.blockNetworkLoads);assertFalse(view.settings.javaScriptEnabled)
                            client.invalidate();view.destroy()
                            HiddenProfileCleanup.clear(ProfileStore.getInstance().getOrCreateProfile(name))
                        }
                        assertTrue(requests.contains("destination.invalid/final/style.css"))
                        assertFalse(requests.contains("source.invalid/style.css"))
                        assertTrue(requests.contains("styles.invalid/assets/pixel.svg"))
                        assertFalse(requests.contains("destination.invalid/final/pixel.svg"))
                    }
                } finally {server.close();worker.get(5,TimeUnit.SECONDS);executor.shutdownNow()}
            }
        }
    }
}
