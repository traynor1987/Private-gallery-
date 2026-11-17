package uk.co.traynor.privategallery.core.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseUpdateTest {
    @Test fun `semantic version treats newer release as update`() {
        assertTrue(ReleaseVersion.parse("1.1.0") > ReleaseVersion.parse("1.0.0"))
        assertTrue(ReleaseVersion.parse("1.0.1") > ReleaseVersion.parse("1.0.0"))
    }

    @Test fun `metadata requires apk and sha256`() {
        val result = ReleaseMetadata.parse(VALID_RELEASE)
        assertEquals("1.7.38.8", result.version.raw)
    }

    @Test fun `metadata rejects missing apk`() {
        runCatching { ReleaseMetadata.parse("""{"tag_name":"v1.1.0","assets":[]}""") }
            .onSuccess { throw AssertionError("Expected metadata rejection") }
    }

    @Test fun `equal release is not offered as an update`() {
        val service = GithubReleaseUpdateService { VALID_RELEASE.encodeToByteArray() }
        assertEquals("1.7.38.8", (service.check("1.7.38.8") as UpdateCheck.UpToDate).release.version.raw)
    }

    @Test fun `network failure is reported separately`() {
        val service = GithubReleaseUpdateService { throw java.io.IOException("offline") }
        assertEquals(UpdateCheck.Failed(UpdateFailure.NETWORK_ERROR), service.check("1.0.1"))
    }

    @Test fun `malformed release response is reported as parse error`() {
        val service = GithubReleaseUpdateService { "not json".encodeToByteArray() }
        assertEquals(UpdateCheck.Failed(UpdateFailure.PARSE_ERROR), service.check("1.0.1"))
    }

    @Test fun `missing release asset is reported separately`() {
        val service = GithubReleaseUpdateService { """{"tag_name":"v1.0.2","assets":[]}""".encodeToByteArray() }
        assertEquals(UpdateCheck.Failed(UpdateFailure.MISSING_ASSET), service.check("1.0.1"))
    }

    @Test fun `github 404 means no valid release`() {
        val service = GithubReleaseUpdateService { throw GithubHttpException(404) }
        assertEquals(UpdateCheck.Failed(UpdateFailure.NO_VALID_RELEASE), service.check("1.0.1"))
    }

    @Test fun `Build 83 discovers a four-component stable release`() {
        val service = GithubReleaseUpdateService { VALID_RELEASE.encodeToByteArray() }
        assertEquals("1.7.38.8", (service.check("1.7.38.7") as UpdateCheck.Available).release.version.raw)
    }

    @Test fun `draft and prerelease are excluded`() {
        listOf("draft", "prerelease").forEach { flag ->
            val service = GithubReleaseUpdateService { VALID_RELEASE.replace(""""$flag":false""", """"$flag":true""").encodeToByteArray() }
            assertEquals(UpdateCheck.Failed(UpdateFailure.NO_VALID_RELEASE), service.check("1.7.38.7"))
        }
    }

    @Test fun `production identity must match the independent digest asset`() {
        val release = ReleaseMetadata.parse(VALID_RELEASE)
        val digest = "ab".repeat(32)
        val identity = """{"package":"uk.co.traynor.privategallery","versionName":"1.7.38.8","versionCode":84,"certificateSha256":"94f2bfc6567f26d067d29077111cfd0ce86d38263c642ad115e43365f05b0d17","apkSha256":"$digest"}"""
        fun service(metadata: String, checksum: String) = GithubReleaseUpdateService {
            (if (it == release.identityUrl) metadata else checksum).encodeToByteArray()
        }
        assertEquals(84L, service(identity, "$digest  private-gallery-release.apk").verifiedIdentity(release)?.versionCode)
        assertEquals(null, service(identity, "ff".repeat(32)).verifiedIdentity(release))
        assertEquals(null, service(identity.replace("privategallery", "otherapp"), digest).verifiedIdentity(release))
    }

    @Test fun `metadata rejects an arbitrary update host`() {
        val invalid = VALID_RELEASE.replace("https://github.com/traynor1987/Private-gallery-", "https://example.invalid")
        assertTrue(runCatching { ReleaseMetadata.parse(invalid) }.isFailure)
    }

    private companion object {
        const val VALID_RELEASE = """{"tag_name":"v1.7.38.8","draft":false,"prerelease":false,"assets":[{"name":"private-gallery-release.apk","browser_download_url":"https://github.com/traynor1987/Private-gallery-/releases/download/v1.7.38.8/private-gallery-release.apk"},{"name":"private-gallery-release.apk.sha256","browser_download_url":"https://github.com/traynor1987/Private-gallery-/releases/download/v1.7.38.8/private-gallery-release.apk.sha256"},{"name":"private-gallery-release.metadata.json","browser_download_url":"https://github.com/traynor1987/Private-gallery-/releases/download/v1.7.38.8/private-gallery-release.metadata.json"}]}"""
    }
}
