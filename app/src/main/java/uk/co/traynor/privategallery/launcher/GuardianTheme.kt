package uk.co.traynor.privategallery.launcher

import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import uk.co.traynor.privategallery.core.ui.AppTheme
import uk.co.traynor.privategallery.ui.PrivateGalleryTheme

internal val GuardianNavy = Color(0xFF0D1727)
internal val GuardianGlass = Color(0xFF172F44)
internal val GuardianBorder = Color(0xFF2B455D)
internal val GuardianMuted = Color(0xFFB6C9DF)
internal val GuardianBlue = Color(0xFF9BCBFF)
internal val GuardianMint = Color(0xFF9DE3CE)
internal val GuardianAmber = Color(0xFFF0C79B)

internal val GuardianPrimary = Color(0xFFF1F6FC)
private val GuardianElevated = Color(0xFF203951)
private val GuardianDisabled = Color(0xFFACBED2)
private val GuardianDivider = Color(0xFF536D86)

@Composable
internal fun GuardianTheme(content: @Composable () -> Unit) {
    PrivateGalleryTheme(AppTheme.DARK) {
        MaterialTheme(
            colorScheme = MaterialTheme.colorScheme.copy(
                background = GuardianNavy, onBackground = GuardianPrimary,
                surface = GuardianNavy, onSurface = GuardianPrimary,
                surfaceVariant = GuardianGlass, onSurfaceVariant = GuardianMuted,
                surfaceContainerLow = GuardianGlass, surfaceContainer = GuardianGlass,
                surfaceContainerHigh = GuardianElevated, surfaceContainerHighest = GuardianElevated,
                primary = GuardianBlue, secondaryContainer = GuardianElevated, onSecondaryContainer = GuardianPrimary,
                outline = GuardianMuted, outlineVariant = GuardianDivider, error = GuardianAmber,
            ),
            content = content,
        )
    }
}

@Composable
internal fun guardianCardColors() = CardDefaults.cardColors(
    containerColor = GuardianGlass, contentColor = GuardianPrimary,
    disabledContainerColor = GuardianGlass, disabledContentColor = GuardianDisabled,
)

@Composable
internal fun GuardianAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    properties: androidx.compose.ui.window.DialogProperties = androidx.compose.ui.window.DialogProperties(),
) {
    GuardianTheme {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = onDismissRequest,
            confirmButton = confirmButton,
            modifier = modifier,
            dismissButton = dismissButton,
            icon = icon,
            title = title,
            text = text,
            properties = properties,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
            containerColor = GuardianGlass,
            titleContentColor = GuardianPrimary,
            textContentColor = GuardianMuted,
        )
    }
}
