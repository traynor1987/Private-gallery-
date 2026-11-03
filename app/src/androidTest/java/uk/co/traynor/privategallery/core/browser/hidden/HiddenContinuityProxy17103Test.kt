package uk.co.traynor.privategallery.core.browser.hidden

import org.junit.Assert.*
import org.junit.Test
import java.net.*
import java.util.concurrent.atomic.*

/** Real loopback proxy/admission threads, intentionally no origin socket admission. */
class HiddenContinuityProxy17103Test {
    @Test fun staleRepositoryEpochCannotInheritForegroundAuthority() {
        val context=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        val root=java.io.File(context.noBackupFilesDir,"continuity-epoch-${java.util.UUID.randomUUID()}")
        val owned=object:android.content.ContextWrapper(context){override fun getNoBackupFilesDir()=root}
        val session=uk.co.traynor.privategallery.core.hidden.HiddenSession(android.os.SystemClock::elapsedRealtime)
        assertTrue(session.complete(session.beginAuthentication(),ByteArray(32){61}))
        val old=session.authority();session.onBackground(30_000);session.onForeground()
        try {
            assertThrows(IllegalStateException::class.java){
                uk.co.traynor.privategallery.core.hidden.HiddenRepository(owned,session,operationAuthority=old).items()
            }
            assertTrue(uk.co.traynor.privategallery.core.hidden.HiddenRepository(owned,session).items().isEmpty())
        }finally {session.lock();root.deleteRecursively()}
    }
    @Test fun preparedProxyRejectsRequestsUntilCommitAndRetiredCredentialsStayDead() {
        val admitted=AtomicBoolean();val lease=AtomicBoolean(true);val resolutions=AtomicInteger()
        val proxy=HiddenVpnProxy(javax.net.SocketFactory.getDefault(),{resolutions.incrementAndGet();throw UnknownHostException("Fixture stops before upstream socket")},{admitted.get()},leaseCurrent={lease.get()})
        val port=proxy.port
        fun attempt()=runCatching {Socket("127.0.0.1",port).use {socket ->
            socket.soTimeout=1000
            val credential=java.util.Base64.getEncoder().encodeToString("hidden:${proxy.password}".toByteArray())
            socket.getOutputStream().write("CONNECT example.com:443 HTTP/1.1\r\nHost: example.com:443\r\nProxy-Authorization: Basic $credential\r\n\r\n".toByteArray())
            socket.getInputStream().read()
        }}
        try {
            attempt();Thread.sleep(250);attempt()
            assertEquals("Prepared listener grants no DNS/upstream IO",0,resolutions.get())
            admitted.set(true);attempt()
            assertEquals("The same listener accepts only after authoritative commit",1,resolutions.get())
            lease.set(false);Thread.sleep(250);attempt()
            assertEquals("Revoked lease closes listener even if admission flag remained true",1,resolutions.get())
        }finally {proxy.close()}
    }
}
