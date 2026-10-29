package uk.co.traynor.privategallery.core.browser.hidden

import android.content.ContextWrapper
import android.graphics.Bitmap
import androidx.test.platform.app.InstrumentationRegistry
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.*
import org.junit.Test
import uk.co.traynor.privategallery.core.hidden.HiddenRepository
import uk.co.traynor.privategallery.core.hidden.HiddenSession

/** Mock SOCKS endpoint and real encrypted storage. No real Tor/VPN claim. */
class HiddenDownloadTest {
    private fun fixture(revoke:Boolean,mime:String="image/png") {
        val base=InstrumentationRegistry.getInstrumentation().targetContext
        val root=File(base.noBackupFilesDir,"hidden-download-test-${UUID.randomUUID()}")
        val context=object:ContextWrapper(base){override fun getNoBackupFilesDir()=root}
        val session=HiddenSession(android.os.SystemClock::elapsedRealtime)
        assertTrue(session.complete(session.beginAuthentication(),ByteArray(32){7}))
        val repo=HiddenRepository(context,session)
        val allowed=AtomicBoolean(true)
        val bytes=if(mime=="application/pdf") "%PDF-1.7\n1 0 obj\n<<>>\nendobj\n%%EOF\n".toByteArray() else if(mime=="text/csv") "column,value\nprivate-fixture,42".toByteArray() else Bitmap.createBitmap(16,16,Bitmap.Config.ARGB_8888).let {bitmap ->
            try {ByteArrayOutputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it);it.toByteArray()}} finally {bitmap.recycle()}
        }
        val executor=Executors.newSingleThreadExecutor()
        try {
            ServerSocket(0,1,InetAddress.getByAddress(byteArrayOf(127,0,0,1))).use {server ->
                server.soTimeout=10000
                val peer=executor.submit {
                    server.accept().use {socket ->
                        socket.soTimeout=10000
                        val input=DataInputStream(socket.getInputStream());val output=socket.getOutputStream()
                        check(input.readUnsignedByte()==5);repeat(input.readUnsignedByte()){input.readUnsignedByte()}
                        output.write(byteArrayOf(5,0));output.flush()
                        check(input.readUnsignedByte()==5);check(input.readUnsignedByte()==1);input.readUnsignedByte();check(input.readUnsignedByte()==3)
                        val host=ByteArray(input.readUnsignedByte());input.readFully(host);input.readUnsignedShort()
                        check(String(host)=="download-fixture.invalid")
                        output.write(byteArrayOf(5,0,0,1,0,0,0,0,0,80));output.flush()
                        var end="";while(!end.endsWith("\r\n\r\n"))end=(end+input.readUnsignedByte().toChar()).takeLast(4)
                        output.write("HTTP/1.1 200 OK\r\nContent-Type: $mime\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray())
                        output.write(bytes);output.flush()
                    }
                }
                HiddenHttpTransport.tor(server.localPort){allowed.get()}.use {transport ->
                    val observed=mutableListOf<Pair<Long,Long>>()
                    val result=runCatching {transport.download("http://download-fixture.invalid/private.png",progress={done,total ->observed.add(done to total)}) {mime,input ->
                        if(revoke)allowed.set(false)
                        repo.importMedia("private-download-name.png",mime,input)
                    }}
                    if(revoke) {assertTrue(result.isFailure);assertTrue(repo.items().isEmpty())}
                    else {
                        result.getOrThrow()
                        assertEquals(0L,observed.first().first)
                        assertEquals(bytes.size.toLong(),observed.last().first)
                        assertEquals(bytes.size.toLong(),observed.last().second)
                        assertTrue(observed.zipWithNext().all {(a,b)->b.first>=a.first})
                        val item=repo.items().single();repo.verify(item.id);assertArrayEquals(bytes,repo.read(item.id))
                        root.walkTopDown().filter {it.isFile}.forEach {file ->
                            assertEquals("enc",file.extension)
                            assertFalse(file.readBytes().contentEquals(bytes))
                            assertFalse(file.readBytes().toString(Charsets.ISO_8859_1).contains("private-download-name"))
                        }
                    }
                }
                peer.get(10,TimeUnit.SECONDS)
            }
        } finally {allowed.set(false);session.lock();executor.shutdownNow();bytes.fill(0);root.deleteRecursively()}
    }
    @Test fun downloadedMediaIsEncryptedBeforePersistence()=fixture(false)
    @Test fun revokedDownloadCannotPublishMedia()=fixture(true)
    @Test fun downloadedPdfProgressEndsOnlyAfterEncryptedPersistence()=fixture(false,"application/pdf")
    @Test fun downloadedCsvRemainsInertAndEncrypted()=fixture(false,"text/csv")
    @Test fun revokedDocumentDownloadCannotPublish()=fixture(true,"application/pdf")
}
