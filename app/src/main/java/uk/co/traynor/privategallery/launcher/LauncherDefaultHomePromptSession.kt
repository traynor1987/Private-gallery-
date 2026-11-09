package uk.co.traynor.privategallery.launcher

internal class LauncherDefaultHomePromptSession(offered: Boolean = false) {
    var offered = offered
        private set

    fun claim(isDefault: Boolean?, ask: Boolean, safeHome: Boolean): Boolean {
        if (offered || isDefault != false || !ask || !safeHome) return false
        offered = true
        return true
    }
}
