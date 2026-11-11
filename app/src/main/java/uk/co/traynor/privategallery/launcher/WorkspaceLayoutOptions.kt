package uk.co.traynor.privategallery.launcher

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

@Composable
internal fun WorkspaceLayoutOptions(openWorkspace: () -> Unit, reloadLauncher: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val store = remember(context) { WorkspaceLayoutStore(context) }
    var status by remember(store) { mutableStateOf(store.read()) }
    var choosing by remember { mutableStateOf(false) }
    var restoring by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, store) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) status = store.read()
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    fun finish(saved: Boolean, unlockPrimary: Boolean = false) {
        failed = !saved
        status = store.read()
        if (saved) {
            choosing = false
            reloadLauncher()
            if (unlockPrimary) openWorkspace()
        }
    }

    Text("Workspace Layout", style = MaterialTheme.typography.titleMedium)
    Text(if (status.mode == WorkspaceLayoutMode.MIRROR) "Mirror Layouts" else "Separate Layouts")
    Text(
        "Mirroring shares compatible Nova app shortcuts, their folders and dock entries, grid, and bundled wallpaper. " +
            "Android apps, smart folders, App Library, personal wallpaper and widgets remain local. Edit shared entries in Nova Launcher.",
        style = MaterialTheme.typography.bodySmall,
    )
    if (status.pending != null) {
        OutlinedButton(openWorkspace, Modifier.testTag("workspace-layout-continue")) { Text("Continue in authenticated Primary NovaOS") }
        TextButton(onClick = { finish(store.cancelPending()) }) { Text("Cancel pending change") }
    } else {
        OutlinedButton(onClick = { choosing = true }, modifier = Modifier.testTag("workspace-layout-choose")) {
            Text(if (status.mode == WorkspaceLayoutMode.SEPARATE) "Mirror Layouts" else "Separate Layouts")
        }
    }
    if (status.pending == null && store.hasEarlierLauncherLayout()) {
        TextButton(onClick = { restoring = true }) { Text("Restore earlier Nova Launcher layout") }
    }
    if (restoring) {
        GuardianAlertDialog(
            onDismissRequest = { restoring = false },
            title = { Text("Restore earlier launcher layout?") },
            text = { Text("Restore the saved cover and unfolded launcher layouts. Primary NovaOS keeps its current layout.") },
            confirmButton = {
                TextButton(onClick = { restoring = false; finish(store.restorePrevious()) }) { Text("Restore") }
            },
            dismissButton = { TextButton(onClick = { restoring = false }) { Text("Cancel") } },
        )
    }
    if (failed) Text("The workspace layout change could not be saved.", color = MaterialTheme.colorScheme.error)
    if (choosing) {
        val mirroring = status.mode == WorkspaceLayoutMode.MIRROR
        GuardianAlertDialog(
            onDismissRequest = { choosing = false },
            title = { Text(if (mirroring) "Separate Layouts" else "Mirror Layouts") },
            text = {
                Text(if (mirroring) "Choose whether to copy the compatible shared layout or restore the saved separate layouts."
                    else "Choose a source for compatible items. The separate layouts are retained.")
            },
            confirmButton = {
                Column {
                    TextButton(onClick = {
                        if (mirroring) finish(store.requestCopyToBoth(), unlockPrimary = true)
                        else finish(store.useLauncher())
                    }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (mirroring) "Copy Current Layout to Both" else "Use Nova Launcher Layout")
                    }
                    TextButton(onClick = {
                        if (mirroring) finish(store.restorePrevious())
                        else finish(store.requestPrimary(), unlockPrimary = true)
                    }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (mirroring) "Restore Previous Separate Layouts" else "Use Primary NovaOS Layout")
                    }
                    TextButton(onClick = { choosing = false }) { Text("Cancel") }
                }
            },
        )
    }
}
