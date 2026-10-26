package uk.co.traynor.privategallery.core.browser.hidden

import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import org.junit.Assert.*
import org.junit.Test
import uk.co.traynor.privategallery.MainActivity
import uk.co.traynor.privategallery.core.hidden.HiddenSession
import java.io.DataInputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Deterministic failure control through the actual runtime/navigation callbacks, not owner data. */
class HiddenRuntimeFailureTest {
    @Test(timeout=210_000) fun stalledConnectTimeoutDisconnectsRuntimeAndClosesSocket()=stalled(false)
    @Test(timeout=210_000) fun sessionRevocationDuringStalledConnectCancelsRuntimeAndClosesSocket()=stalled(true)

    private fun stalled(revoke:Boolean) {
        val session=HiddenSession(SystemClock::elapsedRealtime)
        assertTrue(session.complete(session.beginAuthentication(),ByteArray(32){23}))
        val authority=session.authority()
        val host="a".repeat(56)+".onion"
        val started=SystemClock.elapsedRealtime()
        var runtime:HiddenBrowserRuntime?=null
        var replacement:HiddenHttpTransport?=null
        var ownedTor:HiddenTorConnection?=null
        ActivityScenario.launch(MainActivity::class.java).use {scenario ->
            try {
                scenario.onActivity {activity ->
                    runtime=HiddenBrowserRuntime(activity,session,HiddenConnectionPolicy.Mode.TOR_ONLY)
                    runtime!!.start()
                }
                val startupDeadline=SystemClock.elapsedRealtime()+HiddenBrowserRuntime.STARTUP_TIMEOUT_MS+5000
                var ready=false
                while(!ready && SystemClock.elapsedRealtime()<startupDeadline) {
                    scenario.onActivity {ready=runtime!!.connected}
                    if(!ready)Thread.sleep(100)
                }
                assertTrue("Real runtime startup did not complete within its existing budget",ready)
                scenario.onActivity {
                    ownedTor=HiddenBrowserRuntime::class.java.getDeclaredField("tor").apply {isAccessible=true}.get(runtime) as HiddenTorConnection
                    assertTrue(ownedTor!!.alive())
                }
                ServerSocket(0,1,InetAddress.getByAddress(byteArrayOf(127,0,0,1))).use {server ->
                    server.soTimeout=8000
                    val handshake=CountDownLatch(1)
                    val executor=Executors.newSingleThreadExecutor()
                    val peer=executor.submit<Int> {
                        server.accept().use {socket ->
                            socket.soTimeout=45_000
                            val input=DataInputStream(socket.getInputStream())
                            val output=socket.getOutputStream()
                            check(input.readUnsignedByte()==5)
                            repeat(input.readUnsignedByte()){input.readUnsignedByte()}
                            output.write(byteArrayOf(5,0));output.flush()
                            check(input.readUnsignedByte()==5 && input.readUnsignedByte()==1)
                            check(input.readUnsignedByte()==0 && input.readUnsignedByte()==3)
                            val domain=ByteArray(input.readUnsignedByte());input.readFully(domain)
                            check(String(domain,Charsets.US_ASCII)==host && input.readUnsignedShort()==80)
                            // Deliberately never send CONNECT reply. Success here would invalidate the control.
                            handshake.countDown()
                            input.read()
                        }
                    }
                    var originalFailure:Throwable?=null
                    try {
                        replacement=HiddenHttpTransport.tor(server.localPort) {
                            runCatching {session.beginOperation(authority).use {session.isCurrent(it)}}.getOrDefault(false)
                        }
                        scenario.onActivity {
                            val field=HiddenBrowserRuntime::class.java.getDeclaredField("transport").apply {isAccessible=true}
                            (field.get(runtime) as? HiddenHttpTransport)?.close()
                            field.set(runtime,replacement)
                            runtime!!.navigate("http://$host/")
                        }
                        assertTrue("Original navigation must reach unresolved DOMAIN SOCKS CONNECT",handshake.await(8,TimeUnit.SECONDS))
                        val connectedAt=SystemClock.elapsedRealtime()
                        if(revoke)scenario.onActivity {session.lock()}
                        val deadline=connectedAt+if(revoke)8000 else 38_000
                        var disconnected=false
                        while(!disconnected && SystemClock.elapsedRealtime()<deadline) {
                            scenario.onActivity {disconnected=!runtime!!.connected && runtime!!.tabs.isEmpty()}
                            if(!disconnected)Thread.sleep(100)
                        }
                        val postconditionFailures=mutableListOf<Throwable>()
                        fun collect(check:()->Unit) {try {check()} catch(failure:Throwable) {postconditionFailures.add(failure)}}
                        var category="OTHER"
                        scenario.onActivity {
                            val detail=runtime!!.detail
                            category=when {
                                detail.startsWith("Page transport failed (timeout)") -> "TRANSPORT_TIMEOUT"
                                detail.startsWith("Page transport failed (") -> "TRANSPORT_OTHER"
                                detail.startsWith("Transport lost.") -> "TRANSPORT_LOST"
                                detail.startsWith("Browsing stopped.") -> "CLOSED"
                                detail.startsWith("Timed out during:") -> "STARTUP_TIMEOUT"
                                detail.startsWith("Failed during:") -> "STARTUP_FAILURE"
                                else -> "OTHER"
                            }
                        }
                        println("Runtime failure category: revoke=$revoke category=$category disconnected=$disconnected elapsedMs=${SystemClock.elapsedRealtime()-connectedAt}")
                        collect {assertTrue("Production runtime did not disconnect/clear tabs; diagnostic watchdog is not success",disconnected)}
                        collect {
                            if(!revoke)scenario.onActivity {
                                assertEquals("Connection failed",runtime!!.status)
                                assertTrue("Expected natural production CONNECT timeout",runtime!!.detail.contains("timeout"))
                            }
                        }
                        var peerClosed=false
                        collect {peerClosed=peer.get(3,TimeUnit.SECONDS).toInt()==-1;assertTrue("Stalled peer must observe production socket closure",peerClosed)}
                        var reuseDenied=false
                        collect {reuseDenied=replacement!!.request("http://$host/late","GET").statusCode==503;assertTrue("Closed transport must reject reuse",reuseDenied)}
                        collect {scenario.onActivity {runtime!!.navigate("http://$host/late");runtime!!.newTab();assertTrue(runtime!!.tabs.isEmpty());assertFalse(runtime!!.connected)}}
                        var extraConnection=false
                        collect {
                            server.soTimeout=500
                            try {server.accept().close();extraConnection=true;fail("Revoked/closed runtime opened a subsequent SOCKS connection")}
                            catch(_:java.net.SocketTimeoutException) {}
                        }
                        println("Runtime failure postconditions: revoke=$revoke peerClosed=$peerClosed reuseDenied=$reuseDenied extraConnection=$extraConnection failures=${postconditionFailures.size}")
                        if(postconditionFailures.isNotEmpty()) {
                            val first=postconditionFailures.first()
                            postconditionFailures.drop(1).forEach {first.addSuppressed(it)}
                            throw first
                        }
                        println("Runtime failure control: revoke=$revoke localFailure=true peerClosed=true tabs=0 elapsedMs=${SystemClock.elapsedRealtime()-started}")
                    } catch(failure:Throwable) {originalFailure=failure;throw failure}
                    finally {
                        // Always exercise actual cleanup, including when any earlier assertion throws.
                        try {
                            scenario.onActivity {runtime?.close();assertFalse(runtime?.connected ?: false);assertTrue(runtime?.tabs?.isEmpty() ?: true)}
                            assertFalse("Failure cleanup revokes Tor connection",ownedTor?.alive() ?: false)
                            assertEquals(0,ownedTor?.port() ?: 0)
                        } catch(cleanup:Throwable) {
                            if(originalFailure!=null)originalFailure!!.addSuppressed(cleanup) else throw cleanup
                        } finally {replacement?.close();executor.shutdownNow()}
                    }
                }
            } finally {scenario.onActivity {runtime?.close();session.lock()}}
        }
    }
}
