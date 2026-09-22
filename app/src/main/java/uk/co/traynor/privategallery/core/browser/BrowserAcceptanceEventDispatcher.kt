package uk.co.traynor.privategallery.core.browser

/**
 * WebView calls request interception from a background thread. Acceptance diagnostics feed
 * Compose state, so every event must cross to the main thread before it reaches the UI.
 */
internal class BrowserAcceptanceEventDispatcher(
    private val isMainThread: () -> Boolean,
    private val postToMain: ((() -> Unit) -> Unit),
) {
    fun dispatch(event: () -> Unit) {
        if (isMainThread()) event() else postToMain(event)
    }
}
