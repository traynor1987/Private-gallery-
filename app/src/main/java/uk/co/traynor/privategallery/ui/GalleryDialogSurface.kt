package uk.co.traynor.privategallery.ui

import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Shared surface for bounded application-owned dialogs; platform permission UI is separate. */
@Composable
internal fun GalleryDialogSurface(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.widthIn(max = 600.dp).then(modifier),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        tonalElevation = 6.dp,
        content = content,
    )
}
