package uk.co.traynor.privategallery.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
 * Small, app-local design layer derived from James OS's dense, calm surface system.
 * It deliberately owns chrome and settings only: protected media remains the hero.
 */
object GalleryTokens {
    val PageHorizontal = 16.dp
    val PageVertical = 18.dp
    val ContentGap = 14.dp
    val SectionGap = 10.dp
    val CardPaddingHorizontal = 20.dp
    val CardPaddingVertical = 18.dp
    val RowPaddingHorizontal = 14.dp
    val RowPaddingVertical = 12.dp
    val HeroShape = RoundedCornerShape(24.dp)
    val CardShape = RoundedCornerShape(22.dp)
    val RowShape = RoundedCornerShape(16.dp)
    val MediaShape = RoundedCornerShape(16.dp)
}

@Composable
fun GalleryPageTitle(eyebrow: String, title: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(
            text = eyebrow.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(text = title, style = MaterialTheme.typography.headlineMedium)
    }
}

@Composable
fun GallerySectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        modifier = modifier,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
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
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
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
    Row(verticalAlignment = Alignment.CenterVertically) {
        Spacer(
            Modifier
                .width(4.dp)
                .height(23.dp)
                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp)),
        )
        Spacer(Modifier.width(8.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
    }
}
