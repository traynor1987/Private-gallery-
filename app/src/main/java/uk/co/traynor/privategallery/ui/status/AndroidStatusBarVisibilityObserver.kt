package uk.co.traynor.privategallery.ui.status

import android.view.View
import android.view.ViewTreeObserver
import android.view.WindowInsets

/**
 * Observes original window insets before drawing, independently of child inset consumption.
 * This is an additive draw/attach listener: it never installs an on-apply-insets listener,
 * requests a frame, or schedules a timer. The owner starts it only for a resumed visible surface.
 */
internal class AndroidStatusBarVisibilityObserver(
    private val view: View,
    private val changed: (Boolean) -> Unit
) : View.OnAttachStateChangeListener, ViewTreeObserver.OnPreDrawListener, AutoCloseable {
    private var tree: ViewTreeObserver? = null
    private var lastVisible: Boolean? = null
    private var closed = false

    fun start() {
        view.addOnAttachStateChangeListener(this)
        if (view.isAttachedToWindow) onViewAttachedToWindow(view) else publish(true)
    }

    override fun onViewAttachedToWindow(attached: View) {
        if (closed) return
        removeDrawListener()
        tree = view.rootView.viewTreeObserver.also { it.addOnPreDrawListener(this) }
        readVisibility()
    }

    override fun onViewDetachedFromWindow(detached: View) {
        removeDrawListener()
        publish(true) // Missing original insets must never admit duplicate custom status chrome.
    }

    override fun onPreDraw(): Boolean {
        if (closed) return true
        // Commit the changed composition before drawing content with the new system-bar state.
        // A state change schedules that composition; this listener never drives a redraw loop.
        return !readVisibility()
    }

    private fun readVisibility(): Boolean = publish(
        view.rootView.rootWindowInsets?.isVisible(WindowInsets.Type.statusBars()) != false
    )

    private fun publish(visible: Boolean): Boolean {
        if (closed || lastVisible == visible) return false
        lastVisible = visible
        changed(visible)
        return true
    }

    private fun removeDrawListener() {
        val observer = tree
        tree = null
        if (observer?.isAlive == true) observer.removeOnPreDrawListener(this)
        else view.viewTreeObserver.takeIf { it.isAlive }?.removeOnPreDrawListener(this)
    }

    override fun close() {
        if (closed) return
        closed = true
        view.removeOnAttachStateChangeListener(this)
        removeDrawListener()
    }
}
