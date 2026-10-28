package uk.co.traynor.privategallery.core.browser.v2

/** Ephemeral website request. Never persisted, logged or shared with Hidden. */
data class BrowserScriptDialog(
    val tabId: String,
    val origin: String,
    val message: String,
    val kind: Kind,
    val initialValue: String,
    internal val reply: (String?) -> Unit,
) {
    enum class Kind { ALERT, CONFIRM, PROMPT, LEAVE }
}
