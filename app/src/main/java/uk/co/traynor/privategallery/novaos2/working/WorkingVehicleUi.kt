package uk.co.traynor.privategallery.novaos2.working

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import uk.co.traynor.privategallery.novaos2.NovaSystemSection

@Composable
internal fun WorkingVehicleSettings(vehicle: WorkingVehicle?, signal: WorkingVehicleSignal,
                                    busy: Boolean, noActiveShift: Boolean, onSetUp: () -> Unit,
                                    onDisable: () -> Unit, onDismissOffer: () -> Unit, onStart: () -> Unit) {
    NovaSystemSection("Paired vehicle") {
        if (vehicle == null) {
            Text("Optionally use a paired vehicle’s audio connection to start a working shift.")
        } else {
            Text(signal.description)
            Text(when {
                vehicle.driveOnConnect -> "Open Driving Mode when this vehicle connects while Working Mode is visible."
                vehicle.automatic -> "Start a shift automatically while Working Mode is open."
                else -> "Ask before starting a shift while Working Mode is open."
            }, style = MaterialTheme.typography.bodySmall)
            if (signal == WorkingVehicleSignal.CONNECTED && !vehicle.handled && !vehicle.automatic && noActiveShift) {
                Button(enabled = !busy, onClick = onStart) { Text("Start with connected vehicle") }
                TextButton(enabled = !busy, onClick = onDismissOffer) { Text("Skip this connection") }
            }
        }
        TextButton(onClick = onSetUp, enabled = !busy) { Text(if (vehicle == null) "Set up vehicle" else "Change vehicle activation") }
        if (vehicle != null) TextButton(onClick = onDisable, enabled = !busy) { Text("Remove vehicle") }
    }
}

@Composable
internal fun WorkingVehiclePicker(vehicle: WorkingVehicle?, permissionRevision: Int, busy: Boolean,
                                  allowed: () -> Boolean, onPermission: () -> Unit, onSettings: () -> Unit,
                                  onDismiss: () -> Unit, onSave: (String, Boolean, Boolean) -> Unit) {
    val context = LocalContext.current
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    val foreground = allowed() && lifecycle == Lifecycle.State.RESUMED
    val permission = workingVehiclePermission(context)
    var paired by remember { mutableStateOf<List<WorkingPairedDevice>?>(null) }
    var loading by remember { mutableStateOf(false) }
    var selected by remember(vehicle) { mutableStateOf(vehicle?.identifier) }
    var automatic by remember(vehicle) { mutableStateOf(vehicle?.automatic ?: false) }
    var driveOnConnect by remember(vehicle) { mutableStateOf(vehicle?.driveOnConnect ?: false) }
    LaunchedEffect(context, permissionRevision, permission, foreground) {
        paired = null
        if (foreground && permission) {
            loading = true
            paired = withContext(Dispatchers.IO) { workingPairedDevices(context) }.takeIf { allowed() }
            loading = false
        } else loading = false
    }

    AlertDialog(onDismissRequest = onDismiss, title = { Text("Choose a paired vehicle") },
        properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn),
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Choose a vehicle you already paired in Android.")
                val devices = paired
                when {
                    !permission -> {
                        Text("Nearby devices permission lets NovaOS read paired devices and audio connection status.")
                        Button(onClick = onPermission) { Text("Allow Nearby devices") }
                        TextButton(onClick = onSettings) { Text("Open app permissions") }
                    }
                    loading -> Text("Reading paired devices…")
                    devices == null -> Text("Paired devices unavailable. Check that Bluetooth is on.")
                    devices.isEmpty() -> Text("No paired Bluetooth devices. Pair your vehicle in Android first.")
                    else -> LazyColumn(Modifier.heightIn(max = 200.dp)) {
                        itemsIndexed(devices) { index, device ->
                            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(
                                selected = selected == device.identifier, enabled = !busy,
                                role = Role.RadioButton, onClick = { selected = device.identifier },
                            ), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected == device.identifier, onClick = null, enabled = !busy)
                                Text(device.label ?: "Paired device ${index + 1}", maxLines = 2)
                            }
                        }
                    }
                }
                if (permission && !paired.isNullOrEmpty()) Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(automatic, { enabled ->
                        automatic = enabled
                        if (!enabled) driveOnConnect = false
                    }, enabled = !busy)
                    Text("Start the shift automatically", style = MaterialTheme.typography.bodyMedium)
                }
                if (automatic && permission && !paired.isNullOrEmpty()) Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(driveOnConnect, { driveOnConnect = it }, enabled = !busy)
                    Text("Also open Driving Mode", style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            val choice = selected?.takeIf { id -> paired?.any { it.identifier == id } == true }
            TextButton(onClick = { choice?.let { onSave(it, automatic, automatic && driveOnConnect) } }, enabled = choice != null && foreground && !busy) {
                Text("Use paired vehicle")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}
