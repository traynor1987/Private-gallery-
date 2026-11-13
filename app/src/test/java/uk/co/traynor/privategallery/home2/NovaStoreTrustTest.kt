package uk.co.traynor.privategallery.home2

import org.junit.Assert.*
import org.junit.Test

class NovaStoreTrustTest {
    private val signer = "a".repeat(64)
    private val release = NovaStoreRelease("uk.co.nova.fixture", 20, signer, "b".repeat(64),
        "https://github.com/traynor1987/fixture/releases/download/v20/fixture.apk")

    @Test fun acceptsOnlyPinnedPackageVersionAndSingleSigner() {
        assertTrue(release.accepts("uk.co.nova.fixture", 20, listOf(signer), 19))
        assertFalse(release.accepts("uk.co.nova.other", 20, listOf(signer), 19))
        assertFalse(release.accepts("uk.co.nova.fixture", 21, listOf(signer), 19))
        assertFalse(release.accepts("uk.co.nova.fixture", 20, listOf(signer, "c".repeat(64)), 19))
        assertFalse(release.accepts("uk.co.nova.fixture", 20, listOf(signer), 20))
        assertFalse(release.accepts("uk.co.nova.fixture", 20, emptyList(), null))
    }

    @Test fun restrictsSourceAndRedirects() {
        assertTrue(trustedNovaReleaseUrl(release.url, initial = true))
        assertFalse(trustedNovaReleaseUrl(release.url.replace("traynor1987", "other-owner"), initial = true))
        assertFalse(trustedNovaReleaseUrl("https://github.com@evil.example/traynor1987/repo/releases/download/v1/a.apk", initial = false))
        assertFalse(trustedNovaReleaseUrl("https://github.com/traynor1987/../other/releases/download/v1/a.apk", initial = false))
        val asset = "https://release-assets.githubusercontent.com/github-production-release-asset/test?signature=fixture"
        assertFalse(trustedNovaReleaseUrl(asset, initial = true))
        assertTrue(trustedNovaReleaseUrl(asset, initial = false))
    }
}
