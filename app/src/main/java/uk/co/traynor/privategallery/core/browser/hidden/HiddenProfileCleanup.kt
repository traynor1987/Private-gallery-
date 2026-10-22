package uk.co.traynor.privategallery.core.browser.hidden

import androidx.webkit.Profile
import androidx.webkit.WebStorageCompat
import androidx.webkit.WebViewFeature

/** Profile containers cannot be deleted in their loading process; their data can be cleared. */
internal object HiddenProfileCleanup {
    fun clear(profile: Profile, completed: () -> Unit = {}) {
        check(WebViewFeature.isFeatureSupported(WebViewFeature.DELETE_BROWSING_DATA))
        WebStorageCompat.deleteBrowsingData(profile.webStorage) { completed() }
    }
}
