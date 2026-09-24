package uk.co.traynor.privategallery.ui

import android.content.pm.ActivityInfo
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

/** The supplied WebView custom view stays owned by Chromium, attached once to the Activity window. */
@Composable
internal fun BrowserVideoFullscreen(view: View, onExit: () -> Unit) {
    val activity = LocalContext.current.playerActivity()
    val exit by rememberUpdatedState(onExit)
    BrowserVideoWindow()
    DisposableEffect(view, activity) {
        val decor = activity?.window?.decorView as? ViewGroup
        val host = activity?.let { FrameLayout(it).apply {
            setBackgroundColor(android.graphics.Color.BLACK)
            keepScreenOn = true
            addView(view, FrameLayout.LayoutParams(-1, -1))
            addView(ImageButton(it).apply {
                setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
                contentDescription = "Exit video fullscreen"
                setBackgroundColor(0x99202630.toInt())
                setOnClickListener { exit() }
            }, FrameLayout.LayoutParams((48 * resources.displayMetrics.density).toInt(), (48 * resources.displayMetrics.density).toInt(), android.view.Gravity.TOP or android.view.Gravity.END))
        } }
        if (host != null) decor?.addView(host, ViewGroup.LayoutParams(-1, -1))
        onDispose {
            host?.keepScreenOn = false
            host?.removeView(view)
            if (host != null && host.parent === decor) decor?.removeView(host)
        }
    }
}

@Composable
internal fun BrowserVideoWindow() {
    val activity = LocalContext.current.playerActivity()
    DisposableEffect(activity) {
        val window = activity?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
        val hadKeepAwake = window?.attributes?.flags?.and(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) != 0
        window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val hadFullscreen = window?.attributes?.flags?.and(android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN) != 0
        window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN)
        controller?.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            if (!hadKeepAwake) window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            if (!hadFullscreen) window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN)
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
    }
}
