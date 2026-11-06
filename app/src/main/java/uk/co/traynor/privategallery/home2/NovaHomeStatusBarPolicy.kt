package uk.co.traynor.privategallery.home2

import android.view.WindowInsets
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import uk.co.traynor.privategallery.ui.playerActivity

/** The admitted Home shell owns its status-bar request, and gives the window back on exit.
 * Android's transient bars remain available; NovaSystemStatus independently observes their
 * actual visibility so showing them immediately suppresses the custom indicator.
 */
@Composable
internal fun NovaHomeStatusBarPolicy(enabled: Boolean) {
    val window = LocalContext.current.playerActivity()?.window
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var resumed by remember(lifecycle) {
        mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ ->
            resumed = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    DisposableEffect(window, enabled, resumed) {
        if (window != null && enabled && resumed) {
            val decor = window.decorView
            val controller = WindowCompat.getInsetsController(window, decor)
            val previousVisible = decor.rootWindowInsets?.isVisible(WindowInsets.Type.statusBars()) != false
            val previousBehavior = controller.systemBarsBehavior
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.statusBars())
            onDispose {
                controller.systemBarsBehavior = previousBehavior
                if (previousVisible) controller.show(WindowInsetsCompat.Type.statusBars())
                else controller.hide(WindowInsetsCompat.Type.statusBars())
            }
        } else onDispose { }
    }
}
