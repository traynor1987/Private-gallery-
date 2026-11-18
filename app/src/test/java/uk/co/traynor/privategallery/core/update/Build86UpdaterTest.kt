package uk.co.traynor.privategallery.core.update

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.security.MessageDigest

class Build86UpdaterTest {
    @Test fun installed85Discovers86WithActualApiAssetSizeAndOldMetadataFormat() {
        val digest = "ab".repeat(32)
        val prefix = "https://github.com/traynor1987/Private-gallery-/releases/download/v1.7.40/"
        val service = GithubReleaseUpdateService { url -> when {
            url.endsWith("latest") -> """{"tag_name":"v1.7.40","draft":false,"prerelease":false,"body":"Secure connection and update progress","assets":[{"name":"private-gallery-release.apk","size":151516847,"digest":"sha256:$digest","browser_download_url":"${prefix}private-gallery-release.apk"},{"name":"private-gallery-release.apk.sha256","browser_download_url":"${prefix}private-gallery-release.apk.sha256"},{"name":"private-gallery-release.metadata.json","browser_download_url":"${prefix}private-gallery-release.metadata.json"}]}"""
            url.endsWith("metadata.json") -> """{"package":"uk.co.traynor.privategallery","versionName":"1.7.40","versionCode":86,"certificateSha256":"94f2bfc6567f26d067d29077111cfd0ce86d38263c642ad115e43365f05b0d17","apkSha256":"$digest"}"""
            else -> "$digest  private-gallery-release.apk"
        }.toByteArray() }
        val update = service.check("1.7.39",85) as UpdateCheck.Available
        assertEquals(86L,update.release.versionCode)
        assertEquals(151516847L,update.release.apkSizeBytes)
        assertTrue(service.check("1.7.40",86) is UpdateCheck.UpToDate)
    }
    @Test fun progressAndSpeedComeOnlyFromBytesAndMonotonicElapsedTime() {
        var clock = 0L
        val meter = UpdateTransferMeter { clock }
        assertNull(meter.sample(100,1000))
        clock = 500_000_000
        assertEquals(1000L,meter.sample(500,1000)!!.bytesPerSecond)
        assertEquals(0.5f,NovaUpdateState(bytes=500,total=1000).progress)
        clock = 1_000_000_000
        assertEquals(0L,meter.sample(500,1000)!!.bytesPerSecond)
        assertEquals("151.5",updateMegabytes(151516847))
        assertNull(NovaUpdateState(total=-1).progress)
    }
    @Test fun interruptionAndCancellationNeverProduceACompleteFileAndRetryRestartsAtZero() {
        val body = ByteArray(140000) { it.toByte() }
        val out = ByteArrayOutputStream()
        val observations = mutableListOf<Long>()
        assertThrows(java.io.InterruptedIOException::class.java) {
            VerifiedUpdateTransfer.copy(body.inputStream(),out,body.size.toLong(),256000,MessageDigest.getInstance("SHA-256"),{out.size()>0}) { b,_ -> observations.add(b) }
        }
        assertTrue(out.size() < body.size)
        val retry = ByteArrayOutputStream()
        VerifiedUpdateTransfer.copy(body.inputStream(),retry,body.size.toLong(),256000,MessageDigest.getInstance("SHA-256"),{false}) { b,total ->
            assertTrue(b <= total)
        }
        assertArrayEquals(body,retry.toByteArray())
        assertThrows(IllegalStateException::class.java) {
            VerifiedUpdateTransfer.copy(body.copyOf(10).inputStream(),ByteArrayOutputStream(),body.size.toLong(),256000,MessageDigest.getInstance("SHA-256"),{false}) { _,_ -> }
        }
    }
    @Test fun insufficientStorageAndUnknownSizeFailBeforeTransfer() {
        assertThrows(IllegalStateException::class.java) { UpdateStoragePolicy.requireSpace(150000000,150000000,268435456) }
        assertThrows(IllegalStateException::class.java) { UpdateStoragePolicy.requireSpace(-1,200000000,268435456) }
        UpdateStoragePolicy.requireSpace(150000000,200000000,268435456)
    }
}
