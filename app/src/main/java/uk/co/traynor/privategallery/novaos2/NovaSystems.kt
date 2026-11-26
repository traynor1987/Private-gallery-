package uk.co.traynor.privategallery.novaos2

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import uk.co.traynor.privategallery.core.editor.AiCredentialStore
import uk.co.traynor.privategallery.core.home2.NovaCoreApp
import uk.co.traynor.privategallery.core.sanctuary.SanctuaryLauncherPolicy.Decision
import uk.co.traynor.privategallery.launcher.LauncherApp
import uk.co.traynor.privategallery.launcher.LauncherUiActions
import uk.co.traynor.privategallery.launcher.NovaLauncherState

internal enum class NovaSystemId { THEMES, WORKING, CONTROL, LOCK, DESKTOP, HEALTH, PRIVACY, UTILITIES }

private val systemPanels = setOf(
    Settings.Panel.ACTION_INTERNET_CONNECTIVITY, Settings.Panel.ACTION_VOLUME,
    Settings.ACTION_SETTINGS, Settings.ACTION_WIFI_SETTINGS, Settings.ACTION_SOUND_SETTINGS,
    Settings.ACTION_DISPLAY_SETTINGS, Settings.ACTION_SECURITY_SETTINGS, Settings.ACTION_PRIVACY_SETTINGS,
    Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS, Settings.ACTION_ACCESSIBILITY_SETTINGS,
    Settings.ACTION_LOCATION_SOURCE_SETTINGS, Settings.ACTION_VPN_SETTINGS, Settings.ACTION_USAGE_ACCESS_SETTINGS,
)

internal fun novaSystemSettingsIntent(context: Context, action: String): Intent? = when (action) {
    Settings.ACTION_APPLICATION_DETAILS_SETTINGS -> Intent(action, Uri.fromParts("package", context.packageName, null))
    Settings.ACTION_APP_NOTIFICATION_SETTINGS -> Intent(action).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    in systemPanels -> Intent(action)
    else -> null
}

internal class NovaSystemFeature(
    val id: NovaSystemId,
    val title: String,
    val description: String,
    val content: @Composable (NovaSystemHost) -> Unit,
)

/** Platform actions remain on the existing Home activity and recheck the foreground boundary. */
internal class LauncherNovaSystemsActions(
    val foregroundAllowed: () -> Boolean,
    val launch: (LauncherApp) -> Boolean,
    val openCoreApp: (NovaCoreApp) -> Boolean,
    val openSystemSettings: (String) -> Boolean,
    val openUpdates: () -> Boolean,
)

internal class NovaSystemHost(
    val state: NovaLauncherState,
    val apps: List<LauncherApp>,
    val decisions: Map<String, Decision>,
    val actions: LauncherUiActions,
    val wide: Boolean,
    val available: Set<NovaSystemId>,
    val onBack: () -> Unit,
    val open: (NovaSystemId) -> Boolean,
    private val platform: LauncherNovaSystemsActions,
) {
    val foregroundAllowed: () -> Boolean = platform.foregroundAllowed
    fun launch(app: LauncherApp) = foregroundAllowed() && app in apps && platform.launch(app)
    fun openCoreApp(app: NovaCoreApp) = foregroundAllowed() && platform.openCoreApp(app)
    fun openSystemSettings(action: String) = foregroundAllowed() && platform.openSystemSettings(action)
    fun openUpdates() = foregroundAllowed() && platform.openUpdates()
}

/** Small device settings reuse the established atomic, Keystore-backed, authenticated file store. */
internal class NovaSystemConfig(context: Context, key: String) {
    private val credentials = AiCredentialStore(context.applicationContext)
    private val id = "novaos2-$key".also { require(KEY.matches(key)) }

    fun read(): String? = synchronized(gate) {
        val contents = credentials.read(id) ?: return@synchronized null
        try { contents.decodeToString(throwOnInvalidSequence = true) } finally { contents.fill(0) }
    }

    fun write(value: String) = synchronized(gate) {
        val contents = value.encodeToByteArray()
        try { credentials.save(id, contents) } finally { contents.fill(0) }
    }

    fun clear() = synchronized(gate) { credentials.clear(id) }

    private companion object {
        val KEY = Regex("[a-z0-9-]{1,48}")
        val gate = Any()
    }
}

@Composable
internal fun NovaSystemsPanel(features: List<NovaSystemFeature>, selected: NovaSystemId?, host: NovaSystemHost) {
    val current = features.firstOrNull { it.id == selected }
    Surface(Modifier.fillMaxSize().testTag("novaos2-panel")) {
        Column(Modifier.fillMaxSize().safeDrawingPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(
                Modifier.widthIn(max = 1180.dp).fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                IconButton(onClick = host.onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Text(current?.title ?: "NovaOS systems", Modifier.semantics { heading() },
                    style = MaterialTheme.typography.headlineSmall)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Box(Modifier.widthIn(max = 1180.dp).fillMaxWidth().weight(1f)) {
                if (current != null) current.content(host)
                else LazyVerticalGrid(
                    columns = GridCells.Adaptive(275.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(features, key = { it.id.name }) { feature ->
                        Card(onClick = { host.open(feature.id) }, modifier = Modifier.heightIn(min = 88.dp)) {
                            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(feature.title, style = MaterialTheme.typography.titleMedium)
                                Text(feature.description, style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun NovaSystemSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}
