package uk.co.traynor.privategallery.core.browser.v2

import org.junit.Assert.*
import org.junit.Test
import uk.co.traynor.privategallery.core.security.*
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

/** Synthetic HEAD/manifest phases through the actual producer; no external network or owner data. */
class Phase3ProbePhaseRetirementTest {
    @Test fun onlyThreeFreeSlotsSupportAcknowledgedSequentialMetadataAndManifestPhases() = isolated { _,owner,guard ->
        val holds=(1..13).map { owner.createOwned { attach -> AutoCloseable {}.also(attach) } }
        val metadata=FakeConnection();val manifest=FakeConnection();val factories=AtomicInteger()
        try {
            val result=BrowserMediaProbe.inspect(URL_VALUE,"synthetic","",guard,{ if (factories.incrementAndGet()==1) metadata else manifest }) { false }
            assertEquals(MediaSaveKind.STREAM,result.kind);assertEquals(2,factories.get())
            assertEquals(1,metadata.disconnects.get());assertEquals(1,manifest.disconnects.get())
        } finally { holds.forEach { it.close() } }
    }

    @Test fun blockedMetadataNativeReturnPreventsManifestFactoryUntilOriginalTerminalReturn() = isolated { _,_,guard ->
        val closing=CountDownLatch(1);val allowClose=CountDownLatch(1);val finished=CountDownLatch(1)
        val factories=AtomicInteger();val failure=AtomicReference<Throwable>();val result=AtomicReference<MediaSaveCandidate>()
        val metadata=object : FakeConnection() { override fun disconnect() { closing.countDown();check(allowClose.await(10,TimeUnit.SECONDS));super.disconnect() } }
        val manifest=FakeConnection()
        val producer=thread(isDaemon=true) {
            try { result.set(BrowserMediaProbe.inspect(URL_VALUE,"synthetic","",guard,{ if (factories.incrementAndGet()==1) metadata else manifest }) { false }) }
            catch (problem: Throwable) { failure.set(problem) } finally { finished.countDown() }
        }
        try {
            assertTrue(closing.await(5,TimeUnit.SECONDS));assertEquals("manifest must not exist before metadata native return",1,factories.get())
            assertEquals(1L,finished.count);allowClose.countDown();assertTrue(finished.await(5,TimeUnit.SECONDS))
            failure.get()?.let { throw AssertionError("synthetic phase failure",it) }
            assertEquals(MediaSaveKind.STREAM,result.get().kind);assertEquals(2,factories.get())
            assertEquals(1,metadata.disconnects.get());assertEquals(1,manifest.disconnects.get())
        } finally { allowClose.countDown();producer.interrupt();assertTrue(finished.await(5,TimeUnit.SECONDS)) }
    }

    @Test fun failedMetadataDisconnectDeniesBeforeAnyManifestNativeFactory() = isolated { authority,_,guard ->
        val factories=AtomicInteger();val metadata=object : FakeConnection() { override fun disconnect(): Unit = throw java.io.IOException("synthetic metadata release failure") }
        val result=BrowserMediaProbe.inspect(URL_VALUE,"synthetic","",guard,{ if (factories.incrementAndGet()==1) metadata else FakeConnection() }) { false }
        assertEquals(MediaSaveKind.UNSUPPORTED,result.kind);assertEquals(1,factories.get())
        authority.revoke();assertFalse(authority.cleanupComplete)
        val denied=ByteArray(32) { 9 };assertThrows(IllegalStateException::class.java) { authority.open(denied) };assertArrayEquals(ByteArray(32),denied)
    }

    @Test fun metadataDisconnectRevocationDeniesBeforeAnyManifestFactory() = isolated { authority,_,guard ->
        val factories=AtomicInteger();val metadata=object : FakeConnection() { override fun disconnect() { super.disconnect();authority.revoke() } }
        val result=BrowserMediaProbe.inspect(URL_VALUE,"synthetic","",guard,{ if (factories.incrementAndGet()==1) metadata else FakeConnection() }) { false }
        assertEquals(MediaSaveKind.UNSUPPORTED,result.kind);assertEquals(1,factories.get());assertTrue(authority.cleanupComplete)
    }

    @Test fun cancellationDuringManifestDisposalDeniesFinalResultAfterMetadataRetirement() = isolated { _,_,guard ->
        val cancelled=AtomicBoolean();val factories=AtomicInteger();val metadata=FakeConnection()
        val manifest=object : FakeConnection() {
            override fun getInputStream()=object : ByteArrayInputStream("#EXTM3U\n#EXT-X-ENDLIST\n".toByteArray()) {
                override fun close() { super.close();cancelled.set(true) }
            }
        }
        val result=BrowserMediaProbe.inspect(URL_VALUE,"synthetic","",guard,{ if (factories.incrementAndGet()==1) metadata else manifest }) { cancelled.get() }
        assertEquals(MediaSaveKind.UNSUPPORTED,result.kind);assertTrue(cancelled.get());assertEquals(2,factories.get())
        assertEquals(1,metadata.disconnects.get());assertEquals(1,manifest.disconnects.get())
    }

    private open class FakeConnection : HttpURLConnection(URL(URL_VALUE)) {
        val disconnects=AtomicInteger()
        override fun connect()=Unit
        override fun usingProxy()=false
        override fun disconnect() { disconnects.incrementAndGet() }
        override fun getResponseCode()=200
        override fun getContentType()="application/vnd.apple.mpegurl"
        override fun getInputStream()=ByteArrayInputStream("#EXTM3U\n#EXT-X-ENDLIST\n".toByteArray())
    }
    private fun isolated(test: (PrimarySessionAuthority,PrimaryOperation,ScopedIoGuard) -> Unit) {
        val authority=PrimarySessionAuthority { 0 }
        for (name in listOf("ioReleasePool","presentationReleasePool"))
            PrimarySessionAuthority::class.java.getDeclaredField(name).apply { isAccessible=true }.set(authority,ReleasePool(16))
        authority.open(ByteArray(32) { 0x5a });val owner=authority.operationOrNull(setOf(PrimaryScope.READ,PrimaryScope.BROWSER_UPLOAD_EGRESS))!!
        try { test(authority,owner,ScopedIoGuard(owner,PrimaryScope.BROWSER_UPLOAD_EGRESS)) } finally { owner.close();authority.revoke() }
    }
    private companion object { const val URL_VALUE="https://invalid.example/list.m3u8" }
}
