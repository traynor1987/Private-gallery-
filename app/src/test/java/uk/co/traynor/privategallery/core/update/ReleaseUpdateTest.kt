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
}
