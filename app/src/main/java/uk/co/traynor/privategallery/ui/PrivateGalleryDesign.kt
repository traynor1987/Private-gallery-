package uk.co.traynor.privategallery.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Shared neutral surfaces and compact spacing for Private Gallery.
 * It deliberately owns chrome and settings only: protected media remains the hero.
 */
object GalleryTokens {
    val SpaceSmall = 4.dp
    val SpaceMedium = 8.dp
    val ControlSize = 48.dp
    val IconSize = 24.dp
    val DialogMaxWidth = 480.dp
    val PageHorizontal = 16.dp
    val PageVertical = 12.dp
    val ContentGap = 12.dp
    val SectionGap = 8.dp
    val CardPaddingHorizontal = 14.dp
    val CardPaddingVertical = 12.dp
    val RowPaddingHorizontal = 14.dp
    val RowPaddingVertical = 10.dp
    val HeroShape = RoundedCornerShape(20.dp)
    val CardShape = RoundedCornerShape(16.dp)
    val RowShape = RoundedCornerShape(12.dp)
    val MediaShape = RoundedCornerShape(16.dp)
    // Shared by device and protected media; Adaptive uses the actual pane width.
    val ThumbnailMinSize = 87.dp
    val MediaGap = 3.dp
    val ThumbnailShape = RoundedCornerShape(8.dp)
}

@Composable
fun GalleryPageTitle(eyebrow: String, title: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(
            text = eyebrow.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = title, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
    }
}

@Composable
fun GallerySectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        modifier = modifier,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
fun GalleryCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier,
        shape = GalleryTokens.CardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = GalleryTokens.CardPaddingHorizontal,
                vertical = GalleryTokens.CardPaddingVertical,
            ),
            verticalArrangement = Arrangement.spacedBy(GalleryTokens.SectionGap),
        ) { content() }
    }
}

@Composable
fun GalleryCardHeading(title: String) {
    Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
}

/** Compact shared feedback; contains only the caller's current authenticated message. */
@Composable
fun GalleryFeedback(message: String, modifier: Modifier = Modifier, error: Boolean = false) {
    Surface(modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        shape = GalleryTokens.RowShape,
        color = if (error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (error) Icons.Default.ErrorOutline else Icons.Default.Info, null, Modifier.size(20.dp))
            Text(message, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun GalleryEmptyState(title: String, description: String, icon: ImageVector,
    modifier: Modifier = Modifier, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
    GalleryCard(modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                GalleryCardHeading(title)
                Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (actionLabel != null && onAction != null) FilledTonalButton(onClick = onAction) { Text(actionLabel) }
    }
}

@Composable
fun GallerySettingsRow(title: String, summary: String, icon: ImageVector,
    modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = modifier, shape = GalleryTokens.CardShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(shape = GalleryTokens.RowShape, color = MaterialTheme.colorScheme.secondaryContainer) {
                Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(22.dp)) }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Default.ChevronRight, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
