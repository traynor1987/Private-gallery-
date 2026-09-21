package uk.co.traynor.privategallery.core.browser

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserWebViewLifecyclePolicyTest {
    @Test fun `leaving Browser during VPN grace preserves an in-flight page`() {
        assertFalse(BrowserWebViewLifecyclePolicy.shouldStopLoading(BrowserWebViewLifecycleEvent.LEAVE_BROWSER))
        assertFalse(BrowserWebViewLifecyclePolicy.shouldStopLoading(BrowserWebViewLifecycleEvent.APP_BACKGROUNDED))
    }

    @Test fun `lock and an unconfirmed VPN still stop Browser networking`() {
        assertTrue(BrowserWebViewLifecyclePolicy.shouldStopLoading(BrowserWebViewLifecycleEvent.LOCKED))
        assertTrue(BrowserWebViewLifecyclePolicy.shouldStopLoading(BrowserWebViewLifecycleEvent.VPN_NOT_CONNECTED))
    }
}
