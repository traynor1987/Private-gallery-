package uk.co.traynor.privategallery.home2

import android.content.Context
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import uk.co.traynor.privategallery.core.evidence.EvidenceAccess
import uk.co.traynor.privategallery.core.evidence.EvidenceCatalog
import uk.co.traynor.privategallery.launcher.NovaLauncherStore
import uk.co.traynor.privategallery.launcher.WorkspaceLayoutMode
import uk.co.traynor.privategallery.launcher.WorkspaceLayoutRequest
import uk.co.traynor.privategallery.launcher.WorkspaceLayoutStore
import uk.co.traynor.privategallery.launcher.publicProjection
import uk.co.traynor.privategallery.launcher.withLauncherProjection

/** Runs only inside the authenticated Primary Home; no Hidden context may open the public bridge. */
@Composable
internal fun primaryWorkspaceLayout(
    base: Context,
    access: EvidenceAccess,
    visible: Boolean,
    data: PreviewData,
    widgets: WidgetData,
    wide: Boolean,
): Profile? {
    if (access.owner != EvidenceCatalog.Owner.PRIMARY || !visible || !access.current()) return null
    val application = base.applicationContext
    val store = remember(application) { WorkspaceLayoutStore(application) }
    val launcher = remember(application) { NovaLauncherStore(application) }
    var status by remember(store) { mutableStateOf(store.read()) }
    var generation by remember(store) { mutableStateOf(0) }
    var failed by remember(store) { mutableStateOf(false) }
    fun lastWidgetPage(): Int = widgets.slots("primary").maxOfOrNull(WidgetSlot::page) ?: -1

    fun materializeCopy(): Boolean {
        if (!access.current()) return false
        val copy = store.primaryCopy() ?: return true
        val projected = data.profile.withLauncherProjection(copy.layout, application.packageName, wide, lastWidgetPage())
        return data.materializeWorkspaceCopy(copy.id, projected) && store.acknowledgePrimaryCopy(copy.id)
    }

    LaunchedEffect(access, generation, status.mode) {
        if (status.mode == WorkspaceLayoutMode.SEPARATE) failed = !materializeCopy()
    }

    val request = status.pending
    val requestId = status.requestId
    if (request != null && requestId != null) {
        val usePrimary = request == WorkspaceLayoutRequest.USE_PRIMARY
        fun cancel() {
            failed = !store.cancelPending()
            status = store.read()
        }
        AlertDialog(
            onDismissRequest = ::cancel,
            title = { Text(if (usePrimary) "Share compatible Primary layout?" else "Copy the shared layout?") },
            text = {
                Text(if (usePrimary)
                    "Share the placement of compatible Nova apps and bundled appearance. Primary-only shortcuts, folder names, personal images and widgets stay private."
                else "Copy compatible layout items for this display into Primary NovaOS. Existing Primary-only items stay here and you can restore the previous Primary layout.")
            },
            confirmButton = {
                TextButton(onClick = {
                    if (!access.current()) return@TextButton
                    val saved = if (usePrimary) {
                        val projection = data.profile.publicProjection(application.packageName)
                        access.current() && store.usePrimary(requestId, projection, application.packageName)
                    } else store.copyToBoth(requestId)
                    failed = !saved
                    status = store.read()
                    if (saved) generation++
                }) { Text(if (usePrimary) "Use Primary NovaOS Layout" else "Copy Current Layout to Both") }
            },
            dismissButton = { TextButton(onClick = ::cancel) { Text("Cancel") } },
        )
    } else if (failed) {
        AlertDialog(
            onDismissRequest = { failed = false },
            title = { Text("Workspace layout could not be saved") },
            text = { Text("The current layouts are still available. You can retry the pending copy.") },
            confirmButton = { TextButton(onClick = { failed = false; generation++ }) { Text("Retry") } },
            dismissButton = { TextButton(onClick = { failed = false }) { Text("Close") } },
        )
    }

    return if (status.mode == WorkspaceLayoutMode.MIRROR) {
        val state = remember(launcher, generation) { launcher.read() }
        val widgetPage = lastWidgetPage()
        remember(state, wide, data.profile, widgetPage) {
            data.profile.withLauncherProjection(state, application.packageName, wide, widgetPage)
        }
    } else null
}
