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
        val result = ReleaseMetadata.parse("""{"tag_name":"v1.1.0","assets":[{"name":"private-gallery-release.apk","browser_download_url":"https://github.com/traynor1987/Private-gallery-/releases/download/v1.1.0/private-gallery-release.apk"},{"name":"private-gallery-release.apk.sha256","browser_download_url":"https://github.com/traynor1987/Private-gallery-/releases/download/v1.1.0/private-gallery-release.apk.sha256"}]}""")
        assertEquals("1.1.0", result.version.raw)
    }

    @Test fun `metadata rejects missing apk`() {
        runCatching { ReleaseMetadata.parse("""{"tag_name":"v1.1.0","assets":[]}""") }
            .onSuccess { throw AssertionError("Expected metadata rejection") }
    }

    @Test fun `sha verifier rejects mismatch`() {
        assertTrue(!UpdateVerifier.matchesSha256("abc".encodeToByteArray(), "00".repeat(32)))
    }

    @Test fun `equal release is not offered as an update`() {
        val service = GithubReleaseUpdateService { VALID_RELEASE.encodeToByteArray() }
        assertEquals("1.0.1", (service.check("1.0.1") as UpdateCheck.UpToDate).release.version.raw)
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

    @Test fun `download is refused when its digest does not match`() {
        val metadata = ReleaseMetadata.parse(VALID_RELEASE)
        val service = GithubReleaseUpdateService { "untrusted bytes".encodeToByteArray() }
        assertEquals(null, service.downloadVerified(metadata))
    }

    @Test fun `metadata rejects an arbitrary update host`() {
        val invalid = VALID_RELEASE.replace("https://github.com/traynor1987/Private-gallery-", "https://example.invalid")
        assertTrue(runCatching { ReleaseMetadata.parse(invalid) }.isFailure)
    }

    private companion object {
        const val VALID_RELEASE = """{"tag_name":"v1.0.1","draft":false,"prerelease":false,"assets":[{"name":"private-gallery-release.apk","browser_download_url":"https://github.com/traynor1987/Private-gallery-/releases/download/v1.0.1/private-gallery-release.apk"},{"name":"private-gallery-release.apk.sha256","browser_download_url":"https://github.com/traynor1987/Private-gallery-/releases/download/v1.0.1/private-gallery-release.apk.sha256"}]}"""
    }
}
