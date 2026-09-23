package uk.co.traynor.privategallery.core.browser.v2

import android.view.View
import android.view.ViewGroup
import android.view.ViewParent
import android.webkit.WebView
import androidx.core.view.OneShotPreDrawListener
import java.lang.ref.WeakReference

/** Acceptance-only observer. Never mutates layout, loads documents, or reads page/private text. */
internal class BrowserWebViewPresentationProbe(
    private val view: WebView,
    private val record: (String, Map<String, String>) -> Unit,
) {
    private var previousParent: WeakReference<ViewParent>? = null
    private var attachment = 0
    private var preDraw: OneShotPreDrawListener? = null
    private var disposed = false
    private val attachListener = object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(v: View) {
            attachment++
            val oldParent = previousParent?.get()
            if (oldParent != null && oldParent !== view.parent) record("WEBVIEW_REPARENTED", flags("ATTACHED"))
            previousParent = view.parent?.let { WeakReference(it) }
            record("WEBVIEW_ATTACHED", flags("ATTACHED"))
            recordState("ATTACHED")
            preDraw?.removeListener()
            preDraw = OneShotPreDrawListener.add(view) {
                preDraw = null
                recordState("FIRST_PRE_DRAW")
            }
        }

        override fun onViewDetachedFromWindow(v: View) {
            record("WEBVIEW_DETACHED", flags("DETACHED"))
            preDraw?.removeListener()
            preDraw = null
        }
    }
    private val layoutListener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
        recordState("LAYOUT")
    }

    init {
        view.addOnAttachStateChangeListener(attachListener)
        view.addOnLayoutChangeListener(layoutListener)
        record("WEBVIEW_INITIAL_STATE", flags("CREATED") + mapOf(
            "has_document_url" to (!view.url.isNullOrEmpty()).toString(),
            "content_height" to view.contentHeight.toString(),
        ))
        recordState("CREATED")
    }

    fun recordState(phase: String) {
        if (disposed) return
        val parent = view.parent as? View
        val group = parent as? ViewGroup
        val common = flags(phase)
        val location = IntArray(2)
        view.getLocationInWindow(location)
        record("WEBVIEW_PARENT_STATE", common + mapOf(
            "parent_kind" to when { parent == null -> "none"; group != null -> "view_group"; else -> "view" },
            "parent_width" to (parent?.width ?: 0).toString(),
            "parent_height" to (parent?.height ?: 0).toString(),
            "parent_clip_children" to (group?.clipChildren ?: false).toString(),
            "parent_clip_padding" to (group?.clipToPadding ?: false).toString(),
            "layout_params_width" to (view.layoutParams?.width ?: 0).toString(),
            "layout_params_height" to (view.layoutParams?.height ?: 0).toString(),
            "has_layout_params" to (view.layoutParams != null).toString(),
        ))
        record("WEBVIEW_BOUNDS", common + mapOf(
            "x_window" to location[0].toString(), "y_window" to location[1].toString(),
            "left" to view.left.toString(), "top" to view.top.toString(),
            "width" to view.width.toString(), "height" to view.height.toString(),
            "measured_width" to view.measuredWidth.toString(), "measured_height" to view.measuredHeight.toString(),
            "layout_requested" to view.isLayoutRequested.toString(),
        ))
        record("WEBVIEW_VISIBILITY", common + mapOf(
            "visibility" to view.visibility.toString(), "window_visibility" to view.windowVisibility.toString(),
            "shown" to view.isShown.toString(), "alpha" to view.alpha.toString(),
            "elevation" to view.elevation.toString(), "translation_z" to view.translationZ.toString(),
            "translation_x" to view.translationX.toString(), "translation_y" to view.translationY.toString(),
            "has_view_background" to (view.background != null).toString(),
            "layer_type" to view.layerType.toString(), "hardware_accelerated" to view.isHardwareAccelerated.toString(),
        ))
        record("WEBVIEW_DOCUMENT_STATE", common + mapOf(
            "has_document_url" to (!view.url.isNullOrEmpty()).toString(),
            "content_height" to view.contentHeight.toString(),
        ))
    }

    fun dispose() {
        disposed = true
        preDraw?.removeListener()
        preDraw = null
        view.removeOnAttachStateChangeListener(attachListener)
        view.removeOnLayoutChangeListener(layoutListener)
        previousParent = null
    }

    private fun flags(phase: String) = mapOf(
        "phase" to phase, "attachment" to attachment.toString(),
        "has_parent" to (view.parent != null).toString(),
        "window_attached" to view.isAttachedToWindow.toString(),
    )
}
