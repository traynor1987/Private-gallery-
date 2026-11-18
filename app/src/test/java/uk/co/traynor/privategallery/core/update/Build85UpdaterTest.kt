package uk.co.traynor.privategallery.core.update

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Test

class Build85UpdaterTest {
    private val digest="ab".repeat(32)
    private val prefix="https://github.com/traynor1987/Private-gallery-/releases/download/v1.7.39/"
    private fun response(flag:String="",sum:String=digest)= """{"tag_name":"v1.7.39","body":"Release notes","draft":false,"prerelease":false,$flag"assets":[{"name":"private-gallery-release.apk","digest":"sha256:$sum","browser_download_url":"${prefix}private-gallery-release.apk"},{"name":"private-gallery-release.apk.sha256","browser_download_url":"${prefix}private-gallery-release.apk.sha256"},{"name":"private-gallery-release.metadata.json","browser_download_url":"${prefix}private-gallery-release.metadata.json"}]}"""
    private fun service(code:Long=85,identitySum:String=digest,apiSum:String=digest)=GithubReleaseUpdateService { url ->
        when {
            url.endsWith("latest") -> response(sum=apiSum)
            url.endsWith("metadata.json") -> """{"package":"uk.co.traynor.privategallery","versionName":"1.7.39","versionCode":$code,"certificateSha256":"94f2bfc6567f26d067d29077111cfd0ce86d38263c642ad115e43365f05b0d17","apkSha256":"$identitySum"}"""
            else -> "$digest  private-gallery-release.apk"
        }.toByteArray()
    }
    @Test fun build84Discovers85ByCodeAndShowsNotes() {
        val value=service().check("1.7.38.8",84) as UpdateCheck.Available
        assertEquals(85L,value.release.versionCode);assertEquals("1.7.39",value.release.version.raw)
        assertEquals("Release notes",value.release.notes)
    }
    @Test fun equalAndLowerCodesAreNotUpdatesEvenWhenNameIsNewer() {
        assertTrue(service(84).check("1.7.38.8",84) is UpdateCheck.UpToDate)
        assertTrue(service(83).check("1.7.38.8",84) is UpdateCheck.UpToDate)
    }
    @Test fun trustedApiDigestAndSeparateMetadataMustAgree() {
        assertTrue(service(apiSum="cd".repeat(32)).check("1.7.38.8",84) is UpdateCheck.Failed)
        assertTrue(service(identitySum="cd".repeat(32)).check("1.7.38.8",84) is UpdateCheck.Failed)
    }
    @Test fun apiFailuresAndStableFilteringFailClosed() {
        for(status in listOf(403,429,500)) {
            val check=GithubReleaseUpdateService{throw GithubHttpException(status)}.check("1.7.38.8",84)
            assertEquals(UpdateCheck.Failed(UpdateFailure.GITHUB_API_ERROR),check)
        }
        for(flag in listOf("draft","prerelease")) {
            val check=GithubReleaseUpdateService{response().replace("\"$flag\":false","\"$flag\":true").toByteArray()}.check("1.7.38.8",84)
            assertEquals(UpdateCheck.Failed(UpdateFailure.NO_VALID_RELEASE),check)
        }
    }
    @Test fun wrongSigningIdentityAndPackageMetadataAreRejected() {
        val real="94f2bfc6567f26d067d29077111cfd0ce86d38263c642ad115e43365f05b0d17"
        for(replacement in listOf(real to "ef".repeat(32),"uk.co.traynor.privategallery" to "other.package")) {
            // Replace only authenticated metadata; the official release response stays identical.
            val changed=GithubReleaseUpdateService{url ->
                if(url.endsWith("latest"))response().toByteArray()
                else if(url.endsWith("metadata.json"))"""{"package":"uk.co.traynor.privategallery","versionName":"1.7.39","versionCode":85,"certificateSha256":"$real","apkSha256":"$digest"}""".replace(replacement.first,replacement.second).toByteArray()
                else digest.toByteArray()
            }
            assertTrue(changed.check("1.7.38.8",84) is UpdateCheck.Failed)
        }
    }
    @Test fun privateTransferDetectsTruncationOversizeInterruptionAndAllowsFreshRetry() {
        val body=ByteArray(100){it.toByte()}
        fun copy(length:Long,limit:Long,cancel:()->Boolean={false}):ByteArray {
            val out=ByteArrayOutputStream();VerifiedUpdateTransfer.copy(body.inputStream(),out,length,limit,MessageDigest.getInstance("SHA-256"),cancel){_,_->};return out.toByteArray()
        }
        assertThrows(IllegalStateException::class.java){copy(101,200)}
        assertThrows(IllegalStateException::class.java){copy(-1,50)}
        assertThrows(IOException::class.java){copy(100,200){true}}
        assertArrayEquals(body,copy(100,200))
    }
    @Test fun installerResultIsNeverProofOfInstalledVersion() {
        assertFalse(UpdateInstallPolicy.confirmedInstalled(85,84))
        assertFalse(UpdateInstallPolicy.confirmedInstalled(0,85))
        assertTrue(UpdateInstallPolicy.confirmedInstalled(85,85))
        assertFalse(UpdateInstallPolicy.acceptsAndroid17(36,37))
        assertTrue(UpdateInstallPolicy.acceptsAndroid17(37,37))
    }
    @Test fun busyStatesPreventConcurrentActionsAndProgressIsBounded() {
        assertTrue(NovaUpdateState(UpdatePhase.INSTALLATION_REQUESTED).busy)
        assertTrue(NovaUpdateState(UpdatePhase.VERIFYING).busy)
        assertEquals(1f,NovaUpdateState(bytes=120,total=100).progress)
        assertNull(NovaUpdateState(total=-1).progress)
    }
}
