package uk.co.traynor.privategallery.ui

import android.app.Activity
import android.graphics.HardwareRenderer
import android.os.Build
import android.view.WindowManager
import android.view.inspector.WindowInspector
import androidx.test.platform.app.InstrumentationRegistry

/** Record capture preconditions without changing production capture/security policy. */
internal data class ModalCaptureState1762(val description: String, val secure: Boolean)

internal fun modalCaptureState1762(activity: Activity): ModalCaptureState1762 {
    var state: ModalCaptureState1762? = null
    InstrumentationRegistry.getInstrumentation().runOnMainSync {
        val drawing = if (Build.VERSION.SDK_INT >= 33) HardwareRenderer.isDrawingEnabled().toString() else "unavailable-before-33"
        val roots = if (Build.VERSION.SDK_INT >= 29) WindowInspector.getGlobalWindowViews().filter {
            it.isAttachedToWindow && (it === activity.window.decorView || it.context.playerActivity() === activity)
        } else listOf(activity.window.decorView)
        var secure = activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0
        val windows = roots.joinToString("\n") { root ->
            val params = root.layoutParams as? WindowManager.LayoutParams
            val flags = params?.flags ?: 0
            secure = secure || flags and WindowManager.LayoutParams.FLAG_SECURE != 0
            val location = IntArray(2)
            root.getLocationOnScreen(location)
            "${root.javaClass.name} focused=${root.hasWindowFocus()} visible=${root.windowVisibility} flags=0x${Integer.toHexString(flags)} type=${params?.type} screen=(${location[0]},${location[1]}) size=${root.width}x${root.height}"
        }
        state = ModalCaptureState1762("hardwareDrawingEnabled=$drawing activityFlags=0x${Integer.toHexString(activity.window.attributes.flags)}\n$windows\n", secure)
    }
    return checkNotNull(state)
}
