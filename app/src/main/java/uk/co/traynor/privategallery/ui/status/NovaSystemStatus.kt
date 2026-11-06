package uk.co.traynor.privategallery.ui.status

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AirplanemodeActive
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import uk.co.traynor.privategallery.core.ui.status.SystemStatusProvider
import uk.co.traynor.privategallery.core.ui.status.SystemStatusSession
import uk.co.traynor.privategallery.core.ui.status.SystemStatusSnapshot
import uk.co.traynor.privategallery.core.ui.status.SystemStatusSnapshot.Transport

/**
 * Shared device chrome. Host must disable for covered, locked, retired or unauthenticated surfaces.
 * The real status-bar inset visibility additionally gates both rendering and event subscriptions.
 */
@Composable
internal fun NovaSystemStatus(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    provider: SystemStatusProvider? = null
) {
    val context = LocalContext.current
    val application = context.applicationContext
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val source = provider ?: remember(application) { AndroidSystemStatusProvider(application) }
    val view = LocalView.current
    // Child-dispatched Compose insets may be consumed by decor/ancestors. Until original root
    // visibility is observed, treat the Android bar as present and omit custom status chrome.
    var statusBarVisible by remember(view) { mutableStateOf(true) }
    var resumed by remember(lifecycle) { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    var unlocked by remember(application) { mutableStateOf(screenAvailable(application)) }
    var snapshot by remember(source) { mutableStateOf<SystemStatusSnapshot?>(null) }
    val session = remember(source) { SystemStatusSession(source) { snapshot = it } }
    DisposableEffect(session) { onDispose { session.close() } }

    DisposableEffect(lifecycle, application, session) {
        val observer = LifecycleEventObserver { _, _ ->
            resumed = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            unlocked = screenAvailable(application)
            if (!resumed || !unlocked) session.setVisible(false, resumed, !unlocked, true)
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    DisposableEffect(application, enabled, resumed, statusBarVisible, session) {
        var registered = false
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                unlocked = intent.action != Intent.ACTION_SCREEN_OFF && screenAvailable(application)
                if (!unlocked) session.setVisible(false, resumed, true, true)
            }
        }
        if (enabled && resumed && !statusBarVisible) {
            unlocked = screenAvailable(application)
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_USER_PRESENT)
            }
            application.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            registered = true
        }
        onDispose { if (registered) application.unregisterReceiver(receiver) }
    }
    DisposableEffect(view, session, enabled, resumed, unlocked) {
        snapshot = null
        val observer = if (enabled && resumed && unlocked) {
            AndroidStatusBarVisibilityObserver(view) { androidBarVisible ->
                // Retire callbacks in the Android event itself, before recomposition/drawing.
                session.setVisible(enabled, resumed, !unlocked, androidBarVisible)
                if (androidBarVisible) snapshot = null
                statusBarVisible = androidBarVisible
            }.also { it.start() }
        } else {
            session.setVisible(false, resumed, !unlocked, true)
            null
        }
        onDispose {
            observer?.close()
            session.setVisible(false, false, true, true)
        }
    }
    val visible = enabled && resumed && unlocked && !statusBarVisible
    if (visible) snapshot?.let { NovaSystemStatusContent(it, modifier) }
}

private fun screenAvailable(context: Context): Boolean =
    context.getSystemService(PowerManager::class.java)?.isInteractive == true &&
        context.getSystemService(KeyguardManager::class.java)?.isKeyguardLocked != true

@Composable
private fun NovaSystemStatusContent(status: SystemStatusSnapshot, modifier: Modifier) {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    val description = buildList {
        status.batteryPercent?.let { add("Battery $it percent") }
        if (status.charging) add("Charging") else if (status.chargerConnected) add("Charger connected")
        if (status.powerSaver) add("Battery saver")
        status.networkLabel().takeIf { it.isNotEmpty() }?.let(::add)
        if (status.airplaneMode && status.transport != Transport.DISCONNECTED) add("Airplane mode")
    }.joinToString(", ")
    Row(
        modifier.heightIn(min = 20.dp).testTag("nova-system-status")
            .semantics(mergeDescendants = true) { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        status.batteryPercent?.let { percent ->
            BatteryLevelIcon(status.batteryFillFraction())
            Text("$percent%", Modifier.testTag("nova-status-percent"), color = color,
                style = MaterialTheme.typography.labelSmall, maxLines = 1, softWrap = false)
        }
        if (status.chargerConnected || status.charging) StatusIcon(Icons.Default.Bolt)
        if (status.powerSaver) StatusIcon(Icons.Default.BatterySaver)
        if (status.airplaneMode) StatusIcon(Icons.Default.AirplanemodeActive)
        when (status.transport) {
            Transport.WIFI -> StatusIcon(Icons.Default.Wifi)
            Transport.MOBILE -> {
                StatusIcon(Icons.Default.CellTower)
                status.mobileTechnology?.let { technology ->
                    Text(technology, Modifier.testTag("nova-status-radio"), color = color,
                        style = MaterialTheme.typography.labelSmall, maxLines = 1, softWrap = false)
                }
            }
            Transport.ETHERNET -> StatusIcon(Icons.Default.Lan)
            Transport.DISCONNECTED -> if (!status.airplaneMode) StatusIcon(Icons.Default.CloudOff)
            Transport.OTHER -> if (!status.internetValidated) StatusIcon(Icons.Default.CloudOff)
            Transport.UNKNOWN -> Unit
        }
        if (status.transport in listOf(Transport.WIFI, Transport.MOBILE, Transport.ETHERNET) &&
            !status.internetValidated) StatusIcon(Icons.Default.ErrorOutline)
    }
}

@Composable
private fun BatteryLevelIcon(fillFraction: Float) {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(Modifier.size(14.dp).testTag("nova-status-battery")) {
        val w = size.width
        val h = size.height
        drawRoundRect(
            color = color,
            topLeft = Offset(w * 0.06f, h * 0.23f),
            size = Size(w * 0.76f, h * 0.54f),
            cornerRadius = CornerRadius(w * 0.07f),
            style = Stroke(width = w * 0.08f)
        )
        drawRect(color, topLeft = Offset(w * 0.86f, h * 0.38f),
            size = Size(w * 0.10f, h * 0.24f))
        if (fillFraction > 0f) {
            drawRect(color, topLeft = Offset(w * 0.15f, h * 0.32f),
                size = Size(w * 0.58f * fillFraction, h * 0.36f))
        }
    }
}

@Composable
private fun StatusIcon(image: ImageVector) {
    Icon(image, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
}
