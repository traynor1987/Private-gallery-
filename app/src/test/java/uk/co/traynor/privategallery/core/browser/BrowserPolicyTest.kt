package uk.co.traynor.privategallery.core.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserPolicyTest {
    @Test fun `VPN-required Browser requests fail closed before connection confirmation`() {
        assertFalse(BrowserNetworkGatePolicy.mayStartNetworkRequest(requireVpn = true, vpnConnected = false))
        assertTrue(BrowserNetworkGatePolicy.mayStartNetworkRequest(requireVpn = true, vpnConnected = true))
    }
    @Test fun `host-like address becomes https url`() {
        assertEquals("https://example.com", BrowserAddressPolicy.destinationFor("example.com", BrowserSearchEngine.GOOGLE).url)
    }

    @Test fun `https url is retained`() {
        assertEquals("https://example.com/a?q=b", BrowserAddressPolicy.destinationFor("https://example.com/a?q=b", BrowserSearchEngine.GOOGLE).url)
    }

    @Test fun `ordinary words become a search query`() {
        assertEquals(
            "https://www.google.com/search?q=jenna+ortega",
            BrowserAddressPolicy.destinationFor("jenna ortega", BrowserSearchEngine.GOOGLE).url,
        )
    }

    @Test fun `only http and https navigation is accepted`() {
        assertTrue(BrowserNavigationPolicy.isWebUrl("https://example.com"))
        assertTrue(BrowserNavigationPolicy.isWebUrl("http://example.com"))
        assertFalse(BrowserNavigationPolicy.isWebUrl("file:///data/data/uk.co.traynor.privategallery/files/vault"))
        assertFalse(BrowserNavigationPolicy.isWebUrl("intent://anything"))
        assertFalse(BrowserNavigationPolicy.isWebUrl("mailto:test@example.com"))
    }

    @Test fun `back exits fullscreen before browser history`() {
        assertEquals(BrowserBackAction.EXIT_FULLSCREEN, BrowserNavigationPolicy.backAction(fullscreen = true, canGoBack = true))
        assertEquals(BrowserBackAction.GO_BACK, BrowserNavigationPolicy.backAction(fullscreen = false, canGoBack = true))
        assertEquals(BrowserBackAction.FALL_THROUGH, BrowserNavigationPolicy.backAction(fullscreen = false, canGoBack = false))
    }

    @Test fun `download interception is intentionally not an ordinary public download`() {
        assertEquals(BrowserDownloadAction.REQUEST_VAULT_SAVE, BrowserNavigationPolicy.downloadAction())
    }

    @Test fun `download names cannot escape Vault metadata`() {
        assertEquals("holiday.png", BrowserDownloadPolicy.safeDisplayName("../../holiday.png"))
        assertEquals("download", BrowserDownloadPolicy.safeDisplayName("   "))
    }

    @Test fun `only successful web responses are eligible for Vault download`() {
        assertTrue(BrowserDownloadPolicy.acceptsResponse("https://example.com/file", 200))
        assertFalse(BrowserDownloadPolicy.acceptsResponse("https://example.com/file", 404))
        assertFalse(BrowserDownloadPolicy.acceptsResponse("file:///data/file", 200))
    }

    @Test fun `lock clean up follows explicit preference`() {
        assertTrue(BrowserNavigationPolicy.clearDataOnLock(true))
        assertFalse(BrowserNavigationPolicy.clearDataOnLock(false))
    }

    @Test fun `tls errors are never bypassed`() {
        assertEquals(BrowserTlsAction.CANCEL, BrowserNavigationPolicy.tlsErrorAction())
    }

    @Test fun `browser preserves known-good single-window policy when no child is requested`() {
        assertFalse(BrowserWebSecurityPolicy.javaScriptBridgeEnabled)
        assertFalse(BrowserWebSecurityPolicy.fileAccessEnabled)
        assertFalse(BrowserWebSecurityPolicy.contentAccessEnabled)
        assertFalse(BrowserWebSecurityPolicy.multipleWindowsEnabled)
        assertFalse(BrowserWebSecurityPolicy.automaticWindowOpeningEnabled)
    }

    @Test fun `Vault acquisition feedback never becomes a page load error`() {
        val state = BrowserPresentationState(pageError = null)

        val afterSuccess = state.withAcquisitionFeedback("Saved to Vault.")
        val afterFailure = state.withAcquisitionFeedback("Unable to save to Vault.")

        assertFalse(afterSuccess.replacesWebPage)
        assertFalse(afterFailure.replacesWebPage)
        assertEquals("Saved to Vault.", afterSuccess.feedback)
        assertEquals("Unable to save to Vault.", afterFailure.feedback)
    }

    @Test fun `safe HTTPS popup is loaded in the current browser and unsafe popup is cancelled`() {
        assertEquals(BrowserPopupAction.LOAD_IN_CURRENT_VIEW, BrowserPopupPolicy.actionFor("https://example.com/dialog"))
        assertEquals(BrowserPopupAction.CANCEL, BrowserPopupPolicy.actionFor("intent://payment"))
        assertEquals(BrowserPopupAction.CANCEL, BrowserPopupPolicy.actionFor("file:///data/data/private"))
    }

    @Test fun `ordinary image and image link save their authorised web resource`() {
        assertEquals(BrowserImageAcquisitionAction.SAVE_RESOURCE, BrowserImagePolicy.actionFor(BrowserImageHitType.IMAGE, "https://example.com/image.jpg"))
        assertEquals(BrowserImageAcquisitionAction.SAVE_RESOURCE, BrowserImagePolicy.actionFor(BrowserImageHitType.IMAGE_LINK, "https://example.com/image.jpg"))
    }

    @Test fun `non-resource image explicitly falls back to screenshot and text exposes no acquisition`() {
        assertEquals(BrowserImageAcquisitionAction.SCREENSHOT_FALLBACK, BrowserImagePolicy.actionFor(BrowserImageHitType.IMAGE, "blob:https://example.com/a"))
        assertEquals(null, BrowserImagePolicy.actionFor(BrowserImageHitType.TEXT, null))
    }

    @Test fun `remote image acquisition remains VPN gated and never hunts alternate URLs`() {
        assertFalse(BrowserNetworkGatePolicy.mayStartNetworkRequest(true, false))
        assertEquals("https://example.com/preview.jpg", BrowserImagePolicy.authorisedResource("https://example.com/preview.jpg"))
        assertEquals(null, BrowserImagePolicy.authorisedResource("javascript:alert(1)"))
    }

    @Test fun `responsive browser uses device viewport without overview zoom`() {
        assertTrue(BrowserViewportPolicy.useWideViewport)
        assertFalse(BrowserViewportPolicy.loadWithOverview)
        assertEquals(100, BrowserViewportPolicy.textZoomPercent)
        assertEquals(0, BrowserViewportPolicy.initialScale)
        assertFalse(BrowserViewportPolicy.pageUsesChromeHorizontalMargins)
    }

    @Test fun `compact toolbar swaps reload for stop while loading`() {
        assertEquals(BrowserToolbarAction.RELOAD, BrowserToolbarPolicy.primaryAction(isLoading = false))
        assertEquals(BrowserToolbarAction.STOP, BrowserToolbarPolicy.primaryAction(isLoading = true))
    }

    @Test fun `address field retains Material text and touch height`() {
        assertEquals(56, BrowserToolbarPolicy.addressFieldHeightDp)
    }

    @Test fun `browser chrome is present before WebView is created`() {
        val state = BrowserScreenState.initial()

        assertTrue(state.showChrome)
        assertTrue(state.showStartSurface)
        assertFalse(state.showError)
    }

    @Test fun `webview initialization failure retains chrome and shows an error`() {
        val state = BrowserScreenState.initializationFailed()

        assertTrue(state.showChrome)
        assertFalse(state.showStartSurface)
        assertTrue(state.showError)
    }
}
