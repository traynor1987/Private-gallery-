package uk.co.traynor.privategallery.core.browser.v2

import android.webkit.PermissionRequest

/** A request can complete once, and only camera/microphone resources approved by our UI. */
internal class BrowserPermissionRequest(
    private val request: PermissionRequest,
    private val result: (String, Int) -> Unit,
) : PermissionRequest() {
    private var complete = false
    override fun getOrigin() = request.origin
    override fun getResources(): Array<String> = request.resources
    override fun grant(resources: Array<out String>) {
        if (complete) return
        complete = true
        val approved = resources.filter { it in request.resources && it in setOf(RESOURCE_AUDIO_CAPTURE, RESOURCE_VIDEO_CAPTURE) }.distinct().toTypedArray()
        if (approved.isEmpty()) { request.deny(); result("denied", 0) }
        else { request.grant(approved); result("granted", approved.size) }
    }
    override fun deny() {
        if (complete) return
        complete = true
        request.deny()
        result("denied", 0)
    }
    fun canceled() { if (!complete) { complete = true; result("canceled", 0) } }
}
