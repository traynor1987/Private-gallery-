package uk.co.traynor.privategallery.novaos2

import android.Manifest
import android.app.NotificationManager
import android.app.usage.StorageStatsManager
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.os.PersistableBundle
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Process
import android.os.storage.StorageManager
import android.provider.Settings
import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.withContext
import uk.co.traynor.privategallery.core.backup.BackupSchedule
import uk.co.traynor.privategallery.core.brain.CloudTeacherStore
import uk.co.traynor.privategallery.core.guardian.GuardianCapture
import uk.co.traynor.privategallery.core.guardian.GuardianNotificationListener
import uk.co.traynor.privategallery.core.sanctuary.SanctuaryGuardian
import uk.co.traynor.privategallery.core.sanctuary.SanctuaryStore

private fun readNovaPrivacy(context: Context): NovaPrivacySnapshot {
    fun granted(permission: String) = context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
    fun grant(permission: String) = NovaPrivacyAccess.from(granted(permission))
    fun status(read: () -> Boolean?) = NovaPrivacyAccess.from(runCatching(read).getOrNull())
    val connectivity = context.getSystemService(ConnectivityManager::class.java)
    val vpn = runCatching { connectivity?.getNetworkCapabilities(connectivity.activeNetwork)
        ?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) }.getOrNull()
    val storage = runCatching {
        val manager = checkNotNull(context.getSystemService(StorageManager::class.java))
        val stats = checkNotNull(context.getSystemService(StorageStatsManager::class.java))
            .queryStatsForUid(manager.getUuidForPath(context.filesDir), Process.myUid())
        NovaPrivacyStorage(stats.dataBytes, stats.cacheBytes)
    }.getOrNull()
    return NovaPrivacySnapshot(
        camera = grant(Manifest.permission.CAMERA),
        microphone = grant(Manifest.permission.RECORD_AUDIO),
        contacts = grant(Manifest.permission.READ_CONTACTS),
        location = NovaPrivacyLocation.from(granted(Manifest.permission.ACCESS_FINE_LOCATION),
            granted(Manifest.permission.ACCESS_COARSE_LOCATION)),
        notifications = status { context.getSystemService(NotificationManager::class.java)?.areNotificationsEnabled() },
        guardian = NovaPrivacyGuardian.from(GuardianNotificationListener.hasAccess(context),
            GuardianNotificationListener.state.value.connected, GuardianCapture.isEnabled(context)),
        accessibility = status { SanctuaryGuardian.hasAccessibilityAccess(context) },
        usageAccess = status { SanctuaryGuardian.hasUsageAccess(context) },
        overlay = status { SanctuaryGuardian.hasOverlayAccess(context) },
        vpn = NovaPrivacyVpn.from(vpn),
        sanctuary = if (SanctuaryStore.get(context).current.value == null) "No active Sanctuary session"
            else SanctuaryGuardian.status(context),
        manualCloudConsent = status { CloudTeacherStore(context).enabled() },
        backupSchedule = runCatching { BackupSchedule.status(context) }.getOrDefault("Schedule unavailable"),
        storage = storage,
    )
}

@Composable
internal fun NovaPrivacyDashboard(host: NovaSystemHost) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var snapshot by remember { mutableStateOf<NovaPrivacySnapshot?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(lifecycle, context, refresh) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            snapshot = withContext(Dispatchers.IO) { runCatching { readNovaPrivacy(context.applicationContext) }.getOrNull() }
            if (snapshot == null) message = "NovaOS could not read privacy status. Review the Android settings below."
            awaitCancellation()
        }
    }

    NovaPrivacyContent(snapshot, host.wide, message, onRefresh = {
        message = null
        refresh++
    }, onSettings = { action ->
        message = if (host.openSystemSettings(action)) null else "This Android settings page is unavailable."
    }, openWorkspace = {
        if (host.foregroundAllowed()) host.actions.openWorkspace()
        else message = "Return to NovaOS Home to open the workspace."
    })
}

