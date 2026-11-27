package uk.co.traynor.privategallery.launcher

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontFamily
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import java.time.LocalTime
import kotlinx.coroutines.delay
import uk.co.traynor.privategallery.core.ui.AppTheme
import uk.co.traynor.privategallery.ui.PrivateGalleryTheme

/** Launcher presentation tokens never recolour Guardian or the protected vault surfaces. */
@Composable
internal fun LauncherStudioTheme(appearance: LauncherAppearance, content: @Composable () -> Unit) {
    val theme = when (currentLauncherTheme(appearance)) {
        LauncherTheme.SYSTEM -> AppTheme.SYSTEM
        LauncherTheme.LIGHT -> AppTheme.LIGHT
        LauncherTheme.DARK -> AppTheme.DARK
    }
    PrivateGalleryTheme(theme) {
        val base = MaterialTheme.colorScheme
        val dark = base.surface.luminance() < .2f
        val style = appearance.studio
        val palette = when (style.look) {
            LauncherLook.NOVA -> base
            LauncherLook.ROUNDED -> base.copy(
                surface = if (dark) Color(0xFF111B26) else Color(0xFFF7FAFD),
                surfaceContainer = if (dark) Color(0xFF1D2B39) else Color(0xFFEAF1F9),
                surfaceContainerHigh = if (dark) Color(0xFF293B4B) else Color(0xFFDAE7F5),
            )
            LauncherLook.FLOATING -> base.copy(
                surface = if (dark) Color(0xFF171C31) else Color(0xFFFBF9FF),
                surfaceContainer = if (dark) Color(0xFF282D49) else Color(0xFFF0EBFC),
                surfaceContainerHigh = if (dark) Color(0xFF303756) else Color(0xFFE7DFF8),
            )
        }
        val useBaseAccent = style.look == LauncherLook.NOVA && style.customAccent == null && appearance.accent == LauncherAccent.SKY
        val primary = if (useBaseAccent) base.primary else accessibleAccent(launcherAccent(appearance), palette.surface)
        val typography = MaterialTheme.typography
        val family = when (style.typeface) {
            LauncherTypeface.SYSTEM -> null
            LauncherTypeface.ROUNDED -> FontFamily.SansSerif
            LauncherTypeface.EDITORIAL -> FontFamily.Serif
            LauncherTypeface.MONOSPACE -> FontFamily.Monospace
        }
        MaterialTheme(
            colorScheme = if (useBaseAccent) palette else palette.copy(
                primary = primary, onPrimary = bestForeground(primary), surfaceTint = primary,
            ),
            typography = if (family == null) typography else typography.withFamily(family),
            content = content,
        )
    }
}

@Composable
private fun currentLauncherTheme(appearance: LauncherAppearance): LauncherTheme {
    val schedule = appearance.studio.schedule ?: return appearance.theme
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val hour by produceState(LocalTime.now().hour, schedule, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                val time = LocalTime.now()
                value = time.hour
                delay(60_000L - time.second * 1_000L)
            }
        }
    }
    return schedule.themeAt(hour)
}

internal fun launcherAccent(appearance: LauncherAppearance): Color = appearance.studio.customAccent?.let {
    accessibleAccent(Color(it or 0xFF000000.toInt()), Color(0xFF172638))
} ?: launcherAccent(appearance.accent)

private fun bestForeground(background: Color): Color = if (background.luminance() >= .179f) Color.Black else Color.White

private fun accessibleAccent(color: Color, surface: Color): Color {
    val foreground = bestForeground(surface)
    val surfaceLuminance = surface.luminance()
    for (step in 0..10) {
        val candidate = lerp(color, foreground, step / 10f)
        val candidateLuminance = candidate.luminance()
        if ((maxOf(surfaceLuminance, candidateLuminance) + .05f) /
            (minOf(surfaceLuminance, candidateLuminance) + .05f) >= 4.5f) return candidate
    }
    return foreground
}

private fun Typography.withFamily(family: FontFamily) = copy(
    displayLarge = displayLarge.copy(fontFamily = family), displayMedium = displayMedium.copy(fontFamily = family),
    displaySmall = displaySmall.copy(fontFamily = family), headlineLarge = headlineLarge.copy(fontFamily = family),
    headlineMedium = headlineMedium.copy(fontFamily = family), headlineSmall = headlineSmall.copy(fontFamily = family),
    titleLarge = titleLarge.copy(fontFamily = family), titleMedium = titleMedium.copy(fontFamily = family),
    titleSmall = titleSmall.copy(fontFamily = family), bodyLarge = bodyLarge.copy(fontFamily = family),
    bodyMedium = bodyMedium.copy(fontFamily = family), bodySmall = bodySmall.copy(fontFamily = family),
    labelLarge = labelLarge.copy(fontFamily = family), labelMedium = labelMedium.copy(fontFamily = family),
    labelSmall = labelSmall.copy(fontFamily = family),
)
