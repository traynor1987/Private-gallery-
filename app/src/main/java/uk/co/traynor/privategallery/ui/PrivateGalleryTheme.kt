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

// Fixed neutral palette: system wallpaper colours must not recolour protected UI.
private val GalleryDark = darkColorScheme(
    primary = Color(0xFFB9C7DA), onPrimary = Color(0xFF233044),
    primaryContainer = Color(0xFF303C4E), onPrimaryContainer = Color(0xFFE0E8F5),
    secondary = Color(0xFFBAC4D1), onSecondary = Color(0xFF25303C),
    secondaryContainer = Color(0xFF303943), onSecondaryContainer = Color(0xFFE0E6EE),
    tertiary = Color(0xFFC4C5D0), onTertiary = Color(0xFF2D2E38),
    tertiaryContainer = Color(0xFF43444E), onTertiaryContainer = Color(0xFFE1E1ED),
    background = Color(0xFF121417), onBackground = Color(0xFFE4E6EA),
    surface = Color(0xFF181A1E), onSurface = Color(0xFFE4E6EA),
    surfaceVariant = Color(0xFF292D33), onSurfaceVariant = Color(0xFFBAC0C9),
    surfaceDim = Color(0xFF121417), surfaceBright = Color(0xFF37393E),
    surfaceContainerLowest = Color(0xFF0F1114), surfaceContainerLow = Color(0xFF1C1E22),
    surfaceContainer = Color(0xFF22252A), surfaceContainerHigh = Color(0xFF2A2D32),
    surfaceContainerHighest = Color(0xFF34373D),
    outline = Color(0xFF89919C), outlineVariant = Color(0xFF42474F),
    inverseSurface = Color(0xFFE4E6EA), inverseOnSurface = Color(0xFF292D33),
    inversePrimary = Color(0xFF465B76), surfaceTint = Color(0xFFB9C7DA),
)

private val GalleryLight = lightColorScheme(
    primary = Color(0xFF465B76), onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDCE5F1), onPrimaryContainer = Color(0xFF21344B),
    secondary = Color(0xFF515F70), onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE1E7EF), onSecondaryContainer = Color(0xFF293544),
    tertiary = Color(0xFF5B5D69), onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFE3E3EF), onTertiaryContainer = Color(0xFF30323D),
    background = Color(0xFFF6F7F9), onBackground = Color(0xFF1B1E23),
    surface = Color(0xFFFAFBFC), onSurface = Color(0xFF1B1E23),
    surfaceVariant = Color(0xFFE9ECF0), onSurfaceVariant = Color(0xFF525B67),
    surfaceDim = Color(0xFFDADDE2), surfaceBright = Color(0xFFFAFBFC),
    surfaceContainerLowest = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFF2F4F7),
    surfaceContainer = Color(0xFFEDEFF3), surfaceContainerHigh = Color(0xFFE7E9EE),
    surfaceContainerHighest = Color(0xFFE1E4E9),
    outline = Color(0xFF737D89), outlineVariant = Color(0xFFD2D7DF),
    inverseSurface = Color(0xFF2D3138), inverseOnSurface = Color(0xFFF0F1F5),
    inversePrimary = Color(0xFFB9C7DA), surfaceTint = Color(0xFF465B76),
)

private val GalleryTypography = Typography().let { base ->
    base.copy(
        headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
        headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.Medium),
        labelLarge = base.labelLarge.copy(fontWeight = FontWeight.Medium),
        labelMedium = base.labelMedium.copy(fontWeight = FontWeight.Medium),
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
        shapes = androidx.compose.material3.Shapes(small = GalleryTokens.RowShape, medium = GalleryTokens.CardShape, large = GalleryTokens.HeroShape),
        content = content,
    )
}
