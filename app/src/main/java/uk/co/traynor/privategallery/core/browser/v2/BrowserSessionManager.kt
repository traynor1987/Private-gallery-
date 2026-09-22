package uk.co.traynor.privategallery.core.browser.v2

import java.util.UUID

/** Session-owned tab metadata. The opaque handle is never persisted and is not a WebView address. */
data class BrowserTab(
    val id: String = UUID.randomUUID().toString(),
    val url: String = "",
    val title: String = "New tab",
    val loading: Boolean = false,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val desktopSite: Boolean = false,
    val webViewHandle: String? = null,
    val failure: BrowserTabFailure? = null,
)

enum class BrowserTabFailure { RENDERER_GONE, WEBVIEW_UNAVAILABLE }

/**
 * The only owner of tab lifetime. UI code mutates presentation state through this manager but
 * cannot replace a tab's WebView handle as a side effect of ordinary state changes.
 */
class BrowserSessionManager(private val maximumTabs: Int = 8) {
    init { require(maximumTabs >= 1) }

    private val tabMap = linkedMapOf<String, BrowserTab>()
    private var selectedId: String

    init {
        val initial = BrowserTab()
        tabMap[initial.id] = initial
        selectedId = initial.id
    }

    val tabs: List<BrowserTab> get() = tabMap.values.toList()
    val activeTab: BrowserTab get() = tabMap.getValue(selectedId)

    /** Restores encrypted session metadata, never a stale renderer handle or page runtime. */
    fun restore(savedTabs: List<BrowserTab>, selectedTabId: String?) {
        tabMap.clear()
        savedTabs.asSequence().take(maximumTabs).forEach { tab ->
            tabMap[tab.id] = tab.copy(webViewHandle = null, loading = false, failure = null)
        }
        if (tabMap.isEmpty()) {
            val replacement = BrowserTab()
            tabMap[replacement.id] = replacement
            selectedId = replacement.id
        } else selectedId = selectedTabId?.takeIf { it in tabMap } ?: tabMap.keys.first()
    }

    fun newTab(url: String = ""): BrowserTab {
        while (tabMap.size >= maximumTabs) {
            val evicted = tabMap.keys.firstOrNull { it != selectedId } ?: break
            tabMap.remove(evicted)
        }
        val tab = BrowserTab(url = url)
        tabMap[tab.id] = tab
        selectedId = tab.id
        return tab
    }

    fun select(tabId: String) { require(tabId in tabMap) { "Unknown Browser tab" }; selectedId = tabId }

    fun close(tabId: String) {
        require(tabId in tabMap) { "Unknown Browser tab" }
        tabMap.remove(tabId)
        if (tabMap.isEmpty()) {
            val replacement = BrowserTab()
            tabMap[replacement.id] = replacement
            selectedId = replacement.id
        } else if (selectedId == tabId) selectedId = tabMap.keys.last()
    }

    fun attachWebView(tabId: String, handle: String) = mutate(tabId) { it.copy(webViewHandle = handle, failure = null) }
    fun detachWebView(tabId: String) = mutate(tabId) { it.copy(webViewHandle = null) }
    fun updateNavigation(
        tabId: String,
        url: String,
        title: String,
        loading: Boolean,
        canGoBack: Boolean = false,
        canGoForward: Boolean = false,
    ) = mutate(tabId) {
        it.copy(
            url = url,
            title = title.ifBlank { it.title },
            loading = loading,
            canGoBack = canGoBack,
            canGoForward = canGoForward,
            failure = null,
        )
    }
    fun setDesktopSite(tabId: String, enabled: Boolean) = mutate(tabId) { it.copy(desktopSite = enabled) }
    fun rendererGone(tabId: String) = mutate(tabId) { it.copy(webViewHandle = null, loading = false, failure = BrowserTabFailure.RENDERER_GONE) }
    /** A provider failure is recoverable: preserve tab metadata and leave UI chrome available. */
    fun webViewUnavailable(tabId: String) = mutate(tabId) { it.copy(webViewHandle = null, loading = false, failure = BrowserTabFailure.WEBVIEW_UNAVAILABLE) }
    fun retryWebView(tabId: String) = mutate(tabId) { it.copy(failure = null) }

    private fun mutate(tabId: String, transform: (BrowserTab) -> BrowserTab) {
        tabMap[tabId] = transform(tabMap[tabId] ?: error("Unknown Browser tab"))
    }
}
