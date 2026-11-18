package uk.co.traynor.privategallery.core.update

import org.junit.Assert.*
import org.junit.Test

class Build87UpdaterTest {
    @Test fun installed86DiscoversStable87UsingTheExistingIdentityMetadataWithoutChannelChanges() {
        val digest="b".repeat(64)
        val prefix="https://github.com/traynor1987/Private-gallery-/releases/download/v1.7.41/"
        val service=GithubReleaseUpdateService { url -> when {
            url.endsWith("latest") -> """{"tag_name":"v1.7.41","draft":false,"prerelease":false,"body":"Visible Nova Brain connection status","assets":[{"name":"private-gallery-release.apk","size":151600000,"digest":"sha256:$digest","browser_download_url":"${prefix}private-gallery-release.apk"},{"name":"private-gallery-release.apk.sha256","browser_download_url":"${prefix}private-gallery-release.apk.sha256"},{"name":"private-gallery-release.metadata.json","browser_download_url":"${prefix}private-gallery-release.metadata.json"}]}"""
            url.endsWith("metadata.json") -> """{"package":"uk.co.traynor.privategallery","versionName":"1.7.41","versionCode":87,"certificateSha256":"94f2bfc6567f26d067d29077111cfd0ce86d38263c642ad115e43365f05b0d17","apkSha256":"$digest"}"""
            else -> "$digest  private-gallery-release.apk"
        }.toByteArray() }
        val result=service.check("1.7.40",86) as UpdateCheck.Available
        assertEquals(87L,result.release.versionCode)
        assertEquals("1.7.41",result.release.version.raw)
        assertEquals(151600000L,result.release.apkSizeBytes)
        assertTrue(service.check("1.7.41",87) is UpdateCheck.UpToDate)
    }
}