@Composable
internal fun NovaPrivacyContent(snapshot: NovaPrivacySnapshot?, wide: Boolean, message: String?,
    onRefresh: () -> Unit, onSettings: (String) -> Unit, openWorkspace: () -> Unit) {
    val context = LocalContext.current
    var copied by remember(snapshot) { mutableStateOf<String?>(null) }
    val permissions = @Composable {
        NovaSystemSection("NovaOS app permissions") {
            NovaPrivacyRow("Camera", snapshot?.camera?.label)
            NovaPrivacyRow("Microphone", snapshot?.microphone?.label)
            NovaPrivacyRow("Location", snapshot?.location?.label)
            NovaPrivacyRow("Contacts", snapshot?.contacts?.label)
            NovaPrivacyRow("Post notifications", snapshot?.notifications?.label)
            Text("Android’s privacy dashboard shows recent camera, microphone and location access across apps.",
                style = MaterialTheme.typography.bodySmall)
            PrivacySettingsButton("Open Android privacy settings", Settings.ACTION_PRIVACY_SETTINGS, onSettings)
            PrivacySettingsButton("Manage NovaOS permissions and storage", Settings.ACTION_APPLICATION_DETAILS_SETTINGS, onSettings)
        }
        NovaSystemSection("Special access") {
            NovaPrivacyRow("Sanctuary accessibility", snapshot?.accessibility?.label)
            NovaPrivacyRow("App usage access", snapshot?.usageAccess?.label)
            NovaPrivacyRow("Display over other apps", snapshot?.overlay?.label)
            PrivacySettingsButton("Manage accessibility", Settings.ACTION_ACCESSIBILITY_SETTINGS, onSettings)
            PrivacySettingsButton("Manage usage access", Settings.ACTION_USAGE_ACCESS_SETTINGS, onSettings)
            PrivacySettingsButton("Manage device location", Settings.ACTION_LOCATION_SOURCE_SETTINGS, onSettings)
        }
    }
    val protection = @Composable {
        NovaSystemSection("Protection") {
            NovaPrivacyRow("Guardian notifications", snapshot?.guardian?.label)
            if (snapshot?.guardian == NovaPrivacyGuardian.ACCESS_OFF) {
                Text("Enable notification access in Android if you want Guardian to monitor notifications.",
                    style = MaterialTheme.typography.bodySmall)
            }
            if (snapshot?.notifications == NovaPrivacyAccess.OFF &&
                snapshot.guardian == NovaPrivacyGuardian.CAPTURE_ACTIVE) {
                Text("Guardian capture is enabled. To receive replacement notifications, allow NovaOS to post notifications.",
                    style = MaterialTheme.typography.bodySmall)
            }
            NovaPrivacyRow("Sanctuary", snapshot?.sanctuary)
            NovaPrivacyRow("Network privacy", snapshot?.vpn?.label)
            Text("Android provides no device-wide Tor connection status.", style = MaterialTheme.typography.bodySmall)
            PrivacySettingsButton("Review Guardian notification access", Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS, onSettings)
            PrivacySettingsButton("Open Android VPN settings", Settings.ACTION_VPN_SETTINGS, onSettings)
        }
        NovaSystemSection("Data and consent") {
            NovaPrivacyRow("Cloud Teacher manual review consent", snapshot?.manualCloudConsent?.label)
            NovaPrivacyRow("NovaOS app data including cache", snapshot?.storage?.appDataBytes?.let { Formatter.formatFileSize(context, it) })
            NovaPrivacyRow("NovaOS cache", snapshot?.storage?.cacheBytes?.let { Formatter.formatFileSize(context, it) })
            NovaPrivacyRow("Encrypted backup schedule", snapshot?.backupSchedule)
            Text("Open Nova Backup in the workspace to review verified archives.", style = MaterialTheme.typography.bodySmall)
            TextButton(enabled = snapshot != null, onClick = {
                val report = snapshot?.diagnostics() ?: return@TextButton
                copied = try {
                    val clipboard = checkNotNull(context.getSystemService(ClipboardManager::class.java))
                    val clip = ClipData.newPlainText("NovaOS privacy diagnostics", report)
                    clip.description.extras = PersistableBundle().apply {
                        putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
                    }
                    clipboard.setPrimaryClip(clip)
                    "Privacy status copied."
                } catch (_: RuntimeException) {
                    "Clipboard unavailable."
                }
            }) { Text("Copy privacy diagnostics") }
            copied?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            TextButton(onClick = openWorkspace) { Text("Open NovaOS workspace") }
        }
    }

    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp)
        .testTag("nova-privacy-dashboard"), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("On-device status", Modifier.weight(1f).padding(top = 12.dp), style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = onRefresh) { Text("Refresh") }
        }
        message?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
        if (wide) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) { permissions() }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) { protection() }
            }
        } else {
            permissions()
            protection()
        }
    }
}

@Composable
private fun NovaPrivacyRow(label: String, value: String?) {
    Column(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Text(value ?: "Unavailable", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PrivacySettingsButton(label: String, action: String, onSettings: (String) -> Unit) {
    TextButton(onClick = { onSettings(action) }) { Text(label) }
}
