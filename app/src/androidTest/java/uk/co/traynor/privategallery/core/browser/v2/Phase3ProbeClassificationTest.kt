package uk.co.traynor.privategallery.core.browser.v2

import org.junit.Assert.*
import org.junit.Test
import uk.co.traynor.privategallery.core.security.*
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/** Actual producer, distinct synthetic phases and original read array; no external network/data. */
class Phase3ProbeClassificationTest {
    @Test fun quotedNoneBaitCannotMakeEncryptedManifestEligible() = inspect(
        "#EXTM3U\n#EXT-X-KEY:METHOD=AES-128,URI=\"https://invalid.example/key?x=METHOD=NONE,foo\"\n".toByteArray(),MediaSaveKind.PROTECTED)
    @Test fun namespacedDashProtectionCannotMakeEncryptedManifestEligible() = inspect(
        "<MPD><Period><cenc:ContentProtection xmlns:cenc=\"urn:mpeg:cenc:2013\"/></Period></MPD>".toByteArray(),MediaSaveKind.PROTECTED,"list.mpd","application/dash+xml")
    @Test fun nonAsciiNamespaceCannotMakeEncryptedManifestEligible() = inspect(
        "<MPD><世界:ContentProtection xmlns:世界=\"synthetic\"/></MPD>".toByteArray(),MediaSaveKind.PROTECTED,"list.mpd","application/dash+xml")
    @Test fun legacyUnicodeKeyMarkerStaysProtected() = inspect(
        "#EXTM3U\n#EXT-X-KEY:METHOD=AES-128\n".toByteArray(),MediaSaveKind.PROTECTED)
    @Test fun malformedUtf8CannotProduceAbsenceOfProtectionEvidence() = inspect(
        byteArrayOf(0xc0.toByte(),0xaf.toByte()),MediaSaveKind.UNSUPPORTED)
    @Test fun utf16ProtectionCannotProduceAbsenceOfProtectionEvidence() = inspect(
        "<MPD><ContentProtection/></MPD>".toByteArray(Charsets.UTF_16LE),MediaSaveKind.UNSUPPORTED,"list.mpd","application/dash+xml")
    @Test fun ordinaryUtf8WithNonAsciiUriStaysEligible() = inspect(
        "#EXTM3U\n#EXTINF:4,\n世界.ts\n#EXT-X-ENDLIST\n".toByteArray(),MediaSaveKind.STREAM)
    @Test fun standaloneNoneStaysEligibleWithOriginalBufferWiped() = inspect(
        "#EXTM3U\n#EXT-X-KEY:METHOD=NONE\npart001.ts\n".toByteArray(),MediaSaveKind.STREAM)

    private fun inspect(payload: ByteArray,expected: MediaSaveKind,path: String="list.m3u8",mime: String="application/vnd.apple.mpegurl") {
        val authority=PrimarySessionAuthority { 0 }
        for (name in listOf("ioReleasePool","presentationReleasePool"))
            PrimarySessionAuthority::class.java.getDeclaredField(name).apply { isAccessible=true }.set(authority,ReleasePool(16))
        authority.open(ByteArray(32) { 0x5a });val owner=authority.operationOrNull(setOf(PrimaryScope.READ,PrimaryScope.BROWSER_UPLOAD_EGRESS))!!
        val actualArray=AtomicReference<ByteArray>();val factories=AtomicInteger();val disconnects=AtomicInteger();val closes=AtomicInteger()
        val url="https://invalid.example/$path"
        try {
            val result=BrowserMediaProbe.inspect(url,"synthetic","",ScopedIoGuard(owner,PrimaryScope.BROWSER_UPLOAD_EGRESS),{
                factories.incrementAndGet()
                object : HttpURLConnection(URL(url)) {
                    override fun connect()=Unit
                    override fun usingProxy()=false
                    override fun disconnect() { disconnects.incrementAndGet() }
                    override fun getResponseCode()=200
                    override fun getContentType()=mime
                    override fun getInputStream()=object : ByteArrayInputStream(payload) {
                        override fun read(bytes: ByteArray,off: Int,len: Int): Int { actualArray.set(bytes);return super.read(bytes,off,len) }
                        override fun close() { closes.incrementAndGet();super.close() }
                    }
                }
            }) { false }
            assertEquals(expected,result.kind);assertEquals(2,factories.get());assertEquals(2,disconnects.get());assertEquals(1,closes.get())
            assertTrue(actualArray.get().all { it==0.toByte() });owner.checkValid()
        } finally { owner.close();authority.revoke();payload.fill(0) }
    }
}
