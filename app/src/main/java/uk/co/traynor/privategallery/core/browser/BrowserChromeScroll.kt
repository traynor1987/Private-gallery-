package uk.co.traynor.privategallery.core.browser

/** User-driven scroll distance only: page scripts and viewport resizing cannot collapse chrome. */
class BrowserChromeScroll(private val thresholdPx: Int) {
    var visible: Boolean = true
        private set
    private var distance = 0
    fun reveal() { visible = true; distance = 0 }
    fun onScroll(delta: Int, atTop: Boolean): Boolean {
        if (atTop) { reveal(); return visible }
        if (delta == 0) return visible
        if ((distance > 0 && delta < 0) || (distance < 0 && delta > 0)) distance = 0
        distance = (distance + delta).coerceIn(-thresholdPx, thresholdPx)
        if (distance >= thresholdPx) visible = false
        if (distance <= -thresholdPx) visible = true
        return visible
    }
}
