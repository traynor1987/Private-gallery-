package uk.co.traynor.privategallery.core.security

/** Chooser callback, origin and selected handles are immutable even within one unlock epoch. */
class BrowserUploadRequest<T : Any>(val callback: T, val origin: String, handles: List<ScopedItemHandle>) {
    val handles = handles.toList()
    init {
        check(this.handles.isNotEmpty() && this.handles.all { it.containerId === ContainerId.PRIMARY })
        check(this.handles.map { it.epoch }.distinct().size == 1) { "Mixed upload sessions" }
    }
    fun matches(callback: T?, origin: String): Boolean = this.callback === callback && this.origin == origin
}
