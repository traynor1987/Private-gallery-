package uk.co.traynor.privategallery.home2

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import uk.co.traynor.privategallery.core.backup.BackupCoordinator
import uk.co.traynor.privategallery.core.backup.BackupPolicy
import uk.co.traynor.privategallery.core.backup.backupProviders
import java.text.DateFormat
import java.util.Date

@Composable
internal fun BackupDashboardTheme(content: @Composable () -> Unit) {
    val current = MaterialTheme.colorScheme
    val colors = if (current.background.luminance() < .5f) {
        current.copy(
            primary = Color(0xFF9AE9DD),
            secondary = Color(0xFFC3B7FF),
            background = Color(0xFF0D1020),
            surface = Color(0xFF181C30),
            surfaceVariant = Color(0xFF23283F),
            surfaceContainer = Color(0xFF1C2036),
            onPrimary = Color(0xFF113831),
        )
    } else current
    MaterialTheme(colorScheme = colors, content = content)
}

@Composable
internal fun BackupTopBar(busy: Boolean, onClose: () -> Unit) {
    Row(
        Modifier.widthIn(max = 840.dp).fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Nova Backup", style = MaterialTheme.typography.headlineSmall)
            Text("Private recovery", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        TextButton(onClick = onClose) { Text(if (busy) "Cancel" else "Close") }
    }
}

@Composable
internal fun BackupOverview(
    history: BackupCoordinator.HistorySnapshot?,
    busy: Boolean,
    onCreate: () -> Unit,
    onRestore: () -> Unit,
) {
    val last = history?.lastBackup
    val accent = Color(0xFF9AE9DD)
    Surface(shape = RoundedCornerShape(26.dp), color = Color(0xFF20324D), contentColor = Color.White) {
        Row(
            Modifier.fillMaxWidth().background(
                Brush.linearGradient(listOf(Color(0xFF19374C), Color(0xFF252B50), Color(0xFF3C305B))),
            ).padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(shape = CircleShape, color = accent.copy(alpha = .16f)) {
                Icon(Icons.Default.Lock, null, Modifier.padding(12.dp).size(24.dp), tint = accent)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("ENCRYPTED BACKUPS", color = accent, style = MaterialTheme.typography.labelMedium)
                Text(
                    when {
                        last != null -> "Backup verified"
                        history == null -> "Backup status"
                        else -> "No verified backup yet"
                    },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text("Portable recovery for this Space.", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(
            onCreate, Modifier.weight(1f).heightIn(min = 52.dp),
            enabled = !busy, shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC3B7FF), contentColor = Color(0xFF211A43)),
        ) {
            Icon(Icons.Default.Add, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Create backup")
        }
        FilledTonalButton(onRestore, Modifier.weight(1f).heightIn(min = 52.dp), enabled = !busy, shape = RoundedCornerShape(16.dp)) {
            Icon(Icons.Default.FileDownload, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Restore")
        }
    }
}

@Composable
internal fun BackupOverviewDetails(history: BackupCoordinator.HistorySnapshot?, storage: Long?) {
    val last = history?.lastBackup
    val colors = MaterialTheme.colorScheme
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth >= 600.dp) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BackupArchiveCard(last, Modifier.weight(1.2f))
                BackupStorageCard(storage, Modifier.weight(1f))
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                BackupArchiveCard(last)
                BackupStorageCard(storage)
            }
        }
    }
    history?.events?.firstOrNull()?.takeUnless { it == last }?.let { event ->
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Latest activity", color = colors.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
            BackupStatus(event.status)
        }
    }
}

@Composable
private fun BackupArchiveCard(last: BackupCoordinator.HistoryEntry?, modifier: Modifier = Modifier) {
    BackupSection("Last verified backup", modifier) {
        if (last == null) {
            Text("Your next verified archive will appear here.", style = MaterialTheme.typography.bodyMedium)
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(backupSizeDisplay(last.bytes), style = MaterialTheme.typography.headlineSmall)
                BackupStatus("Verified")
            }
            BackupDetail("Created", backupDate(last.date))
            BackupDetail("Destination", last.destination)
        }
    }
}

@Composable
private fun BackupStorageCard(storage: Long?, modifier: Modifier = Modifier) {
    BackupSection("Vault media storage", modifier) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Storage, null, tint = MaterialTheme.colorScheme.primary)
            Text(storage?.let { backupSizeDisplay(it) } ?: "Unavailable", style = MaterialTheme.typography.headlineSmall)
        }
        Text(
            "Media in this Space. Archive size also includes encryption and supported settings.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun BackupAdvancedDetails(owner: String, events: List<BackupCoordinator.HistoryEntry>) {
    var expanded by remember { mutableStateOf(false) }
    BackupSection("Advanced details") {
        TextButton(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth()) {
            Text(if (expanded) "Hide archive details" else "Show archive details", Modifier.weight(1f))
            Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
        }
        if (expanded) {
            Text("Archive contents", style = MaterialTheme.typography.titleSmall)
            Text(
                "Encrypted active and Recently Deleted images, videos and documents, collection names, covers, membership and pins, media dates, ${BackupPolicy.mediaEditDescription(owner)}, Home layout and wallpaper, navigation, Quick Notes, Mind, saved app definitions, supported app saves and preferences.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(BackupPolicy.unsupported, style = MaterialTheme.typography.bodySmall)
            HorizontalDivider()
            Text("Destinations", style = MaterialTheme.typography.titleSmall)
            backupProviders.forEach { option ->
                Text(option.label + if (option.enabled) " · Available" else " · Disabled", style = MaterialTheme.typography.bodyMedium)
            }
            if (events.isNotEmpty()) {
                HorizontalDivider()
                Text("Recent activity", style = MaterialTheme.typography.titleSmall)
                events.forEachIndexed { index, event ->
                    if (index > 0) HorizontalDivider()
                    BackupDetail(event.operation, event.destination)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        BackupStatus(event.status)
                        Text(backupSizeDisplay(event.bytes), style = MaterialTheme.typography.labelMedium)
                    }
                    Text(backupDate(event.date), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
internal fun BackupOperationNotice(message: String, failure: Boolean) {
    val colors = MaterialTheme.colorScheme
    Surface(
        Modifier.fillMaxWidth().testTag("backup-operation-result").semantics { liveRegion = LiveRegionMode.Polite },
        shape = RoundedCornerShape(18.dp),
        color = if (failure) colors.errorContainer else colors.secondaryContainer,
        contentColor = if (failure) colors.onErrorContainer else colors.onSecondaryContainer,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(if (failure) "Backup needs attention" else "Backup activity", style = MaterialTheme.typography.titleMedium)
            Text(message, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
internal fun BackupSection(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

@Composable
internal fun BackupDetail(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun BackupStatus(status: String) {
    val colors = MaterialTheme.colorScheme
    val color = when (status) {
        "Verified" -> colors.primary
        "Failed" -> colors.error
        else -> colors.tertiary
    }
    Surface(shape = CircleShape, color = color.copy(alpha = .14f), contentColor = color) {
        Row(Modifier.padding(horizontal = 9.dp, vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (status == "Verified") Icon(Icons.Default.CheckCircle, null, Modifier.size(14.dp))
            Text(status, style = MaterialTheme.typography.labelMedium)
        }
    }
}

internal fun backupDate(epochMillis: Long): String = DateFormat.getDateTimeInstance().format(Date(epochMillis))
