package uk.co.traynor.privategallery.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.layout
import kotlin.math.roundToInt

/** Both bars share one animation clock; retain full-size controls while sliding them out. */
@Composable
internal fun BrowserChromeBar(fraction: Float, top: Boolean, content: @Composable () -> Unit) {
    if (fraction <= 0f) return
    Box(Modifier.fillMaxWidth().clipToBounds().layout { measurable, constraints ->
        val child = measurable.measure(constraints.copy(minHeight = 0))
        val visibleHeight = (child.height * fraction).roundToInt()
        layout(child.width, visibleHeight) {
            child.placeRelative(0, if (top) visibleHeight - child.height else 0)
        }
    }) { content() }
}
