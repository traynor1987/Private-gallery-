package uk.co.traynor.privategallery.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.FontWeight
import uk.co.traynor.privategallery.core.ui.AppTheme

private val GalleryDark = darkColorScheme(
    primary = Color(0xFFB7F663),
    onPrimary = Color(0xFF14200C),
    primaryContainer = Color(0xFF26391C),
    onPrimaryContainer = Color(0xFFD7FFC0),
    secondary = Color(0xFF63C7F2),
    onSecondary = Color(0xFF002F3E),
    secondaryContainer = Color(0xFF0F3544),
    background = Color(0xFF080C0E),
    surface = Color(0xFF111719),
    surfaceVariant = Color(0xFF182124),
    onSurface = Color(0xFFF3F6F5),
    onSurfaceVariant = Color(0xFFA8B4B7),
    outline = Color(0xFF2A3538),
    outlineVariant = Color(0xFF202A2D),
)

private val GalleryLight = lightColorScheme(
    primary = Color(0xFF456C19),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD7FFC0),
    onPrimaryContainer = Color(0xFF1E3705),
    secondary = Color(0xFF126A8D),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD2F0FF),
    background = Color(0xFFF2F5F3),
    surface = Color(0xFFFCFEFC),
    surfaceVariant = Color(0xFFE8EEEA),
    onSurface = Color(0xFF17201B),
    onSurfaceVariant = Color(0xFF59665F),
    outline = Color(0xFFCCD5D0),
    outlineVariant = Color(0xFFDDE4E0),
)

private val GalleryTypography = Typography().let { base ->
    base.copy(
        headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
        headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.Bold),
        labelLarge = base.labelLarge.copy(fontWeight = FontWeight.Bold),
        labelMedium = base.labelMedium.copy(fontWeight = FontWeight.Bold),
    )
}

@Composable
fun PrivateGalleryTheme(theme: AppTheme = AppTheme.SYSTEM, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = when (theme) {
            AppTheme.SYSTEM -> if (isSystemInDarkTheme()) GalleryDark else GalleryLight
            AppTheme.LIGHT -> GalleryLight
            AppTheme.DARK -> GalleryDark
        },
        typography = GalleryTypography,
        content = content,
    )
}
