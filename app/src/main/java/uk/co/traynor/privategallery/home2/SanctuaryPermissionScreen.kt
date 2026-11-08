package uk.co.traynor.privategallery.home2

import android.Manifest
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import uk.co.traynor.privategallery.core.sanctuary.SanctuaryGuardian
import kotlinx.coroutines.delay

@Composable
internal fun SanctuaryPermissionScreen(
    context: Context,
    status: String,
    activeSession: Boolean,
    onResume: () -> Unit,
    onBack: () -> Unit,
) {
    var permissionResult by remember { mutableIntStateOf(0) }
    val requestNotifications = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { permissionResult++ }
    LaunchedEffect(context) {
        while (true) {
            delay(750)
            permissionResult++
        }
    }
    val usage = remember(context, permissionResult) { SanctuaryGuardian.hasUsageAccess(context) }
    val overlays = remember(context, permissionResult) { SanctuaryGuardian.hasOverlayAccess(context) }
    val notifications = remember(context, permissionResult) { SanctuaryGuardian.hasNotificationAccess(context) }
    val accessibility = remember(context, permissionResult) {
        SanctuaryGuardian.hasAccessibilityAccess(context)
    }
    val accessibilityConnected = remember(context, permissionResult) {
        SanctuaryGuardian.isAccessibilityConnected()
    }
    val dndAccess = remember(context, permissionResult) {
        context.getSystemService(android.app.NotificationManager::class.java)
            ?.isNotificationPolicyAccessGranted == true
    }
    val managedPinning = remember(context, permissionResult) {
        context.getSystemService(android.app.admin.DevicePolicyManager::class.java)
            ?.isLockTaskPermitted(context.packageName)
    }
    Column(Modifier.fillMaxSize().safeDrawingPadding().testTag("sanctuary-permissions")) {
        PreviewHeader("App Guardian setup", status, onBack)
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PermissionStep("Usage Access", usage, "Detect which Android app is in front. Sanctuary does not read messages or retain a browsing history.") {
                SanctuaryGuardian.openUsageSettings(context)
            }
            PermissionStep("Display over other apps", overlays, "Cover an unapproved app while a Sanctuary session is active.") {
                SanctuaryGuardian.openOverlaySettings(context)
            }
            PermissionStep("Notifications", notifications, "Show that monitoring is active and provide a way back to NovaOS.") {
                if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
                ) SanctuaryGuardian.openNotificationSettings(context)
                else requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            Text("Optional Home navigation", style = MaterialTheme.typography.titleMedium)
            Text(
                when {
                    accessibilityConnected && accessibility -> "Accessibility enabled and connected"
                    accessibility -> "Accessibility enabled · waiting for Android to connect"
                    else -> "Accessibility off · cover only"
                },
                modifier = Modifier.testTag("sanctuary-accessibility-status"),
            )
            Text(
                "Android grants Accessibility services broad powers. Sanctuary uses the foreground package and window " +
                    "transition only, and requests Home once when it confirms an unapproved app. It does not request " +
                    "window content, read text, or record or send events. Enable or disable Nova Sanctuary in Android " +
                    "Accessibility settings. Without this permission, the overlay can still cover a blocked app, " +
                    "but expiry may uncover the app underneath.",
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedButton(
                onClick = { SanctuaryGuardian.openAccessibilitySettings(context) },
                modifier = Modifier.testTag("sanctuary-open-accessibility-settings"),
            ) { Text("Open Android Accessibility settings") }
            HorizontalDivider()
            Text(if (dndAccess) "Do Not Disturb access granted" else "Do Not Disturb access off")
            Text(when (managedPinning) {
                true -> "Screen pinning unavailable under managed lock-task policy."
                false -> "Optional screen pinning uses Android’s confirmation. See the pinning controls for its current state."
                null -> "Screen pinning availability could not be checked."
            })
            Text(
                "Android can pause monitoring when permissions are removed, the app is stopped, or battery limits apply. " +
                    "Phone and Android Settings remain available.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(onClick = onResume, enabled = activeSession && usage && overlays && notifications, modifier = Modifier.fillMaxWidth()) {
                Text("Resume Guardian for the current session")
            }
        }
    }
}

@Composable
private fun PermissionStep(title: String, granted: Boolean, description: String, open: () -> Unit) {
    Text(title, style = MaterialTheme.typography.titleMedium)
    Text(description, style = MaterialTheme.typography.bodyMedium)
    OutlinedButton(onClick = open) { Text(if (granted) "Granted · Review" else "Open settings") }
    HorizontalDivider()
}
