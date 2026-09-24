package uk.co.traynor.privategallery.ui
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import uk.co.traynor.privategallery.core.editor.*
@Composable
fun AiEditingSettings() {
    val context = LocalContext.current
    val consent = remember { AiConsentStore(context) }
    var cleared by remember { mutableStateOf(false) }
    GalleryCard {
        GalleryCardHeading("AI editing")
        Text("Provider · ${AiProviderRegistry.configured?.displayName ?: "Not configured"}")
        Text("AI editing sends the selected image and your edit instructions to the configured AI provider for processing.", style = MaterialTheme.typography.bodyMedium)
        Text(AiProviderRegistry.NETWORK_POLICY, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (AiProviderRegistry.configured == null) Text("A supported provider must be configured before remote tools are available. Local editing works without an account.", style = MaterialTheme.typography.bodyMedium)
        TextButton(onClick = { consent.clear(); cleared = true }) { Text(if (cleared) "Consent cleared" else "Clear remembered consent") }
    }
}
