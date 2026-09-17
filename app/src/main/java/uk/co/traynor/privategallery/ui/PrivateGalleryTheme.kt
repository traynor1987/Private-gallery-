package uk.co.traynor.privategallery.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val GalleryDark = darkColorScheme(
    primary = Color(0xFFB9C7FF),
    secondary = Color(0xFFC2C6DD),
    surface = Color(0xFF111318),
    surfaceVariant = Color(0xFF20232B),
    secondaryContainer = Color(0xFF293044),
)

private val GalleryLight = lightColorScheme(
    primary = Color(0xFF315AA8),
    secondary = Color(0xFF505A77),
    surface = Color(0xFFFAF9FF),
    surfaceVariant = Color(0xFFE6E8F0),
)

@Composable
fun PrivateGalleryTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) GalleryDark else GalleryLight, content = content)
}
