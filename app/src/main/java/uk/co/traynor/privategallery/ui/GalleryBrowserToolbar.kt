package uk.co.traynor.privategallery.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Common Primary, Hidden Tor and isolated VPN chrome; controls wrap on narrow Fold windows. */
@Composable
internal fun GalleryBrowserToolbar(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 2.dp) {
        FlowRow(Modifier.fillMaxWidth().heightIn(min = 56.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            content()
        }
    }
}
