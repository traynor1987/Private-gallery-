package uk.co.traynor.privategallery.ui

import android.graphics.Color
import android.os.Bundle
import android.os.Looper
import android.view.GestureDetector
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.WindowInsets
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import uk.co.traynor.privategallery.core.ui.NovaBlackScreenPolicy

/** Owns only a temporary native presentation in the existing Activity window. */
internal class NovaBlackScreenController(
    private val activity: ComponentActivity,
    private val onRevoked: () -> Unit = {},
) : AutoCloseable {
    private val policy = NovaBlackScreenPolicy()
    private val window get() = activity.window
    private val decor get() = window.decorView as ViewGroup
    private var current: (() -> Boolean)? = null
    private var cover: BlackCover? = null
    private var snapshot: WindowSnapshot? = null
    private var underlying = emptyList<Pair<View, Int>>()
    private var previousFocus: View? = null
    private var previousAccessibilityFocus: View? = null
    private var revoked = false
    private var closed = false
    private var resumed = activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
    private var visible by mutableStateOf(false)
    val active: Boolean get() = visible

    private data class WindowSnapshot(
        val brightness: Float,
        val statusVisible: Boolean,
        val navigationVisible: Boolean,
        val behavior: Int,
    )

    private val back = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() { wake() }
    }
    private val focusListener = ViewTreeObserver.OnWindowFocusChangeListener { focused ->
        if (!closed) {
            if (cover != null && !authorized()) {
                revoke()
            } else {
                policy.windowFocusChanged(focused, authorized(), resumed)
                reconcile()
            }
        }
    }
    private val lifecycleObserver = object : DefaultLifecycleObserver {
        override fun onResume(owner: LifecycleOwner) { resumed = true }
        override fun onPause(owner: LifecycleOwner) { resumed = false; dismiss() }
        override fun onDestroy(owner: LifecycleOwner) { close() }
    }

    init {
        checkMainThread()
        activity.lifecycle.addObserver(lifecycleObserver)
        decor.viewTreeObserver.addOnWindowFocusChangeListener(focusListener)
    }

    /** True includes an authorized pending request while its native menu window is closing. */
    fun request(current: () -> Boolean): Boolean {
        checkMainThread()
        if (closed || revoked) return false
        if (cover != null) {
            // A second action never exchanges the authority of an already visible owner.
            if (!authorized()) revoke()
            return cover != null && !revoked
        }
        this.current = current
        if (!policy.request(authorized(), resumed, decor.hasWindowFocus())) {
            this.current = null
            return false
        }
        reconcile()
        return policy.state == NovaBlackScreenPolicy.State.PENDING || cover != null
    }

    /** Event-driven owner changes cancel pending work or synchronously secure a revoked cover. */
    fun refreshAuthority() {
        checkMainThread()
        if (closed || current == null) return
        if (!authorized()) {
            if (cover != null) revoke() else dismiss()
        }
    }

    fun dismiss() {
        checkMainThread()
        val restoreFocus = !revoked && resumed && decor.hasWindowFocus() && authorized()
        policy.dismiss()
        back.isEnabled = false
        back.remove()
        restoreWindow()
        val oldCover = cover
        cover = null
        visible = false
        oldCover?.keepScreenOn = false
        // Restore only child attributes we actually changed, never Main's decor privacy gate.
        underlying.forEach { (view, importance) -> view.importantForAccessibility = importance }
        underlying = emptyList()
        val oldFocusability = decor.descendantFocusability
        if (!restoreFocus) decor.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
        try { (oldCover?.parent as? ViewGroup)?.removeView(oldCover) }
        finally { decor.descendantFocusability = oldFocusability }
        if (restoreFocus) {
            previousFocus?.takeIf { it.isAttachedToWindow && it.visibility == View.VISIBLE }?.requestFocus()
            previousAccessibilityFocus?.takeIf { it.isAttachedToWindow && it.visibility == View.VISIBLE }
                ?.performAccessibilityAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS, null)
        }
        previousFocus = null
        previousAccessibilityFocus = null
        current = null
        revoked = false
    }

    override fun close() {
        checkMainThread()
        if (closed) return
        dismiss()
        closed = true
        policy.close()
        back.remove()
        activity.lifecycle.removeObserver(lifecycleObserver)
        decor.viewTreeObserver.takeIf { it.isAlive }?.removeOnWindowFocusChangeListener(focusListener)
    }

    private fun authorized(): Boolean = try { current?.invoke() == true } catch (_: Exception) { false }
    private fun checkMainThread() = check(Looper.myLooper() == Looper.getMainLooper()) { "Black Screen is Activity-window scoped" }

    private fun reconcile() {
        if (policy.active && cover == null) activate()
        else if (!policy.active && cover != null && !revoked) dismiss()
        else if (policy.state == NovaBlackScreenPolicy.State.IDLE) current = null
    }

    private fun activate() {
        // Focus and authority are sampled again at the actual window mutation boundary.
        if (!resumed || !decor.hasWindowFocus() || !authorized()) { dismiss(); return }
        val insets = decor.rootWindowInsets ?: run { dismiss(); return }
        val bars = WindowCompat.getInsetsController(window, decor)
        snapshot = WindowSnapshot(window.attributes.screenBrightness,
            insets.isVisible(WindowInsets.Type.statusBars()), insets.isVisible(WindowInsets.Type.navigationBars()),
            bars.systemBarsBehavior)
        previousFocus = activity.currentFocus
        previousAccessibilityFocus = accessibilityFocus(decor)
        underlying = (0 until decor.childCount).map { decor.getChildAt(it) to decor.getChildAt(it).importantForAccessibility }
        underlying.forEach { (view, _) -> view.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS }
        previousFocus?.clearFocus()
        val overlay = BlackCover()
        cover = overlay
        visible = true
        decor.addView(overlay, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        overlay.bringToFront()
        overlay.requestFocus()
        overlay.keepScreenOn = true
        window.attributes = window.attributes.apply { screenBrightness = 0f }
        bars.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        bars.hide(WindowInsetsCompat.Type.ime())
        bars.hide(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())
        back.isEnabled = true
        activity.onBackPressedDispatcher.addCallback(activity, back)
    }

    private fun restoreWindow() {
        cover?.keepScreenOn = false
        val saved = snapshot ?: return
        snapshot = null
        window.attributes = window.attributes.apply { screenBrightness = saved.brightness }
        val bars = WindowCompat.getInsetsController(window, decor)
        bars.systemBarsBehavior = saved.behavior
        if (saved.statusVisible) bars.show(WindowInsetsCompat.Type.statusBars()) else bars.hide(WindowInsetsCompat.Type.statusBars())
        if (saved.navigationVisible) bars.show(WindowInsetsCompat.Type.navigationBars()) else bars.hide(WindowInsetsCompat.Type.navigationBars())
    }

    private fun inputAllowed(source: BlackCover? = null): Boolean {
        // A detached view never inherits the authority of a later explicit activation.
        if (source != null && cover !== source) return false
        if (cover == null) return false
        if (revoked) return false
        if (!authorized()) { revoke(); return false }
        if (!resumed || !decor.hasWindowFocus()) { dismiss(); return false }
        return policy.canInteract(true)
    }

    /** Revocation cannot uncover a stale page before Main synchronously installs its privacy gate. */
    private fun revoke() {
        if (revoked || cover == null) return
        revoked = true
        policy.canInteract(false)
        restoreWindow()
        // Set the held state first: the production callback may dismiss reentrantly.
        onRevoked()
    }

    private fun wake(source: BlackCover? = null) {
        if (inputAllowed(source)) dismiss()
    }

    private fun accessibilityFocus(view: View): View? {
        if (view.isAccessibilityFocused) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) {
            accessibilityFocus(view.getChildAt(i))?.let { return it }
        }
        return null
    }

    private inner class BlackCover : View(activity) {
        private val gestures = GestureDetector(activity, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(event: MotionEvent) = true
            override fun onDoubleTap(event: MotionEvent) = true
            override fun onDoubleTapEvent(event: MotionEvent): Boolean {
                if (event.actionMasked == MotionEvent.ACTION_UP) wake(this@BlackCover)
                return true
            }
        })
        init {
            tag = "nova-black-screen"
            setBackgroundColor(Color.BLACK)
            alpha = 1f
            isClickable = true
            isFocusable = true
            isFocusableInTouchMode = true
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
            contentDescription = "Wake NovaOS"
        }
        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (inputAllowed(this)) gestures.onTouchEvent(event)
            return true
        }
        override fun onGenericMotionEvent(event: MotionEvent): Boolean {
            inputAllowed(this)
            return true
        }
        override fun onHoverEvent(event: MotionEvent): Boolean {
            // Let the native view announce hover exploration, while keeping it above the page.
            if (inputAllowed(this)) super.onHoverEvent(event)
            return true
        }
        override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
            if (!inputAllowed(this)) return true
            return if (systemControlKey(keyCode)) super.onKeyDown(keyCode, event) else true
        }
        override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
            if (!inputAllowed(this)) return true
            if (keyCode == KeyEvent.KEYCODE_ESCAPE || keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_BACK) wake(this)
            return if (systemControlKey(keyCode)) super.onKeyUp(keyCode, event) else true
        }
        private fun systemControlKey(keyCode: Int) = keyCode == KeyEvent.KEYCODE_VOLUME_UP ||
            keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_MUTE ||
            keyCode == KeyEvent.KEYCODE_HEADSETHOOK || keyCode in KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE..KeyEvent.KEYCODE_MEDIA_FAST_FORWARD ||
            keyCode == KeyEvent.KEYCODE_MEDIA_PLAY || keyCode == KeyEvent.KEYCODE_MEDIA_PAUSE || keyCode == KeyEvent.KEYCODE_MEDIA_STOP

        override fun performClick(): Boolean {
            if (inputAllowed(this)) { super.performClick(); wake(this) }
            return true
        }
        override fun performAccessibilityAction(action: Int, arguments: Bundle?): Boolean {
            if (action == AccessibilityNodeInfo.ACTION_CLICK) { wake(this); return true }
            if (!inputAllowed(this)) return false
            return super.performAccessibilityAction(action, arguments)
        }
        override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
            super.onInitializeAccessibilityNodeInfo(info)
            info.className = "android.widget.Button"
            info.addAction(AccessibilityNodeInfo.AccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK, "Wake NovaOS"))
        }
    }
}
